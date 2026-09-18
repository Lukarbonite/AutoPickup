package com.lukarbonite.autopickup.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextFormatting;

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
        if (Minecraft.getMinecraft().player != null) {
            ClientNetworkManager.sendCommand("autopickup query_global");
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
        if (Minecraft.getMinecraft().player != null) {
            ClientNetworkManager.sendCommand("autopickup global " + key + " " + val + " silent");
        }
    }

    public static void sendPlayer(String target, String key, Tristate val) {
        if (target.isEmpty() || Minecraft.getMinecraft().player == null) return;
        ClientNetworkManager.sendCommand("autopickup setPlayerConfig " + target + " " + key + " " + val.val + " silent");
    }

    // --- Enums ---
    public enum Tristate {
        UNSET("unset", TextFormatting.GRAY),
        RESET("reset", TextFormatting.YELLOW),
        TRUE("true", TextFormatting.GREEN),
        FALSE("false", TextFormatting.RED);

        public final String val;
        public final TextFormatting fmt;

        Tristate(String v, TextFormatting f) { this.val = v; this.fmt = f; }
        public String getText() { return fmt + name(); }

        public static Tristate fromEncoded(int i) {
            switch (i) {
                case 1: return TRUE;
                case 2: return FALSE;
                case 3: return RESET;
                default: return UNSET;
            }
        }
    }

    public enum ServerControl {
        ON("ON", TextFormatting.GREEN),
        OFF("OFF", TextFormatting.RED),
        CLIENT("CLIENT DECIDE", TextFormatting.AQUA);

        public final String label;
        public final TextFormatting fmt;

        ServerControl(String l, TextFormatting f) { this.label = l; this.fmt = f; }
        public String getText() { return fmt + label; }

        public static ServerControl fromState(boolean val, boolean allow) {
            if (allow) return CLIENT;
            return val ? ON : OFF;
        }
    }
}
