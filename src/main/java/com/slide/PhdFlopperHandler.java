package com.slide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * PhD Flopper 效果处理器。
 *
 * 效果：
 *   1. 免疫爆炸伤害（任意来源）
 *   2. 免疫摔落伤害
 *   3. 高处飞扑落地时产生爆炸，高低差越大爆炸范围越广
 */
public class PhdFlopperHandler {

    // ── 懒初始化，避免 static 初始化时 Minecraft.getMinecraft() 返回 null ──
    private static Minecraft mc() { return Minecraft.getMinecraft(); }

    // 用于检测落地瞬间
    private static boolean wasOnGround = true;
    private static boolean wasDiving = false;

    // ── 伤害防护（服务端/客户端通用） ──

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!SlideMod.config.phdFlopperEnabled) return;
        if (event.entity == null) return;

        // 只对玩家生效
        if (!(event.entity instanceof net.minecraft.entity.player.EntityPlayer)) return;

        net.minecraft.entity.player.EntityPlayer player =
            (net.minecraft.entity.player.EntityPlayer) event.entity;

        if (!player.isPotionActive(PotionPhdFlopper.instance)) return;

        // 免疫爆炸伤害
        if (event.source.isExplosion()) {
            event.setCanceled(true);
            return;
        }

        // 免疫摔落伤害
        if (event.source == DamageSource.fall) {
            event.setCanceled(true);
            return;
        }
    }

    // ── 落地爆炸（客户端专用，仅视觉效果） ──

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != Phase.END) return;
        if (!SlideMod.config.phdFlopperEnabled) return;

        EntityPlayerSP player = mc().thePlayer;
        if (player == null || player.isDead) return;

        // 不在 PhD 效果中
        if (!player.isPotionActive(PotionPhdFlopper.instance)) {
            wasOnGround = player.onGround;
            return;
        }

        boolean onGround = player.onGround;
        boolean isDiving = SlideHandler.isDiving;

        // 检测落地瞬间：上一 tick 在空中 → 现在在地上
        if (!wasOnGround && onGround) {
            // 飞扑姿势检测
            boolean validDive = true;
            if (SlideMod.config.phdRequireDive) {
                // 玩家处于飞扑状态 或 视角朝下 > 45 度
                validDive = isDiving || player.rotationPitch > 45.0F;
            }

            // 掉落距离必须超过最小阈值
            if (validDive && player.fallDistance > SlideMod.config.phdMinFallDist) {
                // 计算爆炸半径（线性插值，限制在 min~max）
                float fallDist = Math.min(player.fallDistance, SlideMod.config.phdMaxFallDist);
                float ratio = (fallDist - SlideMod.config.phdMinFallDist)
                    / (SlideMod.config.phdMaxFallDist - SlideMod.config.phdMinFallDist);
                ratio = Math.max(0, Math.min(1, ratio));

                float radius = SlideMod.config.phdExplosionMinRadius
                    + ratio * (SlideMod.config.phdExplosionMaxRadius - SlideMod.config.phdExplosionMinRadius);

                // 创建爆炸（不破坏地形，但有爆炸粒子/音效/冲击）
                // createExplosion(entity, x, y, z, strength, isFlaming, isDestroyingTerrain)
                player.worldObj.newExplosion(
                    player,
                    player.posX, player.posY, player.posZ,
                    radius,
                    false,  // 不产生火焰
                    false   // 不破坏地形（纯视觉效果）
                );

                // 额外粒子增强
                for (int i = 0; i < 20; i++) {
                    double px = player.posX + (Math.random() - 0.5) * 3.0;
                    double py = player.posY + Math.random() * 0.5;
                    double pz = player.posZ + (Math.random() - 0.5) * 3.0;
                    double vx = (Math.random() - 0.5) * 1.5;
                    double vy = Math.abs(Math.random() * 1.5);
                    double vz = (Math.random() - 0.5) * 1.5;
                    player.worldObj.spawnParticle(
                        net.minecraft.util.EnumParticleTypes.EXPLOSION_NORMAL,
                        px, py, pz, vx, vy, vz
                    );
                }

                // 音效（createExplosion 自带 random.explode，但额外补一个大的）
                player.worldObj.playSound(
                    player.posX, player.posY, player.posZ,
                    "random.explode",
                    2.0F, 0.6F + ratio * 0.4F,
                    false
                );
            }
        }

        wasOnGround = onGround;
    }
}
