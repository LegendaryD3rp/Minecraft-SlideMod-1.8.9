package com.slide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Method;

/**
 * 滑铲 (Z) + 飞扑 (X) + 全向机动 核心逻辑。
 *
 * 按键：
 *   Z → 地面滑铲
 *   X → 地面/空中飞扑
 *
 * 状态：
 *   isSliding  — 贴地滑行
 *   isDiving   — 飞扑（地面起跳或空中）
 *   二者互斥，不可同时为 true。
 */
public class SlideHandler {

    // ── 跨线程状态 ──
    public static volatile boolean isSliding = false;
    public static volatile boolean isDiving = false;
    public static volatile int slideTicks = 0;
    public static volatile int diveTicks = 0;
    public static int cooldownTicks = 0;
    public static int landDelayTicks = 0;  // 落地恢复延迟

    // ── 当前移动方向（单位向量，全向用） ──
    public static volatile double moveDirX = 0;
    public static volatile double moveDirZ = 0;

    // ── 按键 ──
    @SideOnly(Side.CLIENT)
    private static KeyBinding slideKey;
    @SideOnly(Side.CLIENT)
    private static KeyBinding diveKey;

    @SideOnly(Side.CLIENT)
    public static void registerKeyBindings() {
        slideKey = new KeyBinding(
            SlideMod.config != null ? SlideMod.config.keyBindingName : "key.slide",
            SlideMod.config != null ? SlideMod.config.keyBindingDefault : Keyboard.KEY_Z,
            "Slide Mod"
        );
        diveKey = new KeyBinding(
            SlideMod.config != null ? SlideMod.config.diveKeyName : "key.dive",
            SlideMod.config != null ? SlideMod.config.diveKeyDefault : Keyboard.KEY_X,
            "Slide Mod"
        );
        ClientRegistry.registerKeyBinding(slideKey);
        ClientRegistry.registerKeyBinding(diveKey);
    }

    // ═══════════════════════════════════════
    //  全向移动方向计算（基于 WASD 输入）
    // ═══════════════════════════════════════
    @SideOnly(Side.CLIENT)
    private static void computeMoveDirection(EntityPlayerSP player) {
        float forward = player.movementInput.moveForward;
        float strafe = player.movementInput.moveStrafe;

        if (forward == 0 && strafe == 0) {
            float yawRad = player.rotationYaw * 0.017453292F;
            moveDirX = -MathHelper.sin(yawRad);
            moveDirZ = MathHelper.cos(yawRad);
            return;
        }

        float len = MathHelper.sqrt_float(forward * forward + strafe * strafe);
        forward /= len;
        strafe /= len;

        float sinYaw = MathHelper.sin(-player.rotationYaw * 0.017453292F);
        float cosYaw = MathHelper.cos(-player.rotationYaw * 0.017453292F);

        double mx = strafe * cosYaw - forward * sinYaw;
        double mz = forward * cosYaw + strafe * sinYaw;

        double mLen = Math.sqrt(mx * mx + mz * mz);
        if (mLen > 0.001) {
            moveDirX = mx / mLen;
            moveDirZ = mz / mLen;
        } else {
            float yawRad = player.rotationYaw * 0.017453292F;
            moveDirX = -MathHelper.sin(yawRad);
            moveDirZ = MathHelper.cos(yawRad);
        }
    }

    /** 根据配置返回方向 (moveDirX, moveDirZ) 或 rotationYaw 方向 */
    @SideOnly(Side.CLIENT)
    private static double[] getMoveDir(EntityPlayerSP player) {
        if (SlideMod.config.omniDirectional) {
            return new double[]{moveDirX, moveDirZ};
        }
        float yawRad = player.rotationYaw * 0.017453292F;
        return new double[]{-MathHelper.sin(yawRad), MathHelper.cos(yawRad)};
    }

