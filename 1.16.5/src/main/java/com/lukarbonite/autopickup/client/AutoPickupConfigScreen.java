package com.lukarbonite.autopickup.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.function.Consumer;

public class AutoPickupConfigScreen extends Screen {

    private final boolean[] ui_sVals = new boolean[7];
    private final boolean[] ui_sAllows = new boolean[7];
    private final int[] ui_pVals = new int[7];

    private final Screen parent;
    private int currentTab = 0; // 0 = Client, 1 = Server, 2 = Player

    private boolean listeningForKey = false;
    private Button keybindBtn = null;

    public AutoPickupConfigScreen(Screen parent) {
        super(new TextComponent("Auto Pickup Config"));
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
        listeningForKey = false; // reset on any rebuild

        for (int i = 0; i < 7; i++) {
            ui_sVals[i] = ClientSyncHandler.sVals[i];
            ui_sAllows[i] = ClientSyncHandler.sAllows[i];
            ui_pVals[i] = ClientSyncHandler.pValsEncoded[i];
        }

        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int startY = 50;

        // --- TABS ---
        this.addButton(new Button(leftX, 20, 100, 20, new TextComponent("Client Settings"), btn -> switchTab(0))).active = (currentTab != 0);
        if (ClientSyncHandler.isAdmin) {
            this.addButton(new Button(this.width / 2 - 50, 20, 100, 20, new TextComponent("Server Config"), btn -> switchTab(1))).active = (currentTab != 1);
            this.addButton(new Button(rightX + 50, 20, 100, 20, new TextComponent("Player Mgmt"), btn -> switchTab(2))).active = (currentTab != 2);
        }

        // --- TAB CONTENT ---
        if (currentTab == 0) {
            ClientConfigManager.ClientProfile profile = ClientConfigManager.getProfile();

            keybindBtn = this.addButton(new Button(leftX, startY, 310, 20, getKeybindLabel(), btn -> {
                listeningForKey = true;
                keybindBtn.setMessage(new TextComponent("> Press a key <").withStyle(ChatFormatting.YELLOW));
            }));

            int toggleStartY = startY + 28;
            addClientToggle(leftX, toggleStartY, "Master Toggle", profile.master, v -> profile.master = v, ClientConfigManager.allowMaster);
            addClientToggle(rightX, toggleStartY, "Pickup Blocks", profile.blocks, v -> profile.blocks = v, ClientConfigManager.allowBlocks);
            addClientToggle(leftX, toggleStartY + 24, "Pickup Block XP", profile.blockXp, v -> profile.blockXp = v, ClientConfigManager.allowBlockXp);
            addClientToggle(rightX, toggleStartY + 24, "Pickup Mob Loot", profile.mobLoot, v -> profile.mobLoot = v, ClientConfigManager.allowMobLoot);
            addClientToggle(leftX, toggleStartY + 48, "Pickup Mob XP", profile.mobXp, v -> profile.mobXp = v, ClientConfigManager.allowMobXp);
            addClientToggle(rightX, toggleStartY + 48, "Split Mob Loot", profile.splitMobLoot, v -> profile.splitMobLoot = v, ClientConfigManager.allowSplitMobLoot);
            addClientToggle(leftX, toggleStartY + 72, "Split Mob XP", profile.splitMobXp, v -> profile.splitMobXp = v, ClientConfigManager.allowSplitMobXp);

        } else if (currentTab == 1 && ClientSyncHandler.isAdmin) {
            for (int i = 0; i < 7; i++) {
                int x = (i % 2 == 0) ? leftX : rightX;
                int y = startY + (i / 2) * 24;
                final int idx = i;

                this.addButton(new CyclingButton<>(x, y, 150, 20, new TextComponent(ConfigUIUtils.NAMES[idx]),
                        ConfigUIUtils.ServerControl.values(),
                        ConfigUIUtils.ServerControl.fromState(ui_sVals[idx], ui_sAllows[idx]),
                        ConfigUIUtils.ServerControl::getText, (btn, val) -> {
                            if (val == ConfigUIUtils.ServerControl.ON) { ClientSyncHandler.sAllows[idx] = false; ClientSyncHandler.sVals[idx] = true; }
                            else if (val == ConfigUIUtils.ServerControl.OFF) { ClientSyncHandler.sAllows[idx] = false; ClientSyncHandler.sVals[idx] = false; }
                            else { ClientSyncHandler.sAllows[idx] = true; }

                            ui_sVals[idx] = ClientSyncHandler.sVals[idx];
                            ui_sAllows[idx] = ClientSyncHandler.sAllows[idx];
                        }));
            }
        } else if (currentTab == 2 && ClientSyncHandler.isAdmin) {
            EditBox targetBox = new EditBox(this.font, leftX, startY, 150, 20, new TextComponent("Target Player"));
            targetBox.setMaxLength(16);
            if (ClientSyncHandler.targetPlayerName != null) targetBox.setValue(ClientSyncHandler.targetPlayerName);
            targetBox.setResponder(val -> ClientSyncHandler.targetPlayerName = val);
            this.addButton(targetBox);

            this.addButton(new Button(rightX, startY, 150, 20, new TextComponent("Search / Load"), btn -> {
                String name = targetBox.getValue();
                if (name != null && !name.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
                    ClientNetworkManager.sendCommand("autopickup query_player " + name);
                }
            }));

            int playerStartY = startY + 30;
            for (int i = 0; i < 7; i++) {
                int x = (i % 2 == 0) ? leftX : rightX;
                int y = playerStartY + (i / 2) * 24;
                final int idx = i;

                this.addButton(new CyclingButton<>(x, y, 150, 20, new TextComponent("Override " + ConfigUIUtils.NAMES[idx]),
                        ConfigUIUtils.Tristate.values(),
                        ConfigUIUtils.Tristate.fromEncoded(ui_pVals[idx]),
                        ConfigUIUtils.Tristate::getText, (btn, val) -> {
                            if (val == ConfigUIUtils.Tristate.TRUE) ClientSyncHandler.pValsEncoded[idx] = 1;
                            else if (val == ConfigUIUtils.Tristate.FALSE) ClientSyncHandler.pValsEncoded[idx] = 2;
                            else if (val == ConfigUIUtils.Tristate.RESET) ClientSyncHandler.pValsEncoded[idx] = 3;
                            else ClientSyncHandler.pValsEncoded[idx] = 0;

                            ui_pVals[idx] = ClientSyncHandler.pValsEncoded[idx];
                        }));
            }
        }

        // --- DONE BUTTON ---
        this.addButton(new Button(this.width / 2 - 100, this.height - 30, 200, 20, new TextComponent("Done"), btn -> this.onClose()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningForKey && keybindBtn != null) {
            listeningForKey = false;
            if (keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                KeyMapping km = AutoPickupKeyBindings.toggleMaster;
                if (km != null) {
                    km.setKey(InputConstants.getKey(keyCode, scanCode));
                    KeyMapping.resetMapping();
                    if (this.minecraft != null) this.minecraft.options.save();
                }
            }
            keybindBtn.setMessage(getKeybindLabel());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private Component getKeybindLabel() {
        KeyMapping km = AutoPickupKeyBindings.toggleMaster;
        Component keyName = (km != null) ? km.getTranslatedKeyMessage() : new TextComponent("None");
        return new TextComponent("Toggle Master Keybind: [").append(keyName).append("]");
    }

    private void addClientToggle(int x, int y, String name, boolean currentVal, Consumer<Boolean> setter, boolean allowed) {
        CyclingButton<Boolean> btn = CyclingButton.onOff(x, y, 150, 20, new TextComponent(name), currentVal,
                (b, val) -> setter.accept(val));
        btn.active = this.minecraft == null || this.minecraft.player == null || allowed;
        this.addButton(btn);
    }

    private void switchTab(int tab) {
        if (this.currentTab != tab) {
            this.currentTab = tab;
            this.init(this.minecraft, this.width, this.height);
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
            this.init(this.minecraft, this.width, this.height);
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 8, 0xFFFFFF);
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
