package com.lukarbonite.autopickup.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** The config screen, ported to 1.12.2's GuiScreen: the same three tabs as on newer versions. */
public class AutoPickupConfigScreen extends GuiScreen {

    private static final String TITLE = "Auto Pickup Config";

    private final boolean[] ui_sVals = new boolean[7];
    private final boolean[] ui_sAllows = new boolean[7];
    private final int[] ui_pVals = new int[7];

    private final GuiScreen parent;
    private int currentTab = 0; // 0 = Client, 1 = Server, 2 = Player

    private boolean listeningForKey = false;
    private GuiButton keybindBtn = null;
    private GuiTextField targetBox = null;

    // 1.12.2 buttons report clicks by id, so each button's action is kept here.
    private final Map<Integer, Runnable> actions = new HashMap<>();
    private int nextId = 0;

    public AutoPickupConfigScreen(GuiScreen parent) {
        this.parent = parent;
    }

    public static GuiScreen create(GuiScreen parent) {
        ConfigUIUtils.updateSnapshots();
        ConfigUIUtils.requestGlobalConfig();
        return new AutoPickupConfigScreen(parent);
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        actions.clear();
        nextId = 0;
        targetBox = null;
        listeningForKey = false; // reset on any rebuild
        Keyboard.enableRepeatEvents(true);

        for (int i = 0; i < 7; i++) {
            ui_sVals[i] = ClientSyncHandler.sVals[i];
            ui_sAllows[i] = ClientSyncHandler.sAllows[i];
            ui_pVals[i] = ClientSyncHandler.pValsEncoded[i];
        }

        int leftX = this.width / 2 - 155;
        int rightX = this.width / 2 + 5;
        int startY = 50;

        // --- TABS ---
        addAction(leftX, 20, 100, 20, "Client Settings", () -> switchTab(0)).enabled = (currentTab != 0);
        if (ClientSyncHandler.isAdmin) {
            addAction(this.width / 2 - 50, 20, 100, 20, "Server Config", () -> switchTab(1)).enabled = (currentTab != 1);
            addAction(rightX + 50, 20, 100, 20, "Player Mgmt", () -> switchTab(2)).enabled = (currentTab != 2);
        }

        // --- TAB CONTENT ---
        if (currentTab == 0) {
            ClientConfigManager.ClientProfile profile = ClientConfigManager.getProfile();

            keybindBtn = addAction(leftX, startY, 310, 20, getKeybindLabel(), () -> {
                listeningForKey = true;
                keybindBtn.displayString = TextFormatting.YELLOW + "> Press a key <";
            });

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

                addCycling(new CyclingButton<>(nextId++, x, y, 150, ConfigUIUtils.NAMES[idx],
                        ConfigUIUtils.ServerControl.values(),
                        ConfigUIUtils.ServerControl.fromState(ui_sVals[idx], ui_sAllows[idx]),
                        ConfigUIUtils.ServerControl::getText, val -> {
                            if (val == ConfigUIUtils.ServerControl.ON) { ClientSyncHandler.sAllows[idx] = false; ClientSyncHandler.sVals[idx] = true; }
                            else if (val == ConfigUIUtils.ServerControl.OFF) { ClientSyncHandler.sAllows[idx] = false; ClientSyncHandler.sVals[idx] = false; }
                            else { ClientSyncHandler.sAllows[idx] = true; }

                            ui_sVals[idx] = ClientSyncHandler.sVals[idx];
                            ui_sAllows[idx] = ClientSyncHandler.sAllows[idx];
                        }));
            }
        } else if (currentTab == 2 && ClientSyncHandler.isAdmin) {
            targetBox = new GuiTextField(nextId++, this.fontRenderer, leftX, startY, 150, 20);
            targetBox.setMaxStringLength(16);
            if (ClientSyncHandler.targetPlayerName != null) targetBox.setText(ClientSyncHandler.targetPlayerName);

            addAction(rightX, startY, 150, 20, "Search / Load", () -> {
                String name = targetBox.getText();
                if (name != null && !name.isEmpty() && this.mc.player != null) {
                    ClientNetworkManager.sendCommand("autopickup query_player " + name);
                }
            });

            int playerStartY = startY + 30;
            for (int i = 0; i < 7; i++) {
                int x = (i % 2 == 0) ? leftX : rightX;
                int y = playerStartY + (i / 2) * 24;
                final int idx = i;

                addCycling(new CyclingButton<>(nextId++, x, y, 150, "Override " + ConfigUIUtils.NAMES[idx],
                        ConfigUIUtils.Tristate.values(),
                        ConfigUIUtils.Tristate.fromEncoded(ui_pVals[idx]),
                        ConfigUIUtils.Tristate::getText, val -> {
                            if (val == ConfigUIUtils.Tristate.TRUE) ClientSyncHandler.pValsEncoded[idx] = 1;
                            else if (val == ConfigUIUtils.Tristate.FALSE) ClientSyncHandler.pValsEncoded[idx] = 2;
                            else if (val == ConfigUIUtils.Tristate.RESET) ClientSyncHandler.pValsEncoded[idx] = 3;
                            else ClientSyncHandler.pValsEncoded[idx] = 0;

                            ui_pVals[idx] = ClientSyncHandler.pValsEncoded[idx];
                        }));
            }
        }

