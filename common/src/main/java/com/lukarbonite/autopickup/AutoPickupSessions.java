package com.lukarbonite.autopickup;

import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks short-lived per-player mining sessions and recent block break positions.
 * Also maintains a tight per-player block-drop context used to intercept only
 * the immediate item spawns caused by a player's block break, avoiding siphoning
 * from unrelated sources (farms, player drops, animal eggs, etc.).
 */
public final class AutoPickupSessions {

    private AutoPickupSessions() {}

    private static final ConcurrentHashMap<Integer, Session> SESSIONS = new ConcurrentHashMap<>();

    // Per-player stack of active drop contexts (tight radius around a specific broken block)
    private static final ConcurrentHashMap<Integer, ArrayDeque<DropGuard>> DROP_GUARDS = new ConcurrentHashMap<>();

    // Exact block-position → player-id ownership for deferred XP attribution (VeinMiner etc.)
    private static final ConcurrentHashMap<Long, Integer> BREAK_OWNERS = new ConcurrentHashMap<>();

    // Tunables (can be moved to config later if needed)
    private static final int MAX_RECENT_POSITIONS = 64;         // Positions to remember per session
    private static final double INTERCEPT_RADIUS2 = 36.0;       // 6-block radius squared (session proximity)
    private static final double DROP_CONTEXT_RADIUS2 = 9.0;     // 3-block radius squared (tight drop context)
    private static final double LINK_RADIUS2 = 36.0;            // 6-block link radius for chained breaks
    private static final int SESSION_TTL_TICKS = 8;             // Keep session alive for short chains across ticks (~0.4s)

    // Global tick marker advanced once per server tick (end of tick).
    private static int CURRENT_TICK = 0;

    private static final class Session {
        final WeakReference<Player> playerRef;
        final ArrayDeque<BlockPos> recent = new ArrayDeque<>();
        int ticksToLive = SESSION_TTL_TICKS;
        int lastTouchedTick = 0; // tick when this session was last updated

        Session(Player p) {
            this.playerRef = new WeakReference<>(p);
        }

        void touch() { this.ticksToLive = SESSION_TTL_TICKS; this.lastTouchedTick = CURRENT_TICK; }

        void addPos(BlockPos pos) {
            recent.addLast(pos.immutable());
            while (recent.size() > MAX_RECENT_POSITIONS) {
                recent.removeFirst();
            }
        }

