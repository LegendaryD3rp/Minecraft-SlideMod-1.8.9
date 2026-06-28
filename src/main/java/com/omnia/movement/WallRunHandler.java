package com.omnia.movement;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 滑墙（Titanfall 风格）+ 二段跳 + 视角倾斜。
 *
 * 滑墙：空中且朝墙面前进时，自动吸附墙面并沿墙跑动。
 *       重力被抵消。按跳跃 → 蹬墙跳。
 *
 * 视角倾斜：滑墙时朝墙面反方向倾斜约 15°（可配置）。
 */
@SideOnly(Side.CLIENT)
public class WallRunHandler {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── 滑墙状态 ──
    public static boolean isWallRunning = false;
    public static int wallDirX = 0;       // 墙的法线方向
    public static int wallDirZ = 0;
    private static int wallRunTicks = 0;

    // ── 当前相机倾斜度（用于 CameraSetup 事件） ──
    public static float cameraRoll = 0.0F;
    private static float targetCameraRoll = 0.0F;

    // ── 二段跳状态 ──
    public static boolean hasDoubleJumped = false;

    // ── 内部追踪 ──
    private static boolean wasOnGround = true;
    private static boolean wasJumping = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!OmniConfig.wallRunEnabled) return;

        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.isDead) return;
        if (player.capabilities.isCreativeMode || player.capabilities.isFlying) {
            reset();
            return;
        }

        // 与滑铲/飞扑互斥
        PlayerStateManager state = MovementHandler.getOrCreate(player);
        if (state.isSliding() || state.isDiving()) {
            if (isWallRunning) stopWallRun();
            wasOnGround = player.onGround;
            wasJumping = player.movementInput.jump;
            updateCameraRoll();
            return;
        }

        boolean onGround = player.onGround;
        boolean jumpPressed = player.movementInput.jump;

        // ── 落地重置 ──
        if (onGround) {
            hasDoubleJumped = false;
            wallRunTicks = 0;
            if (isWallRunning) stopWallRun();
        }

        // ── 二段跳 ──
        if (!onGround && jumpPressed && !wasJumping) {
            // 蹬墙跳: 从滑墙状态跳起
            if (isWallRunning) {
                wallJump(player);
                stopWallRun();
            }
            // 空中二段跳: 离开地面后第一次跳跃
            else if (!hasDoubleJumped && !wasOnGround) {
                doubleJump(player);
            }
        }

        // ── 滑墙检测 ──
        if (!onGround && !isWallRunning && OmniConfig.wallRunEnabled) {
            checkForWall(player);
        }

        // ── 滑墙持续处理 ──
        if (isWallRunning) {
            wallRunTicks++;

            // 超时 / 玩家按 S 减速停墙
            if (wallRunTicks > OmniConfig.wallRunMaxTicks) {
                stopWallRun();
                updateCameraRoll();
                wasOnGround = onGround;
                wasJumping = jumpPressed;
                return;
            }

            // 维持高度（抵消重力）
            if (!player.isCollidedHorizontally) {
                // 在墙面上，减缓下落
                if (player.motionY < 0) {
                    player.motionY *= OmniConfig.wallGravityReduction;
                }
            }

            // 沿墙水平滑行
            double speed = OmniConfig.wallRunSpeed;
            double forward = player.movementInput.moveForward;
            double strafe = player.movementInput.moveStrafe;

            // 计算沿墙方向（垂直于法线方向）
            int alongX, alongZ;
            if (wallDirZ != 0) {
                alongX = 1;
                alongZ = 0;
            } else {
                alongX = 0;
                alongZ = 1;
            }

            // 根据移动输入调整方向
            if (forward > 0 || strafe != 0) {
                // 正向移动：沿 wallDirX,wallDirZ 的垂直方向
                int dirX = alongX;
                int dirZ = alongZ;
                if (forward < 0) { dirX = -dirX; dirZ = -dirZ; }
                if (strafe > 0) { dirX = -dirX; dirZ = -dirZ; } // 微调

                player.motionX = dirX * speed;
                player.motionZ = dirZ * speed;
            }

            // 粒子效果
            spawnWallRunParticles(player);

            // 更新视角倾斜目标
            // 墙在 wallDirX/wallDirZ 方向，相机朝反方向倾斜
            if (wallDirX != 0) {
                targetCameraRoll = (float)(wallDirX * OmniConfig.wallRunCameraTilt);
            } else if (wallDirZ != 0) {
                targetCameraRoll = (float)(wallDirZ * OmniConfig.wallRunCameraTilt);
            }
        } else {
            // 不在滑墙 → 倾斜归零
            targetCameraRoll = 0.0F;
        }

        updateCameraRoll();

        wasOnGround = onGround;
        wasJumping = jumpPressed;
    }

    /** 平滑过渡相机倾斜 */
    private void updateCameraRoll() {
        float diff = targetCameraRoll - cameraRoll;
        if (Math.abs(diff) < 0.1F) {
            cameraRoll = targetCameraRoll;
        } else {
            float step = (float) OmniConfig.wallRunTiltSmoothSpeed * 0.05F; // 每 tick 步进
            cameraRoll += Math.signum(diff) * Math.min(Math.abs(diff), step);
        }
    }

    /** 检测前方是否有墙 */
    private void checkForWall(EntityPlayerSP player) {
        if (!player.isCollidedHorizontally) return;

        // 玩家必须朝墙面前进
        double moveX = player.motionX;
        double moveZ = player.motionZ;
        double speed = Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (speed < 0.05) return;

        // 检查四个方向：哪个方向有实心方块
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(0, 0, 0);
        try {
            double px = player.posX;
            double py = player.posY + 1.0;  // 胸口高度
            double pz = player.posZ;

            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
            for (int[] d : dirs) {
                pos.set(
                    (int)(px + d[0] * 0.8),
                    (int)py,
                    (int)(pz + d[1] * 0.8)
                );
                IBlockState state = player.worldObj.getBlockState(pos);
                Block block = state.getBlock();
                if (block != Blocks.air && block.isCollidable()) {
                    // 检查玩家移动方向是否朝向这面墙
                    double dot = moveX * d[0] + moveZ * d[1];
                    if (dot > 0) {
                        // 开始滑墙
                        isWallRunning = true;
                        wallDirX = d[0];
                        wallDirZ = d[1];
                        wallRunTicks = 0;

                        // 重置坠落距离
                        player.fallDistance = 0;

                        // 登墙瞬间抵消下落
                        player.motionY = 0.1;
                        return;
                    }
                }
            }
        } finally {
            // MutableBlockPos 无需释放
        }
    }

    /** 蹬墙跳 */
    private void wallJump(EntityPlayerSP player) {
        // 朝墙面反方向跳
        player.motionX = -wallDirX * OmniConfig.wallJumpHorizontalStrength;
        player.motionZ = -wallDirZ * OmniConfig.wallJumpHorizontalStrength;
        player.motionY = OmniConfig.wallJumpVerticalStrength;
        hasDoubleJumped = true;
    }

    /** 二段跳 */
    private void doubleJump(EntityPlayerSP player) {
        player.motionY = 0.6;  // 跳跃高度
        hasDoubleJumped = true;
    }

    /** 停止滑墙 */
    private void stopWallRun() {
        if (isWallRunning) {
            isWallRunning = false;
            wallDirX = 0;
            wallDirZ = 0;
            wallRunTicks = 0;
        }
    }

    /** 粒子效果 */
    private void spawnWallRunParticles(EntityPlayerSP player) {
        try {
            double px = player.posX;
            double py = player.posY + 0.5;
            double pz = player.posZ;

            // 在墙面位置生成粒子
            double particleX = px + wallDirX * 0.5;
            double particleY = py;
            double particleZ = pz + wallDirZ * 0.5;

            for (int i = 0; i < 2; i++) {
                player.worldObj.spawnParticle(
                    EnumParticleTypes.BLOCK_CRACK,
                    particleX + (Math.random() - 0.5) * 0.3,
                    particleY + (Math.random() - 0.5) * 0.3,
                    particleZ + (Math.random() - 0.5) * 0.3,
                    0, 0, 0,
                    Block.getIdFromBlock(Blocks.stone)
                );
            }
        } catch (Exception ignored) { }
    }

    /** 重置所有状态 */
    private void reset() {
        isWallRunning = false;
        hasDoubleJumped = false;
        wallRunTicks = 0;
        wallDirX = 0;
        wallDirZ = 0;
        cameraRoll = 0.0F;
        targetCameraRoll = 0.0F;
    }
}
