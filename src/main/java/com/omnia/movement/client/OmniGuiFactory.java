package com.omnia.movement.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.omnia.movement.OmniConfig;
import com.omnia.movement.OmniMovement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

/**
 * 全向机动配置 GUI 工厂。
 * 注册在 @Mod(guiFactory=...) 中，让 Mod 列表的「配置」按钮可用。
 */
public class OmniGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {
        // nothing needed
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return OmniGuiConfig.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null; // no runtime categories
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }

    /**
     * 实际配置界面。将所有配置分类展开为 ConfigElement 列表。
     */
    public static class OmniGuiConfig extends GuiConfig {

        private static final String[] CATEGORIES = {"slide", "dive", "landing", "slideCancel", "slope", "debug"};

        private static final String[] CATEGORY_NAMES = {
            "滑铲 (Slide)",
            "飞扑 (Dive)",
            "落地缓冲 (Landing)",
            "滑铲取消 (Cancel)",
            "坡度 (Slope)",
            "调试 (Debug)"
        };

        public OmniGuiConfig(GuiScreen parentScreen) {
            super(parentScreen, buildConfigElements(),
                  OmniMovement.MODID, false, false,
                  "全向机动 · 配置",
                  "改完关闭即生效，无需重启");
        }

        @Override
        public void onGuiClosed() {
            super.onGuiClosed();
            // 将 GUI 中改好的值重新加载到 OmniConfig 静态字段
            OmniConfig.load(OmniMovement.getConfig());
        }

        private static List<IConfigElement> buildConfigElements() {
            List<IConfigElement> elements = new ArrayList<>();
            for (int i = 0; i < CATEGORIES.length; i++) {
                elements.add(new ConfigElement(
                        OmniMovement.getConfig().getCategory(CATEGORIES[i])));
            }
            return elements;
        }
    }
}
