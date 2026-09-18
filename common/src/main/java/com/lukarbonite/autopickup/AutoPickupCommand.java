package com.lukarbonite.autopickup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.lukarbonite.autopickup.core.CommandLogic;
import com.lukarbonite.autopickup.platform.VersionHelper;
import net.minecraft.world.entity.Entity;
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

    private static final VersionHelper VH = VersionHelper.INSTANCE;

    // Bit flags for Boolean configs (Global & Client Sync); defined in core so 1.12.2 shares them.
    public static final int FLAG_MASTER         = CommandLogic.FLAG_MASTER;
    public static final int FLAG_BLOCKS         = CommandLogic.FLAG_BLOCKS;
    public static final int FLAG_BLOCK_XP       = CommandLogic.FLAG_BLOCK_XP;
    public static final int FLAG_MOB_LOOT       = CommandLogic.FLAG_MOB_LOOT;
    public static final int FLAG_MOB_XP         = CommandLogic.FLAG_MOB_XP;
    public static final int FLAG_SPLIT_LOOT     = CommandLogic.FLAG_SPLIT_LOOT;
    public static final int FLAG_SPLIT_XP       = CommandLogic.FLAG_SPLIT_XP;
    public static final int FLAG_ALLOW_CLIENT   = CommandLogic.FLAG_ALLOW_CLIENT;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("autopickup")
                .executes(AutoPickupCommand::showServerStatus)
                .then(Commands.literal("global")
                        .requires(AutoPickupCommand::checkPermission)
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(CommandLogic.GLOBAL_KEYS, b))
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
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(CommandLogic.PLAYER_KEY_SUGGESTIONS, b))
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(CommandLogic.OVERRIDE_VALUES, b))
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
                            VH.sendSuccess(ctx.getSource(), VH.literal("[AP_PERM] " + (op ? 1 : 0)), false);
                            return 1;
                        })
                )
                .then(Commands.literal("query_global")
                        .executes(AutoPickupCommand::executeQueryGlobal)
                )
                .then(Commands.literal("gui")
                        .executes(ctx -> {
                            VH.sendSuccess(ctx.getSource(), VH.literal("[AP_OPEN_GUI]"), false);
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
        ServerPlayer player = playerOf(source);
        if (player != null) {
            return VersionHelper.INSTANCE.isOp(player, source.getServer());
        }
        return true;
    }

    /** The player behind a command source, or null for the console and command blocks. */
    private static ServerPlayer playerOf(CommandSourceStack source) {
        Entity entity = source.getEntity();
        return entity instanceof ServerPlayer ? (ServerPlayer) entity : null;
    }

    private static int executeSync(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            int mask = IntegerArgumentType.getInteger(context, "mask");
            CommandLogic.applyClientSync(player.getUUID(), mask);
        } catch (Exception ignored) {}
        return 1;
    }

    private static int executeQueryPlayer(CommandContext<CommandSourceStack> context) {
        String targetName = StringArgumentType.getString(context, "targetName");
        // Search online players
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);

        if (target != null) {
            final int finalMask = CommandLogic.playerOverrideMask(target.getUUID());
            // Sending feedback directly to the command source (the admin)
            VH.sendSuccess(context.getSource(), VH.literal("[AP_DATA] " + targetName + " " + finalMask), false);
            return 1;
        } else {
            context.getSource().sendFailure(VH.literal("Player '" + targetName + "' not found or is offline."));
            return 0;
        }
    }

    private static int executeQueryGlobal(CommandContext<CommandSourceStack> context) {
        final int finalMask = CommandLogic.globalMask();
        VH.sendSuccess(context.getSource(), VH.literal("[AP_GLOBAL] " + finalMask), false);
        return 1;
    }

    private static int executeSetPlayerConfig(CommandContext<CommandSourceStack> context, boolean silent) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "target");
        String requestedKey = StringArgumentType.getString(context, "key");
        String valueStr = StringArgumentType.getString(context, "value");

        // UI sends "unset" to mean null (return to server default)
        Boolean value;
        try {
            value = CommandLogic.parseOverride(valueStr);
        } catch (IllegalArgumentException e) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException().create(e.getMessage());
        }

        boolean isOp = checkPermission(source);
        AutoPickupConfig config = AutoPickupConfig.getInstance();

        // Translate the "all" parameter into a loop over all valid keys
        List<String> keysToProcess = CommandLogic.expandPlayerKey(requestedKey);

        for (ServerPlayer target : targets) {
            boolean isSelf = playerOf(source) == target;

            for (String key : keysToProcess) {
                boolean allowed = isOp;
                if (!allowed && isSelf) {
                    allowed = CommandLogic.isClientAllowed(config, key);
                }

                if (!allowed) {
                    if (!silent) source.sendFailure(VH.literal("Permission denied for setting: " + key));
                    continue;
                }

                PlayerConfigs.setAdminOverride(target.getUUID(), key, value);

                if (!silent) {
                    Component valText = value == null ? VH.literal("RESET").withStyle(ChatFormatting.YELLOW) :
                            VH.literal(String.valueOf(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
                    VH.sendSuccess(source, VH.literal("Set override for ")
                            .append(target.getName())
                            .append(" [" + key + "] to ")
                            .append(valText), false);
                }
            }
        }
        return 1;
    }

    private static int showServerStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack s = context.getSource();

        VH.sendSuccess(s, VH.literal(CommandLogic.STATUS_TITLE).withStyle(ChatFormatting.LIGHT_PURPLE), false);

        // Header for columns
        VH.sendSuccess(s, VH.literal(CommandLogic.STATUS_HEADER), false);
        VH.sendSuccess(s, VH.literal(CommandLogic.STATUS_RULE).withStyle(ChatFormatting.GRAY), false);

        // Display all rows
        for (CommandLogic.StatusRow row : CommandLogic.statusRows()) {
            VH.sendSuccess(s, formatRow(row), false);
        }

        return 1;
    }

    private static Component formatRow(CommandLogic.StatusRow r) {
        MutableComponent row = VH.literal(r.label()).withStyle(ChatFormatting.WHITE);

        if (r.clientDecides) {
            row.append(VH.literal("CLIENT DECIDE").withStyle(ChatFormatting.AQUA));
        } else {
            row.append(VH.literal(r.value ? "ON" : "OFF").withStyle(r.value ? ChatFormatting.GREEN : ChatFormatting.RED));
        }

        return row;
    }

    private static int setGlobal(CommandContext<CommandSourceStack> ctx, boolean silent) {
        String key = StringArgumentType.getString(ctx, "key");
        boolean value = BoolArgumentType.getBool(ctx, "value");
        CommandLogic.setGlobal(key, value);
        if (!silent) {
            VH.sendSuccess(ctx.getSource(), VH.literal("Global [" + key + "] set to " + value).withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }
}
