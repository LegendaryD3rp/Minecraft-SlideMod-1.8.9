package com.slide;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.util.ArrayList;
import java.util.List;

public class SlideGuiConfig extends GuiConfig {

    public SlideGuiConfig(GuiScreen parent) {
        super(
            parent,
            buildConfigElements(),
            SlideMod.MODID,
            false,
            false,
            "滑铲模组 - 配置"
        );
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (SlideMod.config != null) {
            SlideMod.config.save();
        }
    }

    private static List<IConfigElement> buildConfigElements() {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();

        if (SlideMod.config == null || SlideMod.config.config == null) {
            return elements;
        }

        elements.add(new ConfigElement(SlideMod.config.config.getCategory("general")));
        elements.add(new ConfigElement(SlideMod.config.config.getCategory("mechanics")));
        elements.add(new ConfigElement(SlideMod.config.config.getCategory("dive")));
        elements.add(new ConfigElement(SlideMod.config.config.getCategory("visual")));
        elements.add(new ConfigElement(SlideMod.config.config.getCategory("key")));

        return elements;
    }
}
