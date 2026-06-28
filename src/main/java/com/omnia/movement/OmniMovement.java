package com.omnia.movement;

import com.omnia.movement.keybind.ClientKeyHandler;
import com.omnia.movement.keybind.KeyBindings;
import com.omnia.movement.network.OmniNetwork;
import com.omnia.movement.render.MovementRenderHandler;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = OmniMovement.MODID, version = OmniMovement.VERSION, name = OmniMovement.NAME,
     guiFactory = "com.omnia.movement.client.OmniGuiFactory")
public class OmniMovement {

    public static final String MODID = "omnimovement";
    public static final String VERSION = "1.0.0";
    public static final String NAME = "OmniMovement";

    @Mod.Instance(MODID)
    public static OmniMovement instance;

    private static Configuration config;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // 加载配置
        config = new Configuration(event.getSuggestedConfigurationFile());
        OmniConfig.load(config);

        // 注册网络
        OmniNetwork.init();

        // 注册事件处理器（服务端 + 客户端共用）
        MinecraftForge.EVENT_BUS.register(new MovementHandler());
        MinecraftForge.EVENT_BUS.register(new PhdFlopperHandler());

        // 注册 PhD Flopper 物品和药水效果
        PotionPhdFlopper.register();
        GameRegistry.registerItem(new ItemPhdFlopper(), "phd_flopper");

        // 客户端专用注册
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            // 注册按键（ClientRegistry 是客户端专用）
            ClientKeyHandler.registerKeys();
            // 注册按键事件监听（通过本类代理转发）
            MinecraftForge.EVENT_BUS.register(new InputEventListener());
            // 注册渲染器
            MinecraftForge.EVENT_BUS.register(new MovementRenderHandler());
            // 注册滑墙处理器
            MinecraftForge.EVENT_BUS.register(new WallRunHandler());
        }
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (config.hasChanged()) {
            config.save();
        }
    }

    public static Configuration getConfig() {
        return config;
    }

    /**
     * 按键输入事件监听器。
     * 作为轻量代理，转发到 ClientKeyHandler。
     * 仅在客户端注册。
     */
    public static class InputEventListener {
        @SubscribeEvent
        public void onKeyInput(InputEvent.KeyInputEvent event) {
            ClientKeyHandler.handleKeyInput();
        }
    }
}
