package com.slide;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * 抓钩物品。
 * 右键射出抓钩，再次右键收回。
 */
public class ItemGrapplingHook extends Item {

    public ItemGrapplingHook() {
        setUnlocalizedName("grappling_hook");
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.tabCombat);
        setMaxStackSize(1);
        setMaxDamage(384);  // 耐久度
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player == null) return stack;

        // 检查是否已有活跃抓钩
        EntityGrapplingHook existingHook = EntityGrapplingHook.getActiveHook(player);
        if (existingHook != null) {
            // 已经抓着了 → 收回
            if (existingHook.getState() == EntityGrapplingHook.State.ATTACHED) {
                existingHook.retract();
            } else {
                existingHook.setDead();
            }
            return stack;
        }

        // 射出抓钩
        if (!world.isRemote) {
            EntityGrapplingHook hook = new EntityGrapplingHook(world, player);
            world.spawnEntityInWorld(hook);
        }

        // 消耗耐久
        if (!player.capabilities.isCreativeMode) {
            stack.damageItem(1, player);
            if (stack.stackSize <= 0) {
                return null;
            }
        }

        return stack;
    }
}
