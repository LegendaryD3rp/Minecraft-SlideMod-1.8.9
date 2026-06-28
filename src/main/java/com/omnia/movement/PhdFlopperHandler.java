package com.omnia.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * PhD Flopper 效果处理器。
 *
 * 效果：
 *   1. 免疫爆炸伤害（任意来源）
 *   2. 免疫摔落伤害
 *   3. 高处飞扑落地时产生爆炸（在 MovementHandler 中触发）
 */
public class PhdFlopperHandler {

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    // ── 伤害防护（服务端/客户端通用） ──

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!OmniConfig.phdFlopperEnabled) return;
        if (event.entity == null) return;
        if (!(event.entity instanceof net.minecraft.entity.player.EntityPlayer)) return;

        net.minecraft.entity.player.EntityPlayer player =
            (net.minecraft.entity.player.EntityPlayer) event.entity;

        if (PotionPhdFlopper.instance == null) return;
        if (!player.isPotionActive(PotionPhdFlopper.instance)) return;

        // 免疫爆炸伤害
        if (event.source.isExplosion()) {
            event.setCanceled(true);
            return;
        }

        // 免疫摔落伤害
        if (event.source == DamageSource.fall) {
            event.setCanceled(true);
        }
    }

    // ── 落地爆炸触发（仅客户端视觉效果） ──
    // 实际爆炸点在 MovementHandler.updateDiveClient 中触发，
    // 此处不再单独监听。
    //
    // 这里保留爆炸辅助方法，供 MovementHandler 调用。

    /** 在指定位置生成爆炸效果（客户端）。由 MovementHandler 在飞扑落地时调用。 */
    @SideOnly(Side.CLIENT)
    public static void spawnDiveExplosion(double x, double y, double z, float strength) {
        net.minecraft.world.World world = mc().theWorld;
        if (world == null) return;
        // 客户端爆炸（不破坏方块，仅视觉效果和击退）
        world.createExplosion(
            mc().thePlayer,
            x, y, z,
            strength,
            false  // 不破坏方块
        );
    }
}
