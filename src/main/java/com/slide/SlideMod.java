package com.slide;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

@Mod(modid = SlideMod.MODID, version = SlideMod.VERSION,
     clientSideOnly = true,
     guiFactory = "com.slide.ModGuiFactory")
public class SlideMod {

    public static final String MODID = "slidemod";
    public static final String VERSION = "1.0.0";

    public static Logger logger;
    public static SlideConfig config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new SlideConfig(new Configuration(event.getSuggestedConfigurationFile()));
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new SlideHandler());
        MinecraftForge.EVENT_BUS.register(new SlideRenderer());

        if (event.getSide() == Side.CLIENT) {
            SlideHandler.registerKeyBindings();
        }

        logger.info("Slide Mod 已加载");
    }
}
