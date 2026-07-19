package com.lukarbonite.autopickup;

import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
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
    private static final double LINK_RADIUS2 = 36.0;            // 6-block link radius for loose (leaf-decay) linking
    private static final int SESSION_TTL_TICKS = 8;             // Keep session alive for short chains across ticks (~0.4s)

    // Global tick marker advanced once per server tick (end of tick).
    private static int CURRENT_TICK = 0;

    // --- Causal "on player break stack" signal -------------------------------------------------
    // Set while control flow is *inside* a player's own ServerPlayerGameMode.destroyBlock call, and
    // therefore any non-player break / direct ItemEntity spawn that happens synchronously during it
    // (LiteMiner-style vein mining, bamboo/sugarcane cascades, blocks popped by the player's break).
    // This is the discriminator proximity/timing cannot provide: an automated farm (piston, water,
    // bonemeal, dispenser, observer) breaks/drops during independent world ticking — OFF this stack —
    // so gating the broad proximity finders on it excludes farms that operate near a mining player
    // while still crediting genuine chains, which run on-stack. A depth counter keeps it balanced
    // across nesting and multiple return sites.
    private static final ThreadLocal<Player> ACTION_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<int[]> ACTION_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    public static void enterPlayerBreak(Player player) {
        int[] d = ACTION_DEPTH.get();
        if (d[0] == 0 && player != null) ACTION_PLAYER.set(player);
        d[0]++;
    }

    public static void exitPlayerBreak() {
        int[] d = ACTION_DEPTH.get();
        if (d[0] > 0 && --d[0] == 0) ACTION_PLAYER.remove();
    }

    public static Player currentActionPlayer() { return ACTION_PLAYER.get(); }

    /** A recorded break: its position plus the block type that was there (null if unknown). */
    private record Anchor(BlockPos pos, Block block) {}

    private static final class Session {
        final WeakReference<Player> playerRef;
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

        Session(Player p) {
            this.playerRef = new WeakReference<>(p);
        }

        void touch() { this.ticksToLive = SESSION_TTL_TICKS; this.lastTouchedTick = CURRENT_TICK; }

        boolean brokeRecently() { return (CURRENT_TICK - lastBreakTick) <= SESSION_TTL_TICKS; }

        void addPos(BlockPos pos, Block block) {
            recent.addLast(new Anchor(pos.immutable(), block));
            while (recent.size() > MAX_RECENT_POSITIONS) {
                recent.removeFirst();
            }
        }

        double minDist2(Vec3 v) {
            if (recent.isEmpty()) return Double.MAX_VALUE;
            double sx = v.x, sy = v.y, sz = v.z;
            double best = Double.MAX_VALUE;
            for (Anchor a : recent) {
                double dx = sx - (a.pos.getX() + 0.5);
                double dy = sy - (a.pos.getY() + 0.5);
                double dz = sz - (a.pos.getZ() + 0.5);
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < best) best = d2;
            }
            return best;
        }

        /** True if this session owns a recorded break of exactly {@code block} at {@code target}. */
        boolean ownsTypeAt(BlockPos target, Block block) {
            if (block == null) return false;
            for (Anchor a : recent) {
                if (a.block == block && a.pos.equals(target)) return true;
            }
            return false;
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
        addBreak(player, pos, blockAt(player, pos));
    }

    /**
     * Record a real block break. The block type is captured so the strict chain-link finder can tell a
     * contiguous same-material vein from an unrelated farm. Callers that already hold the {@link Block}
     * (e.g. the generic {@code Level.destroyBlock} hook) pass it directly; the {@code (player, pos)}
     * overload reads it from the world (valid because it is called while the block is still present).
     */
    public static void addBreak(Player player, BlockPos pos, Block block) {
        if (player == null || pos == null) return;
        Session s = SESSIONS.computeIfAbsent(player.getId(), id -> new Session(player));
        s.touch();
        s.lastBreakTick = CURRENT_TICK;
        s.addPos(pos, block);
        BREAK_OWNERS.put(pos.asLong(), player.getId());
    }

    /**
     * Record a *linked* (non-player) break that was credited to a player by the strict chain-link gate.
     * Adds the owned anchor and exact-pos XP ownership so a genuine vein/stalk keeps chaining and its XP
     * is attributed.
     *
     * <p>The attribution window ({@link Session#brokeRecently()}) is refreshed ONLY when this is a
     * genuinely new block in the chain — one this player does not already own. A real vein/stalk
     * consumes new blocks as it grows, so a gradual, multi-tick chain keeps its window alive and
     * continues indefinitely. An automated farm that re-breaks the SAME regenerating position (a stone
     * farm beside a stone block you broke) is already owned, so it never refreshes the window: once you
     * stop mining, {@code brokeRecently()} lapses within SESSION_TTL_TICKS and the farm is cut off,
     * instead of the farm perpetually re-arming itself via its own breaks. A farm cycling a finite set
     * of positions gets at most one refresh per distinct position, then lapses.
     */
    private static void addLinkedBreakAnchor(Player player, BlockPos pos, Block block) {
        if (player == null || pos == null) return;
        Session s = SESSIONS.computeIfAbsent(player.getId(), id -> new Session(player));
        s.touch();
        Integer existingOwner = BREAK_OWNERS.get(pos.asLong());
        boolean newBlock = existingOwner == null || !existingOwner.equals(player.getId());
        if (newBlock) s.lastBreakTick = CURRENT_TICK;
        s.addPos(pos, block);
        BREAK_OWNERS.put(pos.asLong(), player.getId());
    }

    private static Block blockAt(Player player, BlockPos pos) {
        if (player == null || pos == null) return null;
        try {
            BlockState state = player.level().getBlockState(pos);
            return state.isAir() ? null : state.getBlock();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Register a position touched by a right-click "use" interaction (not a block break).
     * Feeds only the tightly-scoped use-context finders (findOwnerInUseContext /
     * findOwnerInDropContext) — never the broad break-proximity finders. This is what keeps
     * interacting with a lever/jukebox/etc. from making the player a magnet for nearby drops.
     */
    public static void addUsePos(Player player, BlockPos pos) {
        if (player == null || pos == null) return;
        Session s = SESSIONS.computeIfAbsent(player.getId(), id -> new Session(player));
        s.touch();
        s.addPos(pos, null);
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
        // On-stack: any unowned drop occurring synchronously during this player's break/use action is
        // theirs, regardless of distance. A fixed radius wrongly cut off long straight vein chains
        // (LiteMiner spawns XP at the block, so orbs past ~6 blocks were lost) and tall bamboo/cactus/
        // sugarcane columns. Off-stack spawns (automated farms ticking independently) have no action
        // player and so are still excluded — that is the farm discriminator, not the distance.
        Player action = ACTION_PLAYER.get();
        return (action != null && !action.isSpectator()) ? action : null;
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

    /**
     * Loose proximity link used only by the opt-in leaf-decay compat paths (FallingTree / TreeHarvester),
     * where the dropped block (leaves) is legitimately a *different* type from the player's break (logs),
     * so the strict same-type gate cannot apply. NOT used for the generic {@code Level.destroyBlock} hook.
     */
    public static Player findLinkedOwnerForBreak(BlockPos pos) {
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Player best = null;
        double bestD2 = Double.MAX_VALUE;
        for (Map.Entry<Integer, Session> e : SESSIONS.entrySet()) {
            Session s = e.getValue();
            Player p = s.playerRef.get();
            if (p == null || p.isSpectator()) continue;
            if (!s.brokeRecently()) continue;
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

    /**
     * Loose linked context for leaf-decay compat. Chains the position so further leaves can link, then
     * opens a scoped drop context. Kept for the FallingTree / TreeHarvester leaf mixins only.
     */
    public static void openLinkedDropContext(BlockPos pos) {
        Player owner = findLinkedOwnerForBreak(pos);
        if (owner != null) {
            addBreak(owner, pos);
            beginDropContext(owner, pos);
            OPEN_LINKED_CONTEXT.get().push(owner.getId());
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
     *       ({@code currentActionPlayer()} is set) — a mod breaking a vein via {@code destroyBlock}, a
     *       neighbour cascade, a RightClickHarvest. It is that player's doing at any distance.</li>
     *   <li><b>Off-stack vertical support-collapse:</b> a bamboo/sugar-cane/cactus segment breaking on a
     *       LATER tick because the same-type block DIRECTLY BELOW it — which the player broke, or a prior
     *       step of this same collapse — is now gone. We attribute upward and extend the chain so the
     *       whole column counts. A farm cannot satisfy this: its breaks are neither same-type-below-owned
     *       nor seeded by a player break, and moss/piston flushes don't remove a player-owned block
     *       directly beneath the dropped block.</li>
     * </ol>
     *
     * @param supportBelowGone whether the block directly below {@code pos} is currently air (support lost)
     */
    public static void openLinkedDropContextStrict(BlockPos pos, Block block, boolean supportBelowGone) {
        // (1) On the acting player's stack: any synchronous non-player break is theirs. Register it so it
        //     both propagates and can seed a vertical collapse chain (RightClickHarvest bottom break).
        Player owner = ACTION_PLAYER.get();
        if (owner != null && block != null && !owner.isSpectator()) {
            addLinkedBreakAnchor(owner, pos, block);
            beginDropContext(owner, pos);
            OPEN_LINKED_CONTEXT.get().push(owner.getId());
            return;
        }
        // (2) Off-stack: only credit a delayed break that is a same-type block collapsing onto a player-
        //     owned break directly below it. Extends the chain upward so a tall column counts in full.
        if (block != null && supportBelowGone) {
            BlockPos below = pos.below();
            Integer id = BREAK_OWNERS.get(below.asLong());
            if (id != null) {
                Session s = SESSIONS.get(id);
                Player p = s != null ? s.playerRef.get() : null;
                if (p != null && !p.isSpectator() && s.ownsTypeAt(below, block)) {
                    addLinkedBreakAnchor(p, pos, block);
                    beginDropContext(p, pos);
                    OPEN_LINKED_CONTEXT.get().push(p.getId());
                    return;
                }
            }
        }
        OPEN_LINKED_CONTEXT.get().push(-1);
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

    // --- Block-use (right-click) context for harvesting interactions ---

    private static final ThreadLocal<ArrayDeque<Integer>> OPEN_USE_CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ConcurrentHashMap<Integer, Integer> ACTIVE_USE_CONTEXT_DEPTH = new ConcurrentHashMap<>();

    public static void openUseContext(Player player, BlockPos pos) {
        if (player == null || pos == null) return;
        // A right-click harvest (RightClickHarvest, sweet berries, ...) runs synchronously inside the
        // use call, so mark it on-stack too: this is what lets a tall cactus/sugarcane/bamboo column
        // harvested from the bottom be attributed for its full height, not just the tight use radius.
        enterPlayerBreak(player);
        begin(player);
        addUsePos(player, pos);
        beginDropContext(player, pos);
        OPEN_USE_CONTEXT.get().push(player.getId());
        ACTIVE_USE_CONTEXT_DEPTH.merge(player.getId(), 1, Integer::sum);
    }

    public static void closeUseContext() {
        ArrayDeque<Integer> stack = OPEN_USE_CONTEXT.get();
        if (stack.isEmpty()) return;
        int id = stack.pop();
        exitPlayerBreak();
        if (id >= 0) {
            Session s = SESSIONS.get(id);
            Player p = s != null ? s.playerRef.get() : null;
            if (p != null) endDropContext(p);
            ACTIVE_USE_CONTEXT_DEPTH.compute(id, (k, v) -> (v == null || v <= 1) ? null : v - 1);
        }
    }

    private static final double USE_CONTEXT_RADIUS2 = 1.0;

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
            if (d2 <= USE_CONTEXT_RADIUS2 && d2 < bestD2) { bestD2 = d2; best = p; }
        }
        return best;
    }

    // --- FallingBlock entity tracking for FallingTree FALL_BLOCK mode ---

    private static final int FALLING_BLOCK_EXPIRY_TICKS = 250;
    private static final int PENDING_FALL_EXPIRY_TICKS = 10;

    private record FallingBlockEntry(WeakReference<Player> playerRef, int expiryTick) {}
    private static final ConcurrentHashMap<Integer, FallingBlockEntry> FALLING_BLOCK_OWNERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, FallingBlockEntry> PENDING_FALL_POSITIONS = new ConcurrentHashMap<>();

    public static void preRegisterFallPosition(BlockPos pos, Player player) {
        PENDING_FALL_POSITIONS.put(pos.immutable(), new FallingBlockEntry(
                new WeakReference<>(player), CURRENT_TICK + PENDING_FALL_EXPIRY_TICKS));
    }

    public static Player getAndRemovePendingFallOwner(BlockPos pos) {
        FallingBlockEntry entry = PENDING_FALL_POSITIONS.remove(pos.immutable());
        if (entry == null || CURRENT_TICK > entry.expiryTick()) return null;
        return entry.playerRef().get();
    }

    public static void trackFallingBlock(int entityId, Player player) {
        FALLING_BLOCK_OWNERS.put(entityId, new FallingBlockEntry(
                new WeakReference<>(player), CURRENT_TICK + FALLING_BLOCK_EXPIRY_TICKS));
    }

    public static Player peekFallingBlockOwner(int entityId) {
        FallingBlockEntry entry = FALLING_BLOCK_OWNERS.get(entityId);
        if (entry == null || CURRENT_TICK > entry.expiryTick()) return null;
        return entry.playerRef().get();
    }

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
        // On-stack only (see findOwner): never attribute an off-stack farm spawn by proximity.
        Player action = ACTION_PLAYER.get();
        if (action == null || action.isSpectator()) return null;
        Session s = SESSIONS.get(action.getId());
        if (s == null || s.recent.isEmpty() || s.lastTouchedTick != CURRENT_TICK || !s.brokeRecently()) return null;
        double sx = spawnPos.x, sy = spawnPos.y, sz = spawnPos.z;
        int checked = 0;
        for (var itPos = s.recent.descendingIterator(); itPos.hasNext() && checked < 6; checked++) {
            Anchor a = itPos.next();
            double dx = sx - (a.pos.getX() + 0.5);
            double dy = sy - (a.pos.getY() + 0.5);
            double dz = sz - (a.pos.getZ() + 0.5);
            double d2 = dx*dx + dy*dy + dz*dz;
            if (d2 <= SAME_TICK_RADIUS2) return action;
        }
        return null;
    }

    /**
     * Called at the end of each server tick to age and prune sessions and their drop contexts.
     */
    public static void onServerTickEnd() {
        // advance global tick counter
        CURRENT_TICK++;
        // Safety net: clear any on-stack action state that outlived its break call (unbalanced
        // HEAD/RETURN, exception mid-break) so it can never leak into the next tick's world ticking.
        ACTION_PLAYER.remove();
        ACTION_DEPTH.get()[0] = 0;
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
