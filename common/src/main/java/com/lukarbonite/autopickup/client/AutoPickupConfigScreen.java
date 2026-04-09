package com.lukarbonite.autopickup.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class AutoPickupConfigScreen extends Screen {

    private final boolean[] ui_sVals = new boolean[7];
    private final boolean[] ui_sAllows = new boolean[7];
    private final int[] ui_pVals = new int[7];

    private final Screen parent;
    private int currentTab = 0; // 0 = Client, 1 = Server, 2 = Player

    public AutoPickupConfigScreen(Screen parent) {
        super(Component.literal("Auto Pickup Config"));
        this.parent = parent;
    }

    public static Screen create(Screen parent) {
        ConfigUIUtils.updateSnapshots();
        ConfigUIUtils.requestGlobalConfig();
        return new AutoPickupConfigScreen(parent);
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < 7; i++) {
            ui_sVals[i] = ClientSyncHandler.sVals[i];
            ui_sAllows[i] = ClientSyncHandler.sAllows[i];
            ui_pVals[i] = ClientSyncHandler.pValsEncoded[i];
        }

        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int startY = 50;

        // --- TABS ---
        this.addRenderableWidget(Button.builder(Component.literal("Client Settings"), btn -> switchTab(0))
                .bounds(leftX, 20, 100, 20).build()).active = (currentTab != 0);
        if (ClientSyncHandler.isAdmin) {
            this.addRenderableWidget(Button.builder(Component.literal("Server Config"), btn -> switchTab(1))
                    .bounds(this.width / 2 - 50, 20, 100, 20).build()).active = (currentTab != 1);
            this.addRenderableWidget(Button.builder(Component.literal("Player Mgmt"), btn -> switchTab(2))
                    .bounds(rightX + 50, 20, 100, 20).build()).active = (currentTab != 2);
        }

        // --- TAB CONTENT ---
        if (currentTab == 0) {
            ClientConfigManager.ClientProfile profile = ClientConfigManager.getProfile();
            addClientToggle(leftX, startY, "Master Toggle", profile.master, v -> profile.master = v, ClientConfigManager.allowMaster);
            addClientToggle(rightX, startY, "Pickup Blocks", profile.blocks, v -> profile.blocks = v, ClientConfigManager.allowBlocks);
            addClientToggle(leftX, startY + 24, "Pickup Block XP", profile.blockXp, v -> profile.blockXp = v, ClientConfigManager.allowBlockXp);
            addClientToggle(rightX, startY + 24, "Pickup Mob Loot", profile.mobLoot, v -> profile.mobLoot = v, ClientConfigManager.allowMobLoot);
            addClientToggle(leftX, startY + 48, "Pickup Mob XP", profile.mobXp, v -> profile.mobXp = v, ClientConfigManager.allowMobXp);
            addClientToggle(rightX, startY + 48, "Split Mob Loot", profile.splitMobLoot, v -> profile.splitMobLoot = v, ClientConfigManager.allowSplitMobLoot);
            addClientToggle(leftX, startY + 72, "Split Mob XP", profile.splitMobXp, v -> profile.splitMobXp = v, ClientConfigManager.allowSplitMobXp);

        } else if (currentTab == 1 && ClientSyncHandler.isAdmin) {
            for (int i = 0; i < 7; i++) {
                int x = (i % 2 == 0) ? leftX : rightX;
                int y = startY + (i / 2) * 24;
                final int idx = i;

                this.addRenderableWidget(CycleButton.builder(ConfigUIUtils.ServerControl::getText)
                        .withInitialValue(ConfigUIUtils.ServerControl.fromState(ui_sVals[idx], ui_sAllows[idx]))
                        .withValues(ConfigUIUtils.ServerControl.values())
                        .create(x, y, 150, 20, Component.literal(ConfigUIUtils.NAMES[idx]), (btn, val) -> {
                            if (val == ConfigUIUtils.ServerControl.ON) { ClientSyncHandler.sAllows[idx] = false; ClientSyncHandler.sVals[idx] = true; }
                            else if (val == ConfigUIUtils.ServerControl.OFF) { ClientSyncHandler.sAllows[idx] = false; ClientSyncHandler.sVals[idx] = false; }
                            else { ClientSyncHandler.sAllows[idx] = true; }

                            ui_sVals[idx] = ClientSyncHandler.sVals[idx];
                            ui_sAllows[idx] = ClientSyncHandler.sAllows[idx];
                        }));
            }
        } else if (currentTab == 2 && ClientSyncHandler.isAdmin) {
            EditBox targetBox = new EditBox(this.font, leftX, startY, 150, 20, Component.literal("Target Player"));
            targetBox.setMaxLength(16);
            if (ClientSyncHandler.targetPlayerName != null) targetBox.setValue(ClientSyncHandler.targetPlayerName);
            targetBox.setResponder(val -> ClientSyncHandler.targetPlayerName = val);
            this.addRenderableWidget(targetBox);

            this.addRenderableWidget(Button.builder(Component.literal("Search / Load"), btn -> {
                String name = targetBox.getValue();
                if (name != null && !name.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.connection.sendCommand("autopickup query_player " + name);
                }
            }).bounds(rightX, startY, 150, 20).build());

            int playerStartY = startY + 30;
            for (int i = 0; i < 7; i++) {
                int x = (i % 2 == 0) ? leftX : rightX;
                int y = playerStartY + (i / 2) * 24;
                final int idx = i;

                this.addRenderableWidget(CycleButton.builder(ConfigUIUtils.Tristate::getText)
                        .withInitialValue(ConfigUIUtils.Tristate.fromEncoded(ui_pVals[idx]))
                        .withValues(ConfigUIUtils.Tristate.values())
                        .create(x, y, 150, 20, Component.literal("Override " + ConfigUIUtils.NAMES[idx]), (btn, val) -> {
                            if (val == ConfigUIUtils.Tristate.TRUE) ClientSyncHandler.pValsEncoded[idx] = 1;
                            else if (val == ConfigUIUtils.Tristate.FALSE) ClientSyncHandler.pValsEncoded[idx] = 2;
                            else if (val == ConfigUIUtils.Tristate.RESET) ClientSyncHandler.pValsEncoded[idx] = 3;
                            else ClientSyncHandler.pValsEncoded[idx] = 0;

                            ui_pVals[idx] = ClientSyncHandler.pValsEncoded[idx];
                        }));
            }
        }

        // --- DONE BUTTON ---
        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    private void addClientToggle(int x, int y, String name, boolean currentVal, Consumer<Boolean> setter, boolean allowed) {
        CycleButton<Boolean> btn = CycleButton.onOffBuilder(currentVal)
                .create(x, y, 150, 20, Component.literal(name), (b, val) -> setter.accept(val));
        btn.active = this.minecraft == null || this.minecraft.player == null || allowed;
        this.addRenderableWidget(btn);
    }

    private void switchTab(int tab) {
        if (this.currentTab != tab) {
            this.currentTab = tab;
            this.rebuildWidgets();
        }
    }

    @Override
    public void tick() {
        super.tick();
        boolean needsRefresh = false;

        for (int i = 0; i < 7; i++) {
            if (ui_sVals[i] != ClientSyncHandler.sVals[i] ||
                    ui_sAllows[i] != ClientSyncHandler.sAllows[i] ||
                    ui_pVals[i] != ClientSyncHandler.pValsEncoded[i]) {
                needsRefresh = true;
                break;
            }
        }

        if (needsRefresh) {
            this.rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        ClientConfigManager.save();
        ClientNetworkManager.sendConfig();
        if (ClientSyncHandler.isAdmin) ConfigUIUtils.applyAdminChanges();

        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}