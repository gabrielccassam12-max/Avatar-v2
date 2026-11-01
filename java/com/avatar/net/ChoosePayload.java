package com.avatar.net;

import com.avatar.Avatar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client -> server: the element picked on the choice screen. */
public record ChoosePayload(int element) implements CustomPayload {
    public static final CustomPayload.Id<ChoosePayload> ID = new CustomPayload.Id<>(Avatar.id("choose"));
    public static final PacketCodec<RegistryByteBuf, ChoosePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ChoosePayload::element,
            ChoosePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
