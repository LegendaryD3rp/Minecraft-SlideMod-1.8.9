package com.slide;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

/**
 * PhD Flopper 药水瓶。
 * 右键饮用后获得 PhD Flopper 效果（20 分钟），返回空瓶。
 */
public class ItemPhdFlopper extends Item {

    // 效果持续 20 分钟（24000 ticks），等级 1
    private static final int EFFECT_DURATION = 24000;
    private static final int EFFECT_AMPLIFIER = 0;

    public ItemPhdFlopper() {
        setUnlocalizedName("phd_flopper");
        setCreativeTab(CreativeTabs.tabBrewing);
        setMaxStackSize(1);
        setFull3D();
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player == null) return stack;

        // 如果已经有 PhD 效果，不重复饮用
        if (player.isPotionActive(PotionPhdFlopper.instance)) {
            return stack;
        }

        player.playSound("random.drink", 0.5F, 1.0F);
        if (!world.isRemote) {
            player.addPotionEffect(new PotionEffect(
                PotionPhdFlopper.instance.getId(),
                EFFECT_DURATION,
                EFFECT_AMPLIFIER,
                false, false
            ));
        }

        // 如果不是创造模式，消耗一瓶返回空瓶
        if (!player.capabilities.isCreativeMode) {
            stack.stackSize--;
            if (stack.stackSize <= 0) {
                return new ItemStack(net.minecraft.init.Items.glass_bottle);
            }
            player.inventory.addItemStackToInventory(new ItemStack(net.minecraft.init.Items.glass_bottle));
        }

        return stack;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.DRINK;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 32; // 饮用动画时长
    }
}
