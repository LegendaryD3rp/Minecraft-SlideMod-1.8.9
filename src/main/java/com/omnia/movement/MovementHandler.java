package com.omnia.movement;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.omnia.movement.keybind.KeyBindings;
import com.omnia.movement.network.OmniNetwork;
import com.omnia.movement.network.PacketSlideState;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 运动处理器。负责：
 *  客户端：状态机、运动预测、碰撞箱、发包
 *  服务端：接收状态包 → 修改 movementSpeed 属性（方案乙）
 *         → 广播给其他客户端
 *
 * 使用 WeakHashMap 按 EntityPlayer 存储状态管理器，
 * 避免内存泄漏。
 */
public class MovementHandler {

    // ---------- 状态存储 ----------

    private static final Map<EntityPlayer, PlayerStateManager> STATE_MAP = new WeakHashMap<>();

    private static final UUID SLIDE_SPEED_MOD_UUID  = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef0123456701");
    private static final UUID DIVE_SPEED_MOD_UUID   = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef0123456702");
    private static final AttributeModifier SLIDE_SPEED_MOD =
            new AttributeModifier(SLIDE_SPEED_MOD_UUID, "slideSpeedBoost",
                    OmniConfig.slideSpeedMultiplier - 1.0, 0); // 加法叠加
    private static final AttributeModifier DIVE_SPEED_MOD =
            new AttributeModifier(DIVE_SPEED_MOD_UUID, "diveSpeedBoost",
                    OmniConfig.diveSpeedMultiplier - 1.0, 0);

    // ---------- 客户端状态 ----------

    // ========== 公开 API ==========

    public static PlayerStateManager getOrCreate(EntityPlayer player) {
        return STATE_MAP.computeIfAbsent(player, k -> new PlayerStateManager());
    }

    /**
     * 获取本地玩家状态管理器。
     * 仅在客户端有效，服务端调用返回 null。
     * 内部使用 ClientHelper 以避免直接引用 Minecraft.getMinecraft()，
     * 确保服务端可以安全加载此类。
     */
    public static PlayerStateManager getLocalState() {
        if (net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .getEffectiveSide().isServer()) {
            return null;
        }
        return getLocalStateClient();
    }

    @SideOnly(Side.CLIENT)
    private static PlayerStateManager getLocalStateClient() {
        return ClientHelper.getLocalState();
    }

    // ========== 滑铲 ==========

    /**
     * 尝试开始滑铲。
     * 调用者：KeyBindings.onKeyInput（客户端）
     */
    public static void tryStartSlide(EntityPlayer player) {
        if (player.worldObj.isRemote) {
            tryStartSlideClient(player);
        } else {
            tryStartSlideServer(player);
        }
    }

    @SideOnly(Side.CLIENT)
    private static void tryStartSlideClient(EntityPlayer player) {
        if (!player.onGround) return;
        if (!player.isSprinting()) return;

        PlayerStateManager state = getOrCreate(player);
        if (!state.canSlide()) return;

        // 进入滑铲
        state.setState(MovementState.SLIDING);
        state.lockDirectionFromLook(player);

        // 应用速度
        Vec3 look = player.getLookVec();
        double sprintSpeed = player.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue();
        double slideSpeed = sprintSpeed * OmniConfig.slideSpeedMultiplier;
        player.motionX = look.xCoord * slideSpeed;
        player.motionZ = look.zCoord * slideSpeed;

        // 调整碰撞箱
        adjustHitbox(player, MovementState.SLIDING);

        // 发包通知服务端
        sendStateToServer(player, MovementState.SLIDING);

        if (OmniConfig.debugSync) {
            System.out.println("[OmniMovement] CLIENT slide START");
        }
    }

    private static void tryStartSlideServer(EntityPlayer player) {
        // 服务端不会自行触发滑铲，必须由客户端发包驱动
        // 此方法仅为完整性保留
    }

    /**
     * 退出滑铲。
     * @param canceled 是否为取消（滑铲取消动作）
     */
    public static void exitSlide(EntityPlayer player, boolean canceled) {
        if (player.worldObj.isRemote) {
            exitSlideClient(player, canceled);
        } else {
            exitSlideServer(player, canceled);
        }
    }

