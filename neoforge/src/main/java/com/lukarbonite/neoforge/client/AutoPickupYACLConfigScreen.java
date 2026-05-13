package com.lukarbonite.neoforge.client;

import com.lukarbonite.autopickup.client.*;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
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

public class AutoPickupYACLConfigScreen {

    private static final List<Option<?>> clientOptions = new ArrayList<>();
    private static final List<Option<?>> serverOptions = new ArrayList<>();
    private static final List<Option<?>> playerOptions = new ArrayList<>();

    private static final boolean[] categoryTickers = new boolean[3];
    private static final List<Option<ConfigUIUtils.Tristate>> pOptionRefs = new ArrayList<>();
    private static final List<Option<ConfigUIUtils.ServerControl>> sOptionRefs = new ArrayList<>();

    public static Screen create(Screen parent) {
        pOptionRefs.clear();
        sOptionRefs.clear();
        clientOptions.clear();
        serverOptions.clear();
        playerOptions.clear();
        Arrays.fill(categoryTickers, false);

        ConfigUIUtils.updateSnapshots();
        ConfigUIUtils.requestGlobalConfig();

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
            ConfigCategory.Builder serverCat = ConfigCategory.createBuilder().name(new DynamicTabTitle("Server Config", serverOptions, 1));
            for (int i = 0; i < 7; i++) {
                final int idx = i;
                Option<ConfigUIUtils.ServerControl> sOpt = Option.<ConfigUIUtils.ServerControl>createBuilder()
                        .name(Component.literal(ConfigUIUtils.NAMES[idx]))
                        .binding(ConfigUIUtils.ServerControl.CLIENT,
                                () -> ConfigUIUtils.ServerControl.fromState(ClientSyncHandler.sVals[idx], ClientSyncHandler.sAllows[idx]),
                                (v) -> {
                                    if (v == ConfigUIUtils.ServerControl.ON) { ClientSyncHandler.sAllows[idx] = false; ClientSyncHandler.sVals[idx] = true; }
                                    else if (v == ConfigUIUtils.ServerControl.OFF) { ClientSyncHandler.sAllows[idx] = false; ClientSyncHandler.sVals[idx] = false; }
                                    else { ClientSyncHandler.sAllows[idx] = true; }
                                })
                        .controller(opt -> CyclingListControllerBuilder.create(opt)
                                .values(Arrays.asList(ConfigUIUtils.ServerControl.ON, ConfigUIUtils.ServerControl.OFF, ConfigUIUtils.ServerControl.CLIENT))
                                .formatValue(ConfigUIUtils.ServerControl::getText))
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
                        if (name != null && !name.isEmpty() && Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.connection.sendCommand("autopickup query_player " + name);
                        }
                    }).build());

            for (int i = 0; i < 7; i++) {
                final int idx = i;
                Option<ConfigUIUtils.Tristate> pOpt = Option.<ConfigUIUtils.Tristate>createBuilder()
                        .name(Component.literal("Override " + ConfigUIUtils.NAMES[idx]))
                        .binding(ConfigUIUtils.Tristate.UNSET,
                                () -> ConfigUIUtils.Tristate.fromEncoded(ClientSyncHandler.pValsEncoded[idx]),
                                v -> {
                                    if (v == ConfigUIUtils.Tristate.TRUE) ClientSyncHandler.pValsEncoded[idx] = 1;
                                    else if (v == ConfigUIUtils.Tristate.FALSE) ClientSyncHandler.pValsEncoded[idx] = 2;
                                    else if (v == ConfigUIUtils.Tristate.RESET) ClientSyncHandler.pValsEncoded[idx] = 3;
                                    else ClientSyncHandler.pValsEncoded[idx] = 0;
                                })
                        .controller(c -> CyclingListControllerBuilder.create(c)
                                .values(Arrays.asList(ConfigUIUtils.Tristate.UNSET, ConfigUIUtils.Tristate.RESET, ConfigUIUtils.Tristate.TRUE, ConfigUIUtils.Tristate.FALSE))
                                .formatValue(ConfigUIUtils.Tristate::getText))
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
            ClientNetworkManager.sendConfig();
            if (ClientSyncHandler.isAdmin) ConfigUIUtils.applyAdminChanges();
        }).build().generateScreen(parent);
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

                            @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                                int x = getDimension().x(), y = getDimension().y(), w = getDimension().width(), h = getDimension().height();
                                boolean canConfig = Minecraft.getInstance().player == null || allowed.get();
                                int color = canConfig ? 0xFFFFFFFF : 0xFFA0A0A0;
                                graphics.drawString(Minecraft.getInstance().font, o.name(), x + 6, y + (h - 8)/2, color, true);
                                int btnW = 50, btnX = x + w - btnW - 6;
                                drawButtonRect(graphics, btnX, y, btnX + btnW, y + h, isMouseOver(mouseX, mouseY) && canConfig, canConfig);
                                graphics.drawCenteredString(Minecraft.getInstance().font, formatValue(), btnX + btnW/2, y + (h-8)/2, color);
                            }
                            @Override
                            public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
                            @Override public NarrationPriority narrationPriority() { return NarrationPriority.NONE; }
                            @Override public void updateNarration(NarrationElementOutput output) {}
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

                            @Override public void render(GuiGraphics graphics, int mx, int my, float dl) {
                                if (s.tabManager.getCurrentTab() instanceof YACLScreen.CategoryTab tab) {
                                    boolean changed = false;
                                    for (int i = 0; i < 7; i++) {
                                        if (ClientSyncHandler.sAllows[i] != ConfigUIUtils.sAllowsSnap[i] || ClientSyncHandler.sVals[i] != ConfigUIUtils.sValsSnap[i]) changed = true;
                                        if (ConfigUIUtils.Tristate.fromEncoded(ClientSyncHandler.pValsEncoded[i]) != ConfigUIUtils.pValsSnap[i]) changed = true;
                                    }
                                    for (Option<?> opt : clientOptions) if (opt.changed()) changed = true;
                                    if (changed && !o.changed()) o.requestSet(!categoryTickers[tickerIdx]);
                                    else if (!changed && o.changed()) o.requestSet(categoryTickers[tickerIdx]);
                                    tab.updateButtons();
                                }
                            }
                            @Override public NarrationPriority narrationPriority() { return NarrationPriority.NONE; }
                            @Override public void updateNarration(NarrationElementOutput output) {}
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
                for (int i = 0; i < 7; i++) if (ClientSyncHandler.sAllows[i] != ConfigUIUtils.sAllowsSnap[i] || ClientSyncHandler.sVals[i] != ConfigUIUtils.sValsSnap[i]) changed = true;
            } else {
                for (int i = 0; i < 7; i++) if (ConfigUIUtils.Tristate.fromEncoded(ClientSyncHandler.pValsEncoded[i]) != ConfigUIUtils.pValsSnap[i]) changed = true;
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
