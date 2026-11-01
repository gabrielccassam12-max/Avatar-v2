package com.avatar.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/** Saved with the player and kept after death. element -1 = not chosen yet. */
public record AData(int element, int xp, int energy, long secrets, boolean avatarOk, List<Integer> stats) {
    // stat slots
    public static final int ST_KILLS = 0;      // 0..3 kills per element
    public static final int ST_CASTS = 4, ST_HEAL = 5, ST_FLIGHT = 6, ST_LIGHTNING = 7, ST_METAL = 8, ST_QUAKE = 9, ST_COMBO = 10;
    public static final int STATS = 16;

    /** Kill counter slot of an element: Fire..Air use 0..3, the newer elements 12..15. */
    public static int killStat(Element e) {
        return e.ordinal() < 4 ? ST_KILLS + e.ordinal() : 12 + (e.ordinal() - 4);
    }

    public static final int[] RANK_XP = {0, 150, 500, 1200, 2600, 5000};

    public static final AData DEFAULT = new AData(-1, 0, 0, 0L, false, blank());

    public static final Codec<AData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("element").forGetter(AData::element),
            Codec.INT.fieldOf("xp").forGetter(AData::xp),
            Codec.INT.fieldOf("energy").forGetter(AData::energy),
            Codec.LONG.fieldOf("secrets").forGetter(AData::secrets),
            Codec.BOOL.fieldOf("avatarOk").forGetter(AData::avatarOk),
            Codec.INT.listOf().fieldOf("stats").forGetter(AData::stats)
    ).apply(i, AData::new));

    public static List<Integer> blank() {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < STATS; i++) l.add(0);
        return l;
    }

    public static int rankOf(int xp) {
        int r = 0;
        for (int i = 0; i < RANK_XP.length; i++) if (xp >= RANK_XP[i]) r = i;
        return r;
    }

    public int rank() {
        return rankOf(xp);
    }

    public Element el() {
        return Element.of(element);
    }

    public int maxEnergy(boolean avatarOn) {
        return 100 + 25 * rank() + (avatarOn ? 150 : 0);
    }

    public int stat(int i) {
        return i >= 0 && i < stats.size() ? stats.get(i) : 0;
    }

    public boolean has(Skill s) {
        return (secrets & (1L << s.bit())) != 0;
    }

    public AData withElement(int v) { return new AData(v, xp, energy, secrets, avatarOk, stats); }
    public AData withXp(int v) { return new AData(element, v, energy, secrets, avatarOk, stats); }
    public AData withEnergy(int v, int max) { return new AData(element, xp, Math.max(0, Math.min(max, v)), secrets, avatarOk, stats); }
    public AData withSecrets(long v) { return new AData(element, xp, energy, v, avatarOk, stats); }
    public AData withAvatarOk(boolean v) { return new AData(element, xp, energy, secrets, v, stats); }

    public AData addStat(int i, int n) {
        List<Integer> l = new ArrayList<>(stats);
        while (l.size() < STATS) l.add(0);
        l.set(i, l.get(i) + n);
        return new AData(element, xp, energy, secrets, avatarOk, l);
    }
}
