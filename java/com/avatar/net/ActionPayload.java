package com.avatar.net;

import com.avatar.Avatar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> server. kind: 0 basic technique (slot 0..6), 1 cast selected special, 2 toggle Avatar State,
 * 3 next special, 4 next element (Avatar State). phase: 0 key pressed, 1 key released (charging).
 */
public record ActionPayload(int kind, int slot, int phase) implements CustomPayload {
    public static final CustomPayload.Id<ActionPayload> ID = new CustomPayload.Id<>(Avatar.id("action"));
    public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ActionPayload::kind,
            PacketCodecs.VAR_INT, ActionPayload::slot,
            PacketCodecs.VAR_INT, ActionPayload::phase,
            ActionPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
