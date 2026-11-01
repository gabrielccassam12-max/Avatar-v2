package com.avatar.net;

import com.avatar.Avatar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: shake the camera (strength 1..10) for some ticks. */
public record ShakePayload(int strength, int ticks) implements CustomPayload {
    public static final CustomPayload.Id<ShakePayload> ID = new CustomPayload.Id<>(Avatar.id("shake"));
    public static final PacketCodec<RegistryByteBuf, ShakePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ShakePayload::strength,
            PacketCodecs.VAR_INT, ShakePayload::ticks,
            ShakePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
