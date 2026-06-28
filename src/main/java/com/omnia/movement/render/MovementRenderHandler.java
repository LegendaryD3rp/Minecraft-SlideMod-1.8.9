package com.omnia.movement.render;

import java.lang.reflect.Field;

import org.lwjgl.opengl.GL11;

import net.minecraftforge.client.event.EntityViewRenderEvent;
import org.lwjgl.util.vector.Vector3f;

import com.omnia.movement.MovementHandler;
import com.omnia.movement.MovementState;
import com.omnia.movement.PlayerStateManager;
import com.omnia.movement.WallRunHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 第三人称渲染处理器。
 * 在 RenderPlayerEvent.Pre/Post 中根据玩家运动状态修改模型。
 * Pre 推矩阵 + 应用变换，Post 弹矩阵，保证栈平衡。
 *
 * 获取 ModelBiped 的方式：在类加载时枚举 RendererLivingEntity 的字段，
 * 找到类型为 ModelBiped（或 ModelBase）的字段，缓存其引用。
 * 这种方式兼容 OptiFine 等修改了字段名的环境。
 */
@SideOnly(Side.CLIENT)
public class MovementRenderHandler {

    // 通过字段搜索缓存的 mainModel 字段引用
    private static Field mainModelField = null;

    static {
        for (Field f : RendererLivingEntity.class.getDeclaredFields()) {
            if (ModelBase.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                mainModelField = f;
                break;
            }
        }
        if (mainModelField == null) {
            System.err.println("[OmniMovement] WARNING: Could not find mainModel field in RendererLivingEntity");
        }
    }

    // ===== 渲染参数 =====

    private static final float SLIDE_LEG_ANGLE     = -1.4F;
    private static final float SLIDE_BODY_ANGLE    = -0.15F;
    private static final float SLIDE_ARM_ANGLE     = -0.6F;
    private static final float SLIDE_Y_TRANSLATE   = -0.35F;

    private static final float DIVE_BODY_ROTATE    = 80.0F;
    private static final float DIVE_ARM_ANGLE      = -0.8F;
    private static final float DIVE_LEG_ANGLE      = 0.6F;
    private static final float DIVE_HEAD_ANGLE     = 0.3F;

    private static final float LANDING_TRANSLATE   = -0.15F;

    // 渲染进度插值
    private static float slideRenderProgress  = 0.0F;
    private static float diveRenderProgress   = 0.0F;
    private static float landingRenderProgress = 0.0F;

    // 矩阵栈追踪：Pre 推了矩阵，Post 需要弹
    private static boolean matrixPushed = false;

    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        EntityPlayer player = event.entityPlayer;
        if (!(player instanceof AbstractClientPlayer)) return;
        if (player.isInvisible()) return;

        ModelBiped model = getModel(event.renderer);
        if (model == null) return;

        updateRenderProgress(player);

        PlayerStateManager state = MovementHandler.getOrCreate(player);
        MovementState currentState = state.getState();

