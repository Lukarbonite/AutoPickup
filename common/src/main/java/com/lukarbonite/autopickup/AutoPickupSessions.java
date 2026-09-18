package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.core.SessionTracker;
import com.lukarbonite.autopickup.platform.VersionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Mojang-mapped front for {@link SessionTracker}, which holds the session, drop-context and
 * break-ownership logic (see its docs). This class only converts BlockPos/Vec3 to coordinates,
 * so every mixin keeps calling the same static methods.
 */
public final class AutoPickupSessions {

    private AutoPickupSessions() {}

    private static final SessionTracker<Player> TRACKER = new SessionTracker<>(
            Player::getId, Player::isSpectator, AutoPickupSessions::blockAt);

    private static Object blockAt(Player player, int x, int y, int z) {
        BlockState state = VersionHelper.INSTANCE.getLevel(player).getBlockState(new BlockPos(x, y, z));
        return state.isAir() ? null : state.getBlock();
    }

    public static void enterPlayerBreak(Player player) { TRACKER.enterPlayerBreak(player); }
    public static void exitPlayerBreak() { TRACKER.exitPlayerBreak(); }
    public static Player currentActionPlayer() { return TRACKER.currentActionPlayer(); }

    public static void begin(Player player) { TRACKER.begin(player); }

    public static void addBreak(Player player, BlockPos pos) {
        if (pos != null) TRACKER.addBreak(player, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void addBreak(Player player, BlockPos pos, Block block) {
        if (pos != null) TRACKER.addBreak(player, pos.getX(), pos.getY(), pos.getZ(), block);
    }

    public static void addUsePos(Player player, BlockPos pos) {
        if (pos != null) TRACKER.addUsePos(player, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void beginDropContext(Player player, BlockPos pos) {
        if (pos != null) TRACKER.beginDropContext(player, pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean hasDropContext(Player player) { return TRACKER.hasDropContext(player); }
    public static void endDropContext(Player player) { TRACKER.endDropContext(player); }

    public static Player findOwner(Vec3 pos) { return TRACKER.findOwner(pos.x, pos.y, pos.z); }
    public static Player findOwnerInDropContext(Vec3 pos) { return TRACKER.findOwnerInDropContext(pos.x, pos.y, pos.z); }
    public static Player findOwnerByBreakPos(Vec3 pos) { return TRACKER.findOwnerByBreakPos(pos.x, pos.y, pos.z); }
    public static Player findOwnerInUseContext(Vec3 pos) { return TRACKER.findOwnerInUseContext(pos.x, pos.y, pos.z); }
    public static Player findOwnerSameTickTight(Vec3 pos) { return TRACKER.findOwnerSameTickTight(pos.x, pos.y, pos.z); }

    public static Player findLinkedOwnerForBreak(BlockPos pos) {
        return TRACKER.findLinkedOwnerForBreak(pos.getX(), pos.getY(), pos.getZ());
    }

    public static void openLinkedDropContext(BlockPos pos) {
        TRACKER.openLinkedDropContext(pos.getX(), pos.getY(), pos.getZ());
    }

    public static void openLinkedDropContextStrict(BlockPos pos, Block block, boolean supportBelowGone) {
        TRACKER.openLinkedDropContextStrict(pos.getX(), pos.getY(), pos.getZ(), block, supportBelowGone);
    }

    public static void closeLinkedDropContext() { TRACKER.closeLinkedDropContext(); }

    public static void openUseContext(Player player, BlockPos pos) {
        if (pos != null) TRACKER.openUseContext(player, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void closeUseContext() { TRACKER.closeUseContext(); }

    public static void preRegisterFallPosition(BlockPos pos, Player player) {
        TRACKER.preRegisterFallPosition(pos.getX(), pos.getY(), pos.getZ(), player);
    }

    public static Player getAndRemovePendingFallOwner(BlockPos pos) {
        return TRACKER.getAndRemovePendingFallOwner(pos.getX(), pos.getY(), pos.getZ());
    }

    public static void trackFallingBlock(int entityId, Player player) { TRACKER.trackFallingBlock(entityId, player); }
    public static Player peekFallingBlockOwner(int entityId) { return TRACKER.peekFallingBlockOwner(entityId); }
    public static Player getAndRemoveFallingBlockOwner(int entityId) { return TRACKER.getAndRemoveFallingBlockOwner(entityId); }

    /** Called at the end of each server tick to age and prune sessions and their drop contexts. */
    public static void onServerTickEnd() { TRACKER.onServerTickEnd(); }
}
