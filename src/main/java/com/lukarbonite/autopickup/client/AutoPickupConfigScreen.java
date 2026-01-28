package com.lukarbonite.autopickup.client;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.AutoPickupCommand;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AutoPickupConfigScreen {

    private static String targetPlayerName = "";
    private static Option<String> targetNameOptionRef = null;
    public static boolean isAdmin = false;
    private static boolean serverDataReceived = false;

    private static final boolean[] sVals = new boolean[7];
    private static final boolean[] sValsSnap = new boolean[7];
    private static final boolean[] sAllows = new boolean[7];
    private static final boolean[] sAllowsSnap = new boolean[7];

    private static final Tristate[] pVals = new Tristate[7];
    private static final Tristate[] pValsSnap = new Tristate[7];

    private static final List<Option<?>> clientOptions = new ArrayList<>();
    private static final List<Option<?>> serverOptions = new ArrayList<>();
    private static final List<Option<?>> playerOptions = new ArrayList<>();

    private static final boolean[] categoryTickers = new boolean[3];

    private static final List<Option<Tristate>> pOptionRefs = new ArrayList<>();
    private static final List<Option<ServerControl>> sOptionRefs = new ArrayList<>();

    enum Tristate {
        UNSET("unset", Formatting.GRAY),
        RESET("reset", Formatting.YELLOW),
        TRUE("true", Formatting.GREEN),
        FALSE("false", Formatting.RED);

        final String val; final Formatting fmt;
        Tristate(String v, Formatting f) { this.val = v; this.fmt = f; }
        public Text getText() { return Text.literal(name()).formatted(fmt); }
        public static Tristate fromInt(int i) {
            return switch(i) { case 1 -> TRUE; case 2 -> FALSE; case 3 -> RESET; default -> UNSET; };
        }
    }

    enum ServerControl {
        ON("ON", Formatting.GREEN),
        OFF("OFF", Formatting.RED),
        CLIENT("CLIENT DECIDE", Formatting.AQUA);

        final String label; final Formatting fmt;
        ServerControl(String l, Formatting f) { this.label = l; this.fmt = f; }
        public Text getText() { return Text.literal(label).formatted(fmt); }

        static ServerControl fromState(boolean val, boolean allow) {
            if (allow) return CLIENT;
            return val ? ON : OFF;
        }
    }

    static {
        boolean[] defaults = {true, true, true, false, true, false, false};
        for (int i = 0; i < 7; i++) {
            sVals[i] = sValsSnap[i] = defaults[i];
            sAllows[i] = sAllowsSnap[i] = true;
            pVals[i] = pValsSnap[i] = Tristate.UNSET;
        }
    }

    public static Screen create(Screen parent) {
        // --- HARD RESET STATE ON OPEN ---
        targetPlayerName = "";
        pOptionRefs.clear();
        sOptionRefs.clear();
        clientOptions.clear();
        serverOptions.clear();
        playerOptions.clear();
        Arrays.fill(categoryTickers, false);
        for(int i=0; i<7; i++) {
            pVals[i] = Tristate.UNSET;
            pValsSnap[i] = Tristate.UNSET;
        }

        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.networkHandler.sendChatCommand("autopickup check_perm");
            MinecraftClient.getInstance().player.networkHandler.sendChatCommand("autopickup query_global");
        }

        ClientConfigManager.ClientProfile profile = ClientConfigManager.getProfile();
        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder().title(Text.literal("Auto Pickup Config"));

        // --- CLIENT ---
        ConfigCategory clientCat = ConfigCategory.createBuilder()
                .name(new DynamicTabTitle("Client Settings", clientOptions, false))
                .option(clientBool("Master Toggle", () -> profile.master, v -> profile.master = v, () -> ClientConfigManager.allowMaster))
                .group(OptionGroup.createBuilder().name(Text.literal("Blocks"))
                        .option(clientBool("Pickup Blocks", () -> profile.blocks, v -> profile.blocks = v, () -> ClientConfigManager.allowBlocks))
                        .option(clientBool("Pickup Block XP", () -> profile.blockXp, v -> profile.blockXp = v, () -> ClientConfigManager.allowBlockXp))
                        .build())
                .group(OptionGroup.createBuilder().name(Text.literal("Mobs"))
                        .option(clientBool("Pickup Mob Loot", () -> profile.mobLoot, v -> profile.mobLoot = v, () -> ClientConfigManager.allowMobLoot))
                        .option(clientBool("Pickup Mob XP", () -> profile.mobXp, v -> profile.mobXp = v, () -> ClientConfigManager.allowMobXp))
                        .build())
                .group(OptionGroup.createBuilder().name(Text.literal("Multiplayer Splitting"))
                        .option(clientBool("Split Mob Loot", () -> profile.splitMobLoot, v -> profile.splitMobLoot = v, () -> ClientConfigManager.allowSplitMobLoot))
                        .option(clientBool("Split Mob XP", () -> profile.splitMobXp, v -> profile.splitMobXp = v, () -> ClientConfigManager.allowSplitMobXp))
                        .build())
                .option(createTickerOption(clientOptions, 0))
                .build();
        builder.category(clientCat);

        // --- ADMIN SECTIONS ---
        if (isAdmin) {
            String[] names = {"Master Toggle", "Pickup Blocks", "Pickup Block XP", "Pickup Mob Loot", "Pickup Mob XP", "Split Mob Loot", "Split Mob XP"};

            // Server Category
            ConfigCategory.Builder serverCat = ConfigCategory.createBuilder().name(new DynamicTabTitle("Server Config", serverOptions, true));
            for (int i = 0; i < 7; i++) {
                final int idx = i;
                Option<ServerControl> sOpt = Option.<ServerControl>createBuilder()
                        .name(Text.literal(names[idx]))
                        .binding(ServerControl.CLIENT,
                                () -> ServerControl.fromState(sVals[idx], sAllows[idx]),
                                (v) -> {
                                    if (v == ServerControl.ON) { sAllows[idx] = false; sVals[idx] = true; }
                                    else if (v == ServerControl.OFF) { sAllows[idx] = false; sVals[idx] = false; }
                                    else { sAllows[idx] = true; }
                                })
                        .controller(opt -> CyclingListControllerBuilder.create(opt)
                                .values(Arrays.asList(ServerControl.ON, ServerControl.OFF, ServerControl.CLIENT))
                                .formatValue(ServerControl::getText))
                        .build();
                sOptionRefs.add(sOpt);
                serverOptions.add(sOpt);
                serverCat.option(sOpt);
            }
            serverCat.option(createTickerOption(serverOptions, 1));
            builder.category(serverCat.build());

            // Player Category
            ConfigCategory.Builder playerCat = ConfigCategory.createBuilder().name(new DynamicTabTitle("Player Management", playerOptions, false));

// 1. Capture the Name Option Reference
            targetNameOptionRef = Option.<String>createBuilder()
                    .name(Text.literal("Target Player Name"))
                    .binding("", () -> targetPlayerName, v -> targetPlayerName = v)
                    .controller(StringControllerBuilder::create)
                    .build();
            playerCat.option(targetNameOptionRef);

// 2. Fix the Button to use the PENDING value (the text currently in the box)
            playerCat.option(ButtonOption.createBuilder()
                    .name(Text.literal("Load Player Config"))
                    .text(Text.literal("Search / Load"))
                    .action((s, o) -> {
                        String name = targetNameOptionRef.pendingValue(); // Use pending value instead of static variable
                        if (name != null && !name.isEmpty()) {
                            System.out.println("[AutoPickup Debug] Requesting data for: " + name);
                            MinecraftClient.getInstance().player.networkHandler.sendChatCommand("autopickup query_player " + name);
                        }
                    }).build());

            for(int i=0; i<7; i++) {
                final int idx = i;
                Option<Tristate> pOpt = Option.<Tristate>createBuilder()
                        .name(Text.literal("Override " + names[idx]))
                        .binding(Tristate.UNSET, () -> pVals[idx], v -> pVals[idx] = v)
                        .controller(c -> CyclingListControllerBuilder.create(c)
                                .values(Arrays.asList(Tristate.UNSET, Tristate.RESET, Tristate.TRUE, Tristate.FALSE))
                                .formatValue(Tristate::getText))
                        .build();
                pOptionRefs.add(pOpt);
                playerOptions.add(pOpt);
                playerCat.option(pOpt);
            }
            playerCat.option(createTickerOption(playerOptions, 2));
            builder.category(playerCat.build());
        }

        return builder.save(() -> {
            ClientConfigManager.save();
            AutoPickupCommand.sendConfig();
            if (isAdmin) applyAdminChanges();
        }).build().generateScreen(parent);
    }

    public static void handleDataResponse(String msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            try {
                if (msg.startsWith("[AP_PERM] ")) {
                    isAdmin = msg.substring(10).trim().equals("1");
                    return;
                }

                if (msg.startsWith("[AP_GLOBAL] ")) {
                    int mask = Integer.parseInt(msg.substring(12).trim());
                    serverDataReceived = true;
                    for(int i = 0; i < 7; i++) {
                        boolean val = (mask & (1 << (i * 2))) != 0;
                        boolean allow = (mask & (1 << (i * 2 + 1))) != 0;

                        // Sync internal state
                        sVals[i] = val;
                        sAllows[i] = allow;
                        sValsSnap[i] = val;
                        sAllowsSnap[i] = allow;

                        if (i < sOptionRefs.size()) {
                            // requestSet updates the UI widget
                            sOptionRefs.get(i).requestSet(ServerControl.fromState(val, allow));
                        }
                    }
                    updateClientAllowances();
                }

                if (msg.startsWith("[AP_DATA] ")) {
                    String[] parts = msg.split(" ");
                    if (parts.length < 3) return;
                    int mask = Integer.parseInt(parts[2]);

                    System.out.println("[AutoPickup Debug] Parsing Player Mask: " + mask);

                    for(int i = 0; i < 7; i++) {
                        Tristate parsed = Tristate.fromInt((mask >> (i * 2)) & 3);

                        // Sync internal state and snapshot so it's not marked as "changed" (red)
                        pVals[i] = parsed;
                        pValsSnap[i] = parsed;

                        if (i < pOptionRefs.size()) {
                            // requestSet forces the Cycling Button to show the new value
                            pOptionRefs.get(i).requestSet(parsed);
                        }
                    }
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

    private static void applyAdminChanges() {
        if (!serverDataReceived) return;
        String[] keys = {"master", "blocks", "blockXp", "mobLoot", "mobXp", "splitMobLoot", "splitMobXp"};
        for (int i = 0; i < 7; i++) {
            if (sVals[i] != sValsSnap[i]) {
                sendGlobal(keys[i], sVals[i]);
                sValsSnap[i] = sVals[i];
            }
            if (sAllows[i] != sAllowsSnap[i]) {
                sendGlobal("allow_" + keys[i], sAllows[i]);
                sAllowsSnap[i] = sAllows[i];
            }
            if (!targetPlayerName.isEmpty() && pVals[i] != pValsSnap[i]) {
                sendPlayer(targetPlayerName, keys[i], pVals[i]);
                pValsSnap[i] = pVals[i];
            }
        }
    }

    private static void sendGlobal(String key, boolean val) {
        if (MinecraftClient.getInstance().player != null) {
            String cmd = "autopickup global " + key + " " + val + " silent";
            System.out.println("[AutoPickup Debug] Sending Global Cmd: " + cmd);
            MinecraftClient.getInstance().player.networkHandler.sendChatCommand(cmd);
        }
    }

    private static void sendPlayer(String target, String key, Tristate val) {
        if (target.isEmpty() || MinecraftClient.getInstance().player == null) return;
        String cmd = "autopickup setPlayerConfig " + target + " " + key + " " + val.val + " silent";
        System.out.println("[AutoPickup Debug] Sending Player Override Cmd: " + cmd);
        MinecraftClient.getInstance().player.networkHandler.sendChatCommand(cmd);
    }

    private static Option<Boolean> clientBool(String name, Supplier<Boolean> g, Consumer<Boolean> s, Supplier<Boolean> allowed) {
        return Option.<Boolean>createBuilder().name(Text.literal(name)).binding(true, g, s)
                .customController(o -> new Controller<Boolean>() {
                    @Override public Option<Boolean> option() { return o; }
                    @Override public Text formatValue() { return o.pendingValue() ? Text.literal("ON").formatted(Formatting.GREEN) : Text.literal("OFF").formatted(Formatting.RED); }
                    @Override public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> dim) {
                        return new AbstractWidget(dim) {
                            private boolean focused = false;
                            @Override public void setFocused(boolean f) { this.focused = f; }
                            @Override public boolean isFocused() { return focused; }
                            @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
                                int x = getDimension().x(), y = getDimension().y(), w = getDimension().width(), h = getDimension().height();
                                boolean canConfig = allowed.get();
                                int color = canConfig ? 0xFFFFFFFF : 0xFFA0A0A0;
                                context.drawText(textRenderer, o.name(), x + 6, y + (h - 8)/2, color, true);
                                int btnW = 50, btnX = x + w - btnW - 6;
                                drawButtonRect(context, btnX, y, btnX + btnW, y + h, isMouseOver(mouseX, mouseY) && canConfig, canConfig);
                                context.drawCenteredTextWithShadow(textRenderer, formatValue(), btnX + btnW/2, y + (h-8)/2, color);
                            }
                            @Override public boolean onMouseClicked(double mouseX, double mouseY, int button) {
                                int btnW = 50, btnX = getDimension().x() + getDimension().width() - btnW - 6;
                                if (allowed.get() && mouseX >= btnX) { o.requestSet(!o.pendingValue()); playDownSound(); return true; }
                                return false;
                            }
                        };
                    }
                }).build();
    }

    private static Option<Boolean> createTickerOption(List<Option<?>> list, int tickerIdx) {
        return Option.<Boolean>createBuilder().name(Text.literal("Internal Sync " + tickerIdx))
                .binding(false, () -> categoryTickers[tickerIdx], v -> categoryTickers[tickerIdx] = v)
                .customController(o -> new Controller<Boolean>() {
                    @Override public Option<Boolean> option() { return o; }
                    @Override public Text formatValue() { return Text.empty(); }
                    @Override public AbstractWidget provideWidget(YACLScreen s, Dimension<Integer> d) {
                        return new AbstractWidget(d) {
                            private boolean focused = false;
                            @Override public void setFocused(boolean f) { this.focused = f; }
                            @Override public boolean isFocused() { return focused; }
                            @Override public void render(DrawContext c, int mx, int my, float dl) {
                                if (s.tabManager.getCurrentTab() instanceof YACLScreen.CategoryTab tab) {
                                    boolean changed = false;
                                    for(int i=0; i<7; i++) {
                                        if (sAllows[i] != sAllowsSnap[i] || sVals[i] != sValsSnap[i] || pVals[i] != pValsSnap[i]) changed = true;
                                    }
                                    for (Option<?> opt : clientOptions) if (opt.changed()) changed = true;
                                    if (isAdmin) {
                                        for (Option<?> opt : serverOptions) if (opt.changed()) changed = true;
                                        for (Option<?> opt : playerOptions) if (opt.changed()) changed = true;
                                    }

                                    if (changed && !o.changed()) o.requestSet(!categoryTickers[tickerIdx]);
                                    else if (!changed && o.changed()) o.requestSet(categoryTickers[tickerIdx]);
                                    tab.updateButtons();
                                }
                            }
                        };
                    }
                }).build();
    }

    private static class DynamicTabTitle implements Text {
        private final List<Option<?>> options;
        private final String label;
        private final boolean isServerTab;
        public DynamicTabTitle(String l, List<Option<?>> o, boolean isServer) { this.label = l; this.options = o; this.isServerTab = isServer; }
        @Override public Style getStyle() {
            for (Option<?> o : options) if (o.changed()) return Style.EMPTY.withColor(Formatting.RED);
            if (isServerTab) {
                for(int i=0; i<7; i++) if (sAllows[i] != sAllowsSnap[i] || sVals[i] != sValsSnap[i]) return Style.EMPTY.withColor(Formatting.RED);
            } else if (label.contains("Player")) {
                for(int i=0; i<7; i++) if (pVals[i] != pValsSnap[i]) return Style.EMPTY.withColor(Formatting.RED);
            }
            return Style.EMPTY;
        }
        @Override public TextContent getContent() { return Text.literal(label).getContent(); }
        @Override public List<Text> getSiblings() { return java.util.Collections.emptyList(); }
        @Override public OrderedText asOrderedText() {
            return (visitor) -> Text.literal(label).asOrderedText().accept((index, style, cp) -> visitor.accept(index, getStyle(), cp));
        }
    }
}