package com.avatar.core;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Every technique. kind: 0 basic (7 per element, key slots), 1 secret sub-bending, 2 elemental combo (Avatar State), 3 supreme.
 * pose: 1 one arm forward, 2 both arms forward, 3 both raised, 4 arms wide, 5 kick, 6 hands pushing down.
 */
public enum Skill {
    // ---- FIRE
    FIRE_PUNCH(Element.FIRE, 0, 0, 0, 10, 24, 1, 8, false),
    FLAME_KICK(Element.FIRE, 0, 1, 0, 14, 40, 5, 10, false),
    FIREBALL(Element.FIRE, 0, 2, 1, 16, 30, 1, 9, true),
    FIRE_BLAST(Element.FIRE, 0, 3, 2, 30, 120, 4, 14, false),
    JET_PROPULSION(Element.FIRE, 0, 4, 2, 22, 100, 2, 24, false),
    DRAGON_BREATH(Element.FIRE, 0, 5, 3, 35, 200, 1, 40, false),
    LIGHTNING(Element.FIRE, 0, 6, 4, 40, 160, 1, 24, true),
    // ---- WATER
    WATER_WHIP(Element.WATER, 0, 0, 0, 10, 24, 1, 10, false),
    WATER_BLADES(Element.WATER, 0, 1, 1, 18, 60, 4, 12, false),
    WATER_SHIELD(Element.WATER, 0, 2, 0, 22, 150, 4, 14, false),
    WATER_PRISON(Element.WATER, 0, 3, 2, 35, 220, 1, 14, false),
    ICE_SPIKES(Element.WATER, 0, 4, 2, 25, 120, 6, 14, false),
    ICE_CAGE(Element.WATER, 0, 5, 3, 32, 200, 1, 14, false),
    HEALING(Element.WATER, 0, 6, 1, 28, 160, 2, 30, false),
    // ---- EARTH
    ROCK_PUNCH(Element.EARTH, 0, 0, 0, 12, 28, 1, 8, false),
    EARTH_WALL(Element.EARTH, 0, 1, 0, 20, 100, 6, 12, false),
    EARTH_SPIKES(Element.EARTH, 0, 2, 1, 24, 110, 6, 12, false),
    BOULDER_THROW(Element.EARTH, 0, 3, 2, 34, 150, 3, 14, true),
    EARTHQUAKE(Element.EARTH, 0, 4, 3, 45, 260, 6, 30, false),
    ROCK_ARMOR(Element.EARTH, 0, 5, 2, 30, 300, 4, 16, false),
    METALBENDING(Element.EARTH, 0, 6, 4, 26, 100, 1, 14, false),
    // ---- AIR
    AIR_PUNCH(Element.AIR, 0, 0, 0, 8, 18, 1, 8, false),
    AIR_BLAST(Element.AIR, 0, 1, 0, 18, 70, 2, 12, false),
    AIR_SCOOTER(Element.AIR, 0, 2, 1, 20, 120, 4, 10, false),
    TORNADO(Element.AIR, 0, 3, 3, 34, 220, 3, 24, false),
    AIR_SHIELD(Element.AIR, 0, 4, 1, 22, 150, 4, 14, false),
    AIR_DASH(Element.AIR, 0, 5, 0, 10, 40, 2, 8, false),
    FLIGHT(Element.AIR, 0, 6, 4, 5, 40, 0, 0, false),
    // ---- SECRET sub-bending
    LIGHTNING_STORM(Element.FIRE, 1, 0, 3, 70, 400, 3, 30, false),
    COMBUSTION(Element.FIRE, 1, 1, 4, 45, 260, 1, 20, false),
    BLOODBENDING(Element.WATER, 1, 0, 4, 40, 300, 2, 40, false),
    ADV_HEALING(Element.WATER, 1, 1, 3, 60, 500, 2, 40, false),
    METAL_SHRAPNEL(Element.EARTH, 1, 0, 4, 40, 200, 4, 14, false),
    LAVABENDING(Element.EARTH, 1, 1, 4, 55, 360, 6, 20, false),
    SEISMIC_SENSE(Element.EARTH, 1, 2, 3, 15, 200, 6, 20, false),
    SPIRIT_PROJECTION(Element.AIR, 1, 0, 4, 40, 600, 4, 20, false),
    PRESSURE(Element.AIR, 1, 1, 4, 45, 260, 2, 20, false),
    // ---- COMBOS (Avatar State)
    FLAMING_TORNADO(null, 2, 0, 5, 70, 500, 3, 30, false),
    STORM(null, 2, 1, 5, 70, 500, 3, 30, false),
    ERUPTION(null, 2, 2, 5, 80, 600, 6, 30, false),
    MUD_PRISON(null, 2, 3, 5, 60, 400, 1, 20, false),
    STEAM_BURST(null, 2, 4, 5, 55, 300, 4, 20, false),
    // ---- SUPREME
    ELEMENTAL_COLLAPSE(null, 3, 0, 5, 120, 1200, 3, 120, true),
    // ---- THUNDER (Raio)
    SPARK_SHOT(Element.THUNDER, 0, 0, 0, 8, 16, 1, 8, false),
    ARC_SLASH(Element.THUNDER, 0, 1, 0, 14, 40, 2, 10, false),
    STATIC_FIELD(Element.THUNDER, 0, 2, 1, 22, 140, 4, 14, false),
    BLINK_STRIKE(Element.THUNDER, 0, 3, 2, 26, 90, 1, 8, false),
    THUNDER_CLAP(Element.THUNDER, 0, 4, 2, 30, 120, 4, 12, false),
    CHAIN_STORM(Element.THUNDER, 0, 5, 3, 38, 200, 1, 16, false),
    SKY_JUDGMENT(Element.THUNDER, 0, 6, 4, 55, 300, 3, 40, false),
    // ---- ICE (Gelo)
    FROST_SHARD(Element.ICE, 0, 0, 0, 8, 18, 1, 8, false),
    FROST_BREATH(Element.ICE, 0, 1, 0, 14, 60, 2, 22, false),
    ICE_ARMOR(Element.ICE, 0, 2, 1, 24, 220, 4, 14, false),
    GLACIER_WALL(Element.ICE, 0, 3, 2, 24, 120, 6, 12, false),
    HAIL_STORM(Element.ICE, 0, 4, 2, 30, 140, 3, 30, false),
    FLASH_FREEZE(Element.ICE, 0, 5, 3, 40, 240, 4, 14, false),
    GLACIAL_COMET(Element.ICE, 0, 6, 4, 55, 300, 3, 30, false),
    // ---- SHADOW (Sombra)
    SHADOW_BOLT(Element.SHADOW, 0, 0, 0, 8, 16, 1, 8, false),
    TENDRILS(Element.SHADOW, 0, 1, 0, 16, 70, 6, 14, false),
    SHADOW_STEP(Element.SHADOW, 0, 2, 1, 20, 100, 1, 8, false),
    SHADOW_CLOAK(Element.SHADOW, 0, 3, 2, 26, 260, 4, 14, false),
    VOID_ORB(Element.SHADOW, 0, 4, 2, 32, 160, 1, 14, false),
    NIGHTMARE(Element.SHADOW, 0, 5, 3, 34, 200, 4, 14, false),
    ECLIPSE(Element.SHADOW, 0, 6, 4, 60, 320, 3, 40, false),
    // ---- SPIRIT (Espirito)
    SPIRIT_BOLT(Element.SPIRIT, 0, 0, 0, 8, 16, 1, 8, false),
    MENDING_LIGHT(Element.SPIRIT, 0, 1, 0, 20, 120, 2, 24, false),
    GUARDIAN_SPIRIT(Element.SPIRIT, 0, 2, 1, 28, 240, 4, 14, false),
    ETHEREAL_FORM(Element.SPIRIT, 0, 3, 2, 30, 240, 4, 14, false),
    SOUL_DRAIN(Element.SPIRIT, 0, 4, 2, 30, 140, 1, 24, false),
    WRAITH_WAVE(Element.SPIRIT, 0, 5, 3, 40, 200, 2, 16, false),
    ASCENSION(Element.SPIRIT, 0, 6, 4, 60, 340, 3, 40, false),
    // ---- new secrets
    OVERLOAD(Element.THUNDER, 1, 0, 3, 60, 360, 3, 30, false),
    ABSOLUTE_ZERO(Element.ICE, 1, 0, 4, 70, 420, 3, 30, false),
    SHADOW_CLONES(Element.SHADOW, 1, 0, 4, 55, 360, 4, 20, false),
    SPIRIT_AWAKENING(Element.SPIRIT, 1, 0, 4, 60, 420, 3, 30, false);

