package com.avatar.net;

import com.avatar.Avatar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Server -> client: open the "choose your element" screen. */
public record OpenChoicePayload() implements CustomPayload {
    public static final CustomPayload.Id<OpenChoicePayload> ID = new CustomPayload.Id<>(Avatar.id("open_choice"));
    public static final PacketCodec<RegistryByteBuf, OpenChoicePayload> CODEC = PacketCodec.unit(new OpenChoicePayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
