package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.core.SessionTracker;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * MCP-mapped front for {@link SessionTracker}, which holds the session, drop-context and
 * break-ownership logic (see its docs). Mirrors common/'s Mojang-mapped {@code AutoPickupSessions}.
 */
public final class AutoPickupSessions {

    private AutoPickupSessions() {}

    private static final SessionTracker<EntityPlayer> TRACKER = new SessionTracker<>(
            EntityPlayer::getEntityId, EntityPlayer::isSpectator, AutoPickupSessions::blockAt);

    private static Object blockAt(EntityPlayer player, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = player.world.getBlockState(pos);
        return state.getBlock().isAir(state, player.world, pos) ? null : state.getBlock();
    }

    public static void enterPlayerBreak(EntityPlayer player) { TRACKER.enterPlayerBreak(player); }
    public static void exitPlayerBreak() { TRACKER.exitPlayerBreak(); }
    public static EntityPlayer currentActionPlayer() { return TRACKER.currentActionPlayer(); }

    public static void begin(EntityPlayer player) { TRACKER.begin(player); }

    public static void addBreak(EntityPlayer player, BlockPos pos) {
        if (pos != null) TRACKER.addBreak(player, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void addBreak(EntityPlayer player, BlockPos pos, Block block) {
        if (pos != null) TRACKER.addBreak(player, pos.getX(), pos.getY(), pos.getZ(), block);
    }

    public static void addUsePos(EntityPlayer player, BlockPos pos) {
        if (pos != null) TRACKER.addUsePos(player, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void beginDropContext(EntityPlayer player, BlockPos pos) {
        if (pos != null) TRACKER.beginDropContext(player, pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean hasDropContext(EntityPlayer player) { return TRACKER.hasDropContext(player); }
    public static void endDropContext(EntityPlayer player) { TRACKER.endDropContext(player); }

    public static EntityPlayer findOwner(Vec3d pos) { return TRACKER.findOwner(pos.x, pos.y, pos.z); }
    public static EntityPlayer findOwnerInDropContext(Vec3d pos) { return TRACKER.findOwnerInDropContext(pos.x, pos.y, pos.z); }
    public static EntityPlayer findOwnerByBreakPos(Vec3d pos) { return TRACKER.findOwnerByBreakPos(pos.x, pos.y, pos.z); }
    public static EntityPlayer findOwnerInUseContext(Vec3d pos) { return TRACKER.findOwnerInUseContext(pos.x, pos.y, pos.z); }
    public static EntityPlayer findOwnerSameTickTight(Vec3d pos) { return TRACKER.findOwnerSameTickTight(pos.x, pos.y, pos.z); }

    public static void openLinkedDropContextStrict(BlockPos pos, Block block, boolean supportBelowGone) {
        TRACKER.openLinkedDropContextStrict(pos.getX(), pos.getY(), pos.getZ(), block, supportBelowGone);
    }

    public static void closeLinkedDropContext() { TRACKER.closeLinkedDropContext(); }

    public static void openUseContext(EntityPlayer player, BlockPos pos) {
        if (pos != null) TRACKER.openUseContext(player, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void closeUseContext() { TRACKER.closeUseContext(); }

    /** Called at the end of each server tick to age and prune sessions and their drop contexts. */
    public static void onServerTickEnd() { TRACKER.onServerTickEnd(); }
}
