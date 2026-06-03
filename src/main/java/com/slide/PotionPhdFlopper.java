package com.slide;

import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * PhD Flopper 药水效果。
 * 免疫爆炸伤害 + 摔落伤害，高处飞扑落地时产生爆炸。
 */
public class PotionPhdFlopper extends Potion {

    public static PotionPhdFlopper instance;

    /**
     * 构造并自动注册到 Potion.potionTypes。
     * @param id 药水 ID（推荐 32+，避开原版）
     */
    public PotionPhdFlopper(int id) {
        super(id, new ResourceLocation("slidemod", "textures/gui/phd_flopper.png"), false, 0xFF00AA);
        setPotionName("effect.phd_flopper");
    }

    /** 注册药水效果（通过反射扩展 potionTypes 数组） */
    public static void register() {
        int id = 32;
        try {
            // Potion.potionTypes 是 private static final Potion[]
            Field field = Potion.class.getDeclaredField("potionTypes");
            field.setAccessible(true);

            // 移除 final 修饰符
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

            Potion[] old = (Potion[]) field.get(null);
            if (id >= old.length) {
                Potion[] expanded = new Potion[id + 1];
                System.arraycopy(old, 0, expanded, 0, old.length);
                field.set(null, expanded);
            }

            instance = new PotionPhdFlopper(id);
        } catch (Exception e) {
            SlideMod.logger.error("Failed to register PhD Flopper potion via reflection", e);
            // 最差情况：在已存在的数组中塞（可能抛异常）
            try {
                instance = new PotionPhdFlopper(id);
            } catch (Exception ignored) {
                SlideMod.logger.error("Complete registration failure for PhD Flopper");
            }
        }
    }
}
