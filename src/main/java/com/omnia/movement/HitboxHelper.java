package com.omnia.movement;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayer;

/**
 * 碰撞箱辅助工具。
 * Minecraft 1.8.9 中 Entity.setSize(float, float) 为 protected，
 * 无法从外部直接调用。使用反射桥接。
 */
public class HitboxHelper {

    private static Field widthField;
    private static Field heightField;

    static {
        try {
            // Forge 1.8.9 SRG 映射名
            widthField  = findField(net.minecraft.entity.Entity.class, "width",  "field_70130_N");
            heightField = findField(net.minecraft.entity.Entity.class, "height", "field_70131_O");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    /**
     * 设置玩家碰撞箱。
     * 等价于 Entity.setSize(width, height)，但通过反射实现。
     */
    public static void setSize(EntityPlayer player, float width, float height) {
        try {
            if (widthField != null)  widthField.set(player,  width);
            if (heightField != null) heightField.set(player, height);

            // 更新眼睛高度
            player.eyeHeight = player.getDefaultEyeHeight();

            // 如果玩家在服务器端，也更新 bounding box
            // （客户端侧 setSize 内部会重建 boundingBox，但反射直接改字段不会）
            // 所以手动重建
            double w = width / 2.0;
            player.setEntityBoundingBox(player.getEntityBoundingBox()
                    .contract(player.getEntityBoundingBox().maxX - player.getEntityBoundingBox().minX - w,
                              0,
                              player.getEntityBoundingBox().maxZ - player.getEntityBoundingBox().minZ - w));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 还原玩家碰撞箱至默认。
     */
    public static void restoreDefaultSize(EntityPlayer player) {
        setSize(player, 0.6F, 1.8F);
        player.eyeHeight = player.getDefaultEyeHeight();
    }

    /**
     * 设置玩家眼睛高度。
     */
    public static void setEyeHeight(EntityPlayer player, float height) {
        player.eyeHeight = height;
    }
}