    @SideOnly(Side.CLIENT)
    private static void exitSlideClient(EntityPlayer player, boolean canceled) {
        PlayerStateManager state = getOrCreate(player);
        if (!state.isSliding()) return;

        state.setState(MovementState.NONE);
        state.unlockDirection();

        // 速度保留
        if (canceled) {
            player.motionX *= OmniConfig.slideCancelSpeedRetention;
            player.motionZ *= OmniConfig.slideCancelSpeedRetention;
        }

        // 恢复碰撞箱
        restoreHitbox(player);

        // 发包通知服务端
        sendStateToServer(player, MovementState.NONE);

        if (OmniConfig.debugSync) {
            System.out.println("[OmniMovement] CLIENT slide END" + (canceled ? "(cancel)" : ""));
        }
    }

    private static void exitSlideServer(EntityPlayer player, boolean canceled) {
        PlayerStateManager state = getOrCreate(player);
        if (!state.isSliding()) return;

        state.setState(MovementState.NONE);
        state.unlockDirection();
        restoreHitbox(player);
        removeSpeedModifier(player);
    }

    // ========== 飞扑 ==========

    public static void tryStartDive(EntityPlayer player) {
        if (player.worldObj.isRemote) {
            tryStartDiveClient(player);
        } else {
            tryStartDiveServer(player);
        }
    }

    @SideOnly(Side.CLIENT)
    private static void tryStartDiveClient(EntityPlayer player) {
        if (!player.onGround) return;
        if (!player.isSprinting()) return;

        PlayerStateManager state = getOrCreate(player);
        if (!state.canDive()) return;

        // 进入飞扑
        state.setState(MovementState.DIVING);
        state.lockDirectionFromLook(player);

        // 爆发速度
        Vec3 look = player.getLookVec();
        double sprintSpeed = player.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue();
        double diveSpeed = sprintSpeed * OmniConfig.diveSpeedMultiplier;
        player.motionX = look.xCoord * diveSpeed;
        player.motionZ = look.zCoord * diveSpeed;
        player.motionY = OmniConfig.diveVerticalVelocity;

        // 调整碰撞箱
        adjustHitbox(player, MovementState.DIVING);

        // 发包
        sendStateToServer(player, MovementState.DIVING);

        if (OmniConfig.debugSync) {
            System.out.println("[OmniMovement] CLIENT dive START");
        }
    }

    private static void tryStartDiveServer(EntityPlayer player) {
        // 服务端被动接收
    }

    // ========== 核心 Tick 循环 ==========

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!(event.entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.entity;

        if (player.worldObj.isRemote) {
            handleClientTick(player);
        } else {
            handleServerTick(player);
        }
    }

    // ---------- 客户端 Tick ----------

    @SideOnly(Side.CLIENT)
    private void handleClientTick(EntityPlayer player) {
        if (!ClientHelper.isLocalPlayer(player)) return;

        PlayerStateManager state = getOrCreate(player);

        switch (state.getState()) {

            case SLIDING:
                updateSlideClient(player, state);
                break;

            case DIVING:
                updateDiveClient(player, state);
                break;

            case LANDING:
                updateLandingClient(player, state);
                break;

            default:
                break;
        }
    }

