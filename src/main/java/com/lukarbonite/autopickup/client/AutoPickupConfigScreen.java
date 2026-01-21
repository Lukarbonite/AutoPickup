package com.lukarbonite.autopickup.client;

import com.lukarbonite.autopickup.AutoPickupCommand;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AutoPickupConfigScreen {

    // --- State Variables ---
    private static String targetPlayerName = "";

    // --- Player Configs ---
    private static Tristate pMaster = Tristate.UNSET, pMasterSnapshot = Tristate.UNSET;
    private static Tristate pBlocks = Tristate.UNSET, pBlocksSnapshot = Tristate.UNSET;
    private static Tristate pBlockXp = Tristate.UNSET, pBlockXpSnapshot = Tristate.UNSET;
    private static Tristate pMobLoot = Tristate.UNSET, pMobLootSnapshot = Tristate.UNSET;
    private static Tristate pMobXp = Tristate.UNSET, pMobXpSnapshot = Tristate.UNSET;
    private static Tristate pSplitLoot = Tristate.UNSET, pSplitLootSnapshot = Tristate.UNSET;
    private static Tristate pSplitXp = Tristate.UNSET, pSplitXpSnapshot = Tristate.UNSET;

    // --- Server Configs ---
    private static boolean sMaster = false, sMasterSnapshot = false;
    private static boolean sBlocks = false, sBlocksSnapshot = false;
    private static boolean sBlockXp = false, sBlockXpSnapshot = false;
    private static boolean sMobLoot = false, sMobLootSnapshot = false;
    private static boolean sMobXp = false, sMobXpSnapshot = false;
    private static boolean sSplitLoot = false, sSplitLootSnapshot = false;
    private static boolean sSplitXp = false, sSplitXpSnapshot = false;
    private static boolean sClientControl = false, sClientControlSnapshot = false;

    private static boolean serverDataReceived = false;

    // References
    private static Option<Tristate> optPMaster, optPBlocks, optPBlockXp, optPMobLoot, optPMobXp, optPSplitLoot, optPSplitXp;
    private static Option<Boolean> optSMaster, optSBlocks, optSBlockXp, optSMobLoot, optSMobXp, optSSplitLoot, optSSplitXp, optSClientControl;

    // Tracking Lists for Tabs
    private static final List<Option<?>> clientOptions = new ArrayList<>();
    private static final List<Option<?>> serverOptions = new ArrayList<>();
    private static final List<Option<?>> playerOptions = new ArrayList<>();

    private static boolean isAdmin = false;

    enum Tristate {
        UNSET("unset", Formatting.GRAY),
        RESET("reset", Formatting.YELLOW),
        TRUE("true", Formatting.GREEN),
        FALSE("false", Formatting.RED);

        final String val; final Formatting fmt;
        Tristate(String v, Formatting f) { this.val = v; this.fmt = f; }
        public Text getText() { return Text.literal(name()).formatted(fmt); }
    }

    /**
     * Custom Component that dynamically calculates its style.
     * Overriding getStyle and asOrderedText ensures the tab color updates every frame.
     */
    private static class DynamicTabTitle implements Text {
        private final List<Option<?>> options;
        private final String label;

        public DynamicTabTitle(String label, List<Option<?>> options) {
            this.label = label;
            this.options = options;
        }

        @Override
        public Style getStyle() {
            for (Option<?> o : options) {
                if (o.changed()) return Style.EMPTY.withColor(Formatting.RED);
            }
            return Style.EMPTY;
        }

        @Override
        public TextContent getContent() {
            return Text.literal(label).getContent();
        }

        @Override
        public List<Text> getSiblings() {
            return java.util.Collections.emptyList();
        }

        @Override
        public OrderedText asOrderedText() {
            return (visitor) -> Text.literal(label).asOrderedText().accept((index, style, cp) ->
                    visitor.accept(index, getStyle(), cp));
        }
    }

    public static Screen create(Screen parent) {
        targetPlayerName = "";
        clientOptions.clear(); serverOptions.clear(); playerOptions.clear();
        resetStates();

        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.networkHandler.sendChatCommand("autopickup check_perm");
            if (isAdmin) {
                MinecraftClient.getInstance().player.networkHandler.sendChatCommand("autopickup query_global");
            }
        }

        ClientConfigManager.ClientProfile profile = ClientConfigManager.getProfile();

        // --- 1. PREPARE CATEGORIES ---

        // Client Tab
        ConfigCategory clientCategory = ConfigCategory.createBuilder()
                .name(new DynamicTabTitle("Client Settings", clientOptions))
                .option(clientBool(clientOptions, "Master Toggle", () -> profile.master, v -> profile.master = v))
                .group(OptionGroup.createBuilder().name(Text.literal("Blocks"))
                        .option(clientBool(clientOptions, "Pickup Blocks", () -> profile.blocks, v -> profile.blocks = v))
                        .option(clientBool(clientOptions, "Pickup Block XP", () -> profile.blockXp, v -> profile.blockXp = v))
                        .build())
                .group(OptionGroup.createBuilder().name(Text.literal("Mobs"))
                        .option(clientBool(clientOptions, "Pickup Mob Loot", () -> profile.mobLoot, v -> profile.mobLoot = v))
                        .option(clientBool(clientOptions, "Pickup Mob XP", () -> profile.mobXp, v -> profile.mobXp = v))
                        .build())
                .group(OptionGroup.createBuilder().name(Text.literal("Multiplayer Splitting"))
                        .option(clientBool(clientOptions, "Split Mob Loot", () -> profile.splitMobLoot, v -> profile.splitMobLoot = v))
                        .option(clientBool(clientOptions, "Split Mob XP", () -> profile.splitMobXp, v -> profile.splitMobXp = v))
                        .build())
                .option(createTickerOption())
                .build();

        ConfigCategory serverCategory = null;
        ConfigCategory playerCategory = null;

        if (isAdmin) {
            ConfigCategory.Builder serverCat = ConfigCategory.createBuilder()
                    .name(new DynamicTabTitle("Server Config", serverOptions));
            optSMaster = serverBool(serverOptions, "Global Master", () -> sMaster, v -> sMaster = v, () -> sMasterSnapshot);
            optSBlocks = serverBool(serverOptions, "Global Blocks", () -> sBlocks, v -> sBlocks = v, () -> sBlocksSnapshot);
            optSBlockXp = serverBool(serverOptions, "Global Block XP", () -> sBlockXp, v -> sBlockXp = v, () -> sBlockXpSnapshot);
            optSMobLoot = serverBool(serverOptions, "Global Mob Loot", () -> sMobLoot, v -> sMobLoot = v, () -> sMobLootSnapshot);
            optSMobXp = serverBool(serverOptions, "Global Mob XP", () -> sMobXp, v -> sMobXp = v, () -> sMobXpSnapshot);
            optSSplitLoot = serverBool(serverOptions, "Global Split Loot", () -> sSplitLoot, v -> sSplitLoot = v, () -> sSplitLootSnapshot);
            optSSplitXp = serverBool(serverOptions, "Global Split XP", () -> sSplitXp, v -> sSplitXp = v, () -> sSplitXpSnapshot);
            optSClientControl = serverBool(serverOptions, "Allow Client Control", () -> sClientControl, v -> sClientControl = v, () -> sClientControlSnapshot);
            serverCat.option(optSMaster); serverCat.option(optSBlocks); serverCat.option(optSBlockXp);
            serverCat.option(optSMobLoot); serverCat.option(optSMobXp); serverCat.option(optSSplitLoot);
            serverCat.option(optSSplitXp); serverCat.option(optSClientControl);
            serverCat.option(createTickerOption());
            serverCategory = serverCat.build();

            ConfigCategory.Builder playerCat = ConfigCategory.createBuilder()
                    .name(new DynamicTabTitle("Player Management", playerOptions));
            playerCat.option(Option.<String>createBuilder().name(Text.literal("Target Player Name")).binding("", () -> targetPlayerName, v -> targetPlayerName = v).controller(StringControllerBuilder::create).listener((opt, val) -> targetPlayerName = val).build());
            playerCat.option(ButtonOption.createBuilder().name(Text.literal("Load Player Config")).text(Text.literal("Search / Load")).action((screen, opt) -> {
                if (!targetPlayerName.isEmpty()) {
                    MinecraftClient.getInstance().player.sendMessage(Text.literal("Requesting config for: " + targetPlayerName).formatted(Formatting.GRAY), false);
                    MinecraftClient.getInstance().player.networkHandler.sendChatCommand("autopickup query_player " + targetPlayerName);
                }
            }).build());
            optPMaster = playerTri(playerOptions, "Override Master", () -> pMaster, v -> pMaster = v, () -> pMasterSnapshot);
            optPBlocks = playerTri(playerOptions, "Override Blocks", () -> pBlocks, v -> pBlocks = v, () -> pBlocksSnapshot);
            optPBlockXp = playerTri(playerOptions, "Override Block XP", () -> pBlockXp, v -> pBlockXp = v, () -> pBlockXpSnapshot);
            optPMobLoot = playerTri(playerOptions, "Override Mob Loot", () -> pMobLoot, v -> pMobLoot = v, () -> pMobLootSnapshot);
            optPMobXp = playerTri(playerOptions, "Override Mob XP", () -> pMobXp, v -> pMobXp = v, () -> pMobXpSnapshot);
            optPSplitLoot = playerTri(playerOptions, "Override Split Loot", () -> pSplitLoot, v -> pSplitLoot = v, () -> pSplitLootSnapshot);
            optPSplitXp = playerTri(playerOptions, "Override Split XP", () -> pSplitXp, v -> pSplitXp = v, () -> pSplitXpSnapshot);
            playerCat.option(optPMaster); playerCat.option(optPBlocks); playerCat.option(optPBlockXp);
            playerCat.option(optPMobLoot); playerCat.option(optPMobXp); playerCat.option(optPSplitLoot);
            playerCat.option(optPSplitXp);
            playerCat.option(createTickerOption());
            playerCategory = playerCat.build();
        }

        // --- 2. ASSEMBLE IN ORDER ---
        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Auto Pickup Config"))
                .save(() -> {
                    ClientConfigManager.save();
                    AutoPickupCommand.sendConfig();
                    if (isAdmin) applyAdminChanges();
                })
                .category(clientCategory); // Order 1: Client

        if (isAdmin) {
            if (serverCategory != null) builder.category(serverCategory); // Order 2: Server
            if (playerCategory != null) builder.category(playerCategory); // Order 3: Player
        }

        return builder.build().generateScreen(parent);
    }

    private static Option<Boolean> createTickerOption() {
        return Option.<Boolean>createBuilder()
                .name(Text.empty())
                .binding(true, () -> true, v -> {})
                .customController(opt -> new Controller<Boolean>() {
                    @Override public Option<Boolean> option() { return opt; }
                    @Override public Text formatValue() { return Text.empty(); }
                    @Override public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> dim) {
                        return new AbstractWidget(dim) {
                            private boolean focused = false;
                            @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
                                // Force button update every frame so Save/Done is always correct across tabs
                                if (screen.tabManager.getCurrentTab() instanceof YACLScreen.CategoryTab tab) tab.updateButtons();
                            }
                            @Override public void appendNarrations(NarrationMessageBuilder builder) {}
                            @Override public boolean isFocused() { return this.focused; }
                            @Override public void setFocused(boolean focused) { this.focused = focused; }
                        };
                    }
                })
                .build();
    }

    private static void resetStates() {
        pMaster = pMasterSnapshot = Tristate.UNSET;
        pBlocks = pBlocksSnapshot = Tristate.UNSET;
        pBlockXp = pBlockXpSnapshot = Tristate.UNSET;
        pMobLoot = pMobLootSnapshot = Tristate.UNSET;
        pMobXp = pMobXpSnapshot = Tristate.UNSET;
        pSplitLoot = pSplitLootSnapshot = Tristate.UNSET;
        pSplitXp = pSplitXpSnapshot = Tristate.UNSET;
        sMaster = sMasterSnapshot = false;
        sBlocks = sBlocksSnapshot = false;
        sBlockXp = sBlockXpSnapshot = false;
        sMobLoot = sMobLootSnapshot = false;
        sMobXp = sMobXpSnapshot = false;
        sSplitLoot = sSplitLootSnapshot = false;
        sSplitXp = sSplitXpSnapshot = false;
        sClientControl = sClientControlSnapshot = false;
        serverDataReceived = false;
    }

    private static Option<Boolean> clientBool(List<Option<?>> list, String name, Supplier<Boolean> g, Consumer<Boolean> s) {
        Option<Boolean> opt = Option.<Boolean>createBuilder().name(Text.literal(name)).binding(true, g, s).controller(TickBoxControllerBuilder::create).build();
        list.add(opt);
        return opt;
    }

    private static Option<Boolean> serverBool(List<Option<?>> list, String name, Supplier<Boolean> g, Consumer<Boolean> s, Supplier<Boolean> def) {
        Option<Boolean> opt = Option.<Boolean>createBuilder().name(Text.literal(name)).binding(new SnapshotBinding<>(g, s, def)).controller(TickBoxControllerBuilder::create).build();
        list.add(opt);
        return opt;
    }

    private static Option<Tristate> playerTri(List<Option<?>> list, String name, Supplier<Tristate> g, Consumer<Tristate> s, Supplier<Tristate> def) {
        Option<Tristate> opt = Option.<Tristate>createBuilder().name(Text.literal(name)).binding(new SnapshotBinding<>(g, s, def))
                .controller(cOpt -> CyclingListControllerBuilder.create(cOpt).values(Arrays.asList(Tristate.RESET, Tristate.TRUE, Tristate.FALSE)).formatValue(Tristate::getText)).build();
        list.add(opt);
        return opt;
    }

    private static class SnapshotBinding<T> implements Binding<T> {
        private final Supplier<T> getter;
        private final Consumer<T> setter;
        private final Supplier<T> defaultGetter;
        public SnapshotBinding(Supplier<T> getter, Consumer<T> setter, Supplier<T> defaultGetter) { this.getter = getter; this.setter = setter; this.defaultGetter = defaultGetter; }
        @Override public void setValue(T value) { setter.accept(value); }
        @Override public T getValue() { return getter.get(); }
        @Override public T defaultValue() { return defaultGetter.get(); }
    }

    public static void handleDataResponse(String msg) {
        MinecraftClient.getInstance().execute(() -> {
            try {
                if (msg.startsWith("[AP_PERM] ")) {
                    isAdmin = msg.substring(10).trim().equals("1");
                    return;
                }
                if (msg.startsWith("[AP_GLOBAL] ")) {
                    int mask = Integer.parseInt(msg.substring(12).trim());
                    serverDataReceived = true;
                    updateServerOpt(optSMaster, (mask & 1) != 0, v -> { sMaster = v; sMasterSnapshot = v; });
                    updateServerOpt(optSBlocks, (mask & 2) != 0, v -> { sBlocks = v; sBlocksSnapshot = v; });
                    updateServerOpt(optSBlockXp, (mask & 4) != 0, v -> { sBlockXp = v; sBlockXpSnapshot = v; });
                    updateServerOpt(optSMobLoot, (mask & 8) != 0, v -> { sMobLoot = v; sMobLootSnapshot = v; });
                    updateServerOpt(optSMobXp, (mask & 16) != 0, v -> { sMobXp = v; sMobXpSnapshot = v; });
                    updateServerOpt(optSSplitLoot, (mask & 32) != 0, v -> { sSplitLoot = v; sSplitLootSnapshot = v; });
                    updateServerOpt(optSSplitXp, (mask & 64) != 0, v -> { sSplitXp = v; sSplitXpSnapshot = v; });
                    updateServerOpt(optSClientControl, (mask & 128) != 0, v -> { sClientControl = v; sClientControlSnapshot = v; });
                    return;
                }
                if (msg.startsWith("[AP_DATA] ")) {
                    String[] parts = msg.split(" ");
                    if (parts.length < 3) return;
                    if (!targetPlayerName.isEmpty() && !parts[1].equalsIgnoreCase(targetPlayerName)) return;
                    int mask = Integer.parseInt(parts[2]);
                    updatePlayerOpt(optPMaster, decodeTristate(mask, 0), v -> { pMaster = v; pMasterSnapshot = v; });
                    updatePlayerOpt(optPBlocks, decodeTristate(mask, 2), v -> { pBlocks = v; pBlocksSnapshot = v; });
                    updatePlayerOpt(optPBlockXp, decodeTristate(mask, 4), v -> { pBlockXp = v; pBlockXpSnapshot = v; });
                    updatePlayerOpt(optPMobLoot, decodeTristate(mask, 6), v -> { pMobLoot = v; pMobLootSnapshot = v; });
                    updatePlayerOpt(optPMobXp, decodeTristate(mask, 8), v -> { pMobXp = v; pMobXpSnapshot = v; });
                    updatePlayerOpt(optPSplitLoot, decodeTristate(mask, 10), v -> { pSplitLoot = v; pSplitLootSnapshot = v; });
                    updatePlayerOpt(optPSplitXp, decodeTristate(mask, 12), v -> { pSplitXp = v; pSplitXpSnapshot = v; });
                }
            } catch (Exception e) {}
        });
    }

    private static void updateServerOpt(Option<Boolean> opt, boolean val, Consumer<Boolean> setter) {
        if (opt != null) { setter.accept(val); opt.requestSet(val); } else { setter.accept(val); }
    }

    private static void updatePlayerOpt(Option<Tristate> opt, Tristate val, Consumer<Tristate> setter) {
        if (opt != null) { setter.accept(val); opt.requestSet(val); } else { setter.accept(val); }
    }

    private static Tristate decodeTristate(int mask, int shift) {
        int val = (mask >> shift) & 3;
        return switch(val) { case 1 -> Tristate.TRUE; case 2 -> Tristate.FALSE; default -> Tristate.RESET; };
    }

    private static void applyAdminChanges() {
        if (!serverDataReceived) return;
        if (hasChanged(sMaster, sMasterSnapshot)) sendGlobal("master", sMaster);
        if (hasChanged(sBlocks, sBlocksSnapshot)) sendGlobal("blocks", sBlocks);
        if (hasChanged(sBlockXp, sBlockXpSnapshot)) sendGlobal("blockXp", sBlockXp);
        if (hasChanged(sMobLoot, sMobLootSnapshot)) sendGlobal("mobLoot", sMobLoot);
        if (hasChanged(sMobXp, sMobXpSnapshot)) sendGlobal("mobXp", sMobXp);
        if (hasChanged(sSplitLoot, sSplitLootSnapshot)) sendGlobal("splitMobLoot", sSplitLoot);
        if (hasChanged(sSplitXp, sSplitXpSnapshot)) sendGlobal("splitMobXp", sSplitXp);
        if (hasChanged(sClientControl, sClientControlSnapshot)) sendGlobal("allowClientControl", sClientControl);

        if (!targetPlayerName.isEmpty()) {
            if (pMaster != pMasterSnapshot) sendPlayer(targetPlayerName, "master", pMaster);
            if (pBlocks != pBlocksSnapshot) sendPlayer(targetPlayerName, "blocks", pBlocks);
            if (pBlockXp != pBlockXpSnapshot) sendPlayer(targetPlayerName, "blockXp", pBlockXp);
            if (pMobLoot != pMobLootSnapshot) sendPlayer(targetPlayerName, "mobLoot", pMobLoot);
            if (pMobXp != pMobXpSnapshot) sendPlayer(targetPlayerName, "mobXp", pMobXp);
            if (pSplitLoot != pSplitLootSnapshot) sendPlayer(targetPlayerName, "splitMobLoot", pSplitLoot);
            if (pSplitXp != pSplitXpSnapshot) sendPlayer(targetPlayerName, "splitMobXp", pSplitXp);
        }
        targetPlayerName = "";
    }

    private static boolean hasChanged(Boolean current, Boolean snapshot) { return !Objects.equals(current, snapshot); }

    private static void sendGlobal(String key, boolean val) {
        if (MinecraftClient.getInstance().player != null) MinecraftClient.getInstance().player.networkHandler.sendChatCommand("autopickup global " + key + " " + val + " silent");
    }

    private static void sendPlayer(String target, String key, Tristate val) {
        if (val == Tristate.UNSET) return;
        if (MinecraftClient.getInstance().player != null) MinecraftClient.getInstance().player.networkHandler.sendChatCommand("autopickup setPlayerConfig " + target + " " + key + " " + val.val + " silent");
    }
}