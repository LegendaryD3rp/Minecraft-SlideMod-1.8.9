package com.slide;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Logger;

@Mod(modid = SlideMod.MODID, version = SlideMod.VERSION,
     clientSideOnly = true,
     guiFactory = "com.slide.ModGuiFactory")
public class SlideMod {

    public static final String MODID = "slidemod";
    public static final String VERSION = "1.2.0";

    public static Logger logger;
    public static SlideConfig config;

    // ── 物品实例 ──
    public static Item itemPhdFlopper;
    public static Item itemGrapplingHook;

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
        GameRegistry.registerItem(itemPhdFlopper, "phd_flopper");
        logger.info("PhD Flopper item registered");

        itemGrapplingHook = new ItemGrapplingHook();
        GameRegistry.registerItem(itemGrapplingHook, "grappling_hook");
        logger.info("Grappling Hook item registered");

        // ── 注册抓钩实体 ──
        EntityRegistry.registerModEntity(
            EntityGrapplingHook.class, "grappling_hook_entity",
            0, this, 64, 1, true);
        logger.info("Grappling Hook entity registered");

        // ── 注册事件处理器 ──
        MinecraftForge.EVENT_BUS.register(new SlideHandler());
        MinecraftForge.EVENT_BUS.register(new SlideRenderer());
        MinecraftForge.EVENT_BUS.register(new WallRunHandler());
        MinecraftForge.EVENT_BUS.register(new PhdFlopperHandler());
        MinecraftForge.EVENT_BUS.register(new GrapplingHookHandler());

        // ── 按键绑定 ──
        if (event.getSide() == Side.CLIENT) {
            SlideHandler.registerKeyBindings();
            GrapplingHookHandler.registerKeyBindings();
        }

        // ── 注册渲染器 ──
        if (event.getSide() == Side.CLIENT) {
            registerRenderers();
        }

        // ── 注册物品模型 ──
        if (event.getSide() == Side.CLIENT) {
            registerItemModels();
        }

        // ── 合成配方 ──
        // PhD Flopper = 烈焰粉 + 恶魂之泪 + 玻璃瓶
        GameRegistry.addShapelessRecipe(
            new ItemStack(itemPhdFlopper, 1),
            Items.blaze_powder,
            Items.ghast_tear,
            Items.glass_bottle
        );

        // 抓钩 = 铁锭 x3 + 线 x2（简单配方）
        GameRegistry.addRecipe(
            new ItemStack(itemGrapplingHook, 1),
            " I ",
            " IS",
            "S  ",
            'I', Items.iron_ingot,
            'S', Items.string
        );

        logger.info("Slide Mod v{} 已加载 (滑墙 + 二段跳 + PhD Flopper + 抓钩)", VERSION);
    }

    @SideOnly(Side.CLIENT)
    private void registerRenderers() {
        RenderingRegistry.registerEntityRenderingHandler(
            EntityGrapplingHook.class,
            new RenderGrapplingHook(
                net.minecraft.client.Minecraft.getMinecraft().getRenderManager()));
    }

    @SideOnly(Side.CLIENT)
    private void registerItemModels() {
        // 1.8.9 Forge JSON 模型注册
        net.minecraft.client.resources.model.ModelResourceLocation loc;

        loc = new net.minecraft.client.resources.model.ModelResourceLocation(
            "slidemod:phd_flopper", "inventory");
        net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(
            itemPhdFlopper, 0, loc);

        loc = new net.minecraft.client.resources.model.ModelResourceLocation(
            "slidemod:grappling_hook", "inventory");
        net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(
            itemGrapplingHook, 0, loc);
    }
}
