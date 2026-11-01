package com.avatar.net;

import com.avatar.Avatar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> owner and everyone nearby: Avatar State on/off and the element in use (for glowing eyes). */
public record StatePayload(int entityId, boolean avatar, int element) implements CustomPayload {
    public static final CustomPayload.Id<StatePayload> ID = new CustomPayload.Id<>(Avatar.id("state"));
    public static final PacketCodec<RegistryByteBuf, StatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, StatePayload::entityId,
            PacketCodecs.BOOL, StatePayload::avatar,
            PacketCodecs.VAR_INT, StatePayload::element,
            StatePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
