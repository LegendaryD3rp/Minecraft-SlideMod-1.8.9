package com.slide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * 抓钩事件处理器。
 * 处理抓钩收回快捷键、玩家拉近检测等。
 */
@SideOnly(Side.CLIENT)
public class GrapplingHookHandler {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // 收回键（R）
    public static KeyBinding retractKey;

    public static void registerKeyBindings() {
        retractKey = new KeyBinding(
            "key.retract_hook",
            Keyboard.KEY_R,
            "key.categories.slidemod"
        );
        ClientRegistry.registerKeyBinding(retractKey);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        // 按下收回键
        if (retractKey != null && retractKey.isPressed()) {
            EntityGrapplingHook hook = EntityGrapplingHook.getActiveHook(player);
            if (hook != null) {
                if (hook.getState() == EntityGrapplingHook.State.ATTACHED) {
                    hook.retract();
                } else {
                    hook.setDead();
                }
            }
        }

        // 如果玩家在以抓钩拉动时按住跳跃键，松钩 + 跳
        if (mc.gameSettings.keyBindJump.isKeyDown()) {
            EntityGrapplingHook hook = EntityGrapplingHook.getActiveHook(player);
            if (hook != null && hook.getState() == EntityGrapplingHook.State.ATTACHED) {
                // 给一个向上的跳跃 boost
                player.motionY += 0.42;
                hook.setDead();
            }
        }
    }
}
