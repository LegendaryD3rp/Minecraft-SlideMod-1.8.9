package com.omnia.movement;

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

    public PotionPhdFlopper(int id) {
        super(id, new ResourceLocation("slidemod", "textures/gui/phd_flopper.png"), false, 0xFF00AA);
        setPotionName("effect.phd_flopper");
    }

    /** 注册药水效果（通过反射扩展 potionTypes 数组） */
    public static void register() {
        int id = 32;
        try {
            Field field = Potion.class.getDeclaredField("potionTypes");
            field.setAccessible(true);

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
            System.err.println("[OmniMovement] Failed to register PhD Flopper potion: " + e.getMessage());
            try {
                instance = new PotionPhdFlopper(id);
            } catch (Exception ignored) { }
        }
    }
}
