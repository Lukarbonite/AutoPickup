package com.lukarbonite.autopickup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class AutoPickupCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("autopickup")
                .requires(source -> source.hasPermissionLevel(2)) // OP Level 2
                .executes(AutoPickupCommand::showStatus)
                .then(CommandManager.literal("master")
                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> setConfig(ctx, "auto_pickup", BoolArgumentType.getBool(ctx, "value")))))
                .then(CommandManager.literal("blocks")
                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> setConfig(ctx, "auto_pickup_blocks", BoolArgumentType.getBool(ctx, "value")))))
                .then(CommandManager.literal("mobloot")
                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> setConfig(ctx, "auto_pickup_mob_loot", BoolArgumentType.getBool(ctx, "value")))))
                .then(CommandManager.literal("xp")
                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> setConfig(ctx, "auto_pickup_xp", BoolArgumentType.getBool(ctx, "value")))))
                .then(CommandManager.literal("allowClientControl")
                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> setConfig(ctx, "allow_client_control", BoolArgumentType.getBool(ctx, "value")))))
        );
    }

    private static int showStatus(CommandContext<ServerCommandSource> context) {
        AutoPickupConfig config = AutoPickupConfig.getInstance();
        ServerCommandSource source = context.getSource();

        source.sendFeedback(() -> Text.literal("--- AutoPickup Config ---").formatted(Formatting.GOLD), false);
        source.sendFeedback(() -> formatStatus("Master", config.autoPickup), false);
        source.sendFeedback(() -> formatStatus("Blocks", config.autoPickupBlocks), false);
        source.sendFeedback(() -> formatStatus("Mob Loot", config.autoPickupMobLoot), false);
        source.sendFeedback(() -> formatStatus("XP", config.autoPickupXp), false);
        source.sendFeedback(() -> formatStatus("Allow Client Control", config.allowClientControl), false);

        return 1;
    }

    private static Text formatStatus(String name, boolean value) {
        return Text.literal(name + ": ").append(Text.literal(String.valueOf(value)).formatted(value ? Formatting.GREEN : Formatting.RED));
    }

    private static int setConfig(CommandContext<ServerCommandSource> context, String key, boolean value) {
        AutoPickupConfig config = AutoPickupConfig.getInstance();
        switch (key) {
            case "auto_pickup" -> config.autoPickup = value;
            case "auto_pickup_blocks" -> config.autoPickupBlocks = value;
            case "auto_pickup_mob_loot" -> config.autoPickupMobLoot = value;
            case "auto_pickup_xp" -> config.autoPickupXp = value;
            case "allow_client_control" -> config.allowClientControl = value;
        }
        config.save();
        context.getSource().sendFeedback(() -> Text.literal("Set " + key + " to " + value).formatted(Formatting.GREEN), true);
        return 1;
    }
}