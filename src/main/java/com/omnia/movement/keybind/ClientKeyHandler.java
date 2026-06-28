package com.omnia.movement.keybind;

import org.lwjgl.input.Keyboard;

import com.omnia.movement.MovementHandler;
import com.omnia.movement.OmniConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 客户端按键处理器。
 * 引用所有客户端专用 API（Minecraft、KeyBinding、ClientRegistry）。
 * 此类只能在客户端加载，不可在服务端使用。
 *
 * 通过 KeyBindings 代理类暴露给 MovementHandler。
 */
@SideOnly(Side.CLIENT)
public class ClientKeyHandler {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static KeyBinding slideKey;
    private static KeyBinding diveKey;

    private static boolean wasSlideDown = false;
    private static boolean wasDiveDown = false;
    private static boolean wasJumpDown = false;

    private static boolean slideCancelTriggered = false;

    /** 按键注册（在客户端 preInit 中调用） */
    public static void registerKeys() {
        slideKey = new KeyBinding("key.omni.slide", Keyboard.KEY_C, "key.categories.omnimovement");
        diveKey  = new KeyBinding("key.omni.dive",  Keyboard.KEY_LCONTROL, "key.categories.omnimovement");
        ClientRegistry.registerKeyBinding(slideKey);
        ClientRegistry.registerKeyBinding(diveKey);
    }

    /** 在客户端 tick 中处理按键输入 */
    public static void handleKeyInput() {
        if (slideKey == null || diveKey == null) return;
        if (mc.thePlayer == null) return;
        if (mc.currentScreen != null) return;

        boolean slideDown = slideKey.isKeyDown();
        boolean diveDown  = diveKey.isKeyDown();
        boolean jumpDown  = mc.gameSettings.keyBindJump.isKeyDown();

        // ---- 滑铲按下瞬间（按一次即滑，松开不影响） ----
        if (slideDown && !wasSlideDown) {
            if (MovementHandler.getLocalState() != null &&
                MovementHandler.getLocalState().isSliding()) {
                // 滑行中再按一次 → 取消
                slideCancelTriggered = true;
            } else {
                MovementHandler.tryStartSlide(mc.thePlayer);
            }
        }
        // 不再有「松开滑铲键 → 结束滑行」的逻辑。
        // 滑铲持续到：超时 / 撞墙 / 上坡急停 / 手动取消（再按一次） / 跳跃

        // ---- 飞扑按下瞬间 ----
        if (diveDown && !wasDiveDown) {
            MovementHandler.tryStartDive(mc.thePlayer);
        }

        // ---- 跳跃按下瞬间 → 滑铲取消 ----
        if (jumpDown && !wasJumpDown) {
            slideCancelTriggered = true;
        }

        // ---- 更新上一帧按键状态 ----
        wasSlideDown = slideDown;
        wasDiveDown = diveDown;
        wasJumpDown = jumpDown;
    }

    // ---- 供外部查询的静态方法 ----

    public static boolean consumeSlideCancel() {
        if (slideCancelTriggered) {
            slideCancelTriggered = false;
            return true;
        }
        return false;
    }

    public static boolean isSlideKeyDown() {
        return slideKey != null && slideKey.isKeyDown();
    }

    public static boolean isDiveKeyDown() {
        return diveKey != null && diveKey.isKeyDown();
    }

    public static boolean isJumpKeyDown() {
        return mc.gameSettings.keyBindJump.isKeyDown();
    }

    /** 触发滑铲（由 KeyBindings 代理调用） */
    public static void tryStartSlide() {
        if (mc.thePlayer == null) return;
        if (MovementHandler.getLocalState() != null &&
            MovementHandler.getLocalState().isSliding()) {
            slideCancelTriggered = true;
        } else {
            MovementHandler.tryStartSlide(mc.thePlayer);
        }
    }

    /** 触发飞扑 */
    public static void tryStartDive() {
        if (mc.thePlayer != null) {
            MovementHandler.tryStartDive(mc.thePlayer);
        }
    }

    /** 退出滑铲 */
    public static void exitSlide(boolean canceled) {
        if (mc.thePlayer != null) {
            MovementHandler.exitSlide(mc.thePlayer, canceled);
        }
    }
}
