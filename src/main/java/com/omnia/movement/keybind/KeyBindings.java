package com.omnia.movement.keybind;

import com.omnia.movement.MovementHandler;

/**
 * 按键状态提供器。
 *
 * 为了在服务端不加载客户端专用类（Minecraft、ClientRegistry、KeyBinding），
 * 本类采用延迟初始化 + 服务端安全模式：
 *
 * - 服务端：isClient=false，所有键状态固定返回 false
 * - 客户端：通过 ClientKeyHandler 实际处理按键输入
 *
 * MovementHandler 中所有 @SideOnly(Side.CLIENT) 的方法
 * 直接调用 ClientKeyHandler 的静态方法，由它处理所有真实逻辑。
 */
public class KeyBindings {

    /** 当前是否为客户端环境 */
    private static final boolean IS_CLIENT;

    static {
        boolean client = false;
        try {
            client = net.minecraftforge.fml.common.FMLCommonHandler.instance()
                    .getSide().isClient();
        } catch (Exception ignored) {}
        IS_CLIENT = client;
    }

    // ---- 服务端空桩 ----

    /** 服务端调用时总是返回 false。客户端调用走 ClientKeyHandler。 */
    public static boolean consumeSlideCancel() {
        if (!IS_CLIENT) return false;
        return ClientKeyHandler.consumeSlideCancel();
    }

    public static boolean isSlideKeyDown() {
        if (!IS_CLIENT) return false;
        return ClientKeyHandler.isSlideKeyDown();
    }

    public static boolean isDiveKeyDown() {
        if (!IS_CLIENT) return false;
        return ClientKeyHandler.isDiveKeyDown();
    }

    public static boolean isJumpKeyDown() {
        if (!IS_CLIENT) return false;
        return ClientKeyHandler.isJumpKeyDown();
    }

    /**
     * 触发滑铲（客户端环境下由 ClientKeyHandler 调用）。
     */
    public static void tryStartSlide() {
        if (!IS_CLIENT) return;
        ClientKeyHandler.tryStartSlide();
    }

    /**
     * 触发飞扑（客户端环境下由 ClientKeyHandler 调用）。
     */
    public static void tryStartDive() {
        if (!IS_CLIENT) return;
        ClientKeyHandler.tryStartDive();
    }

    /**
     * 退出滑铲。
     */
    public static void exitSlide(boolean canceled) {
        if (!IS_CLIENT) return;
        ClientKeyHandler.exitSlide(canceled);
    }
}
