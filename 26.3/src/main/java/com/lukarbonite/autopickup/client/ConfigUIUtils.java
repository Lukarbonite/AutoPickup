package com.lukarbonite.autopickup.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class ConfigUIUtils {

    public static final String[] NAMES = {
            "Master Toggle", "Pickup Blocks", "Pickup Block XP",
            "Pickup Mob Loot", "Pickup Mob XP", "Split Mob Loot", "Split Mob XP"
    };

    public static final String[] KEYS = {
            "master", "blocks", "blockXp", "mobLoot", "mobXp", "splitMobLoot", "splitMobXp"
    };

    // --- State Snapshots ---
    public static final boolean[] sValsSnap = new boolean[7];
    public static final boolean[] sAllowsSnap = new boolean[7];
    public static final Tristate[] pValsSnap = new Tristate[7];

    public static void updateSnapshots() {
        for (int i = 0; i < 7; i++) {
            sValsSnap[i] = ClientSyncHandler.sVals[i];
            sAllowsSnap[i] = ClientSyncHandler.sAllows[i];
            pValsSnap[i] = Tristate.fromEncoded(ClientSyncHandler.pValsEncoded[i]);
        }
    }

    public static void requestGlobalConfig() {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.connection.sendCommand("autopickup query_global");
        }
    }

    // --- Shared Network/Admin Logic ---
    public static void applyAdminChanges() {
        if (!ClientSyncHandler.serverDataReceived) return;
        for (int i = 0; i < 7; i++) {
            if (ClientSyncHandler.sVals[i] != sValsSnap[i]) {
                sendGlobal(KEYS[i], ClientSyncHandler.sVals[i]);
            }
            if (ClientSyncHandler.sAllows[i] != sAllowsSnap[i]) {
                sendGlobal("allow_" + KEYS[i], ClientSyncHandler.sAllows[i]);
            }

            Tristate currentP = Tristate.fromEncoded(ClientSyncHandler.pValsEncoded[i]);
            if (ClientSyncHandler.targetPlayerName != null && !ClientSyncHandler.targetPlayerName.isEmpty() && currentP != pValsSnap[i]) {
                sendPlayer(ClientSyncHandler.targetPlayerName, KEYS[i], currentP);
            }
        }
        updateSnapshots();
    }

    public static void sendGlobal(String key, boolean val) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.connection.sendCommand("autopickup global " + key + " " + val + " silent");
        }
    }

    public static void sendPlayer(String target, String key, Tristate val) {
        if (target.isEmpty() || Minecraft.getInstance().player == null) return;
        Minecraft.getInstance().player.connection.sendCommand("autopickup setPlayerConfig " + target + " " + key + " " + val.val + " silent");
    }

    // --- Enums ---
    public enum Tristate {
        UNSET("unset", ChatFormatting.GRAY),
        RESET("reset", ChatFormatting.YELLOW),
        TRUE("true", ChatFormatting.GREEN),
        FALSE("false", ChatFormatting.RED);

        public final String val;
        public final ChatFormatting fmt;

        Tristate(String v, ChatFormatting f) { this.val = v; this.fmt = f; }
        public Component getText() { return Component.literal(name()).withStyle(fmt); }

        public static Tristate fromEncoded(int i) {
            return switch(i) { case 1 -> TRUE; case 2 -> FALSE; case 3 -> RESET; default -> UNSET; };
        }
    }

    public enum ServerControl {
        ON("ON", ChatFormatting.GREEN),
        OFF("OFF", ChatFormatting.RED),
        CLIENT("CLIENT DECIDE", ChatFormatting.AQUA);

        public final String label;
        public final ChatFormatting fmt;

        ServerControl(String l, ChatFormatting f) { this.label = l; this.fmt = f; }
        public Component getText() { return Component.literal(label).withStyle(fmt); }

        public static ServerControl fromState(boolean val, boolean allow) {
            if (allow) return CLIENT;
            return val ? ON : OFF;
        }
    }
}