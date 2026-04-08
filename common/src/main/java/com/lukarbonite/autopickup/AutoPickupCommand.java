package com.lukarbonite.autopickup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.Collection;
import java.util.List;

public class AutoPickupCommand {

    // Bit flags for Boolean configs (Global & Client Sync)
    public static final int FLAG_MASTER         = 1;       // Bit 0
    public static final int FLAG_BLOCKS         = 2;       // Bit 1
    public static final int FLAG_BLOCK_XP       = 4;       // Bit 2
    public static final int FLAG_MOB_LOOT       = 8;       // Bit 3
    public static final int FLAG_MOB_XP         = 16;      // Bit 4
    public static final int FLAG_SPLIT_LOOT     = 32;      // Bit 5
    public static final int FLAG_SPLIT_XP       = 64;      // Bit 6
    public static final int FLAG_ALLOW_CLIENT   = 128;     // Bit 7 (Global only)

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("autopickup")
                .executes(AutoPickupCommand::showServerStatus)
                .then(Commands.literal("global")
                        .requires(AutoPickupCommand::checkPermission)
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        List.of("master", "allow_master", "blocks", "allow_blocks", "blockXp", "allow_blockXp",
                                                "mobLoot", "allow_mobLoot", "mobXp", "allow_mobXp", "splitMobLoot", "allow_splitMobLoot",
                                                "splitMobXp", "allow_splitMobXp"), b))
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setGlobal(ctx, false))
                                        .then(Commands.literal("silent")
                                                .executes(ctx -> setGlobal(ctx, true)))
                                )
                        )
                )
                .then(Commands.literal("setPlayerConfig")
                        .then(Commands.argument("target", EntityArgument.players())
                                .requires(source -> true)
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                List.of("master","blocks","blockXp","mobLoot","mobXp","splitMobLoot","splitMobXp","all"), b))
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("true","false","reset"), b))
                                                .executes(ctx -> executeSetPlayerConfig(ctx, false))
                                                .then(Commands.literal("silent")
                                                        .executes(ctx -> executeSetPlayerConfig(ctx, true)))
                                        )
                                )
                        )
                )
                .then(Commands.literal("query_player")
                        .requires(AutoPickupCommand::checkPermission)
                        .then(Commands.argument("targetName", StringArgumentType.string())
                                .executes(AutoPickupCommand::executeQueryPlayer))
                )
                .then(Commands.literal("check_perm")
                        .executes(ctx -> {
                            boolean op = checkPermission(ctx.getSource());
                            ctx.getSource().sendSuccess(() -> Component.literal("[AP_PERM] " + (op ? 1 : 0)), false);
                            return 1;
                        })
                )
                .then(Commands.literal("query_global")
                        .executes(AutoPickupCommand::executeQueryGlobal)
                )
                .then(Commands.literal("gui")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal("[AP_OPEN_GUI]"), false);
                            return 1;
                        })
                )
        );

        dispatcher.register(Commands.literal("ap_config_sync")
                .requires(source -> true)
                .then(Commands.argument("mask", IntegerArgumentType.integer())
                        .executes(AutoPickupCommand::executeSync))
        );
    }

    private static boolean checkPermission(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            return source.getServer().getPlayerList().isOp(player.nameAndId());
        }
        return true;
    }

    private static int executeSync(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            int mask = IntegerArgumentType.getInteger(context, "mask");
            PlayerConfigs.setClientPreference(player.getUUID(),
                    (mask & FLAG_MASTER) != 0, (mask & FLAG_BLOCKS) != 0, (mask & FLAG_BLOCK_XP) != 0,
                    (mask & FLAG_MOB_LOOT) != 0, (mask & FLAG_MOB_XP) != 0, (mask & FLAG_SPLIT_LOOT) != 0, (mask & FLAG_SPLIT_XP) != 0);
        } catch (Exception ignored) {}
        return 1;
    }

    private static int executeQueryPlayer(CommandContext<CommandSourceStack> context) {
        String targetName = StringArgumentType.getString(context, "targetName");
        // Search online players
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);

        if (target != null) {
            PlayerConfigs.PlayerState s = PlayerConfigs.getState(target.getUUID());

            int mask = 0;
            mask |= encodeTristate(s.overrideMaster) << 0;
            mask |= encodeTristate(s.overrideBlocks) << 2;
            mask |= encodeTristate(s.overrideBlockXp) << 4;
            mask |= encodeTristate(s.overrideMobLoot) << 6;
            mask |= encodeTristate(s.overrideMobXp) << 8;
            mask |= encodeTristate(s.overrideSplitMobLoot) << 10;
            mask |= encodeTristate(s.overrideSplitMobXp) << 12;

            final int finalMask = mask;
            // Sending feedback directly to the command source (the admin)
            context.getSource().sendSuccess(() -> Component.literal("[AP_DATA] " + targetName + " " + finalMask), false);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Player '" + targetName + "' not found or is offline."));
            return 0;
        }
    }

    private static int executeQueryGlobal(CommandContext<CommandSourceStack> context) {
        AutoPickupConfig config = AutoPickupConfig.getInstance();
        int mask = 0;

        // Pack bits in pairs to match the UI loop (i*2 = val, i*2+1 = allow)
        mask |= (config.autoPickup ? 1 : 0) << 0;
        mask |= (config.allowMaster ? 1 : 0) << 1;

        mask |= (config.autoPickupBlocks ? 1 : 0) << 2;
        mask |= (config.allowBlocks ? 1 : 0) << 3;

        mask |= (config.autoPickupBlockXp ? 1 : 0) << 4;
        mask |= (config.allowBlockXp ? 1 : 0) << 5;

        mask |= (config.autoPickupMobLoot ? 1 : 0) << 6;
        mask |= (config.allowMobLoot ? 1 : 0) << 7;

        mask |= (config.autoPickupMobXp ? 1 : 0) << 8;
        mask |= (config.allowMobXp ? 1 : 0) << 9;

        mask |= (config.autoPickupSplitMobLoot ? 1 : 0) << 10;
        mask |= (config.allowSplitMobLoot ? 1 : 0) << 11;

        mask |= (config.autoPickupSplitMobXp ? 1 : 0) << 12;
        mask |= (config.allowSplitMobXp ? 1 : 0) << 13;

        final int finalMask = mask;
        context.getSource().sendSuccess(() -> Component.literal("[AP_GLOBAL] " + finalMask), false);
        return 1;
    }

    private static int encodeTristate(Boolean b) {
        if (b == null) return 0; // Server treats both Unset and Reset as null
        return b ? 1 : 2;        // 1 = True, 2 = False
    }

    private static int executeSetPlayerConfig(CommandContext<CommandSourceStack> context, boolean silent) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "target");
        String requestedKey = StringArgumentType.getString(context, "key");
        String valueStr = StringArgumentType.getString(context, "value");

        // UI sends "unset" to mean null (return to server default)
        Boolean value = switch (valueStr.toLowerCase()) {
            case "true" -> true;
            case "false" -> false;
            case "reset", "unset" -> null;
            default -> throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().create("Value must be true, false, or reset");
        };

        boolean isOp = checkPermission(source);
        AutoPickupConfig config = AutoPickupConfig.getInstance();

        // Translate the "all" parameter into a loop over all valid keys
        List<String> keysToProcess = requestedKey.equalsIgnoreCase("all")
                ? List.of("master", "blocks", "blockXp", "mobLoot", "mobXp", "splitMobLoot", "splitMobXp")
                : List.of(requestedKey);

        for (ServerPlayer target : targets) {
            boolean isSelf = source.isPlayer() && source.getPlayer() == target;

            for (String key : keysToProcess) {
                boolean allowed = isOp;
                if (!allowed && isSelf) {
                    allowed = switch (key.toLowerCase()) {
                        case "master" -> config.allowMaster;
                        case "blocks" -> config.allowBlocks;
                        case "blockxp" -> config.allowBlockXp;
                        case "mobloot" -> config.allowMobLoot;
                        case "mobxp" -> config.allowMobXp;
                        case "splitmobloot" -> config.allowSplitMobLoot;
                        case "splitmobxp" -> config.allowSplitMobXp;
                        default -> false;
                    };
                }

                if (!allowed) {
                    if (!silent) source.sendFailure(Component.literal("Permission denied for setting: " + key));
                    continue;
                }

                PlayerConfigs.setAdminOverride(target.getUUID(), key, value);

                if (!silent) {
                    Component valText = value == null ? Component.literal("RESET").withStyle(ChatFormatting.YELLOW) :
                            Component.literal(String.valueOf(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
                    source.sendSuccess(() -> Component.literal("Set override for ")
                            .append(target.getName())
                            .append(" [" + key + "] to ")
                            .append(valText), false);
                }
            }
        }
        return 1;
    }

    private static int showServerStatus(CommandContext<CommandSourceStack> context) {
        AutoPickupConfig config = AutoPickupConfig.getInstance();
        CommandSourceStack s = context.getSource();

        s.sendSuccess(() -> Component.literal("--- AutoPickup Global Configuration ---").withStyle(ChatFormatting.LIGHT_PURPLE), false);

        // Header for columns
        s.sendSuccess(() -> Component.literal(String.format("%-20s | %s", "Feature", "Server State")), false);
        s.sendSuccess(() -> Component.literal("---------------------------------------------").withStyle(ChatFormatting.GRAY), false);

        // Display all rows
        s.sendSuccess(() -> formatRow("Master Toggle", config.autoPickup, config.allowMaster), false);
        s.sendSuccess(() -> formatRow("Blocks", config.autoPickupBlocks, config.allowBlocks), false);
        s.sendSuccess(() -> formatRow("Block XP", config.autoPickupBlockXp, config.allowBlockXp), false);
        s.sendSuccess(() -> formatRow("Mob Loot", config.autoPickupMobLoot, config.allowMobLoot), false);
        s.sendSuccess(() -> formatRow("Mob XP", config.autoPickupMobXp, config.allowMobXp), false);
        s.sendSuccess(() -> formatRow("Split Mob Loot", config.autoPickupSplitMobLoot, config.allowSplitMobLoot), false);
        s.sendSuccess(() -> formatRow("Split Mob XP", config.autoPickupSplitMobXp, config.allowSplitMobXp), false);

        return 1;
    }

    private static Component formatRow(String name, boolean value, boolean allowed) {
        MutableComponent row = Component.literal(String.format("%-20s | ", name)).withStyle(ChatFormatting.WHITE);

        if (allowed) {
            row.append(Component.literal("CLIENT DECIDE").withStyle(ChatFormatting.AQUA));
        } else {
            row.append(Component.literal(value ? "ON" : "OFF").withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
        }

        return row;
    }

    private static int setGlobal(CommandContext<CommandSourceStack> ctx, boolean silent) {
        String key = StringArgumentType.getString(ctx, "key");
        boolean value = BoolArgumentType.getBool(ctx, "value");
        AutoPickupConfig config = AutoPickupConfig.getInstance();

        switch (key) {
            case "master" -> config.autoPickup = value;
            case "allow_master" -> config.allowMaster = value;

            case "blocks" -> config.autoPickupBlocks = value;
            case "allow_blocks" -> config.allowBlocks = value;

            case "blockXp" -> config.autoPickupBlockXp = value;
            case "allow_blockXp" -> config.allowBlockXp = value;

            case "mobLoot" -> config.autoPickupMobLoot = value;
            case "allow_mobLoot" -> config.allowMobLoot = value;

            case "mobXp" -> config.autoPickupMobXp = value;
            case "allow_mobXp" -> config.allowMobXp = value;

            case "splitMobLoot" -> config.autoPickupSplitMobLoot = value;
            case "allow_splitMobLoot" -> config.allowSplitMobLoot = value;

            case "splitMobXp" -> config.autoPickupSplitMobXp = value;
            case "allow_splitMobXp" -> config.allowSplitMobXp = value;
        }

        config.save();
        if (!silent) {
            ctx.getSource().sendSuccess(() -> Component.literal("Global [" + key + "] set to " + value).withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }
}