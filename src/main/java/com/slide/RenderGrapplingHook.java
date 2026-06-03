package com.slide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * 抓钩渲染器。
 * 渲染一条从玩家到钩子的线段（类似钓鱼线）。
 */
public class RenderGrapplingHook extends Render<EntityGrapplingHook> {

    private static final ResourceLocation HOOK_TEXTURE =
        new ResourceLocation("slidemod:textures/items/grappling_hook.png");

    public RenderGrapplingHook(RenderManager renderManager) {
        super(renderManager);
    }

    @Override
    public void doRender(EntityGrapplingHook hook, double x, double y, double z,
                         float entityYaw, float partialTicks) {
        super.doRender(hook, x, y, z, entityYaw, partialTicks);

        EntityPlayer shooter = hook.getShooter();
        if (shooter == null) return;

        // 获取玩家和钩子的插值位置
        double px = shooter.lastTickPosX + (shooter.posX - shooter.lastTickPosX) * partialTicks;
        double py = shooter.lastTickPosY + (shooter.posY - shooter.lastTickPosY) * partialTicks;
        double pz = shooter.lastTickPosZ + (shooter.posZ - shooter.lastTickPosZ) * partialTicks;

        double hx = hook.lastTickPosX + (hook.posX - hook.lastTickPosX) * partialTicks;
        double hy = hook.lastTickPosY + (hook.posY - hook.lastTickPosY) * partialTicks;
        double hz = hook.lastTickPosZ + (hook.posZ - hook.lastTickPosZ) * partialTicks;

        // 对钩子坐标做平移（render offset 已由上级处理好）
        // x/y/z 就是钩子的位置，我们需要计算相对位置
        double dx = px - x;
        double dy = py - (hook.getEyeHeight() * 0.5) - y;
        double dz = pz - z;

        // 渲染线段（类似钓鱼线）
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();

        GL11.glLineWidth(3.0F);
        worldrenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        // 从钩子到玩家的线段（分为多段，制造轻微弧线效果）
        int segments = 8;
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            float progress = t * t;  // 弧线偏向玩家端

            double bx = x + dx * t;
            double by = y + dy * t;
            double bz = z + dz * t;

            // 弧线偏移（仅 Y 轴，产生下垂效果）
            double sag = Math.sin(t * Math.PI) * 0.5;
            by += sag;

            float alpha = 1.0F - t * 0.3F;  // 钩子端亮，玩家端淡
            worldrenderer.pos(bx, by, bz)
                .color(0.7F, 0.7F, 0.7F, alpha * 0.9F).endVertex();
        }

        tessellator.draw();

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityGrapplingHook entity) {
        return HOOK_TEXTURE;
    }
}
