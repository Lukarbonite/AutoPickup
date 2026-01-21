package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.accessor.DamageTrackerAccessor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LootSplittingLogic {

    public static List<PlayerEntity> getOrderedPickers(LivingEntity mob, ServerWorld world, PlayerEntity killer) {
        List<PlayerEntity> pickers = new ArrayList<>();

        // Killer is always first
        pickers.add(killer);

        if (mob instanceof DamageTrackerAccessor tracker) {
            List<UUID> history = tracker.autopickup_getAttackers();
            for (UUID uuid : history) {
                if (uuid.equals(killer.getUuid())) continue; // Already added

                PlayerEntity p = world.getServer().getPlayerManager().getPlayer(uuid);
                // Basic validation: Must be online, in the same world, and not a spectator
                if (p != null && !p.isSpectator() && p.getEntityWorld() == world) {
                    pickers.add(p);
                }
            }
        }
        return pickers;
    }

    public static List<ItemStack> distributeLoot(PlayerEntity killer, LivingEntity mob, List<ItemStack> drops) {
        if (drops.isEmpty()) return drops;
        ServerWorld world = (ServerWorld) killer.getEntityWorld();

        // 1. Determine if splitting is active
        if (!AutoPickupApi.isSplitMobLootEnabled(killer)) {
            // No split -> Try to give everything to killer
            return AutoPickupApi.tryPickupFromMob(killer, drops);
        }

        // 2. Get Participants
        List<PlayerEntity> participants = getOrderedPickers(mob, world, killer);

        // 3. Round Robin Distribution
        List<ItemStack> unpicked = new ArrayList<>();
        int pIndex = 0;

        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;

            // Note: If a stack has count > 1, we treat it as one "slot" of loot for simplicity,
            // or we could split the stack. Standard RPG split usually distributes by item slot.
            // Let's stick to item slot distribution to match "drops" list iteration.

            PlayerEntity beneficiary = participants.get(pIndex % participants.size());
            pIndex++;

            // Check if this specific beneficiary has AutoPickup enabled
            if (AutoPickupApi.isMasterEnabled(beneficiary) && AutoPickupApi.isMobLootEnabled(beneficiary)) {
                List<ItemStack> rem = AutoPickupApi.tryPickupFromMob(beneficiary, List.of(stack));
                if (!rem.isEmpty()) unpicked.addAll(rem);
            } else {
                // If they don't have autopickup, it drops to the ground (unpicked)
                unpicked.add(stack);
            }
        }

        return unpicked;
    }

    public static void distributeXp(PlayerEntity killer, LivingEntity mob, int amount) {
        if (amount <= 0) return;
        ServerWorld world = (ServerWorld) killer.getEntityWorld();

        // 1. Check Split
        if (!AutoPickupApi.isSplitMobXpEnabled(killer)) {
            // No split -> Killer gets all
            AutoPickupApi.tryPickupMobExperience(killer, amount);
            return;
        }

        // 2. Participants
        List<PlayerEntity> participants = getOrderedPickers(mob, world, killer);
        if (participants.isEmpty()) return;

        // 3. Split Evenly
        int splitAmount = amount / participants.size();
        int remainder = amount % participants.size();

        for (int i = 0; i < participants.size(); i++) {
            PlayerEntity p = participants.get(i);
            int xp = splitAmount + (i < remainder ? 1 : 0); // Distribute remainder

            AutoPickupApi.tryPickupMobExperience(p, xp);
        }
    }
}