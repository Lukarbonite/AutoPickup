package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.platform.VersionHelper;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Credits items spawned directly by a player-killed mob's death to the killer as mob loot:
 * <ul>
 *   <li>while LivingEntity#die runs (e.g. Butchery's carcasses, spawned from a LivingDeathEvent
 *       handler with addFreshEntity), via a scope MobDeathScopeMixin opens;</li>
 *   <li>on the corpse's next tick, from inside its hitbox (e.g. the lead a leashed mob drops).</li>
 * </ul>
 * ServerLevelMixin asks {@link #capture} about every ownerless ItemEntity.
 */
public final class MobDeathDrops {

    private MobDeathDrops() {}

    /** How far outside the dying mob's hitbox a spawn during die() still counts as its drop. */
    private static final double CAPTURE_MARGIN = 2.0;
    /** How far outside the corpse's hitbox a spawn on its next tick counts as its drop. */
    private static final double CORPSE_MARGIN = 0.5;
    /**
     * Corpses are tracked for the rest of the kill tick and the next one: a dead mob drops its lead on
     * its next entity tick, which is the same tick (killed while packets are handled) or the next one
     * (killed during entity ticking, after it already ticked).
     */
    private static final int CORPSE_TICKS = 2;

    private static final class Scope {
        final LivingEntity mob;
        final Player killer; // null when the death is not eligible, so nested deaths stay balanced

        Scope(LivingEntity mob, Player killer) {
            this.mob = mob;
            this.killer = killer;
        }
    }

    private static final class Corpse {
        final LivingEntity mob;
        final Player killer;
        final long expiresAt;

        Corpse(LivingEntity mob, Player killer, long expiresAt) {
            this.mob = mob;
            this.killer = killer;
            this.expiresAt = expiresAt;
        }
    }

    private static final Deque<Scope> SCOPES = new ArrayDeque<>();
    private static final List<Corpse> CORPSES = new ArrayList<>();
    private static long tick;

    public static void begin(LivingEntity mob, DamageSource source) {
        Player killer = null;
        Entity attacker = source.getEntity();
        if (!(mob instanceof Player) && attacker instanceof Player) {
            Player player = (Player) attacker;
            if (!player.isSpectator()
                    && AutoPickupApi.isMasterEnabled(player)
                    && AutoPickupApi.isMobLootEnabled(player)) {
                killer = player;
            }
        }
        SCOPES.push(new Scope(mob, killer));
        if (killer != null) CORPSES.add(new Corpse(mob, killer, tick + CORPSE_TICKS));
    }

    public static void end() {
        SCOPES.poll();
    }

    /**
     * Called at the end of each server tick: drops any scope an exception left open inside die()
     * and forgets corpses whose window is over.
     */
    public static void onServerTickEnd() {
        SCOPES.clear();
        tick++;
        for (Iterator<Corpse> it = CORPSES.iterator(); it.hasNext(); ) {
            if (it.next().expiresAt <= tick) it.remove();
        }
    }

    /**
     * Picks up an ItemEntity spawned by a dying mob or its corpse, splitting it between attackers
     * like loot-table drops. Returns true when the whole stack was taken (cancel the spawn);
     * otherwise the entity's stack is reduced to what is left.
     */
    public static boolean capture(ItemEntity item, Level level) {
        Scope scope = SCOPES.peek();
        if (scope != null) {
            if (scope.killer != null && isNear(item, level, scope.mob, CAPTURE_MARGIN)) {
                return take(item, scope.killer, scope.mob);
            }
            return false;
        }

        for (Corpse corpse : CORPSES) {
            if (!corpse.mob.isAlive() && isNear(item, level, corpse.mob, CORPSE_MARGIN)) {
                // Re-check: the killer may have changed settings or left since the kill.
                Player killer = corpse.killer;
                if (killer.isSpectator() || VersionHelper.INSTANCE.getLevel(killer) != level
                        || !AutoPickupApi.isMasterEnabled(killer) || !AutoPickupApi.isMobLootEnabled(killer)) {
                    return false;
                }
                return take(item, killer, corpse.mob);
            }
        }
        return false;
    }

    private static boolean isNear(ItemEntity item, Level level, LivingEntity mob, double margin) {
        if (level != VersionHelper.INSTANCE.getLevel(mob)) return false;
        AABB area = mob.getBoundingBox().inflate(margin);
        return area.contains(item.getX(), item.getY(), item.getZ());
    }

    private static boolean take(ItemEntity item, Player killer, LivingEntity mob) {
        ItemStack stack = item.getItem();
        List<ItemStack> remaining = LootSplittingLogic.distributeLoot(
                killer, mob, Collections.singletonList(stack.copy()));

        // Every remaining stack is a share of the one input stack, so they merge back into one.
        int left = 0;
        for (ItemStack rem : remaining) left += rem.getCount();
        if (left <= 0) return true;

        if (left != stack.getCount()) {
            ItemStack rest = stack.copy();
            rest.setCount(left);
            item.setItem(rest);
        }
        return false;
    }
}
