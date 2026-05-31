package com.lukarbonite.autopickup;

import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

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
    }

    /**
     * Begin a tight drop context around a specific broken block for the given player.
     */
    public static void beginDropContext(Player player, BlockPos pos) {
        if (player == null || pos == null) return;
        ArrayDeque<DropGuard> stack = DROP_GUARDS.computeIfAbsent(player.getId(), id -> new ArrayDeque<>());
        stack.push(new DropGuard(pos));
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
    private static final ThreadLocal<ArrayDeque<Integer>> OPEN_LINKED_CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);

    public static void openLinkedDropContext(BlockPos pos) {
        Player owner = findLinkedOwnerForBreak(pos);
        if (owner != null) {
            // Add the chained position so subsequent hops (e.g. bamboo/sugarcane segments
            // further up the stalk) can still find the session within LINK_RADIUS2.
            addBreak(owner, pos);
            beginDropContext(owner, pos);
            OPEN_LINKED_CONTEXT.get().push(owner.getId());
        } else {
            OPEN_LINKED_CONTEXT.get().push(-1);
        }
    }

    public static void closeLinkedDropContext() {
        ArrayDeque<Integer> stack = OPEN_LINKED_CONTEXT.get();
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

    // --- Block-use (right-click) context for harvesting interactions (e.g. sweet berries, RightClickHarvest) ---

    private static final ThreadLocal<ArrayDeque<Integer>> OPEN_USE_CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);

    // Player IDs with at least one open use context — used by ServerLevelMixin for wider attribution.
    private static final ConcurrentHashMap<Integer, Integer> ACTIVE_USE_CONTEXT_DEPTH = new ConcurrentHashMap<>();

    /**
     * Open a drop context for a player right-clicking a block.
     * Also registers a wider 6-block session fallback so multi-block harvests
     * (e.g. tall sugarcane via RightClickHarvest) are attributed correctly.
     */
    public static void openUseContext(Player player, BlockPos pos) {
        if (player == null || pos == null) return;
        begin(player);
        addBreak(player, pos);
        beginDropContext(player, pos);
        OPEN_USE_CONTEXT.get().push(player.getId());
        ACTIVE_USE_CONTEXT_DEPTH.merge(player.getId(), 1, Integer::sum);
    }

    /**
     * Close the drop context opened by {@link #openUseContext}.
     */
    public static void closeUseContext() {
        ArrayDeque<Integer> stack = OPEN_USE_CONTEXT.get();
        if (stack.isEmpty()) return;
        int id = stack.pop();
        if (id >= 0) {
            Session s = SESSIONS.get(id);
            Player p = s != null ? s.playerRef.get() : null;
            if (p != null) {
                endDropContext(p);
            }
            ACTIVE_USE_CONTEXT_DEPTH.compute(id, (k, v) -> (v == null || v <= 1) ? null : v - 1);
        }
    }

    private static final double USE_CONTEXT_RADIUS2 = 1.0; // 1-block radius squared

    /**
     * Attribution fallback during active right-click use interactions.
     * Uses a tight 1-block radius against pre-registered column positions
     * (e.g. every block in a sugarcane column) so attribution is precise.
     */
    public static Player findOwnerInUseContext(Vec3 spawnPos) {
        if (ACTIVE_USE_CONTEXT_DEPTH.isEmpty()) return null;
        Player best = null;
        double bestD2 = Double.MAX_VALUE;
        for (Integer id : ACTIVE_USE_CONTEXT_DEPTH.keySet()) {
            Session s = SESSIONS.get(id);
            if (s == null) continue;
            Player p = s.playerRef.get();
            if (p == null || p.isSpectator()) continue;
            double d2 = s.minDist2(spawnPos);
            if (d2 <= USE_CONTEXT_RADIUS2 && d2 < bestD2) {
                bestD2 = d2;
                best = p;
            }
        }
        return best;
    }

    // --- FallingBlock entity tracking for FallingTree FALL_BLOCK mode ---

    // Tick-based expiry: immune to TPS fluctuations (a lagging server won't prune early).
    // MC gravity: v = (v - 0.04) * 0.98 per tick, terminal velocity = 2 blocks/tick.
    // Simulating from rest, falling 384 blocks (max world height) takes ~235 ticks.
    // 250 ticks covers the full world height with a small buffer.
    private static final int FALLING_BLOCK_EXPIRY_TICKS = 250;
    // Pending positions are consumed on entity spawn (same tick or next); 10 ticks is generous.
    private static final int PENDING_FALL_EXPIRY_TICKS = 10;

    private record FallingBlockEntry(WeakReference<Player> playerRef, int expiryTick) {}
    private static final ConcurrentHashMap<Integer, FallingBlockEntry> FALLING_BLOCK_OWNERS = new ConcurrentHashMap<>();

    // Pre-registered block positions → player for delayed FallingBlockEntity spawns.
    // FallingTree's breakTree is synchronous, but Fabric may queue addFreshEntity past the
    // end of breakTree (and past blockBreaker being cleared). This map bridges that gap.
    private static final ConcurrentHashMap<BlockPos, FallingBlockEntry> PENDING_FALL_POSITIONS = new ConcurrentHashMap<>();

    /**
     * Pre-register a block position as belonging to the given player before its FallingBlockEntity
     * is spawned. The entry is consumed on spawn and expires after a short TTL.
     */
    public static void preRegisterFallPosition(BlockPos pos, Player player) {
        PENDING_FALL_POSITIONS.put(pos.immutable(), new FallingBlockEntry(
                new WeakReference<>(player),
                CURRENT_TICK + PENDING_FALL_EXPIRY_TICKS));
    }

    /**
     * Consume the pre-registered player for a block position when its FallingBlockEntity spawns.
     * Returns null if no entry exists or it has expired.
     */
    public static Player getAndRemovePendingFallOwner(BlockPos pos) {
        FallingBlockEntry entry = PENDING_FALL_POSITIONS.remove(pos.immutable());
        if (entry == null || CURRENT_TICK > entry.expiryTick()) return null;
        return entry.playerRef().get();
    }

    /**
     * Register a FallingBlockEntity as owned by the given player (called when the entity is added to the world).
     */
    public static void trackFallingBlock(int entityId, Player player) {
        FALLING_BLOCK_OWNERS.put(entityId, new FallingBlockEntry(
                new WeakReference<>(player),
                CURRENT_TICK + FALLING_BLOCK_EXPIRY_TICKS));
    }

    /**
     * Look up the owning player without removing the entry.
     * Returns null if not tracked or expired.
     */
    public static Player peekFallingBlockOwner(int entityId) {
        FallingBlockEntry entry = FALLING_BLOCK_OWNERS.get(entityId);
        if (entry == null || CURRENT_TICK > entry.expiryTick()) return null;
        return entry.playerRef().get();
    }

    /**
     * Remove and return the owning player for a FallingBlockEntity.
     * Returns null if not tracked or expired.
     */
    public static Player getAndRemoveFallingBlockOwner(int entityId) {
        FallingBlockEntry entry = FALLING_BLOCK_OWNERS.remove(entityId);
        if (entry == null || CURRENT_TICK > entry.expiryTick()) return null;
        return entry.playerRef().get();
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
        // Clean up expired falling-block entries
        if (!FALLING_BLOCK_OWNERS.isEmpty() || !PENDING_FALL_POSITIONS.isEmpty()) {
            FALLING_BLOCK_OWNERS.entrySet().removeIf(e ->
                    e.getValue().playerRef().get() == null || CURRENT_TICK > e.getValue().expiryTick());
            PENDING_FALL_POSITIONS.entrySet().removeIf(e ->
                    e.getValue().playerRef().get() == null || CURRENT_TICK > e.getValue().expiryTick());
        }
        if (SESSIONS.isEmpty()) return;
        Iterator<Map.Entry<Integer, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Session> e = it.next();
            Session s = e.getValue();
            Integer id = e.getKey();
            if (s.playerRef.get() == null) {
                it.remove();
                DROP_GUARDS.remove(id);
                continue;
            }
            s.ticksToLive--;
            if (s.ticksToLive <= 0) {
                it.remove();
                DROP_GUARDS.remove(id);
            }
        }
    }
}
