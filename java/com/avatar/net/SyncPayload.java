package com.avatar.net;

import com.avatar.Avatar;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/** Server -> owner: everything the HUD and the menu show. cd = remaining cooldown ticks per Skill ordinal. */
public record SyncPayload(int element, int xp, int energy, int max, long secrets, boolean avatarOk, boolean avatarOn,
                          int active, int selected, int[] cd, List<Integer> stats) implements CustomPayload {
    public static final CustomPayload.Id<SyncPayload> ID = new CustomPayload.Id<>(Avatar.id("sync"));
    public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.of(SyncPayload::write, SyncPayload::read);

    private static void write(SyncPayload p, RegistryByteBuf b) {
        b.writeVarInt(p.element() + 1);
        b.writeVarInt(p.xp());
        b.writeVarInt(p.energy());
        b.writeVarInt(p.max());
        b.writeLong(p.secrets());
        b.writeBoolean(p.avatarOk());
        b.writeBoolean(p.avatarOn());
        b.writeVarInt(p.active() + 1);
        b.writeVarInt(p.selected());
        b.writeVarInt(p.cd().length);
        for (int v : p.cd()) b.writeVarInt(v);
        b.writeVarInt(p.stats().size());
        for (int v : p.stats()) b.writeVarInt(v);
    }

    private static SyncPayload read(RegistryByteBuf b) {
        int el = b.readVarInt() - 1;
        int xp = b.readVarInt();
        int en = b.readVarInt();
        int max = b.readVarInt();
        long sec = b.readLong();
        boolean ok = b.readBoolean();
        boolean on = b.readBoolean();
        int act = b.readVarInt() - 1;
        int sel = b.readVarInt();
        int n = b.readVarInt();
        int[] cd = new int[n];
        for (int i = 0; i < n; i++) cd[i] = b.readVarInt();
        int m = b.readVarInt();
        List<Integer> st = new ArrayList<>();
        for (int i = 0; i < m; i++) st.add(b.readVarInt());
        return new SyncPayload(el, xp, en, max, sec, ok, on, act, sel, cd, st);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