    @SideOnly(Side.CLIENT)
    private void updateSlideClient(EntityPlayer player, PlayerStateManager state) {
        state.tickTimer();

        // ---- 滑铲取消检测 ----
        if (KeyBindings.consumeSlideCancel()) {
            exitSlide(player, true);
            return;
        }

        // ---- 超时退出 ----
        if (state.getStateTimer() >= OmniConfig.slideDurationTicks) {
            exitSlide(player, false);
            return;
        }

        // ---- 撞墙急停 ----
        if (player.isCollidedHorizontally) {
            exitSlide(player, false);
            return;
        }

        // ---- 上坡急停 ----
        if (isAscendingSlope(player)) {
            exitSlide(player, false);
            return;
        }

        // ---- 运动 ----
        // 锁定方向
        if (state.isDirectionLocked()) {
            double speed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
            Vec3 dir = getLockedDirection(state);
            double newSpeed = speed * OmniConfig.slideFriction;
            player.motionX = dir.xCoord * newSpeed;
            player.motionZ = dir.zCoord * newSpeed;
        }

        // ---- 下坡加速 ----
        if (isDescendingSlope(player)) {
            player.motionX *= (1.0 + OmniConfig.downSlopeSpeedBoost);
            player.motionZ *= (1.0 + OmniConfig.downSlopeSpeedBoost);
            player.motionY -= OmniConfig.downSlopeVerticalBoost;
        }

        // ---- 防止坠落伤害（滑铲时摩擦减速，不会摔太狠） ----
        player.fallDistance = 0;

        // ---- 定时发包同步 ----
        if (state.getStateTimer() % 10 == 0) {
            sendStateToServer(player, MovementState.SLIDING);
        }
    }

    @SideOnly(Side.CLIENT)
    private void updateDiveClient(EntityPlayer player, PlayerStateManager state) {
        state.tickTimer();

        // ---- 锁定方向（空中不可转向） ----
        if (state.isDirectionLocked()) {
            double speed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
            Vec3 dir = getLockedDirection(state);
            double newSpeed = speed * OmniConfig.diveAirFriction;
            player.motionX = dir.xCoord * newSpeed;
            player.motionZ = dir.zCoord * newSpeed;
        }

        // ---- 触地 → 落地缓冲 ----
        if (player.onGround) {
            // PhD Flopper：飞扑落地爆炸
            if (OmniConfig.phdFlopperEnabled && PotionPhdFlopper.instance != null
                    && player.isPotionActive(PotionPhdFlopper.instance)) {
                float fallDist = player.fallDistance;
                float explosionStrength = (float) Math.min(
                    fallDist * OmniConfig.phdExplosionStrengthPerBlock,
                    OmniConfig.phdMaxExplosionStrength);
                if (explosionStrength > 0.5F) {
                    // 客户端爆炸效果
                    PhdFlopperHandler.spawnDiveExplosion(
                        player.posX, player.posY, player.posZ, explosionStrength);
                    // 对周围实体造成伤害（服务端 + 客户端）
                    player.worldObj.createExplosion(
                        player, player.posX, player.posY, player.posZ,
                        explosionStrength, OmniConfig.phdDestroyBlocks);
                }
            }
            state.setState(MovementState.LANDING);
            state.setLandingTimer(OmniConfig.diveLandingDuration);
            state.unlockDirection();

            // 防摔伤
            player.fallDistance = 0;

            // 减速
            player.motionX *= OmniConfig.landingSpeedReduction;
            player.motionZ *= OmniConfig.landingSpeedReduction;

            // 恢复碰撞箱为潜行高度（过渡）
            HitboxHelper.setSize(player, 0.6F, 1.5F);
            HitboxHelper.setEyeHeight(player, 1.35F);

            sendStateToServer(player, MovementState.LANDING);

            if (OmniConfig.debugSync) {
                System.out.println("[OmniMovement] CLIENT dive → landing");
            }
            return;
        }

        // ---- 碰墙提前结束 ----
        if (player.isCollidedHorizontally) {
            state.setState(MovementState.NONE);
            state.unlockDirection();
            restoreHitbox(player);
            sendStateToServer(player, MovementState.NONE);
            return;
        }

        // ---- 碰撞箱持续压低（防止空中碰撞恢复） ----
        adjustHitbox(player, MovementState.DIVING);

        // ---- 心跳包 ----
        if (state.getStateTimer() % 10 == 0) {
            sendStateToServer(player, MovementState.DIVING);
        }
    }