    public final Element element;
    public final int kind, slot, minRank, cost, cooldown, pose, poseTicks;
    public final boolean chargeable;

    Skill(Element element, int kind, int slot, int minRank, int cost, int cooldown, int pose, int poseTicks, boolean chargeable) {
        this.element = element;
        this.kind = kind;
        this.slot = slot;
        this.minRank = minRank;
        this.cost = cost;
        this.cooldown = cooldown;
        this.pose = pose;
        this.poseTicks = poseTicks;
        this.chargeable = chargeable;
    }

    public Text displayName() {
        return Text.translatable("skill.avatar." + name().toLowerCase());
    }

    /** Bit in the saved "secrets" mask. Old secrets keep their old bit; new ones use bits that were never used. */
    public int bit() {
        return switch (this) {
            case OVERLOAD -> 38;
            case ABSOLUTE_ZERO -> 39;
            case SHADOW_CLONES -> 40;
            case SPIRIT_AWAKENING -> 41;
            default -> ordinal();
        };
    }

    public boolean isSecret() {
        return kind == 1;
    }

    /** The seven basic techniques of an element, by key slot. */
    public static Skill basic(Element e, int slot) {
        if (slot < 0 || slot > 6) return null;
        for (Skill s : values()) if (s.kind == 0 && s.element == e && s.slot == slot) return s;
        return null;
    }

