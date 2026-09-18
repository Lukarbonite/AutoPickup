package com.lukarbonite.autopickup.core;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Tracks short-lived per-player mining sessions and recent block break positions.
 * Also maintains a tight per-player block-drop context used to intercept only
 * the immediate item spawns caused by a player's block break, avoiding siphoning
 * from unrelated sources (farms, player drops, animal eggs, etc.).
 *
 * <p>Generic over the version's player type, with positions as plain coordinates and block types as
 * opaque identity-compared objects, so it has no Minecraft dependency. Each version's
 * {@code AutoPickupSessions} holds one instance and converts its own BlockPos/Vec3 types.
 */
public final class SessionTracker<P> {

    /** Reads the block type at a position in the player's world, or null for air/unknown. */
    public interface BlockLookup<P> {
        Object blockAt(P player, int x, int y, int z);
    }

    private final ToIntFunction<P> idOf;
    private final Predicate<P> isSpectator;
    private final BlockLookup<P> blockLookup;

    public SessionTracker(ToIntFunction<P> idOf, Predicate<P> isSpectator, BlockLookup<P> blockLookup) {
        this.idOf = idOf;
        this.isSpectator = isSpectator;
        this.blockLookup = blockLookup;
    }

    private final ConcurrentHashMap<Integer, Session> SESSIONS = new ConcurrentHashMap<>();

    // Per-player stack of active drop contexts (tight radius around a specific broken block)
    private final ConcurrentHashMap<Integer, ArrayDeque<DropGuard>> DROP_GUARDS = new ConcurrentHashMap<>();

    // Exact block-position → player-id ownership for deferred XP attribution (VeinMiner etc.)
    private final ConcurrentHashMap<Long, Integer> BREAK_OWNERS = new ConcurrentHashMap<>();

    // Tunables (can be moved to config later if needed)
    private static final int MAX_RECENT_POSITIONS = 64;         // Positions to remember per session
    private static final double DROP_CONTEXT_RADIUS2 = 9.0;     // 3-block radius squared (tight drop context)
    private static final double LINK_RADIUS2 = 36.0;            // 6-block link radius for loose (leaf-decay) linking
    private static final int SESSION_TTL_TICKS = 8;             // Keep session alive for short chains across ticks (~0.4s)

    // Global tick marker advanced once per server tick (end of tick).
    private int CURRENT_TICK = 0;

    /**
     * Packs block coordinates into one key. Only used as a map key here, so it does not need to match
     * any Minecraft version's {@code BlockPos.asLong} layout. 26 bits for x/z, 12 for y (-2048 to 2047).
     */
    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    // --- Causal "on player break stack" signal -------------------------------------------------
    // Set while control flow is *inside* a player's own ServerPlayerGameMode.destroyBlock call, and
    // therefore any non-player break / direct ItemEntity spawn that happens synchronously during it
    // (LiteMiner-style vein mining, bamboo/sugarcane cascades, blocks popped by the player's break).
    // This is the discriminator proximity/timing cannot provide: an automated farm (piston, water,
    // bonemeal, dispenser, observer) breaks/drops during independent world ticking, OFF this stack,
    // so gating the broad proximity finders on it excludes farms that operate near a mining player
    // while still crediting genuine chains, which run on-stack. A depth counter keeps it balanced
    // across nesting and multiple return sites.
    private final ThreadLocal<P> ACTION_PLAYER = new ThreadLocal<>();
    private final ThreadLocal<int[]> ACTION_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    public void enterPlayerBreak(P player) {
        int[] d = ACTION_DEPTH.get();
        if (d[0] == 0 && player != null) ACTION_PLAYER.set(player);
        d[0]++;
    }

    public void exitPlayerBreak() {
        int[] d = ACTION_DEPTH.get();
        if (d[0] > 0 && --d[0] == 0) ACTION_PLAYER.remove();
    }

    public P currentActionPlayer() { return ACTION_PLAYER.get(); }

    private boolean usable(P p) { return p != null && !isSpectator.test(p); }

    /** A recorded break: its position plus the block type that was there (null if unknown). */
    private static final class Anchor {
        final int x, y, z;
        final Object block;