        double minDist2(Vec3 v) {
            if (recent.isEmpty()) return Double.MAX_VALUE;
            double sx = v.x, sy = v.y, sz = v.z;
            double best = Double.MAX_VALUE;
            for (BlockPos b : recent) {
                double dx = sx - (b.getX() + 0.5);
                double dy = sy - (b.getY() + 0.5);
                double dz = sz - (b.getZ() + 0.5);
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < best) best = d2;
            }
            return best;
        }
    }

    private static final class DropGuard {
        final BlockPos origin;
        DropGuard(BlockPos origin) { this.origin = origin.immutable(); }
    }

    public static void begin(Player player) {
        if (player == null) return;
        SESSIONS.computeIfAbsent(player.getId(), id -> new Session(player)).touch();
    }

    public static void addBreak(Player player, BlockPos pos) {
        if (player == null || pos == null) return;
        Session s = SESSIONS.computeIfAbsent(player.getId(), id -> new Session(player));
        s.touch();
        s.addPos(pos);
        BREAK_OWNERS.put(pos.asLong(), player.getId());
    }

    /**
     * Begin a tight drop context around a specific broken block for the given player.
     */
    public static void beginDropContext(Player player, BlockPos pos) {
        if (player == null || pos == null) return;
        ArrayDeque<DropGuard> stack = DROP_GUARDS.computeIfAbsent(player.getId(), id -> new ArrayDeque<>());
        stack.push(new DropGuard(pos));
    }

    public static boolean hasDropContext(Player player) {
        if (player == null) return false;
        ArrayDeque<DropGuard> stack = DROP_GUARDS.get(player.getId());
        return stack != null && !stack.isEmpty();
    }

    /**
     * End the most recent drop context for the given player.
     */
    public static void endDropContext(Player player) {
        if (player == null) return;
        ArrayDeque<DropGuard> stack = DROP_GUARDS.get(player.getId());
        if (stack != null && !stack.isEmpty()) stack.pop();
    }

    /**
     * Find the most plausible owner of an item/XP spawn near recent break positions.
     * Returns null if no session is close enough.
     */
    public static Player findOwner(Vec3 spawnPos) {
        Player best = null;
        double bestD2 = Double.MAX_VALUE;
        for (Map.Entry<Integer, Session> e : SESSIONS.entrySet()) {
            Session s = e.getValue();
            Player p = s.playerRef.get();
            if (p == null || p.isSpectator()) continue;
            double d2 = s.minDist2(spawnPos);
            if (d2 <= INTERCEPT_RADIUS2 && d2 < bestD2) {
                bestD2 = d2;
                best = p;
            }
        }
        return best;
    }

    /**
     * Find the player that currently has an active drop context closest to the given spawn position.
     * Only returns a player if the spawn is within a small radius of that context.
     */
    public static Player findOwnerInDropContext(Vec3 spawnPos) {
        Player best = null;
        double bestD2 = Double.MAX_VALUE;
        for (Map.Entry<Integer, ArrayDeque<DropGuard>> e : DROP_GUARDS.entrySet()) {
            ArrayDeque<DropGuard> stack = e.getValue();
            if (stack == null || stack.isEmpty()) continue;
            Session s = SESSIONS.get(e.getKey());
            if (s == null) continue;
            Player p = s.playerRef.get();
            if (p == null || p.isSpectator()) continue;
            DropGuard g = stack.peek();
            if (g == null) continue;
            double dx = spawnPos.x - (g.origin.getX() + 0.5);
            double dy = spawnPos.y - (g.origin.getY() + 0.5);
            double dz = spawnPos.z - (g.origin.getZ() + 0.5);
            double d2 = dx*dx + dy*dy + dz*dz;
            if (d2 <= DROP_CONTEXT_RADIUS2 && d2 < bestD2) {
                bestD2 = d2;
                best = p;
            }
        }
        return best;
    }

    /**
     * Exact-position lookup for deferred XP attribution. Converts the spawn Vec3 to the
     * containing BlockPos and looks up the registered break owner. Safer than radius-based
     * findOwner when multiple players may be mining nearby simultaneously.
     */
    public static Player findOwnerByBreakPos(Vec3 spawnPos) {
        long key = BlockPos.containing(spawnPos).asLong();
        Integer id = BREAK_OWNERS.get(key);
        if (id == null) return null;
        Session s = SESSIONS.get(id);
        if (s == null) return null;
        Player p = s.playerRef.get();
        return (p != null && !p.isSpectator()) ? p : null;
    }

    // --- Linked-break helpers for veinminer/liteminer compatibility ---

    public static Player findLinkedOwnerForBreak(BlockPos pos) {
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Player best = null;
        double bestD2 = Double.MAX_VALUE;
        for (Map.Entry<Integer, Session> e : SESSIONS.entrySet()) {
            Session s = e.getValue();
            Player p = s.playerRef.get();
            if (p == null || p.isSpectator()) continue;
            double d2 = s.minDist2(center);
            if (d2 <= LINK_RADIUS2 && d2 < bestD2) {
                bestD2 = d2;
                best = p;
            }
        }
        return best;
    }

    // Maintain a small thread-local stack to balance HEAD/TAIL injections safely
    private static final ThreadLocal<java.util.ArrayDeque<Integer>> OPEN_LINKED_CONTEXT = ThreadLocal.withInitial(java.util.ArrayDeque::new);

    public static void openLinkedDropContext(BlockPos pos) {
        Player owner = findLinkedOwnerForBreak(pos);
        if (owner != null) {
            beginDropContext(owner, pos);
            OPEN_LINKED_CONTEXT.get().push(owner.getId());
        } else {
            OPEN_LINKED_CONTEXT.get().push(-1);
        }
    }

    public static void closeLinkedDropContext() {
        java.util.ArrayDeque<Integer> stack = OPEN_LINKED_CONTEXT.get();
        if (stack.isEmpty()) return;
        int id = stack.pop();
        if (id >= 0) {
            Session s = SESSIONS.get(id);
            Player p = s != null ? s.playerRef.get() : null;
            if (p != null) {
                endDropContext(p);
            }
        }
    }

    // --- Same-tick tiny-radius fallback for direct ItemEntity spawns ---
    private static final double SAME_TICK_RADIUS2 = 2.25; // 1.5 blocks squared

    /**
     * If no drop context matched, attribute spawns that happen in the same tick and within a tiny radius
     * of a recent break position to the corresponding player.
     */
    public static Player findOwnerSameTickTight(Vec3 spawnPos) {
        Player best = null; double bestD2 = Double.MAX_VALUE;
        for (Map.Entry<Integer, Session> e : SESSIONS.entrySet()) {
            Session s = e.getValue();
            Player p = s.playerRef.get();
            if (p == null || p.isSpectator() || s.recent.isEmpty()) continue;
            if (s.lastTouchedTick != CURRENT_TICK) continue;
            double sx = spawnPos.x, sy = spawnPos.y, sz = spawnPos.z;
            int checked = 0;
            for (var itPos = s.recent.descendingIterator(); itPos.hasNext() && checked < 6; checked++) {
                BlockPos b = itPos.next();
                double dx = sx - (b.getX() + 0.5);
                double dy = sy - (b.getY() + 0.5);
                double dz = sz - (b.getZ() + 0.5);
                double d2 = dx*dx + dy*dy + dz*dz;
                if (d2 <= SAME_TICK_RADIUS2 && d2 < bestD2) { bestD2 = d2; best = p; }
            }
        }
        return best;
    }

    /**
     * Called at the end of each server tick to age and prune sessions and their drop contexts.
     */
    public static void onServerTickEnd() {
        // advance global tick counter
        CURRENT_TICK++;
        if (SESSIONS.isEmpty()) return;
        Iterator<Map.Entry<Integer, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Session> e = it.next();
            Session s = e.getValue();
            Integer id = e.getKey();
            if (s.playerRef.get() == null) {
                it.remove();
                DROP_GUARDS.remove(id);
                BREAK_OWNERS.values().removeIf(ownerId -> ownerId.equals(id));
                continue;
            }
            s.ticksToLive--;
            if (s.ticksToLive <= 0) {
                it.remove();
                DROP_GUARDS.remove(id);
                BREAK_OWNERS.values().removeIf(ownerId -> ownerId.equals(id));
            }
        }
    }
}