    @SideOnly(Side.CLIENT)
    private void updateLandingClient(EntityPlayer player, PlayerStateManager state) {
        state.decLandingTimer();

        // ---- 缓冲期限制 ----
        if (OmniConfig.landingBlockJump && mcHasJumpPressed()) {
            // 禁止跳跃——因为已经跳不起来了，但防止被地面再次弹起
        }
        if (OmniConfig.landingBlockSprint) {
            player.setSprinting(false);
        }
        if (OmniConfig.landingBlockSlide && isSlidePressed()) {
            // 不触发滑铲
        }
        if (OmniConfig.landingBlockDive && isDivePressed()) {
            // 不触发飞扑
        }

        // ---- 缓冲结束 ----
        if (state.getLandingTimer() <= 0) {
            state.setState(MovementState.NONE);
            restoreHitbox(player);
            sendStateToServer(player, MovementState.NONE);

            if (OmniConfig.debugSync) {
                System.out.println("[OmniMovement] CLIENT landing end");
            }
        }
    }

    // ---------- 服务端 Tick ----------

    private void handleServerTick(EntityPlayer player) {
        EntityPlayerMP mp = (EntityPlayerMP) player;
        PlayerStateManager state = getOrCreate(player);

        switch (state.getState()) {

            case SLIDING:
                // 速度修饰器
                applySpeedModifier(player, SLIDE_SPEED_MOD);
                // 碰撞箱
                adjustHitbox(player, MovementState.SLIDING);
                // 防摔
                player.fallDistance = 0;
                break;

            case DIVING:
                applySpeedModifier(player, DIVE_SPEED_MOD);
                adjustHitbox(player, MovementState.DIVING);
                if (player.onGround) {
                    // 客户端已处理触地逻辑，服务端只是同步
                    state.setState(MovementState.LANDING);
                    state.setLandingTimer(OmniConfig.diveLandingDuration);
                    state.unlockDirection();
                    player.fallDistance = 0;
                    removeSpeedModifier(player);
                }
                break;

            case LANDING:
                state.decLandingTimer();
                if (state.getLandingTimer() <= 0) {
                    state.setState(MovementState.NONE);
                    restoreHitbox(player);
                } else {
                    // 缓冲期用 1.5 高碰撞箱
                    HitboxHelper.setSize(player, 0.6F, 1.5F);
                    HitboxHelper.setEyeHeight(player, 1.35F);
                }
                player.fallDistance = 0;
                break;

            default:
                // 确保没有残留修饰器
                removeSpeedModifier(player);
                break;
        }
    }

    // ========== 辅助方法 ==========

    private static void applySpeedModifier(EntityPlayer player, AttributeModifier mod) {
        if (player.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
                .getModifier(mod.getID()) == null) {
            player.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
                    .applyModifier(mod);
        }
    }

    private static void removeSpeedModifier(EntityPlayer player) {
        player.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
                .removeModifier(SLIDE_SPEED_MOD);
        player.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
                .removeModifier(DIVE_SPEED_MOD);
    }

    /**
     * 根据运动状态调整碰撞箱和眼睛高度。
     */
    public static void adjustHitbox(EntityPlayer player, MovementState state) {
        switch (state) {
            case SLIDING:
                HitboxHelper.setSize(player, 0.6F, (float) OmniConfig.slideHitboxHeight);
                HitboxHelper.setEyeHeight(player, (float) OmniConfig.slideEyeHeight);
                break;
            case DIVING:
                HitboxHelper.setSize(player, 0.6F, (float) OmniConfig.diveHitboxHeight);
                HitboxHelper.setEyeHeight(player, (float) OmniConfig.diveEyeHeight);
                break;
            case LANDING:
                HitboxHelper.setSize(player, 0.6F, 1.5F);
                HitboxHelper.setEyeHeight(player, 1.35F);
                break;
            default:
                restoreHitbox(player);
                break;
        }
    }

    public static void restoreHitbox(EntityPlayer player) {
        HitboxHelper.restoreDefaultSize(player);
    }

