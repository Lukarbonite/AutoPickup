package com.lukarbonite.autopickup.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.function.Supplier;

public class ClientSyncHandler {
    public static boolean isAdmin = false;
    public static boolean serverDataReceived = false;

    public static final boolean[] sVals = new boolean[7];
    public static final boolean[] sAllows = new boolean[7];
    public static final int[] pValsEncoded = new int[7];

    public static String targetPlayerName = "";

    public static Supplier<Screen> yaclScreenFactory = null;

    static {
        boolean[] defaults = {true, true, true, false, false, false, false};
        for (int i = 0; i < 7; i++) {
            sVals[i] = defaults[i];
            sAllows[i] = true;
        }
    }

    public static void handleDataResponse(String msg) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            try {
                if (msg.startsWith("[AP_PERM] ")) {
                    isAdmin = msg.substring(10).trim().equals("1");
                    return;
                }

                if (msg.startsWith("[AP_GLOBAL] ")) {
                    int mask = Integer.parseInt(msg.substring(12).trim());
                    serverDataReceived = true;
                    for (int i = 0; i < 7; i++) {
                        sVals[i] = (mask & (1 << (i * 2))) != 0;
                        sAllows[i] = (mask & (1 << (i * 2 + 1))) != 0;
                    }
                    updateClientAllowances();
                }

                if (msg.startsWith("[AP_DATA] ")) {
                    String[] parts = msg.split(" ");
                    if (parts.length < 3) return;
                    int mask = Integer.parseInt(parts[2]);
                    for (int i = 0; i < 7; i++) {
                        pValsEncoded[i] = (mask >> (i * 2)) & 3;
                    }
                }

                if (msg.startsWith("[AP_OPEN_GUI]")) {
                    client.execute(() -> {
                        if (yaclScreenFactory != null)
                            client.setScreen(yaclScreenFactory.get());
                        else
                            client.setScreen(AutoPickupConfigScreen.create(null));
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void updateClientAllowances() {
        ClientConfigManager.allowMaster = sAllows[0];
        ClientConfigManager.allowBlocks = sAllows[1];
        ClientConfigManager.allowBlockXp = sAllows[2];
        ClientConfigManager.allowMobLoot = sAllows[3];
        ClientConfigManager.allowMobXp = sAllows[4];
        ClientConfigManager.allowSplitMobLoot = sAllows[5];
        ClientConfigManager.allowSplitMobXp = sAllows[6];
    }
}
