package com.lukarbonite.autopickup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.reflect.Method;

public class AutoPickupCommand {

    // Bit flags for compression
    private static final int FLAG_MASTER    = 1; // 0001
    private static final int FLAG_BLOCKS    = 2; // 0010
    private static final int FLAG_MOBLOOT   = 4; // 0100
    private static final int FLAG_XP        = 8; // 1000

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Admin Commands (OP Level 2)
        // Allows admins to change the global server config
        dispatcher.register(CommandManager.literal("autopickup")
                .requires(source -> source.hasPermissionLevel(2))
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

        // Tunnel Command (No Permission)
        // Hidden command for players to sync their personal settings
        dispatcher.register(CommandManager.literal("ap_config_sync")
                .requires(source -> true)
                .then(CommandManager.argument("mask", IntegerArgumentType.integer())
                        .executes(AutoPickupCommand::executeSync))
        );
    }

    // Server Sync Logic
    private static int executeSync(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            int mask = IntegerArgumentType.getInteger(context, "mask");

            boolean master  = (mask & FLAG_MASTER)  != 0;
            boolean blocks  = (mask & FLAG_BLOCKS)  != 0;
            boolean looting = (mask & FLAG_MOBLOOT) != 0;
            boolean xp      = (mask & FLAG_XP)      != 0;

            // Updates the specific player's settings in PlayerConfigs map
            PlayerConfigs.setPlayerConfig(player.getUuid(), master, blocks, looting, xp);

        } catch (Exception e) {
            // Ignore errors (e.g. executed by console or non-player)
        }
        return 1;
    }

    // Client Send Logic
    @Environment(EnvType.CLIENT)
    public static void sendConfig() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        AutoPickupConfig config = AutoPickupConfig.getInstance();

        int mask = 0;
        if (config.autoPickup)        mask |= FLAG_MASTER;
        if (config.autoPickupBlocks)  mask |= FLAG_BLOCKS;
        if (config.autoPickupMobLoot) mask |= FLAG_MOBLOOT;
        if (config.autoPickupXp)      mask |= FLAG_XP;

        String command = "ap_config_sync " + mask;

        // Try standard Fabric/Yarn method first
        try {
            client.player.networkHandler.sendCommand(command);
        } catch (Throwable t) {
            try {
                // Reflection fallback for mapping mismatches
                Method method = client.player.networkHandler.getClass().getMethod("sendCommand", String.class);
                method.invoke(client.player.networkHandler, command);
            } catch (Exception e) {
                // Last resort: Chat message
                client.player.networkHandler.sendChatMessage("/" + command);
            }
        }
    }

    // Admin Helper

    private static int showStatus(CommandContext<ServerCommandSource> context) {
        AutoPickupConfig config = AutoPickupConfig.getInstance();
        ServerCommandSource source = context.getSource();

        sendMsg(source, Text.literal("--- AutoPickup Global Config ---").formatted(Formatting.GOLD));
        sendMsg(source, formatStatus("Master", config.autoPickup));
        sendMsg(source, formatStatus("Blocks", config.autoPickupBlocks));
        sendMsg(source, formatStatus("Mob Loot", config.autoPickupMobLoot));
        sendMsg(source, formatStatus("XP", config.autoPickupXp));
        sendMsg(source, formatStatus("Client Control", config.allowClientControl));

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
        sendMsg(context.getSource(), Text.literal("Set " + key + " to " + value).formatted(Formatting.GREEN));
        return 1;
    }

    private static void sendMsg(ServerCommandSource source, Text text) {
        source.sendFeedback(() -> text, false);
    }
}