package com.lukarbonite.autopickup;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Credits items spawned directly by a player-killed mob's death to the killer as mob loot:
 * <ul>
 *   <li>while onDeath runs, including what vanilla subclasses drop after super.onDeath()
 *       (horse/donkey inventory and chest, pig saddle) and what mods spawn from LivingDeathEvent
 *       (e.g. Butchery carcasses), via a scope MobDeathScopeMixin opens;</li>
 *   <li>on the corpse's next tick, from inside its hitbox (e.g. the lead a leashed mob drops).</li>
 * </ul>
 * MCP copy of common's MobDeathDrops; WorldServerMixin asks {@link #capture}.
 */
public final class MobDeathDrops {

    private MobDeathDrops() {}

    /** How far outside the dying mob's hitbox a spawn during onDeath() still counts as its drop. */
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
        final EntityLivingBase mob;
        final EntityPlayer killer; // null when the death is not eligible, so nested deaths stay balanced

        Scope(EntityLivingBase mob, EntityPlayer killer) {
            this.mob = mob;
            this.killer = killer;
        }
    }

    private static final class Corpse {
        final EntityLivingBase mob;
        final EntityPlayer killer;
        final long expiresAt;

        Corpse(EntityLivingBase mob, EntityPlayer killer, long expiresAt) {
            this.mob = mob;
            this.killer = killer;
            this.expiresAt = expiresAt;
        }
    }

    private static final Deque<Scope> SCOPES = new ArrayDeque<>();
    private static final List<Corpse> CORPSES = new ArrayList<>();
    private static long tick;

    public static void begin(EntityLivingBase mob, DamageSource source) {
        EntityPlayer killer = null;
        Entity attacker = source.getTrueSource();
        if (!(mob instanceof EntityPlayer) && attacker instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) attacker;
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
     * Called at the end of each server tick: drops any scope an exception left open inside onDeath()
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
     * Picks up an EntityItem spawned by a dying mob or its corpse, splitting it between attackers
     * like loot-table drops. Returns true when the whole stack was taken (cancel the spawn);
     * otherwise the entity's stack is reduced to what is left.
     */
    public static boolean capture(EntityItem item) {
        Scope scope = SCOPES.peek();
        if (scope != null) {
            if (scope.killer != null && isNear(item, scope.mob, CAPTURE_MARGIN)) {
                return take(item, scope.killer, scope.mob);
            }
            return false;
        }

        for (Corpse corpse : CORPSES) {
            if (!corpse.mob.isEntityAlive() && isNear(item, corpse.mob, CORPSE_MARGIN)) {
                // Re-check: the killer may have changed settings or left since the kill.
                EntityPlayer killer = corpse.killer;
                if (killer.isSpectator() || killer.world != item.world
                        || !AutoPickupApi.isMasterEnabled(killer) || !AutoPickupApi.isMobLootEnabled(killer)) {
                    return false;
                }
                return take(item, killer, corpse.mob);
            }
        }
        return false;
    }

    private static boolean isNear(EntityItem item, EntityLivingBase mob, double margin) {
        if (item.world != mob.world) return false;
        AxisAlignedBB area = mob.getEntityBoundingBox().grow(margin);
        return area.contains(new Vec3d(item.posX, item.posY, item.posZ));
    }

    private static boolean take(EntityItem item, EntityPlayer killer, EntityLivingBase mob) {
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