        Anchor(int x, int y, int z, Object block) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
        }
    }

    private final class Session {
        final WeakReference<P> playerRef;
        final ArrayDeque<Anchor> recent = new ArrayDeque<>();
        int ticksToLive = SESSION_TTL_TICKS;
        int lastTouchedTick = 0; // tick when this session was last updated
        // Tick of the most recent *real block break* by this player. Right-click "use" contexts
        // (levers, jukeboxes, sweet berries, item frames, sugar-cane harvest, ...) deliberately do
        // NOT update this. The broad proximity finders (findOwner / findOwnerSameTickTight) only
        // attribute drops to a session that has actually broken a block recently, so a mere
        // interaction can't turn the player into a 6-block magnet for unrelated drops (a redstone
        // dropper dispensing, a piston breaking carpet, etc.).
        int lastBreakTick = -1000;

        Session(P p) {
            this.playerRef = new WeakReference<>(p);
        }

        void touch() { this.ticksToLive = SESSION_TTL_TICKS; this.lastTouchedTick = CURRENT_TICK; }

        boolean brokeRecently() { return (CURRENT_TICK - lastBreakTick) <= SESSION_TTL_TICKS; }

        void addPos(int x, int y, int z, Object block) {
            recent.addLast(new Anchor(x, y, z, block));
            while (recent.size() > MAX_RECENT_POSITIONS) {
                recent.removeFirst();
            }
        }

        double minDist2(double sx, double sy, double sz) {
            if (recent.isEmpty()) return Double.MAX_VALUE;
            double best = Double.MAX_VALUE;
            for (Anchor a : recent) {
                double dx = sx - (a.x + 0.5);
                double dy = sy - (a.y + 0.5);
                double dz = sz - (a.z + 0.5);
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < best) best = d2;
            }
            return best;
        }

        /** True if this session owns a recorded break of exactly {@code block} at the target. */
        boolean ownsTypeAt(int x, int y, int z, Object block) {
            if (block == null) return false;
            for (Anchor a : recent) {
                if (a.block == block && a.x == x && a.y == y && a.z == z) return true;
            }
            return false;
        }
    }

    private static final class DropGuard {
        final int x, y, z;
        DropGuard(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }

    private Session session(P player) {
        return SESSIONS.computeIfAbsent(idOf.applyAsInt(player), id -> new Session(player));
    }

    public void begin(P player) {
        if (player == null) return;
        session(player).touch();
    }

    public void addBreak(P player, int x, int y, int z) {
        if (player == null) return;
        addBreak(player, x, y, z, blockAt(player, x, y, z));
    }

    /**
     * Record a real block break. The block type is captured so the strict chain-link finder can tell a
     * contiguous same-material vein from an unrelated farm. Callers that already hold the block
     * (e.g. the generic {@code Level.destroyBlock} hook) pass it directly; the {@code (player, pos)}
     * overload reads it from the world (valid because it is called while the block is still present).
     */
    public void addBreak(P player, int x, int y, int z, Object block) {
        if (player == null) return;
        Session s = session(player);
        s.touch();
        s.lastBreakTick = CURRENT_TICK;
        s.addPos(x, y, z, block);
        BREAK_OWNERS.put(key(x, y, z), idOf.applyAsInt(player));
    }

    /**
     * Record a *linked* (non-player) break that was credited to a player by the strict chain-link gate.
     * Adds the owned anchor and exact-pos XP ownership so a genuine vein/stalk keeps chaining and its XP
     * is attributed.
     *
     * <p>The attribution window ({@link Session#brokeRecently()}) is refreshed ONLY when this is a
     * genuinely new block in the chain: one this player does not already own. A real vein/stalk
     * consumes new blocks as it grows, so a gradual, multi-tick chain keeps its window alive and
     * continues indefinitely. An automated farm that re-breaks the SAME regenerating position (a stone
     * farm beside a stone block you broke) is already owned, so it never refreshes the window: once you
     * stop mining, {@code brokeRecently()} lapses within SESSION_TTL_TICKS and the farm is cut off,
     * instead of the farm perpetually re-arming itself via its own breaks. A farm cycling a finite set
     * of positions gets at most one refresh per distinct position, then lapses.
     */
    private void addLinkedBreakAnchor(P player, int x, int y, int z, Object block) {
        if (player == null) return;
        int id = idOf.applyAsInt(player);
        Session s = session(player);
        s.touch();
        Integer existingOwner = BREAK_OWNERS.get(key(x, y, z));
        boolean newBlock = existingOwner == null || existingOwner != id;
        if (newBlock) s.lastBreakTick = CURRENT_TICK;
        s.addPos(x, y, z, block);
        BREAK_OWNERS.put(key(x, y, z), id);
    }

    private Object blockAt(P player, int x, int y, int z) {
        if (player == null) return null;
        try {
            return blockLookup.blockAt(player, x, y, z);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Register a position touched by a right-click "use" interaction (not a block break).
     * Feeds only the tightly-scoped use-context finders (findOwnerInUseContext /
     * findOwnerInDropContext), never the broad break-proximity finders. This is what keeps
     * interacting with a lever/jukebox/etc. from making the player a magnet for nearby drops.
     */
    public void addUsePos(P player, int x, int y, int z) {
        if (player == null) return;
        Session s = session(player);
        s.touch();
        s.addPos(x, y, z, null);
    }

    /**
     * Begin a tight drop context around a specific broken block for the given player.
     */
    public void beginDropContext(P player, int x, int y, int z) {
        if (player == null) return;
        ArrayDeque<DropGuard> stack = DROP_GUARDS.computeIfAbsent(idOf.applyAsInt(player), id -> new ArrayDeque<>());
        stack.push(new DropGuard(x, y, z));
    }

    public boolean hasDropContext(P player) {
        if (player == null) return false;
        ArrayDeque<DropGuard> stack = DROP_GUARDS.get(idOf.applyAsInt(player));
        return stack != null && !stack.isEmpty();
    }

    /**
     * End the most recent drop context for the given player.
     */
    public void endDropContext(P player) {
        if (player == null) return;
        ArrayDeque<DropGuard> stack = DROP_GUARDS.get(idOf.applyAsInt(player));
        if (stack != null && !stack.isEmpty()) stack.pop();
    }

    /**
     * Find the most plausible owner of an item/XP spawn near recent break positions.
     * Returns null if no session is close enough.
     */
    public P findOwner(double x, double y, double z) {
        // On-stack: any unowned drop occurring synchronously during this player's break/use action is
        // theirs, regardless of distance. A fixed radius wrongly cut off long straight vein chains
        // (LiteMiner spawns XP at the block, so orbs past ~6 blocks were lost) and tall bamboo/cactus/
        // sugarcane columns. Off-stack spawns (automated farms ticking independently) have no action
        // player and so are still excluded: that is the farm discriminator, not the distance.
        P action = ACTION_PLAYER.get();
        return usable(action) ? action : null;
    }

    /**
     * Find the player that currently has an active drop context closest to the given spawn position.
     * Only returns a player if the spawn is within a small radius of that context.
     */
    public P findOwnerInDropContext(double x, double y, double z) {
        P best = null;
        double bestD2 = Double.MAX_VALUE;
        for (Map.Entry<Integer, ArrayDeque<DropGuard>> e : DROP_GUARDS.entrySet()) {
            ArrayDeque<DropGuard> stack = e.getValue();
            if (stack == null || stack.isEmpty()) continue;
            Session s = SESSIONS.get(e.getKey());
            if (s == null) continue;
            P p = s.playerRef.get();
            if (!usable(p)) continue;
            DropGuard g = stack.peek();
            if (g == null) continue;
            double dx = x - (g.x + 0.5);
            double dy = y - (g.y + 0.5);
            double dz = z - (g.z + 0.5);
            double d2 = dx*dx + dy*dy + dz*dz;
            if (d2 <= DROP_CONTEXT_RADIUS2 && d2 < bestD2) {
                bestD2 = d2;
                best = p;
            }
        }
        return best;
    }

    /**
     * Exact-position lookup for deferred XP attribution. Converts the spawn position to the
     * containing block and looks up the registered break owner. Safer than radius-based
     * findOwner when multiple players may be mining nearby simultaneously.
     */
    public P findOwnerByBreakPos(double x, double y, double z) {
        Integer id = BREAK_OWNERS.get(key(floor(x), floor(y), floor(z)));
        if (id == null) return null;
        Session s = SESSIONS.get(id);
        if (s == null) return null;
        P p = s.playerRef.get();
        return usable(p) ? p : null;
    }

    // --- Linked-break helpers for veinminer/liteminer compatibility ---

    /**
     * Loose proximity link used only by the opt-in leaf-decay compat paths (FallingTree / TreeHarvester),
     * where the dropped block (leaves) is legitimately a *different* type from the player's break (logs),
     * so the strict same-type gate cannot apply. NOT used for the generic {@code Level.destroyBlock} hook.
     */
    public P findLinkedOwnerForBreak(int x, int y, int z) {
        double cx = x + 0.5, cy = y + 0.5, cz = z + 0.5;
        P best = null;
        double bestD2 = Double.MAX_VALUE;
        for (Map.Entry<Integer, Session> e : SESSIONS.entrySet()) {
            Session s = e.getValue();
            P p = s.playerRef.get();
            if (!usable(p)) continue;
            if (!s.brokeRecently()) continue;
            double d2 = s.minDist2(cx, cy, cz);
            if (d2 <= LINK_RADIUS2 && d2 < bestD2) {
                bestD2 = d2;
                best = p;
            }
        }
        return best;
    }

    // Maintain a small thread-local stack to balance HEAD/TAIL injections safely
    private final ThreadLocal<ArrayDeque<Integer>> OPEN_LINKED_CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Loose linked context for leaf-decay compat. Chains the position so further leaves can link, then
     * opens a scoped drop context. Kept for the FallingTree / TreeHarvester leaf mixins only.
     */
    public void openLinkedDropContext(int x, int y, int z) {
        P owner = findLinkedOwnerForBreak(x, y, z);
        if (owner != null) {
            addBreak(owner, x, y, z);
            beginDropContext(owner, x, y, z);
            OPEN_LINKED_CONTEXT.get().push(idOf.applyAsInt(owner));
        } else {
            OPEN_LINKED_CONTEXT.get().push(-1);
        }
    }

    /**
     * Linked context for the generic {@code Level.destroyBlock} hook (veinminer, plant-column collapse,
     * etc.). Credits the break to a player in two cases:
     *
     * <ol>
     *   <li><b>On-stack:</b> the break is happening synchronously inside a player's own break/use action
     *       ({@code currentActionPlayer()} is set): a mod breaking a vein via {@code destroyBlock}, a
     *       neighbour cascade, a RightClickHarvest. It is that player's doing at any distance.</li>
     *   <li><b>Off-stack vertical support-collapse:</b> a bamboo/sugar-cane/cactus segment breaking on a
     *       LATER tick because the same-type block DIRECTLY BELOW it (which the player broke, or a prior
     *       step of this same collapse) is now gone. We attribute upward and extend the chain so the
     *       whole column counts. A farm cannot satisfy this: its breaks are neither same-type-below-owned
     *       nor seeded by a player break, and moss/piston flushes don't remove a player-owned block
     *       directly beneath the dropped block.</li>
     * </ol>
     *
     * @param supportBelowGone whether the block directly below the position is currently air (support lost)
     */
    public void openLinkedDropContextStrict(int x, int y, int z, Object block, boolean supportBelowGone) {
        // (1) On the acting player's stack: any synchronous non-player break is theirs. Register it so it
        //     both propagates and can seed a vertical collapse chain (RightClickHarvest bottom break).
        P owner = ACTION_PLAYER.get();
        if (block != null && usable(owner)) {
            addLinkedBreakAnchor(owner, x, y, z, block);
            beginDropContext(owner, x, y, z);
            OPEN_LINKED_CONTEXT.get().push(idOf.applyAsInt(owner));
            return;
        }
        // (2) Off-stack: only credit a delayed break that is a same-type block collapsing onto a player-
        //     owned break directly below it. Extends the chain upward so a tall column counts in full.
        if (block != null && supportBelowGone) {
            Integer id = BREAK_OWNERS.get(key(x, y - 1, z));
            if (id != null) {
                Session s = SESSIONS.get(id);
                P p = s != null ? s.playerRef.get() : null;
                if (usable(p) && s.ownsTypeAt(x, y - 1, z, block)) {
                    addLinkedBreakAnchor(p, x, y, z, block);
                    beginDropContext(p, x, y, z);
                    OPEN_LINKED_CONTEXT.get().push(idOf.applyAsInt(p));
                    return;
                }
            }
        }
        OPEN_LINKED_CONTEXT.get().push(-1);
    }

    public void closeLinkedDropContext() {
        ArrayDeque<Integer> stack = OPEN_LINKED_CONTEXT.get();
        if (stack.isEmpty()) return;
        int id = stack.pop();
        if (id >= 0) {
            Session s = SESSIONS.get(id);
            P p = s != null ? s.playerRef.get() : null;
            if (p != null) {
                endDropContext(p);
            }
        }
    }

    // --- Block-use (right-click) context for harvesting interactions ---

    private final ThreadLocal<ArrayDeque<Integer>> OPEN_USE_CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);
    private final ConcurrentHashMap<Integer, Integer> ACTIVE_USE_CONTEXT_DEPTH = new ConcurrentHashMap<>();

    public void openUseContext(P player, int x, int y, int z) {
        if (player == null) return;
        // A right-click harvest (RightClickHarvest, sweet berries, ...) runs synchronously inside the
        // use call, so mark it on-stack too: this is what lets a tall cactus/sugarcane/bamboo column
        // harvested from the bottom be attributed for its full height, not just the tight use radius.
        enterPlayerBreak(player);
        begin(player);
        addUsePos(player, x, y, z);
        beginDropContext(player, x, y, z);
        int id = idOf.applyAsInt(player);
        OPEN_USE_CONTEXT.get().push(id);
        ACTIVE_USE_CONTEXT_DEPTH.merge(id, 1, Integer::sum);
    }

    public void closeUseContext() {
        ArrayDeque<Integer> stack = OPEN_USE_CONTEXT.get();
        if (stack.isEmpty()) return;
        int id = stack.pop();
        exitPlayerBreak();
        if (id >= 0) {
            Session s = SESSIONS.get(id);
            P p = s != null ? s.playerRef.get() : null;
            if (p != null) endDropContext(p);
            ACTIVE_USE_CONTEXT_DEPTH.compute(id, (k, v) -> (v == null || v <= 1) ? null : v - 1);
        }
    }

    private static final double USE_CONTEXT_RADIUS2 = 1.0;

    public P findOwnerInUseContext(double x, double y, double z) {
        if (ACTIVE_USE_CONTEXT_DEPTH.isEmpty()) return null;
        P best = null;
        double bestD2 = Double.MAX_VALUE;
        for (Integer id : ACTIVE_USE_CONTEXT_DEPTH.keySet()) {
            Session s = SESSIONS.get(id);
            if (s == null) continue;
            P p = s.playerRef.get();
            if (!usable(p)) continue;
            double d2 = s.minDist2(x, y, z);
            if (d2 <= USE_CONTEXT_RADIUS2 && d2 < bestD2) { bestD2 = d2; best = p; }
        }
        return best;
    }

    // --- FallingBlock entity tracking for FallingTree FALL_BLOCK mode ---

    private static final int FALLING_BLOCK_EXPIRY_TICKS = 250;
    private static final int PENDING_FALL_EXPIRY_TICKS = 10;

    private static final class FallingBlockEntry<Q> {
        final WeakReference<Q> playerRef;
        final int expiryTick;

        FallingBlockEntry(Q player, int expiryTick) {
            this.playerRef = new WeakReference<>(player);
            this.expiryTick = expiryTick;
        }
    }
    private final ConcurrentHashMap<Integer, FallingBlockEntry<P>> FALLING_BLOCK_OWNERS = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, FallingBlockEntry<P>> PENDING_FALL_POSITIONS = new ConcurrentHashMap<>();

    public void preRegisterFallPosition(int x, int y, int z, P player) {
        PENDING_FALL_POSITIONS.put(key(x, y, z), new FallingBlockEntry<>(player, CURRENT_TICK + PENDING_FALL_EXPIRY_TICKS));
    }

    public P getAndRemovePendingFallOwner(int x, int y, int z) {
        FallingBlockEntry<P> entry = PENDING_FALL_POSITIONS.remove(key(x, y, z));
        if (entry == null || CURRENT_TICK > entry.expiryTick) return null;
        return entry.playerRef.get();
    }

    public void trackFallingBlock(int entityId, P player) {
        FALLING_BLOCK_OWNERS.put(entityId, new FallingBlockEntry<>(player, CURRENT_TICK + FALLING_BLOCK_EXPIRY_TICKS));
    }

    public P peekFallingBlockOwner(int entityId) {
        FallingBlockEntry<P> entry = FALLING_BLOCK_OWNERS.get(entityId);
        if (entry == null || CURRENT_TICK > entry.expiryTick) return null;
        return entry.playerRef.get();
    }

    public P getAndRemoveFallingBlockOwner(int entityId) {
        FallingBlockEntry<P> entry = FALLING_BLOCK_OWNERS.remove(entityId);
        if (entry == null || CURRENT_TICK > entry.expiryTick) return null;
        return entry.playerRef.get();
    }

    // --- Same-tick tiny-radius fallback for direct ItemEntity spawns ---
    private static final double SAME_TICK_RADIUS2 = 2.25; // 1.5 blocks squared

    /**
     * If no drop context matched, attribute spawns that happen in the same tick and within a tiny radius
     * of a recent break position to the corresponding player.
     */
    public P findOwnerSameTickTight(double x, double y, double z) {
        // On-stack only (see findOwner): never attribute an off-stack farm spawn by proximity.
        P action = ACTION_PLAYER.get();
        if (!usable(action)) return null;
        Session s = SESSIONS.get(idOf.applyAsInt(action));
        if (s == null || s.recent.isEmpty() || s.lastTouchedTick != CURRENT_TICK || !s.brokeRecently()) return null;
        int checked = 0;
        for (Iterator<Anchor> itPos = s.recent.descendingIterator(); itPos.hasNext() && checked < 6; checked++) {
            Anchor a = itPos.next();
            double dx = x - (a.x + 0.5);
            double dy = y - (a.y + 0.5);
            double dz = z - (a.z + 0.5);
            double d2 = dx*dx + dy*dy + dz*dz;
            if (d2 <= SAME_TICK_RADIUS2) return action;
        }
        return null;
    }

    /**
     * Called at the end of each server tick to age and prune sessions and their drop contexts.
     */
    public void onServerTickEnd() {
        // advance global tick counter
        CURRENT_TICK++;
        // Safety net: clear any on-stack action state that outlived its break call (unbalanced
        // HEAD/RETURN, exception mid-break) so it can never leak into the next tick's world ticking.
        ACTION_PLAYER.remove();
        ACTION_DEPTH.get()[0] = 0;
        // Clean up expired falling-block entries
        if (!FALLING_BLOCK_OWNERS.isEmpty() || !PENDING_FALL_POSITIONS.isEmpty()) {
            FALLING_BLOCK_OWNERS.entrySet().removeIf(e ->
                    e.getValue().playerRef.get() == null || CURRENT_TICK > e.getValue().expiryTick);
            PENDING_FALL_POSITIONS.entrySet().removeIf(e ->
                    e.getValue().playerRef.get() == null || CURRENT_TICK > e.getValue().expiryTick);
        }
        if (SESSIONS.isEmpty()) return;
        Iterator<Map.Entry<Integer, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Session> e = it.next();
            Session s = e.getValue();
            Integer id = e.getKey();
            if (s.playerRef.get() == null || --s.ticksToLive <= 0) {
                it.remove();
                DROP_GUARDS.remove(id);
                BREAK_OWNERS.values().removeIf(ownerId -> ownerId.equals(id));
            }
        }
    }
}