    /**
     * 检测是否正在上坡（前方有比脚下高的方块）。
     */
    private static boolean isAscendingSlope(EntityPlayer player) {
        if (!player.onGround) return false;
        Vec3 look = player.getLookVec();
        double dx = look.xCoord * 0.5;
        double dz = look.zCoord * 0.5;
        BlockPos ahead = new BlockPos(
                MathHelper.floor_double(player.posX + dx),
                MathHelper.floor_double(player.posY + 0.5),
                MathHelper.floor_double(player.posZ + dz));
        BlockPos aheadAbove = ahead.up();
        return !player.worldObj.isAirBlock(ahead) ||
               player.worldObj.isBlockNormalCube(aheadAbove, false);
    }

    /**
     * 检测是否正在下坡（脚下前方没有方块）。
     */
    private static boolean isDescendingSlope(EntityPlayer player) {
        if (!player.onGround) return false;
        Vec3 look = player.getLookVec();
        double dx = look.xCoord * 0.5;
        double dz = look.zCoord * 0.5;
        BlockPos below = new BlockPos(
                MathHelper.floor_double(player.posX + dx),
                MathHelper.floor_double(player.posY - 0.1),
                MathHelper.floor_double(player.posZ + dz));
        return player.worldObj.isAirBlock(below);
    }

    private static Vec3 getLockedDirection(PlayerStateManager state) {
        double mx = state.getLockedMotionX();
        double mz = state.getLockedMotionZ();
        double len = Math.sqrt(mx * mx + mz * mz);
        if (len < 0.001) return new Vec3(0, 0, 0);
        return new Vec3(mx / len, 0, mz / len);
    }

    private static void sendStateToServer(EntityPlayer player, MovementState state) {
        if (player.worldObj.isRemote) {
            OmniNetwork.sendToServer(new PacketSlideState(state));
        }
    }

    // ---------- 客户端辅助 ----------

    @SideOnly(Side.CLIENT)
    private static boolean mcHasJumpPressed() {
        return ClientHelper.isJumpPressed();
    }

    @SideOnly(Side.CLIENT)
    private static boolean isSlidePressed() {
        return KeyBindings.isSlideKeyDown();
    }

    @SideOnly(Side.CLIENT)
    private static boolean isDivePressed() {
        return KeyBindings.isDiveKeyDown();
    }

    // ========== 网络回调（服务端接收包后调用） ==========

    /**
     * 服务端收到客户端状态包后调用。
     * 在 OmniNetwork.onPacketReceived 中被调用。
     */
    public static void onServerPacket(EntityPlayerMP player, PacketSlideState packet) {
        PlayerStateManager state = getOrCreate(player);
        MovementState newState = packet.getState();

        if (OmniConfig.debugSync) {
            System.out.println("[OmniMovement] SERVER packet from " + player.getName()
                    + " → " + newState);
        }

        switch (newState) {
            case SLIDING:
                state.setState(MovementState.SLIDING);
                state.lockDirectionFromLook(player);
                adjustHitbox(player, MovementState.SLIDING);
                player.fallDistance = 0;
                // 同步速度给服务端
                {
                    Vec3 look = player.getLookVec();
                    double speed = player.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue();
                    double slideSpeed = speed * OmniConfig.slideSpeedMultiplier;
                    player.motionX = look.xCoord * slideSpeed;
                    player.motionZ = look.zCoord * slideSpeed;
                }
                break;

            case DIVING:
                state.setState(MovementState.DIVING);
                state.lockDirectionFromLook(player);
                adjustHitbox(player, MovementState.DIVING);
                player.fallDistance = 0;
                {
                    Vec3 look = player.getLookVec();
                    double speed = player.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue();
                    double diveSpeed = speed * OmniConfig.diveSpeedMultiplier;
                    player.motionX = look.xCoord * diveSpeed;
                    player.motionZ = look.zCoord * diveSpeed;
                    player.motionY = OmniConfig.diveVerticalVelocity;
                }
                break;

            case LANDING:
                state.setState(MovementState.LANDING);
                state.setLandingTimer(OmniConfig.diveLandingDuration);
                state.unlockDirection();
                player.fallDistance = 0;
                break;

            case NONE:
            default:
                state.setState(MovementState.NONE);
                state.unlockDirection();
                restoreHitbox(player);
                removeSpeedModifier(player);
                break;
        }
    }
}