        // 推矩阵：所有 GL 变换在此作用域内
        GlStateManager.pushMatrix();
        matrixPushed = true;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 应用模型角度修改 + GL 变换
        switch (currentState) {
            case SLIDING:
                applySlideRender(model);
                break;
            case DIVING:
                applyDiveRender(model);
                break;
            case LANDING:
                applyLandingRender(model);
                break;
            default:
                applyTransitionOut(model);
                break;
        }
    }

    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (matrixPushed) {
            GlStateManager.popMatrix();
            matrixPushed = false;
        }
    }

    // ========== 获取 ModelBiped ==========

    private ModelBiped getModel(RenderPlayer renderer) {
        if (mainModelField == null) return null;
        try {
            return (ModelBiped) mainModelField.get(renderer);
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 渲染进度 ==========

    private void updateRenderProgress(EntityPlayer player) {
        boolean isLocal = (player == Minecraft.getMinecraft().thePlayer);
        if (!isLocal) return;

        PlayerStateManager state = MovementHandler.getOrCreate(player);
        MovementState currentState = state.getState();

        final float SPEED = 0.15F;

        switch (currentState) {
            case SLIDING:
                slideRenderProgress   = Math.min(1.0F, slideRenderProgress   + SPEED);
                diveRenderProgress    = Math.max(0.0F, diveRenderProgress    - SPEED);
                landingRenderProgress = Math.max(0.0F, landingRenderProgress - SPEED);
                break;
            case DIVING:
                diveRenderProgress    = Math.min(1.0F, diveRenderProgress    + SPEED);
                slideRenderProgress   = Math.max(0.0F, slideRenderProgress   - SPEED);
                landingRenderProgress = Math.max(0.0F, landingRenderProgress - SPEED);
                break;
            case LANDING:
                landingRenderProgress = Math.min(1.0F, landingRenderProgress + SPEED);
                slideRenderProgress   = Math.max(0.0F, slideRenderProgress   - SPEED);
                diveRenderProgress    = Math.max(0.0F, diveRenderProgress    - SPEED);
                break;
            case NONE:
            default:
                slideRenderProgress   = Math.max(0.0F, slideRenderProgress   - SPEED);
                diveRenderProgress    = Math.max(0.0F, diveRenderProgress    - SPEED);
                landingRenderProgress = Math.max(0.0F, landingRenderProgress - SPEED);
                break;
        }
    }

    // ========== 滑铲 ==========

    private void applySlideRender(ModelBiped model) {
        float p = clamp(slideRenderProgress);
        if (p < 0.01F) return;

        GlStateManager.translate(0.0F, SLIDE_Y_TRANSLATE * p, 0.0F);

        model.bipedBody.rotateAngleX     = SLIDE_BODY_ANGLE * p;
        model.bipedRightLeg.rotateAngleX = SLIDE_LEG_ANGLE  * p;
        model.bipedLeftLeg.rotateAngleX  = SLIDE_LEG_ANGLE  * p;
        model.bipedRightLeg.rotateAngleY =  0.1F * p;
        model.bipedLeftLeg.rotateAngleY  = -0.1F * p;
        model.bipedRightArm.rotateAngleX = SLIDE_ARM_ANGLE * p;
        model.bipedLeftArm.rotateAngleX  = SLIDE_ARM_ANGLE * p;
        model.bipedHead.rotateAngleX     = 0.1F * p;
    }

    // ========== 飞扑 ==========

    private void applyDiveRender(ModelBiped model) {
        float p = clamp(diveRenderProgress);
        if (p < 0.01F) return;

        // 重心→旋转→移回→前推
        GlStateManager.translate(0.0F, 0.9F, 0.0F);
        GlStateManager.rotate(DIVE_BODY_ROTATE * p, 1.0F, 0.0F, 0.0F);
        GlStateManager.translate(0.0F, -0.9F, 0.0F);
        GlStateManager.translate(0.0F, 0.0F, 0.3F * p);

        model.bipedRightArm.rotateAngleX = DIVE_ARM_ANGLE * p;
        model.bipedLeftArm.rotateAngleX  = DIVE_ARM_ANGLE * p;
        model.bipedRightArm.rotateAngleZ = -0.15F * p;
        model.bipedLeftArm.rotateAngleZ  =  0.15F * p;
        model.bipedRightLeg.rotateAngleX = DIVE_LEG_ANGLE * p;
        model.bipedLeftLeg.rotateAngleX  = DIVE_LEG_ANGLE * p;
        model.bipedRightLeg.rotateAngleY =  0.15F * p;
        model.bipedLeftLeg.rotateAngleY  = -0.15F * p;
        model.bipedBody.rotateAngleX     =  0.1F * p;
        model.bipedHead.rotateAngleX     = DIVE_HEAD_ANGLE * p;
    }

    // ========== 缓冲 ==========

    private void applyLandingRender(ModelBiped model) {
        float p = clamp(landingRenderProgress);
        if (p < 0.01F) return;

        GlStateManager.translate(0.0F, LANDING_TRANSLATE * p, 0.0F);

        model.bipedRightLeg.rotateAngleX =  0.5F * p;
        model.bipedLeftLeg.rotateAngleX  =  0.5F * p;
        model.bipedRightLeg.rotateAngleY =  0.2F * p;
        model.bipedLeftLeg.rotateAngleY  = -0.2F * p;
        model.bipedBody.rotateAngleX     =  0.3F * p;
        model.bipedRightArm.rotateAngleX = -0.3F * p;
        model.bipedLeftArm.rotateAngleX  = -0.3F * p;
        model.bipedRightArm.rotateAngleZ = -0.1F * p;
        model.bipedLeftArm.rotateAngleZ  =  0.1F * p;
    }

    // ========== 过渡退出（状态切回 NONE 后渐隐） ==========

    private void applyTransitionOut(ModelBiped model) {
        float sp = slideRenderProgress;
        float dp = diveRenderProgress;
        float lp = landingRenderProgress;

        if (sp < 0.01F && dp < 0.01F && lp < 0.01F) return;

        if (sp > 0.01F) {
            GlStateManager.translate(0.0F, SLIDE_Y_TRANSLATE * sp, 0.0F);
            model.bipedRightLeg.rotateAngleX = SLIDE_LEG_ANGLE * sp;
            model.bipedLeftLeg.rotateAngleX  = SLIDE_LEG_ANGLE * sp;
            model.bipedBody.rotateAngleX     = SLIDE_BODY_ANGLE * sp;
            model.bipedRightArm.rotateAngleX = SLIDE_ARM_ANGLE * sp;
            model.bipedLeftArm.rotateAngleX  = SLIDE_ARM_ANGLE * sp;
        }
        if (dp > 0.01F) {
            GlStateManager.translate(0.0F, 0.9F, 0.0F);
            GlStateManager.rotate(DIVE_BODY_ROTATE * dp, 1.0F, 0.0F, 0.0F);
            GlStateManager.translate(0.0F, -0.9F, 0.0F);
            GlStateManager.translate(0.0F, 0.0F, 0.3F * dp);
        }
        if (lp > 0.01F) {
            GlStateManager.translate(0.0F, LANDING_TRANSLATE * lp, 0.0F);
        }
    }

    private static float clamp(float v) {
        return Math.min(1.0F, Math.max(0.0F, v));
    }

    // ═══════════════════════════════════════
    //  滑墙视角倾斜
    // ═══════════════════════════════════════

    @SubscribeEvent
    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        if (WallRunHandler.isWallRunning) {
            // 在主视角倾斜基础上叠加 WallRunHandler 计算好的 roll
            event.roll += WallRunHandler.cameraRoll;
        }
    }
}