    /** Special-key list: unlocked secrets of the active element, then combos and the supreme technique in Avatar State. */
    public static List<Skill> specials(Element active, long secrets, boolean avatarOn) {
        List<Skill> l = new ArrayList<>();
        for (Skill s : values()) {
            if (s.kind == 1 && s.element == active && (secrets & (1L << s.bit())) != 0) l.add(s);
        }
        if (avatarOn) for (Skill s : values()) if (s.kind >= 2) l.add(s);
        return l;
    }

    // ---- unlock challenges of secret techniques: stat index, target, rank (see AData stat constants)
    public int statIndex() {
        return switch (this) {
            case LIGHTNING_STORM -> AData.ST_LIGHTNING;
            case COMBUSTION -> AData.ST_KILLS + 0;
            case BLOODBENDING -> AData.ST_KILLS + 1;
            case ADV_HEALING -> AData.ST_HEAL;
            case METAL_SHRAPNEL -> AData.ST_METAL;
            case LAVABENDING -> AData.ST_KILLS + 2;
            case SEISMIC_SENSE -> AData.ST_QUAKE;
            case SPIRIT_PROJECTION -> AData.ST_FLIGHT;
            case PRESSURE -> AData.ST_KILLS + 3;
            case OVERLOAD, ABSOLUTE_ZERO, SHADOW_CLONES, SPIRIT_AWAKENING -> AData.killStat(element);
            default -> -1;
        };
    }

    public int statTarget() {
        return switch (this) {
            case LIGHTNING_STORM -> 20;
            case COMBUSTION -> 20;
            case BLOODBENDING -> 20;
            case ADV_HEALING -> 200;
            case METAL_SHRAPNEL -> 15;
            case LAVABENDING -> 30;
            case SEISMIC_SENSE -> 10;
            case SPIRIT_PROJECTION -> 2400;
            case PRESSURE -> 25;
            case OVERLOAD, ABSOLUTE_ZERO, SHADOW_CLONES, SPIRIT_AWAKENING -> 25;
            default -> 0;
        };
    }
}
