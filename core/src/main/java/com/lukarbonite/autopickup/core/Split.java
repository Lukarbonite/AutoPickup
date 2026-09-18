package com.lukarbonite.autopickup.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Who shares a mob's loot or XP, and how it divides. Generic over the version's player type;
 * each version's {@code LootSplittingLogic} does the actual item and XP handling.
 */
public final class Split {

    private Split() {}

    public enum Kind { LOOT, XP }

    /**
     * Determines the list of players eligible for a split.
     *
     * <p>A player is only considered a participant if:
     * <ol>
     *     <li>They are online, in the same world, and not a spectator ({@code eligibleOnline}).</li>
     *     <li>They have <b>Master AutoPickup</b> enabled.</li>
     *     <li>They have the specific <b>Mob Pickup</b> toggle enabled (Loot or XP).</li>
     *     <li>They have the specific <b>Split</b> toggle enabled.</li>
     * </ol>
     *
     * @param killer         the player who dealt the killing blow (always index 0)
     * @param killerId       the killer's UUID
     * @param attackers      recent attackers of the mob, most recent first (may be empty)
     * @param eligibleOnline looks a UUID up among online players, returning null unless that player
     *                       is in the mob's world and not a spectator
     */
    public static <P> List<P> participants(P killer, UUID killerId, Kind kind, List<UUID> attackers,
                                           Function<UUID, P> eligibleOnline) {
        List<P> participants = new ArrayList<>();

        // Killer is always Index 0.
        // Note: Validity of killer (Master/MobEnabled) is checked in Mixin before calling this.
        participants.add(killer);

        // Only split if the killer initiated it (must have Split enabled)
        boolean killerSplitEnabled = kind == Kind.LOOT
                ? Settings.isSplitMobLootEnabled(killerId)
                : Settings.isSplitMobXpEnabled(killerId);
        if (!killerSplitEnabled) return participants;

        for (UUID uuid : attackers) {
            if (uuid.equals(killerId)) continue;
            P p = eligibleOnline.apply(uuid);
            if (p == null) continue;

            // STRICT VALIDATION:
            // Player must have Master ON, specific Pickup ON, AND specific Split ON.
            boolean pickupOn = kind == Kind.LOOT ? Settings.isMobLootEnabled(uuid) : Settings.isMobXpEnabled(uuid);
            boolean splitOn = kind == Kind.LOOT ? Settings.isSplitMobLootEnabled(uuid) : Settings.isSplitMobXpEnabled(uuid);
            if (Settings.isMasterEnabled(uuid) && pickupOn && splitOn) {
                participants.add(p);
            }
        }
        return participants;
    }

    /** Divides {@code total} into {@code count} shares; the remainder goes to index 0 (the killer). */
    public static int[] shares(int total, int count) {
        int[] shares = new int[count];
        int perPerson = total / count;
        int remainder = total % count;
        for (int i = 0; i < count; i++) shares[i] = perPerson;
        if (count > 0) shares[0] += remainder;
        return shares;
    }
}
