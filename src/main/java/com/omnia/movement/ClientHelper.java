package com.omnia.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 客户端辅助类。
 * 所有引用 Minecraft.getMinecraft() 的方法集中在此，
 * 确保服务端不会接触客户端专用类。
 * 只在 @SideOnly 代码路径中使用。
 */
@SideOnly(Side.CLIENT)
public class ClientHelper {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /** 获取本地玩家状态管理器 */
    public static PlayerStateManager getLocalState() {
        if (mc.thePlayer == null) return null;
        return MovementHandler.getOrCreate(mc.thePlayer);
    }

    /** 判断给定玩家是否为本地玩家 */
    public static boolean isLocalPlayer(EntityPlayer player) {
        return player == mc.thePlayer;
    }

    /** 跳跃键是否按下 */
    public static boolean isJumpPressed() {
        return mc.gameSettings.keyBindJump.isKeyDown();
    }
}
