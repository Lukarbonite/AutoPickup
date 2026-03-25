package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.accessor.DamageTrackerAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LootSplittingLogic {

    private enum SplitType {
        LOOT,
        XP
    }

    /**
     * Determines the list of players eligible for a split based on the specific type (Loot or XP).
     *
     * <p>A player is only considered a participant if:
     * <ol>
     *     <li>They are online, in the same world, and not a spectator.</li>
     *     <li>They have <b>Master AutoPickup</b> enabled.</li>
     *     <li>They have the specific <b>Mob Pickup</b> toggle enabled (Loot or XP).</li>
     *     <li>They have the specific <b>Split</b> toggle enabled.</li>
     * </ol>
     *
     * @param mob    The entity that died.
     * @param world  The world where the entity died.
     * @param killer The player who dealt the killing blow (always Index 0).
     * @param type   The type of split being calculated (LOOT or XP).
     * @return A list of eligible players.
     */
    private static List<Player> getParticipants(LivingEntity mob, ServerLevel world, Player killer, SplitType type) {
        List<Player> participants = new ArrayList<>();

        // Killer is always Index 0.
        // Note: Validity of killer (Master/MobEnabled) is checked in Mixin before calling this.
        participants.add(killer);

        // 1. Check if Killer initiated the split (Must have Split Enabled)
        boolean killerSplitEnabled = (type == SplitType.LOOT)
                ? AutoPickupApi.isSplitMobLootEnabled(killer)
                : AutoPickupApi.isSplitMobXpEnabled(killer);

        if (!killerSplitEnabled) {
            return participants;
        }

        // 2. Add other participants from history
        if (mob instanceof DamageTrackerAccessor tracker) {
            List<UUID> history = tracker.autopickup_getAttackers();

            for (UUID uuid : history) {
                if (uuid.equals(killer.getUUID())) continue;

                Player p = world.getServer().getPlayerList().getPlayer(uuid);

                // Basic Validation
                if (p != null && !p.isSpectator() && p.level() == world) {

                    // STRICT VALIDATION:
                    // Player must have Master ON, specific Pickup ON, AND specific Split ON.
                    boolean isMasterOn = AutoPickupApi.isMasterEnabled(p);
                    boolean isPickupOn;
                    boolean isSplitOn;

                    if (type == SplitType.LOOT) {
                        isPickupOn = AutoPickupApi.isMobLootEnabled(p);
                        isSplitOn = AutoPickupApi.isSplitMobLootEnabled(p);
                    } else {
                        isPickupOn = AutoPickupApi.isMobXpEnabled(p);
                        isSplitOn = AutoPickupApi.isSplitMobXpEnabled(p);
                    }

                    if (isMasterOn && isPickupOn && isSplitOn) {
                        participants.add(p);
                    }
                }
            }
        }

        return participants;
    }

    public static List<ItemStack> distributeLoot(Player killer, LivingEntity mob, List<ItemStack> drops) {
        if (drops.isEmpty()) return drops;
        ServerLevel world = (ServerLevel) killer.level();

        List<Player> participants = getParticipants(mob, world, killer, SplitType.LOOT);
        List<ItemStack> unpicked = new ArrayList<>();

        int participantCount = participants.size();

        for (ItemStack originalStack : drops) {
            if (originalStack.isEmpty()) continue;

            int totalItems = originalStack.getCount();
            int perPerson = totalItems / participantCount;
            int remainder = totalItems % participantCount;

            for (int i = 0; i < participantCount; i++) {
                Player beneficiary = participants.get(i);

                // Calculate amount
                int amountForPlayer = perPerson;
                if (i == 0) amountForPlayer += remainder; // Killer gets remainder

                if (amountForPlayer <= 0) continue;

                ItemStack splitStack = originalStack.copy();
                splitStack.setCount(amountForPlayer);

                // Attempt Pickup
                // We re-check master/mob settings here just to be safe, though getParticipants filters mostly.
                if (AutoPickupApi.isMasterEnabled(beneficiary) && AutoPickupApi.isMobLootEnabled(beneficiary)) {
                    List<ItemStack> rem = AutoPickupApi.tryPickupFromMob(beneficiary, new ArrayList<>(List.of(splitStack)));
                    if (!rem.isEmpty()) {
                        unpicked.addAll(rem); // Inventory full -> drop
                    }
                } else {
                    unpicked.add(splitStack); // Pickup disabled -> drop
                }
            }
        }

        return unpicked;
    }

    public static void distributeXp(Player killer, LivingEntity mob, int amount) {
        if (amount <= 0) return;
        ServerLevel world = (ServerLevel) killer.level();

        List<Player> participants = getParticipants(mob, world, killer, SplitType.XP);
        if (participants.isEmpty()) return;

        int count = participants.size();
        int splitAmount = amount / count;
        int remainder = amount % count;

        for (int i = 0; i < count; i++) {
            Player p = participants.get(i);
            int xpToGive = splitAmount;
            if (i == 0) xpToGive += remainder;

            if (xpToGive > 0) {
                // If this fails (e.g. somehow config changed mid-tick), it vanishes.
                // But getParticipants ensures they are eligible.
                AutoPickupApi.tryPickupMobExperience(p, xpToGive);
            }
        }
    }
}