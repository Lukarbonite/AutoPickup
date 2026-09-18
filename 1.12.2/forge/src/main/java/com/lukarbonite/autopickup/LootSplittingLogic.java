package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.accessor.DamageTrackerAccessor;
import com.lukarbonite.autopickup.core.Split;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Splits mob loot and XP between the killer and eligible attackers; who and how much comes from {@link Split}. */
public class LootSplittingLogic {

    private static List<EntityPlayer> getParticipants(EntityLivingBase mob, EntityPlayer killer, Split.Kind kind) {
        World world = killer.world;
        List<UUID> attackers = mob instanceof DamageTrackerAccessor
                ? ((DamageTrackerAccessor) mob).autopickup_getAttackers()
                : Collections.<UUID>emptyList();
        return Split.participants(killer, killer.getUniqueID(), kind, attackers, uuid -> {
            EntityPlayer p = world.getMinecraftServer().getPlayerList().getPlayerByUUID(uuid);
            return p != null && !p.isSpectator() && p.world == world ? p : null;
        });
    }

    public static List<ItemStack> distributeLoot(EntityPlayer killer, EntityLivingBase mob, List<ItemStack> drops) {
        if (drops.isEmpty()) return drops;

        List<EntityPlayer> participants = getParticipants(mob, killer, Split.Kind.LOOT);
        List<ItemStack> unpicked = new ArrayList<>();

        for (ItemStack originalStack : drops) {
            if (originalStack.isEmpty()) continue;

            int[] shares = Split.shares(originalStack.getCount(), participants.size());
            for (int i = 0; i < shares.length; i++) {
                if (shares[i] <= 0) continue;
                EntityPlayer beneficiary = participants.get(i);

                ItemStack splitStack = originalStack.copy();
                splitStack.setCount(shares[i]);

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

    public static void distributeXp(EntityPlayer killer, EntityLivingBase mob, int amount) {
        if (amount <= 0) return;

        List<EntityPlayer> participants = getParticipants(mob, killer, Split.Kind.XP);
        int[] shares = Split.shares(amount, participants.size());
        for (int i = 0; i < shares.length; i++) {
            // getParticipants ensures they are eligible; if a config changed mid-tick, the share vanishes.
            if (shares[i] > 0) AutoPickupApi.tryPickupMobExperience(participants.get(i), shares[i]);
        }
    }
}
