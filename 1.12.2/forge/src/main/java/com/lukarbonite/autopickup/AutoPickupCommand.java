package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.core.CommandLogic;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@code /autopickup} on 1.12.2, which has no Brigadier: the same subcommands as common/'s
 * {@code AutoPickupCommand}, parsed by hand, with the behavior shared through {@link CommandLogic}.
 * {@code /ap_config_sync} is {@link SyncCommand}.
 */
public class AutoPickupCommand extends CommandBase {

    // Bit flags for Boolean configs (Global & Client Sync); defined in core so every version shares them.
    public static final int FLAG_MASTER         = CommandLogic.FLAG_MASTER;
    public static final int FLAG_BLOCKS         = CommandLogic.FLAG_BLOCKS;
    public static final int FLAG_BLOCK_XP       = CommandLogic.FLAG_BLOCK_XP;
    public static final int FLAG_MOB_LOOT       = CommandLogic.FLAG_MOB_LOOT;
    public static final int FLAG_MOB_XP         = CommandLogic.FLAG_MOB_XP;
    public static final int FLAG_SPLIT_LOOT     = CommandLogic.FLAG_SPLIT_LOOT;
    public static final int FLAG_SPLIT_XP       = CommandLogic.FLAG_SPLIT_XP;
    public static final int FLAG_ALLOW_CLIENT   = CommandLogic.FLAG_ALLOW_CLIENT;

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "global", "setPlayerConfig", "query_player", "check_perm", "query_global", "gui");

    @Override
    public String getName() { return "autopickup"; }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/autopickup [global|setPlayerConfig|query_player|check_perm|query_global|gui]";
    }

    /**
     * Everyone may run it, like the Brigadier version; admin-only subcommands check {@link #isOp}
     * themselves. (1.12.2 only lets non-ops run a handful of vanilla commands whatever the level.)
     */
    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }

    @Override
    public int getRequiredPermissionLevel() { return 0; }

    /** Ops (and the console or command blocks) may change server-wide settings. */
    static boolean isOp(MinecraftServer server, ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            return server.getPlayerList().canSendCommands(((EntityPlayerMP) sender).getGameProfile());
        }
        return true;
    }

    static ITextComponent text(String s, TextFormatting color) {
        ITextComponent c = new TextComponentString(s);
        if (color != null) c.setStyle(new Style().setColor(color));
        return c;
    }

    private static void reply(ICommandSender sender, ITextComponent message) { sender.sendMessage(message); }

    private static void fail(ICommandSender sender, String message) { reply(sender, text(message, TextFormatting.RED)); }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            showServerStatus(sender);
            return;
        }
        switch (args[0]) {
            case "global":
                requireOp(server, sender);
                if (args.length < 3) throw new WrongUsageException("/autopickup global <key> <true|false> [silent]");
                setGlobal(sender, args[1], parseBoolean(args[2]), isSilent(args, 3));
                return;
            case "setPlayerConfig":
                if (args.length < 4) throw new WrongUsageException("/autopickup setPlayerConfig <target> <key> <true|false|reset> [silent]");
                setPlayerConfig(server, sender, args[1], args[2], args[3], isSilent(args, 4));
                return;
            case "query_player":
                requireOp(server, sender);
                if (args.length < 2) throw new WrongUsageException("/autopickup query_player <name>");
                queryPlayer(server, sender, args[1]);
                return;
            case "check_perm":
                reply(sender, text("[AP_PERM] " + (isOp(server, sender) ? 1 : 0), null));
                return;
            case "query_global":
                reply(sender, text("[AP_GLOBAL] " + CommandLogic.globalMask(), null));
                return;
            case "gui":
                reply(sender, text("[AP_OPEN_GUI]", null));
                return;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private static void requireOp(MinecraftServer server, ICommandSender sender) throws CommandException {
        if (!isOp(server, sender)) throw new CommandException("commands.generic.permission");
    }

    private static boolean isSilent(String[] args, int index) {
        return args.length > index && args[index].equals("silent");
    }

    private static void setGlobal(ICommandSender sender, String key, boolean value, boolean silent) {
        CommandLogic.setGlobal(key, value);
        if (!silent) reply(sender, text("Global [" + key + "] set to " + value, TextFormatting.GREEN));
    }

    private static void queryPlayer(MinecraftServer server, ICommandSender sender, String targetName) {
        EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(targetName);
        if (target != null) {
            // Sending feedback directly to the command source (the admin)
            reply(sender, text("[AP_DATA] " + targetName + " " + CommandLogic.playerOverrideMask(target.getUniqueID()), null));
        } else {
            fail(sender, "Player '" + targetName + "' not found or is offline.");
        }
    }

    private static void setPlayerConfig(MinecraftServer server, ICommandSender sender, String targetArg,
                                        String requestedKey, String valueStr, boolean silent) throws CommandException {
        List<EntityPlayerMP> targets = getPlayers(server, sender, targetArg);

        // UI sends "unset" to mean null (return to server default)
        Boolean value;
        try {
            value = CommandLogic.parseOverride(valueStr);
        } catch (IllegalArgumentException e) {
            throw new CommandException(e.getMessage());
        }

        boolean isOp = isOp(server, sender);
        AutoPickupConfig config = AutoPickupConfig.getInstance();

        // Translate the "all" parameter into a loop over all valid keys
        List<String> keysToProcess = CommandLogic.expandPlayerKey(requestedKey);

        for (EntityPlayerMP target : targets) {
            boolean isSelf = sender == target;

            for (String key : keysToProcess) {
                boolean allowed = isOp;
                if (!allowed && isSelf) {
                    allowed = CommandLogic.isClientAllowed(config, key);
                }

                if (!allowed) {
                    if (!silent) fail(sender, "Permission denied for setting: " + key);
                    continue;
                }

                PlayerConfigs.setAdminOverride(target.getUniqueID(), key, value);

                if (!silent) {
                    ITextComponent valText = value == null ? text("RESET", TextFormatting.YELLOW)
                            : text(String.valueOf(value), value ? TextFormatting.GREEN : TextFormatting.RED);
                    reply(sender, text("Set override for ", null)
                            .appendSibling(target.getDisplayName())
                            .appendText(" [" + key + "] to ")
                            .appendSibling(valText));
                }
            }
        }
    }

    private static void showServerStatus(ICommandSender sender) {
        reply(sender, text(CommandLogic.STATUS_TITLE, TextFormatting.LIGHT_PURPLE));

        // Header for columns
        reply(sender, text(CommandLogic.STATUS_HEADER, null));
        reply(sender, text(CommandLogic.STATUS_RULE, TextFormatting.GRAY));

        // Display all rows
        for (CommandLogic.StatusRow r : CommandLogic.statusRows()) {
            ITextComponent row = text(r.label(), TextFormatting.WHITE);
            if (r.clientDecides) {
                row.appendSibling(text("CLIENT DECIDE", TextFormatting.AQUA));
            } else {
                row.appendSibling(text(r.value ? "ON" : "OFF", r.value ? TextFormatting.GREEN : TextFormatting.RED));
            }
            reply(sender, row);
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        boolean op = isOp(server, sender);
        switch (args[0]) {
            case "global":
                if (!op) break;
                if (args.length == 2) return getListOfStringsMatchingLastWord(args, CommandLogic.GLOBAL_KEYS);
                if (args.length == 3) return getListOfStringsMatchingLastWord(args, "true", "false");
                if (args.length == 4) return getListOfStringsMatchingLastWord(args, "silent");
                break;
            case "setPlayerConfig":
                if (args.length == 2) return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
                if (args.length == 3) return getListOfStringsMatchingLastWord(args, CommandLogic.PLAYER_KEY_SUGGESTIONS);
                if (args.length == 4) return getListOfStringsMatchingLastWord(args, CommandLogic.OVERRIDE_VALUES);
                if (args.length == 5) return getListOfStringsMatchingLastWord(args, "silent");
                break;
            case "query_player":
                if (op && args.length == 2) return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
                break;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 1 && args.length > 0 && args[0].equals("setPlayerConfig");
    }

    /** {@code /ap_config_sync <mask>}: the client's preferences, sent on join and whenever they change. */
    public static class SyncCommand extends CommandBase {

        @Override
        public String getName() { return "ap_config_sync"; }

        @Override
        public String getUsage(ICommandSender sender) { return "/ap_config_sync <mask>"; }

        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }

        @Override
        public int getRequiredPermissionLevel() { return 0; }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
            if (!(sender instanceof EntityPlayerMP) || args.length < 1) return;
            try {
                CommandLogic.applyClientSync(((EntityPlayerMP) sender).getUniqueID(), Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {}
        }
    }
}
