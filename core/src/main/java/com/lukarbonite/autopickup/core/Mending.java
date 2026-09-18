package com.lukarbonite.autopickup.core;

import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/** Mending repair math, independent of how each version represents item stacks. */
public final class Mending {

    private Mending() {}

    /**
     * Spends {@code experience} repairing randomly chosen items from {@code mendable} (2 durability per
     * XP point, as vanilla does) until the XP or the damaged items run out. Fully repaired items are
     * removed from the list.
     *
     * @param randomIndex returns a random index below its argument (the player's RNG)
     * @return the XP left over, which the caller gives to the player as normal experience
     */
    public static <S> int repair(int experience, List<S> mendable, IntUnaryOperator randomIndex,
                                 ToIntFunction<S> getDamage, ObjIntConsumer<S> setDamage) {
        // Distribute XP across all damaged Mending items, exhausting remaining XP rather than stopping after one pick.
        while (experience > 0 && !mendable.isEmpty()) {
            S item = mendable.get(randomIndex.applyAsInt(mendable.size()));
            int damage = getDamage.applyAsInt(item);
            int repair = Math.min(experience * 2, damage);
            setDamage.accept(item, damage - repair);
            experience -= (repair + 1) / 2;
            if (damage - repair <= 0) mendable.remove(item);
        }
        return experience;
    }
}
