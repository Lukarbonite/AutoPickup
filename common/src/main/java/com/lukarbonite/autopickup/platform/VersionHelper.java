package com.lukarbonite.autopickup.platform;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Per-Minecraft-version API differences that common code cannot express directly.
 *
 * <p>Common is compiled into every version from 1.16.5 to 26.x, so it must stick to APIs and
 * Java 8 syntax that exist everywhere. Anything that was renamed or reshaped across that range
 * lives behind this interface instead.
 */
public interface VersionHelper {

    VersionHelper INSTANCE = Loader.load();

    /** True if the stack has the Mending enchantment. */
    boolean hasMending(ItemStack stack, ServerLevel level);

    /** True if this slot should be checked when distributing Mending XP. */
    boolean isArmorOrHandSlot(EquipmentSlot slot);

    /** True if the player is an operator on the server. */
    boolean isOp(ServerPlayer player, MinecraftServer server);

    /** The level an entity is in: the {@code level} field up to 1.19, {@code level()} from 1.20. */
    Level getLevel(Entity entity);

    /** A player's inventory: the {@code inventory} field up to 1.16, {@code getInventory()} from 1.17. */
    Inventory getInventory(Player player);

    /** A plain text component: {@code new TextComponent} up to 1.18, {@code Component.literal} from 1.19. */
    MutableComponent literal(String text);

    /** Sends feedback to a command source; from 1.20 {@code sendSuccess} takes a supplier. */
    void sendSuccess(CommandSourceStack source, Component message, boolean broadcastToOps);

    /** ServiceLoader lookup without Java 9's {@code findFirst()}. */
    final class Loader {
        private Loader() {}

        static VersionHelper load() {
            Iterator<VersionHelper> it = ServiceLoader.load(VersionHelper.class).iterator();
            if (!it.hasNext()) throw new RuntimeException("No VersionHelper implementation found");
            return it.next();
        }
    }
}
