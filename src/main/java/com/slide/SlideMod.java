package com.slide;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Logger;

@Mod(modid = SlideMod.MODID, version = SlideMod.VERSION,
     clientSideOnly = true,
     guiFactory = "com.slide.ModGuiFactory")
public class SlideMod {

    public static final String MODID = "slidemod";
    public static final String VERSION = "1.1.0";

    public static Logger logger;
    public static SlideConfig config;

    // ── PhD Flopper 物品实例 ──
    public static Item itemPhdFlopper;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new SlideConfig(new Configuration(event.getSuggestedConfigurationFile()));
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // ── 注册药水效果 ──
        PotionPhdFlopper.register();
        logger.info("PhD Flopper potion registered (ID: {})",
            PotionPhdFlopper.instance.getId());

        // ── 注册物品 ──
        itemPhdFlopper = new ItemPhdFlopper();
        net.minecraftforge.fml.common.registry.GameRegistry.registerItem(
            itemPhdFlopper, "phd_flopper");
        logger.info("PhD Flopper item registered");

        // ── 注册事件处理器 ──
        MinecraftForge.EVENT_BUS.register(new SlideHandler());
        MinecraftForge.EVENT_BUS.register(new SlideRenderer());
        MinecraftForge.EVENT_BUS.register(new WallRunHandler());
        MinecraftForge.EVENT_BUS.register(new PhdFlopperHandler());

        // ── 按键绑定 ──
        if (event.getSide() == Side.CLIENT) {
            SlideHandler.registerKeyBindings();
        }

        // ── 合成配方：PhD Flopper = 岩浆膏 + 火药 + 水箭（三选一配置） ──
        // 简单配方：烈焰粉 + 恶魂之泪 → PhD Flopper
        net.minecraftforge.fml.common.registry.GameRegistry.addShapelessRecipe(
            new ItemStack(itemPhdFlopper, 1),
            Items.blaze_powder,
            Items.ghast_tear,
            Items.glass_bottle
        );

        logger.info("Slide Mod v{} 已加载 (滑墙 + 二段跳 + PhD Flopper)", VERSION);
    }
}
