package com.lukarbonite.autopickup.client;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AutoPickupConfigScreen {

    private static final boolean[] sValsSnap = new boolean[7];
    private static final boolean[] sAllowsSnap = new boolean[7];
    private static final Tristate[] pValsSnap = new Tristate[7];

    private static final List<Option<?>> clientOptions = new ArrayList<>();
    private static final List<Option<?>> serverOptions = new ArrayList<>();
    private static final List<Option<?>> playerOptions = new ArrayList<>();

    private static final boolean[] categoryTickers = new boolean[3];
    private static final List<Option<Tristate>> pOptionRefs = new ArrayList<>();
    private static final List<Option<ServerControl>> sOptionRefs = new ArrayList<>();

    enum Tristate {
        UNSET("unset", ChatFormatting.GRAY),
        RESET("reset", ChatFormatting.YELLOW),
        TRUE("true", ChatFormatting.GREEN),
        FALSE("false", ChatFormatting.RED);

        final String val; final ChatFormatting fmt;
        Tristate(String v, ChatFormatting f) { this.val = v; this.fmt = f; }
        public Component getText() { return Component.literal(name()).withStyle(fmt); }
        public static Tristate fromEncoded(int i) {
            return switch(i) { case 1 -> TRUE; case 2 -> FALSE; case 3 -> RESET; default -> UNSET; };
        }
    }

    enum ServerControl {
        ON("ON", ChatFormatting.GREEN),
        OFF("OFF", ChatFormatting.RED),
        CLIENT("CLIENT DECIDE", ChatFormatting.AQUA);

        final String label; final ChatFormatting fmt;
        ServerControl(String l, ChatFormatting f) { this.label = l; this.fmt = f; }
        public Component getText() { return Component.literal(label).withStyle(fmt); }

        static ServerControl fromState(boolean val, boolean allow) {
            if (allow) return CLIENT;
            return val ? ON : OFF;
        }
    }

    public static Screen create(Screen parent) {
        pOptionRefs.clear();
        sOptionRefs.clear();
        clientOptions.clear();
        serverOptions.clear();
        playerOptions.clear();
        Arrays.fill(categoryTickers, false);

        for (int i = 0; i < 7; i++) {
            sValsSnap[i] = ClientSyncHandler.sVals[i];
            sAllowsSnap[i] = ClientSyncHandler.sAllows[i];
            pValsSnap[i] = Tristate.fromEncoded(ClientSyncHandler.pValsEncoded[i]);
        }

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.connection.sendCommand("autopickup query_global");
        }

        ClientConfigManager.ClientProfile profile = ClientConfigManager.getProfile();
        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder().title(Component.literal("Auto Pickup Config"));

        ConfigCategory clientCat = ConfigCategory.createBuilder()
                .name(new DynamicTabTitle("Client Settings", clientOptions, 0))
                .option(clientBool("Master Toggle", () -> profile.master, v -> profile.master = v, () -> ClientConfigManager.allowMaster))
                .group(OptionGroup.createBuilder().name(Component.literal("Blocks"))
                        .option(clientBool("Pickup Blocks", () -> profile.blocks, v -> profile.blocks = v, () -> ClientConfigManager.allowBlocks))
                        .option(clientBool("Pickup Block XP", () -> profile.blockXp, v -> profile.blockXp = v, () -> ClientConfigManager.allowBlockXp))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Mobs"))
                        .option(clientBool("Pickup Mob Loot", () -> profile.mobLoot, v -> profile.mobLoot = v, () -> ClientConfigManager.allowMobLoot))
                        .option(clientBool("Pickup Mob XP", () -> profile.mobXp, v -> profile.mobXp = v, () -> ClientConfigManager.allowMobXp))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Multiplayer Splitting"))
                        .option(clientBool("Split Mob Loot", () -> profile.splitMobLoot, v -> profile.splitMobLoot = v, () -> ClientConfigManager.allowSplitMobLoot))
                        .option(clientBool("Split Mob XP", () -> profile.splitMobXp, v -> profile.splitMobXp = v, () -> ClientConfigManager.allowSplitMobXp))
                        .build())
                .option(createTickerOption(0))
                .build();
        builder.category(clientCat);

        if (ClientSyncHandler.isAdmin) {
            String[] names = {"Master Toggle", "Pickup Blocks", "Pickup Block XP", "Pickup Mob Loot", "Pickup Mob XP", "Split Mob Loot", "Split Mob XP"};

            ConfigCategory.Builder serverCat = ConfigCategory.createBuilder().name(new DynamicTabTitle("Server Config", serverOptions, 1));
            for (int i = 0; i < 7; i++) {
                final int idx = i;
                Option<ServerControl> sOpt = Option.<ServerControl>createBuilder()
                        .name(Component.literal(names[idx]))
                        .binding(ServerControl.CLIENT,
                                () -> ServerControl.fromState(ClientSyncHandler.sVals[idx], ClientSyncHandler.sAllows[idx]),
                                (v) -> {
                                    if (v == ServerControl.ON) { ClientSyncHandler.sAllows[idx] = false; ClientSyncHandler.sVals[idx] = true; }
                                    else if (v == ServerControl.OFF) { ClientSyncHandler.sAllows[idx] = false; ClientSyncHandler.sVals[idx] = false; }
                                    else { ClientSyncHandler.sAllows[idx] = true; }
                                })
                        .controller(opt -> CyclingListControllerBuilder.create(opt)
                                .values(Arrays.asList(ServerControl.ON, ServerControl.OFF, ServerControl.CLIENT))
                                .formatValue(ServerControl::getText))
                        .build();
                sOptionRefs.add(sOpt);
                serverOptions.add(sOpt);
                serverCat.option(sOpt);
            }
            serverCat.option(createTickerOption(1));
            builder.category(serverCat.build());

            ConfigCategory.Builder playerCat = ConfigCategory.createBuilder().name(new DynamicTabTitle("Player Management", playerOptions, 2));

            Option<String> targetNameOpt = Option.<String>createBuilder()
                    .name(Component.literal("Target Player Name"))
                    .binding("", () -> ClientSyncHandler.targetPlayerName, v -> ClientSyncHandler.targetPlayerName = v)
                    .controller(StringControllerBuilder::create)
                    .build();
            playerCat.option(targetNameOpt);

            playerCat.option(ButtonOption.createBuilder()
                    .name(Component.literal("Load Player Config"))
                    .text(Component.literal("Search / Load"))
                    .action((s, o) -> {
                        String name = targetNameOpt.pendingValue();
                        if (name != null && !name.isEmpty()) {
                            Minecraft.getInstance().player.connection.sendCommand("autopickup query_player " + name);
                        }
                    }).build());

            for(int i=0; i<7; i++) {
                final int idx = i;
                Option<Tristate> pOpt = Option.<Tristate>createBuilder()
                        .name(Component.literal("Override " + names[idx]))
                        .binding(Tristate.UNSET,
                                () -> Tristate.fromEncoded(ClientSyncHandler.pValsEncoded[idx]),
                                v -> {
                                    if (v == Tristate.TRUE) ClientSyncHandler.pValsEncoded[idx] = 1;
                                    else if (v == Tristate.FALSE) ClientSyncHandler.pValsEncoded[idx] = 2;
                                    else if (v == Tristate.RESET) ClientSyncHandler.pValsEncoded[idx] = 3;
                                    else ClientSyncHandler.pValsEncoded[idx] = 0;
                                })
                        .controller(c -> CyclingListControllerBuilder.create(c)
                                .values(Arrays.asList(Tristate.UNSET, Tristate.RESET, Tristate.TRUE, Tristate.FALSE))
                                .formatValue(Tristate::getText))
                        .build();
                pOptionRefs.add(pOpt);
                playerOptions.add(pOpt);
                playerCat.option(pOpt);
            }
            playerCat.option(createTickerOption(2));
            builder.category(playerCat.build());
        }

        return builder.save(() -> {
            ClientConfigManager.save();
            FabricClientCommands.sendConfig();
            if (ClientSyncHandler.isAdmin) applyAdminChanges();
        }).build().generateScreen(parent);
    }

    private static void applyAdminChanges() {
        if (!ClientSyncHandler.serverDataReceived) return;
        String[] keys = {"master", "blocks", "blockXp", "mobLoot", "mobXp", "splitMobLoot", "splitMobXp"};
        for (int i = 0; i < 7; i++) {
            if (ClientSyncHandler.sVals[i] != sValsSnap[i]) sendGlobal(keys[i], ClientSyncHandler.sVals[i]);
            if (ClientSyncHandler.sAllows[i] != sAllowsSnap[i]) sendGlobal("allow_" + keys[i], ClientSyncHandler.sAllows[i]);
            Tristate currentP = Tristate.fromEncoded(ClientSyncHandler.pValsEncoded[i]);
            if (!ClientSyncHandler.targetPlayerName.isEmpty() && currentP != pValsSnap[i]) sendPlayer(ClientSyncHandler.targetPlayerName, keys[i], currentP);
        }
        // Update snapshots to reflect saved state
        for (int i = 0; i < 7; i++) {
            sValsSnap[i] = ClientSyncHandler.sVals[i];
            sAllowsSnap[i] = ClientSyncHandler.sAllows[i];
            pValsSnap[i] = Tristate.fromEncoded(ClientSyncHandler.pValsEncoded[i]);
        }
    }

    private static void sendGlobal(String key, boolean val) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.connection.sendCommand("autopickup global " + key + " " + val + " silent");
        }
    }

    private static void sendPlayer(String target, String key, Tristate val) {
        if (target.isEmpty() || Minecraft.getInstance().player == null) return;
        Minecraft.getInstance().player.connection.sendCommand("autopickup setPlayerConfig " + target + " " + key + " " + val.val + " silent");
    }

    private static Option<Boolean> clientBool(String name, Supplier<Boolean> g, Consumer<Boolean> s, Supplier<Boolean> allowed) {
        return Option.<Boolean>createBuilder().name(Component.literal(name)).binding(true, g, s)
                .customController(o -> new Controller<Boolean>() {
                    @Override public Option<Boolean> option() { return o; }
                    @Override public Component formatValue() { return o.pendingValue() ? Component.literal("ON").withStyle(ChatFormatting.GREEN) : Component.literal("OFF").withStyle(ChatFormatting.RED); }
                    @Override public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> dim) {
                        return new AbstractWidget(dim) {
                            private boolean focused = false;
                            @Override public void setFocused(boolean f) { this.focused = f; }
                            @Override public boolean isFocused() { return this.focused; }

                            @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                                int x = getDimension().x(), y = getDimension().y(), w = getDimension().width(), h = getDimension().height();
                                // Allow configuration in main menu; when connected, check server permission
                                boolean canConfig = Minecraft.getInstance().player == null || allowed.get();
                                int color = canConfig ? 0xFFFFFFFF : 0xFFA0A0A0;
                                graphics.text(Minecraft.getInstance().font, o.name(), x + 6, y + (h - 8)/2, color, true);
                                int btnW = 50, btnX = x + w - btnW - 6;
                                drawButtonRect(graphics, btnX, y, btnX + btnW, y + h, isMouseOver(mouseX, mouseY) && canConfig, canConfig);
                                graphics.centeredText(Minecraft.getInstance().font, formatValue(), btnX + btnW/2, y + (h-8)/2, color);
                            }
                            @Override
                            public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
                                // Extract values from the event object
                                double mouseX = event.x();
                                double mouseY = event.y();
                                int button = event.button();

                                int btnW = 50;
                                int btnX = getDimension().x() + getDimension().width() - btnW - 6;

                                boolean canConfig = Minecraft.getInstance().player == null || allowed.get();

                                if (canConfig && mouseX >= btnX) {
                                    o.requestSet(!o.pendingValue());
                                    playDownSound();
                                    return true;
                                }

                                return false;
                            }
                        };
                    }
                }).build();
    }

    private static Option<Boolean> createTickerOption(int tickerIdx) {
        return Option.<Boolean>createBuilder().name(Component.literal("Internal Sync"))
                .binding(false, () -> categoryTickers[tickerIdx], v -> categoryTickers[tickerIdx] = v)
                .customController(o -> new Controller<Boolean>() {
                    @Override public Option<Boolean> option() { return o; }
                    @Override public Component formatValue() { return Component.empty(); }
                    @Override public AbstractWidget provideWidget(YACLScreen s, Dimension<Integer> d) {
                        return new AbstractWidget(d) {
                            private boolean focused = false;
                            @Override public void setFocused(boolean f) { this.focused = f; }
                            @Override public boolean isFocused() { return this.focused; }

                            @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float dl) {
                                if (s.tabManager.getCurrentTab() instanceof YACLScreen.CategoryTab tab) {
                                    boolean changed = false;
                                    for(int i=0; i<7; i++) {
                                        if (ClientSyncHandler.sAllows[i] != sAllowsSnap[i] || ClientSyncHandler.sVals[i] != sValsSnap[i]) changed = true;
                                        if (Tristate.fromEncoded(ClientSyncHandler.pValsEncoded[i]) != pValsSnap[i]) changed = true;
                                    }
                                    for (Option<?> opt : clientOptions) if (opt.changed()) changed = true;
                                    if (changed && !o.changed()) o.requestSet(!categoryTickers[tickerIdx]);
                                    else if (!changed && o.changed()) o.requestSet(categoryTickers[tickerIdx]);
                                    tab.updateButtons();
                                }
                            }
                        };
                    }
                }).build();
    }

    private static class DynamicTabTitle implements Component {
        private final List<Option<?>> options;
        private final String label;
        private final int type;

        public DynamicTabTitle(String l, List<Option<?>> o, int type) { this.label = l; this.options = o; this.type = type; }

        @Override public Style getStyle() {
            boolean changed = false;
            if (type == 0) {
                for (Option<?> o : options) if (o.changed()) changed = true;
            } else if (type == 1) {
                for (int i=0; i<7; i++) if (ClientSyncHandler.sAllows[i] != sAllowsSnap[i] || ClientSyncHandler.sVals[i] != sValsSnap[i]) changed = true;
            } else {
                for (int i=0; i<7; i++) if (Tristate.fromEncoded(ClientSyncHandler.pValsEncoded[i]) != pValsSnap[i]) changed = true;
            }
            return changed ? Style.EMPTY.withColor(ChatFormatting.RED) : Style.EMPTY;
        }

        @Override public ComponentContents getContents() { return Component.literal(label).getContents(); }
        @Override public List<Component> getSiblings() { return java.util.Collections.emptyList(); }
        @Override public FormattedCharSequence getVisualOrderText() {
            return (visitor) -> Component.literal(label).getVisualOrderText().accept((index, style, cp) -> visitor.accept(index, getStyle(), cp));
        }
    }
}