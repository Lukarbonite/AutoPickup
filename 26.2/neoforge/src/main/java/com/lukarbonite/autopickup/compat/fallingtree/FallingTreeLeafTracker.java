package com.lukarbonite.autopickup.compat.fallingtree;

import fr.rakambda.fallingtree.common.tree.TreePart;
import fr.rakambda.fallingtree.common.tree.TreePartType;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FallingTreeLeafTracker {

    private record Entry(UUID playerUUID, long expiryMs) {}

    private static final ConcurrentHashMap<BlockPos, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final long EXPIRY_MS = 30_000L;

    public static void trackLeaves(Collection<TreePart> parts, UUID playerUUID) {
        long expiry = System.currentTimeMillis() + EXPIRY_MS;
        for (TreePart part : parts) {
            TreePartType type = part.treePartType();
            if (type == TreePartType.LEAF || type == TreePartType.LEAF_NEED_BREAK) {
                ENTRIES.put(((BlockPos) part.blockPos().getRaw()).immutable(), new Entry(playerUUID, expiry));
            }
        }
    }

    public static void trackPos(BlockPos pos, UUID playerUUID) {
        ENTRIES.put(pos.immutable(), new Entry(playerUUID, System.currentTimeMillis() + EXPIRY_MS));
    }

    public static UUID findOwner(BlockPos pos) {
        Entry entry = ENTRIES.remove(pos);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expiryMs()) return null;
        return entry.playerUUID();
    }
}
