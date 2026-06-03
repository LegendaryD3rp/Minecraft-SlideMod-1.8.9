package com.slide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class SlideRenderer {

    private static final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!SlideMod.config.enabled) return;
        if (!SlideHandler.isSliding && !SlideHandler.isDiving) return;
        if (!SlideMod.config.showBody) return;
        if (mc.gameSettings.thirdPersonView != 0) return;

        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.isDead) return;

        Object raw = mc.getRenderManager().getEntityRenderObject(player);
        if (!(raw instanceof RenderPlayer)) return;
        RenderPlayer renderer = (RenderPlayer) raw;
        ModelPlayer model = renderer.getMainModel();
        float pt = event.partialTicks;

        // ── 摄像机位置 ──
        Entity view = mc.getRenderViewEntity();
        if (view == null) view = player;
        double renderX = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double renderY = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt
                + view.getEyeHeight();
        double renderZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * pt - renderX;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * pt - renderY;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * pt - renderZ;

        boolean diving = SlideHandler.isDiving;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(px, py, pz);

            // ── 计算朝向：全向模式用移动方向，否则用镜头方向 ──
            float yaw;
            if (SlideMod.config.omniDirectional) {
                double dx = SlideHandler.moveDirX;
                double dz = SlideHandler.moveDirZ;
                yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            } else {
                yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * pt;
            }
            GlStateManager.rotate(180.0F - yaw, 0, 1, 0);

            // ── 根据状态选择姿态 ──
            float pitchAngle = diving ? SlideMod.config.diveBodyPitch : SlideMod.config.slideBodyPitch;

            // 身体前倾（滑铲 65°, 飞扑 80°）
            GlStateManager.rotate(pitchAngle, 1, 0, 0);

            // 位置偏移（飞扑更靠前）
            if (diving) {
                GlStateManager.translate(0, -0.1F, 0.6F);
            } else {
                GlStateManager.translate(0, -0.2F, 0.3F);
            }

            float matrixScale = 0.9375F;
            GlStateManager.scale(matrixScale, -matrixScale, -matrixScale);
            float modelScale = 0.0625F;

            // ── 隐藏头部 ──
            model.bipedHead.isHidden = true;
            model.bipedHeadwear.isHidden = true;

            // ── 动画 ──
            model.swingProgress = player.getSwingProgress(pt);
            model.isRiding = false;
            model.isChild = player.isChild();
            model.isSneak = false;

            float limbSwing = player.limbSwing - player.limbSwingAmount * (1 - pt)
                    + player.limbSwingAmount * pt;
            float limbSwingAmount = Math.min(player.limbSwingAmount * pt, 1.0F);
            float headYaw = player.prevRotationYawHead
                    + (player.rotationYawHead - player.prevRotationYawHead) * pt - yaw;
            float headPitch = player.prevRotationPitch
                    + (player.rotationPitch - player.prevRotationPitch) * pt;

            model.setRotationAngles(limbSwing, limbSwingAmount,
                    player.ticksExisted + pt, headYaw, headPitch, modelScale, player);

            // ── 飞扑：手臂前伸，腿后伸 ──
            if (diving) {
                model.bipedRightArm.rotateAngleX = -2.0F;
                model.bipedLeftArm.rotateAngleX = -2.0F;
                model.bipedRightLeg.rotateAngleX = 1.2F;
                model.bipedLeftLeg.rotateAngleX = 1.2F;
            }

            // ── 渲染 ──
            float alpha = 0.8F;
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.color(1, 1, 1, alpha);

            model.bipedBody.render(modelScale);
            model.bipedRightLeg.render(modelScale);
            model.bipedLeftLeg.render(modelScale);
            model.bipedRightArm.render(modelScale);
            model.bipedLeftArm.render(modelScale);

            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.disableBlend();

            model.bipedHead.isHidden = false;
            model.bipedHeadwear.isHidden = false;
        } finally {
            GlStateManager.popMatrix();
        }

        // ── 粒子 ──
        if (SlideHandler.isSliding) {
            spawnSlideParticles(player);
        } else if (diving && player.onGround && SlideHandler.landDelayTicks > 0) {
            // 落地粒子爆发
            spawnLandParticles(player);
        }
    }

    private void spawnSlideParticles(EntityPlayer player) {
        if (!SlideMod.config.enableParticles || SlideMod.config.particleCount <= 0) return;
        if (!player.worldObj.isRemote || !player.onGround) return;

        for (int i = 0; i < SlideMod.config.particleCount; i++) {
            double px = player.posX + (Math.random() - 0.5) * 0.6;
            double py = player.getEntityBoundingBox().minY;
            double pz = player.posZ + (Math.random() - 0.5) * 0.6;
            spawnGroundParticle(player, px, py, pz);
        }
    }

    private void spawnLandParticles(EntityPlayer player) {
        if (!SlideMod.config.enableParticles) return;
        if (!player.worldObj.isRemote) return;

        for (int i = 0; i < 8; i++) {
            double px = player.posX + (Math.random() - 0.5) * 1.2;
            double py = player.getEntityBoundingBox().minY;
            double pz = player.posZ + (Math.random() - 0.5) * 1.2;
            spawnGroundParticle(player, px, py, pz);
        }
    }

    private void spawnGroundParticle(EntityPlayer player, double px, double py, double pz) {
        BlockPos ground = new BlockPos(
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.getEntityBoundingBox().minY) - 1,
            MathHelper.floor_double(player.posZ)
        );

        if (player.worldObj.getBlockState(ground).getBlock() == Blocks.water) {
            player.worldObj.spawnParticle(EnumParticleTypes.WATER_SPLASH,
                px, py + 0.1, pz, 0, 0.1, 0);
        } else {
            int blockId = net.minecraft.block.Block.getIdFromBlock(Blocks.dirt) + (0 << 12);
            player.worldObj.spawnParticle(EnumParticleTypes.BLOCK_CRACK,
                px, py + 0.1, pz,
                (Math.random() - 0.5) * 0.1, 0.1, (Math.random() - 0.5) * 0.1,
                blockId);
        }
    }
}
