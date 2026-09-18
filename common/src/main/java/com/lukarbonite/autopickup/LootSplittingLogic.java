package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.accessor.DamageTrackerAccessor;
import com.lukarbonite.autopickup.core.Split;
import com.lukarbonite.autopickup.platform.VersionHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Splits mob loot and XP between the killer and eligible attackers; who and how much comes from {@link Split}. */
public class LootSplittingLogic {

    private static List<Player> getParticipants(LivingEntity mob, ServerLevel world, Player killer, Split.Kind kind) {
        List<UUID> attackers = mob instanceof DamageTrackerAccessor
                ? ((DamageTrackerAccessor) mob).autopickup_getAttackers()
                : Collections.<UUID>emptyList();
        return Split.participants(killer, killer.getUUID(), kind, attackers, uuid -> {
            Player p = world.getServer().getPlayerList().getPlayer(uuid);
            return p != null && !p.isSpectator() && VersionHelper.INSTANCE.getLevel(p) == world ? p : null;
        });
    }

    public static List<ItemStack> distributeLoot(Player killer, LivingEntity mob, List<ItemStack> drops) {
        if (drops.isEmpty()) return drops;
        ServerLevel world = (ServerLevel) VersionHelper.INSTANCE.getLevel(killer);

        List<Player> participants = getParticipants(mob, world, killer, Split.Kind.LOOT);
        List<ItemStack> unpicked = new ArrayList<>();

        for (ItemStack originalStack : drops) {
            if (originalStack.isEmpty()) continue;

            int[] shares = Split.shares(originalStack.getCount(), participants.size());
            for (int i = 0; i < shares.length; i++) {
                if (shares[i] <= 0) continue;
                Player beneficiary = participants.get(i);

                ItemStack splitStack = originalStack.copy();
                splitStack.setCount(shares[i]);

                // Attempt Pickup
                // We re-check master/mob settings here just to be safe, though getParticipants filters mostly.
                if (AutoPickupApi.isMasterEnabled(beneficiary) && AutoPickupApi.isMobLootEnabled(beneficiary)) {
                    List<ItemStack> rem = AutoPickupApi.tryPickupFromMob(beneficiary, new ArrayList<>(Collections.singletonList(splitStack)));
                    unpicked.addAll(rem); // Inventory full -> drop
                } else {
                    unpicked.add(splitStack); // Pickup disabled -> drop
                }
            }
        }

        return unpicked;
    }

    public static void distributeXp(Player killer, LivingEntity mob, int amount) {
        if (amount <= 0) return;
        ServerLevel world = (ServerLevel) VersionHelper.INSTANCE.getLevel(killer);

        List<Player> participants = getParticipants(mob, world, killer, Split.Kind.XP);
        int[] shares = Split.shares(amount, participants.size());
        for (int i = 0; i < shares.length; i++) {
            // getParticipants ensures they are eligible; if a config changed mid-tick, the share vanishes.
            if (shares[i] > 0) AutoPickupApi.tryPickupMobExperience(participants.get(i), shares[i]);
        }
    }
}
