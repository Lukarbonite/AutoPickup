package com.lukarbonite.forge.client;

import com.lukarbonite.autopickup.client.AutoPickupConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Set;

/** The Mods list's "Config" button, named in AutoPickupForge's {@code @Mod(guiFactory = ...)}. */
public class AutoPickupGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {}

    @Override
    public boolean hasConfigGui() { return true; }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        return AutoPickupConfigScreen.create(parentScreen);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() { return null; }
}
