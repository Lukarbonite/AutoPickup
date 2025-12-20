package com.lukarbonite.autopickup.client;

import com.lukarbonite.autopickup.AutoPickupConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class AutoPickupConfigScreen extends Screen {
    private final Screen parent;
    private final AutoPickupConfig config;

    public AutoPickupConfigScreen(Screen parent) {
        super(Text.literal("Auto Pickup Configuration"));
        this.parent = parent;
        this.config = AutoPickupConfig.getInstance();
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 24;

        // Start buttons lower to accommodate the title and separator
        int y = 55;

        // Master Toggle
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.autoPickup)
                .build(center - 100, y, buttonWidth, buttonHeight, Text.literal("Master Auto Pickup"), (button, value) -> {
                    config.autoPickup = value;
                }));
        y += spacing;

        // Blocks Toggle
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.autoPickupBlocks)
                .build(center - 100, y, buttonWidth, buttonHeight, Text.literal("Auto Pickup Blocks"), (button, value) -> {
                    config.autoPickupBlocks = value;
                }));
        y += spacing;

        // Mob Loot Toggle
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.autoPickupMobLoot)
                .build(center - 100, y, buttonWidth, buttonHeight, Text.literal("Auto Pickup Mob Loot"), (button, value) -> {
                    config.autoPickupMobLoot = value;
                }));
        y += spacing;

        // XP Toggle
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.autoPickupXp)
                .build(center - 100, y, buttonWidth, buttonHeight, Text.literal("Auto Pickup XP"), (button, value) -> {
                    config.autoPickupXp = value;
                }));

        // Done Button
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.close())
                .dimensions(center - 100, this.height - 40, buttonWidth, buttonHeight)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Background Logic
        if (this.client.world == null) {
            // Main Menu: Sleek Opaque Gradient (Slate Blue)
            context.fillGradient(0, 0, this.width, this.height, 0xFF0F2027, 0xFF2C5364);
        } else {
            // In-Game: Transparent Gradient
            // Top: Darker (Alpha B0) for readability. Bottom: Lighter (Alpha 40).
            context.fillGradient(0, 0, this.width, this.height, 0xB0000000, 0x40000000);
        }

        // Title Header separator line
        context.fill(0, 40, this.width, 41, 0x50FFFFFF);

        // Title Text
        // Color: 0xFFFFD700 (Alpha=FF, Red=FF, Green=D7, Blue=00)
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFD700);

        // Render buttons
        super.render(context, mouseX, mouseY, delta);

        // Footer separator line
        context.fill(0, this.height - 52, this.width, this.height - 51, 0x50FFFFFF);
    }

    @Override
    public void close() {
        config.save();
        this.client.setScreen(parent);
    }
}