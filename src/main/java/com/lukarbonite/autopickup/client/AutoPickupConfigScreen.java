package com.lukarbonite.autopickup.client;

import com.lukarbonite.autopickup.AutoPickupCommand;
import com.lukarbonite.autopickup.AutoPickupConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class AutoPickupConfigScreen {

    public static Screen create(Screen parent) {
        // Get the config instance
        AutoPickupConfig config = AutoPickupConfig.getInstance();

        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Auto Pickup Configuration"))
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("General"))
                        .tooltip(Text.literal("General settings for Auto Pickup"))

                        // Option: Master Auto Pickup
                        .option(Option.<Boolean>createBuilder()
                                .name(Text.literal("Master Auto Pickup"))
                                .description(OptionDescription.of(Text.literal("Globally toggle the auto pickup feature.")))
                                .binding(
                                        true,
                                        () -> config.autoPickup,
                                        val -> config.autoPickup = val
                                )
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        // Option: Auto Pickup Blocks
                        .option(Option.<Boolean>createBuilder()
                                .name(Text.literal("Auto Pickup Blocks"))
                                .description(OptionDescription.of(Text.literal("Automatically pick up mined blocks.")))
                                .binding(
                                        true,
                                        () -> config.autoPickupBlocks,
                                        val -> config.autoPickupBlocks = val
                                )
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        // Option: Auto Pickup Mob Loot
                        .option(Option.<Boolean>createBuilder()
                                .name(Text.literal("Auto Pickup Mob Loot"))
                                .description(OptionDescription.of(Text.literal("Automatically pick up drops from mobs.")))
                                .binding(
                                        true,
                                        () -> config.autoPickupMobLoot,
                                        val -> config.autoPickupMobLoot = val
                                )
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        // Option: Auto Pickup XP
                        .option(Option.<Boolean>createBuilder()
                                .name(Text.literal("Auto Pickup XP"))
                                .description(OptionDescription.of(Text.literal("Automatically absorb XP orbs.")))
                                .binding(
                                        true,
                                        () -> config.autoPickupXp,
                                        val -> config.autoPickupXp = val
                                )
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        // Option: Allow Client Control
                        .option(Option.<Boolean>createBuilder()
                                .name(Text.literal("Allow Client Control"))
                                .description(OptionDescription.of(Text.literal("Allow the client to override server settings (if applicable).")))
                                .binding(
                                        true,
                                        () -> config.allowClientControl,
                                        val -> config.allowClientControl = val
                                )
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .build())

                // Save to disk
                .save(() -> {
                    config.save();

                    // Sync to server (The Command Tunnel)
                    if (MinecraftClient.getInstance().world != null) {
                        // We call the command directly.
                        // It reads the config instance, bitmasks the booleans, and fires the hidden command.
                        AutoPickupCommand.sendConfig();
                    }
                })
                .build()
                .generateScreen(parent);
    }
}