        // --- DONE BUTTON ---
        addAction(this.width / 2 - 100, this.height - 30, 200, 20, "Done", () -> this.mc.displayGuiScreen(this.parent));
    }

    private GuiButton addAction(int x, int y, int w, int h, String label, Runnable action) {
        GuiButton button = new GuiButton(nextId++, x, y, w, h, label);
        this.buttonList.add(button);
        actions.put(button.id, action);
        return button;
    }

    private <T> CyclingButton<T> addCycling(CyclingButton<T> button) {
        this.buttonList.add(button);
        actions.put(button.id, button::cycle);
        return button;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        Runnable action = actions.get(button.id);
        if (action != null) action.run();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (listeningForKey && keybindBtn != null) {
            listeningForKey = false;
            if (keyCode != Keyboard.KEY_ESCAPE) {
                KeyBinding km = AutoPickupKeyBindings.toggleMaster;
                if (km != null) {
                    km.setKeyCode(keyCode);
                    KeyBinding.resetKeyBindingArrayAndHash();
                    this.mc.gameSettings.saveOptions();
                }
            }
            keybindBtn.displayString = getKeybindLabel();
            return;
        }
        if (targetBox != null && targetBox.textboxKeyTyped(typedChar, keyCode)) {
            ClientSyncHandler.targetPlayerName = targetBox.getText();
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            // Back to the parent (vanilla would close to the game); onGuiClosed saves either way.
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (targetBox != null) targetBox.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private String getKeybindLabel() {
        KeyBinding km = AutoPickupKeyBindings.toggleMaster;
        return "Toggle Master Keybind: [" + (km != null ? km.getDisplayName() : "None") + "]";
    }

    private void addClientToggle(int x, int y, String name, boolean currentVal, Consumer<Boolean> setter, boolean allowed) {
        CyclingButton<Boolean> btn = addCycling(new CyclingButton<>(nextId++, x, y, 150, name,
                new Boolean[] {Boolean.TRUE, Boolean.FALSE}, currentVal,
                v -> I18n.format(v ? "options.on" : "options.off"), setter));
        btn.enabled = this.mc.player == null || allowed;
    }

    private void switchTab(int tab) {
        if (this.currentTab != tab) {
            this.currentTab = tab;
            this.initGui();
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (targetBox != null) targetBox.updateCursorCounter();

        for (int i = 0; i < 7; i++) {
            if (ui_sVals[i] != ClientSyncHandler.sVals[i] ||
                    ui_sAllows[i] != ClientSyncHandler.sAllows[i] ||
                    ui_pVals[i] != ClientSyncHandler.pValsEncoded[i]) {
                this.initGui();
                return;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (targetBox != null) targetBox.drawTextBox();
        this.drawCenteredString(this.fontRenderer, TITLE, this.width / 2, 8, 0xFFFFFF);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        ClientConfigManager.save();
        ClientNetworkManager.sendConfig();
        if (ClientSyncHandler.isAdmin) ConfigUIUtils.applyAdminChanges();
    }

    /** Stand-in for newer versions' CycleButton: shows "Label: Value" and advances on each click. */
    static class CyclingButton<T> extends GuiButton {
        private final String label;
        private final T[] values;
        private final Function<T, String> valueText;
        private final Consumer<T> listener;
        private int index;

        CyclingButton(int id, int x, int y, int width, String label, T[] values, T initial,
                      Function<T, String> valueText, Consumer<T> listener) {
            super(id, x, y, width, 20, "");
            this.label = label;
            this.values = values;
            this.valueText = valueText;
            this.listener = listener;
            this.index = Math.max(0, Arrays.asList(values).indexOf(initial));
            updateMessage();
        }

        void cycle() {
            index = (index + 1) % values.length;
            updateMessage();
            listener.accept(values[index]);
        }

        private void updateMessage() {
            displayString = label + ": " + valueText.apply(values[index]);
        }
    }
}
