package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.client.ClientConfigManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

public class AutoPickupCommand {

    // Bit flags for Boolean configs (Global & Client Sync)
    private static final int FLAG_MASTER         = 1;       // Bit 0
    private static final int FLAG_BLOCKS         = 2;       // Bit 1
    private static final int FLAG_BLOCK_XP       = 4;       // Bit 2
    private static final int FLAG_MOB_LOOT       = 8;       // Bit 3
    private static final int FLAG_MOB_XP         = 16;      // Bit 4
    private static final int FLAG_SPLIT_LOOT     = 32;      // Bit 5
    private static final int FLAG_SPLIT_XP       = 64;      // Bit 6
    private static final int FLAG_ALLOW_CLIENT   = 128;     // Bit 7 (Global only)

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("autopickup")
                .requires(source -> checkPermission(source))
                .executes(AutoPickupCommand::showServerStatus)
                .then(CommandManager.literal("global")
                        .then(CommandManager.argument("key", StringArgumentType.word())
                                .suggests((ctx, b) -> CommandSource.suggestMatching(
                                        java.util.List.of("master","blocks","blockXp","mobLoot","mobXp","splitMobLoot","splitMobXp","allowClientControl"), b))
                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setGlobal(ctx, false))
                                        .then(CommandManager.literal("silent")
                                                .executes(ctx -> setGlobal(ctx, true)))
                                )
                        )
                )
                .then(CommandManager.literal("setPlayerConfig")
                        .then(CommandManager.argument("target", EntityArgumentType.players())
                                .then(CommandManager.argument("key", StringArgumentType.word())
                                        .suggests((ctx, b) -> CommandSource.suggestMatching(
                                                java.util.List.of("master","blocks","blockXp","mobLoot","mobXp","splitMobLoot","splitMobXp","all"), b))
                                        .then(CommandManager.argument("value", StringArgumentType.word())
                                                .suggests((ctx, b) -> CommandSource.suggestMatching(java.util.List.of("true","false","reset"), b))
                                                .executes(ctx -> executeSetPlayerConfig(ctx, false))
                                                .then(CommandManager.literal("silent")
                                                        .executes(ctx -> executeSetPlayerConfig(ctx, true)))
                                        )
                                )
                        )
                )
                .then(CommandManager.literal("query_player")
                        .then(CommandManager.argument("targetName", StringArgumentType.string())
                                .executes(AutoPickupCommand::executeQueryPlayer))
                )
                .then(CommandManager.literal("check_perm")
                        .executes(ctx -> {
                            boolean op = checkPermission(ctx.getSource());
                            // Send 1 for true, 0 for false
                            ctx.getSource().sendFeedback(() -> Text.literal("[AP_PERM] " + (op ? 1 : 0)), false);
                            return 1;
                        })
                )
                .then(CommandManager.literal("query_global")
                        .executes(AutoPickupCommand::executeQueryGlobal)
                )
        );

        dispatcher.register(CommandManager.literal("ap_config_sync")
                .requires(source -> true)
                .then(CommandManager.argument("mask", IntegerArgumentType.integer())
                        .executes(AutoPickupCommand::executeSync))
        );
    }

    private static boolean checkPermission(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            return source.getServer().getPlayerManager().isOperator(player.getPlayerConfigEntry());
        }
        return true;
    }

    private static int executeSync(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            int mask = IntegerArgumentType.getInteger(context, "mask");
            PlayerConfigs.setClientPreference(player.getUuid(),
                    (mask & FLAG_MASTER) != 0, (mask & FLAG_BLOCKS) != 0, (mask & FLAG_BLOCK_XP) != 0,
                    (mask & FLAG_MOB_LOOT) != 0, (mask & FLAG_MOB_XP) != 0, (mask & FLAG_SPLIT_LOOT) != 0, (mask & FLAG_SPLIT_XP) != 0);
        } catch (Exception e) {}
        return 1;
    }

    private static int executeQueryPlayer(CommandContext<ServerCommandSource> context) {
        String targetName = StringArgumentType.getString(context, "targetName");
        ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetName);

        if (target != null) {
            PlayerConfigs.PlayerState s = PlayerConfigs.getState(target.getUuid());

            // Encode Tristates using 2 bits per value:
            // 0 (00) = Reset/Null
            // 1 (01) = True
            // 2 (10) = False
            int mask = 0;
            mask |= encodeTristate(s.overrideMaster)       << 0;
            mask |= encodeTristate(s.overrideBlocks)       << 2;
            mask |= encodeTristate(s.overrideBlockXp)      << 4;
            mask |= encodeTristate(s.overrideMobLoot)      << 6;
            mask |= encodeTristate(s.overrideMobXp)        << 8;
            mask |= encodeTristate(s.overrideSplitMobLoot) << 10;
            mask |= encodeTristate(s.overrideSplitMobXp)   << 12;

            final int finalMask = mask;
            context.getSource().sendFeedback(() -> Text.literal("[AP_DATA] " + targetName + " " + finalMask), false);
        }
        return 1;
    }

    private static int executeQueryGlobal(CommandContext<ServerCommandSource> context) {
        AutoPickupConfig config = AutoPickupConfig.getInstance();
        int mask = 0;
        if (config.autoPickup)             mask |= FLAG_MASTER;
        if (config.autoPickupBlocks)       mask |= FLAG_BLOCKS;
        if (config.autoPickupBlockXp)      mask |= FLAG_BLOCK_XP;
        if (config.autoPickupMobLoot)      mask |= FLAG_MOB_LOOT;
        if (config.autoPickupMobXp)        mask |= FLAG_MOB_XP;
        if (config.autoPickupSplitMobLoot) mask |= FLAG_SPLIT_LOOT;
        if (config.autoPickupSplitMobXp)   mask |= FLAG_SPLIT_XP;
        if (config.allowClientControl)     mask |= FLAG_ALLOW_CLIENT;

        final int finalMask = mask;
        context.getSource().sendFeedback(() -> Text.literal("[AP_GLOBAL] " + finalMask), false);
        return 1;
    }

    private static int encodeTristate(Boolean b) {
        if (b == null) return 0; // 00
        return b ? 1 : 2;        // 01 : 10
    }

    private static int executeSetPlayerConfig(CommandContext<ServerCommandSource> context, boolean silent) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "target");
        String key = StringArgumentType.getString(context, "key");
        String valueStr = StringArgumentType.getString(context, "value");

        Boolean value = switch (valueStr.toLowerCase()) {
            case "true" -> true;
            case "false" -> false;
            case "reset" -> null;
            default -> throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().create("Value must be true, false, or reset");
        };

        boolean isOp = checkPermission(source);
        boolean allowClientControl = AutoPickupConfig.getInstance().allowClientControl;

        for (ServerPlayerEntity target : targets) {
            boolean isSelf = source.isExecutedByPlayer() && source.getPlayer() == target;
            if (!isOp) {
                if (!allowClientControl || !isSelf) {
                    if (!silent) source.sendError(Text.literal("Permission denied."));
                    continue;
                }
            }
            PlayerConfigs.setAdminOverride(target.getUuid(), key, value);

            if (!silent) {
                Text valText = value == null ? Text.literal("RESET").formatted(Formatting.YELLOW) :
                        Text.literal(String.valueOf(value)).formatted(value ? Formatting.GREEN : Formatting.RED);
                source.sendFeedback(() -> Text.literal("Set override for " + target.getName().getString() + " [" + key + "] to ").append(valText), false);
            }
        }
        return 1;
    }

    private static int showServerStatus(CommandContext<ServerCommandSource> context) {
        AutoPickupConfig config = AutoPickupConfig.getInstance();
        ServerCommandSource s = context.getSource();
        s.sendFeedback(() -> Text.literal("--- AutoPickup Config ---").formatted(Formatting.LIGHT_PURPLE), false);
        s.sendFeedback(() -> formatStatus("Master", config.autoPickup), false);
        s.sendFeedback(() -> formatStatus("Blocks", config.autoPickupBlocks), false);
        return 1;
    }

    private static int setGlobal(CommandContext<ServerCommandSource> ctx, boolean silent) {
        String key = StringArgumentType.getString(ctx, "key");
        boolean value = BoolArgumentType.getBool(ctx, "value");
        AutoPickupConfig config = AutoPickupConfig.getInstance();

        switch (key) {
            case "master" -> config.autoPickup = value;
            case "blocks" -> config.autoPickupBlocks = value;
            case "blockXp" -> config.autoPickupBlockXp = value;
            case "mobLoot" -> config.autoPickupMobLoot = value;
            case "mobXp" -> config.autoPickupMobXp = value;
            case "splitMobLoot" -> config.autoPickupSplitMobLoot = value;
            case "splitMobXp" -> config.autoPickupSplitMobXp = value;
            case "allowClientControl" -> config.allowClientControl = value;
        }
        config.save();
        if (!silent) {
            ctx.getSource().sendFeedback(() -> Text.literal("Global [" + key + "] set to " + value).formatted(Formatting.GREEN), false);
        }
        return 1;
    }

    private static Text formatStatus(String name, boolean value) {
        return Text.literal(name + ": " + value);
    }

    @Environment(EnvType.CLIENT)
    public static void sendConfig() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int mask = 0;
        if (ClientConfigManager.isMaster())    mask |= FLAG_MASTER;
        if (ClientConfigManager.isBlocks())    mask |= FLAG_BLOCKS;
        if (ClientConfigManager.isBlockXp())   mask |= FLAG_BLOCK_XP;
        if (ClientConfigManager.isMobLoot())   mask |= FLAG_MOB_LOOT;
        if (ClientConfigManager.isMobXp())     mask |= FLAG_MOB_XP;
        if (ClientConfigManager.isSplitMobLoot()) mask |= FLAG_SPLIT_LOOT;
        if (ClientConfigManager.isSplitMobXp())   mask |= FLAG_SPLIT_XP;

        client.player.networkHandler.sendChatCommand("ap_config_sync " + mask);
    }
}