package com.lukarbonite.autopickup.util;

import net.minecraft.util.math.BlockPos;
import java.util.UUID;

public class OwnedBlockPos extends BlockPos {
    private final UUID ownerUUID;

    public OwnedBlockPos(BlockPos pos, UUID ownerUUID) {
        super(pos.getX(), pos.getY(), pos.getZ());
        this.ownerUUID = ownerUUID;
    }

    public UUID getOwnerUUID() { return ownerUUID; }
}