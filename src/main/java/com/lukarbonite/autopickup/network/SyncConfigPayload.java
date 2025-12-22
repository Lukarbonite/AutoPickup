package com.lukarbonite.autopickup.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record SyncConfigPayload(boolean master, boolean blocks, boolean mobLoot, boolean xp) {
    public static final Identifier ID = new Identifier("auto-pickup", "sync_config");

    public SyncConfigPayload(PacketByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(master);
        buf.writeBoolean(blocks);
        buf.writeBoolean(mobLoot);
        buf.writeBoolean(xp);
    }
}