    // ══════════════════════════════════════
    //  客户端：按键检测 + 状态管理 + 全向移动
    // ══════════════════════════════════════
    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != Phase.START) return;
        if (!SlideMod.config.enabled) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (mc.isGamePaused()) return;

        EntityPlayerSP player = mc.thePlayer;

        // 冷却递减（双键共用冷却）
        if (cooldownTicks > 0) cooldownTicks--;

        // 计算全向移动方向
        computeMoveDirection(player);

        // ── 正在滑铲 ──
        if (isSliding) {
            slideTicks--;

            if (!slideKey.isKeyDown() || slideTicks <= 0 || player.isInWater() || player.isOnLadder()) {
                stopSlide(player);
                return;
            }

            // 全向滑铲维持
            if (player.onGround) {
                double[] dir = getMoveDir(player);
                float speed = player.isSprinting()
                    ? SlideMod.config.slideSpeedMultiplier * 0.12F
                    : SlideMod.config.slideSpeedMultiplier * 0.08F;
                player.motionX = dir[0] * speed;
                player.motionZ = dir[1] * speed;
            }
            return;
        }

        // ── 正在飞扑 ──
        if (isDiving) {
            diveTicks--;

            // 落地恢复倒计时
            if (player.onGround && landDelayTicks > 0) {
                landDelayTicks--;
                if (landDelayTicks <= 0) {
                    stopDive(player);
                }
            }

            if (diveTicks <= 0 || player.isInWater() || player.isCollidedHorizontally) {
                if (player.onGround) {
                    stopDive(player);
                } else {
                    diveTicks = 0;
                }
                return;
            }

            // 全向飞扑维持
            if (!player.onGround) {
                double[] dir = getMoveDir(player);
                float speed = SlideMod.config.diveForwardBoost * 0.12F;
                player.motionX = dir[0] * speed;
                player.motionZ = dir[1] * speed;
            }
            return;
        }

        // ── 启动判断 ──
        if (cooldownTicks > 0) return;

        // 滑铲 (Z) — 仅地面
        if (slideKey.isKeyDown() && player.onGround
            && !player.isInWater() && !player.isOnLadder()) {
            startSlide(player);
            return;
        }

        // 飞扑 (X) — 地面或空中均可
        if (diveKey.isKeyDown() && !player.isInWater() && !player.isOnLadder()
            && SlideMod.config.diveEnabled) {
            startDive(player);
            return;
        }
    }

    // ══════════════════════════════════════
    //  服务端：仅 hitbox + 摔落免疫
    // ══════════════════════════════════════
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != Phase.START) return;
        if (!SlideMod.config.enabled) return;
        if (!isSliding && !isDiving) return;

        EntityPlayer player = event.player;

        if (!player.worldObj.isRemote && player instanceof EntityPlayerMP) {
            EntityPlayerMP mp = (EntityPlayerMP) player;

            if (isSliding) {
                setEntitySize(mp, 0.6F, SlideMod.config.slideHitboxHeight);
                mp.eyeHeight = SlideMod.config.slideHitboxHeight * 0.85F;
            }

            if (isDiving) {
                setEntitySize(mp, 0.6F, SlideMod.config.diveHitboxHeight);
                mp.eyeHeight = SlideMod.config.diveHitboxHeight * 0.85F;
            }
        }

        if (player.worldObj.isRemote && player instanceof EntityPlayerSP) {
            EntityPlayerSP sp = (EntityPlayerSP) player;
            if (isSliding) {
                sp.eyeHeight = SlideMod.config.slideHitboxHeight * 0.85F;
            } else if (isDiving) {
                sp.eyeHeight = SlideMod.config.diveHitboxHeight * 0.85F;
            }
        }
    }

    // ══════════════════════════════════════
    //  摔落免疫
    // ══════════════════════════════════════
    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (!SlideMod.config.enabled) return;
        if (!(event.entity instanceof EntityPlayer)) return;
        if (!isSliding && !isDiving) return;
        if (SlideMod.config.preventFallDamage) {
            event.setCanceled(true);
        }
    }

    // ══════════════════════════════════════
    //  启动 / 停止
    // ══════════════════════════════════════
    @SideOnly(Side.CLIENT)
    private void startSlide(EntityPlayerSP player) {
        isSliding = true;
        isDiving = false;
        slideTicks = SlideMod.config.slideDurationTicks;
        landDelayTicks = 0;

        double[] dir = getMoveDir(player);
        float boost = SlideMod.config.initialBoost * 0.12F;
        player.motionX = dir[0] * boost;
        player.motionZ = dir[1] * boost;

        setEntitySize(player, 0.6F, SlideMod.config.slideHitboxHeight);
        player.eyeHeight = SlideMod.config.slideHitboxHeight * 0.85F;
    }

    @SideOnly(Side.CLIENT)
    private void startDive(EntityPlayerSP player) {
        isDiving = true;
        isSliding = false;
        diveTicks = 40;
        landDelayTicks = 0;

        double[] dir = getMoveDir(player);
        float boost = SlideMod.config.diveForwardBoost * 0.12F;
        player.motionX = dir[0] * boost;
        player.motionZ = dir[1] * boost;
        player.motionY = SlideMod.config.diveUpwardBoost * 0.3F;

        setEntitySize(player, 0.6F, SlideMod.config.diveHitboxHeight);
        player.eyeHeight = SlideMod.config.diveHitboxHeight * 0.85F;
    }

    @SideOnly(Side.CLIENT)
    private void stopSlide(EntityPlayerSP player) {
        isSliding = false;
        slideTicks = 0;
        cooldownTicks = SlideMod.config.slideCooldownTicks;
        resetEntitySize(player);
    }

    @SideOnly(Side.CLIENT)
    private void stopDive(EntityPlayerSP player) {
        isDiving = false;
        diveTicks = 0;
        cooldownTicks = SlideMod.config.slideCooldownTicks;
        resetEntitySize(player);
    }

    // ══════════════════════════════════════
    //  Hitbox 反射
    // ══════════════════════════════════════
    private static Method setSizeMethod;

    static {
        try {
            setSizeMethod = Entity.class.getDeclaredMethod("setSize", float.class, float.class);
            setSizeMethod.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setEntitySize(Entity entity, float w, float h) {
        try {
            if (setSizeMethod != null) {
                setSizeMethod.invoke(entity, w, h);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SideOnly(Side.CLIENT)
    private void resetEntitySize(EntityPlayerSP player) {
        setEntitySize(player, 0.6F, 1.8F);
        player.eyeHeight = player.getDefaultEyeHeight();
    }
}
