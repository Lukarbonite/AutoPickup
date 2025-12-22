package com.lukarbonite.autopickup.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SyncConfigPayload(boolean master, boolean blocks, boolean mobLoot, boolean xp) implements CustomPayload {
    public static final CustomPayload.Id<SyncConfigPayload> ID = new CustomPayload.Id<>(Identifier.of("auto-pickup", "sync_config"));

    // Manually define BOOL codec to avoid mapping naming issues (PacketCodecs.BOOL vs BOOLEAN etc)
    private static final PacketCodec<ByteBuf, Boolean> BOOL_CODEC = PacketCodec.ofStatic(
            ByteBuf::writeBoolean,
            ByteBuf::readBoolean
    );

    public static final PacketCodec<RegistryByteBuf, SyncConfigPayload> CODEC = PacketCodec.tuple(
            BOOL_CODEC, SyncConfigPayload::master,
            BOOL_CODEC, SyncConfigPayload::blocks,
            BOOL_CODEC, SyncConfigPayload::mobLoot,
            BOOL_CODEC, SyncConfigPayload::xp,
            SyncConfigPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}