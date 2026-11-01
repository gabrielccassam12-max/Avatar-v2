package com.avatar.particle;

import com.avatar.Avatar;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mod's 3D cube particles, one palette per element: young -> middle -> old.
 * Names match assets/avatar/particles/<name>.json. Prefix f_ fire, w_ water, e_ earth, a_ air, s_ spirit / avatar.
 */
public final class ModParticles {
    private ModParticles() {}

    public static final Map<String, CubeProfile> PROFILES = new LinkedHashMap<>();
    public static final Map<String, SimpleParticleType> TYPES = new LinkedHashMap<>();

    static {
        //                              young     middle    old       size   var    life var  grav    drag   shrink spin  flat  alpha inner
        // ---- fire: yellow -> orange -> red
        PROFILES.put("f_fire",  new CubeProfile(0xFFF3B0, 0xFFA928, 0xE2480F, 0.20f, 0.10f, 14, 6, 0.0f,    0.91f, 0.955f, 14f, 1f,    0.80f, true));
        PROFILES.put("f_big",   new CubeProfile(0xFFF0A0, 0xFF9A22, 0xD83C0C, 0.55f, 0.25f, 11, 4, 0.0f,    0.88f, 0.930f, 8f,  1f,    0.55f, true));
        PROFILES.put("f_core",  new CubeProfile(0xFFFFFF, 0xFFF0A0, 0xFFB030, 0.35f, 0.10f, 5,  2, 0.0f,    0.80f, 0.880f, 20f, 1f,    0.95f, true));
        PROFILES.put("f_ember", new CubeProfile(0xFFEFA0, 0xFF9A20, 0xB8300A, 0.075f, 0.03f, 28, 12, -0.004f, 0.97f, 0.985f, 25f, 1f,   0.95f, false));
        PROFILES.put("f_ring",  new CubeProfile(0xFFE9B0, 0xFFB444, 0xE07A22, 0.15f, 0.03f, 5,  1, 0.0f,    1.00f, 0.900f, 0f,  0.25f, 0.90f, false));
        PROFILES.put("f_smoke", new CubeProfile(0x5A5550, 0x3A3735, 0x2A2828, 0.35f, 0.15f, 30, 10, -0.002f, 0.94f, 1.010f, 6f,  1f,    0.35f, false));
        // ---- water: white -> cyan -> deep blue
        PROFILES.put("w_drop",  new CubeProfile(0xF2FCFF, 0x59C2FF, 0x1C63D8, 0.15f, 0.07f, 16, 6, 0.012f,  0.94f, 0.965f, 18f, 1f,    0.80f, true));
        PROFILES.put("w_big",   new CubeProfile(0xDDF6FF, 0x3FA9FF, 0x1752C0, 0.48f, 0.20f, 13, 4, 0.0f,    0.90f, 0.945f, 8f,  1f,    0.50f, true));
        PROFILES.put("w_mist",  new CubeProfile(0xFFFFFF, 0xCFE9F7, 0x9DBBD0, 0.34f, 0.14f, 26, 8, -0.002f, 0.95f, 1.008f, 5f,  1f,    0.28f, false));
        PROFILES.put("w_ice",   new CubeProfile(0xFFFFFF, 0xBFEFFF, 0x6FB8F0, 0.22f, 0.09f, 26, 8, 0.0f,    0.92f, 0.975f, 12f, 1f,    0.85f, true));
        PROFILES.put("w_ring",  new CubeProfile(0xE6FAFF, 0x59C2FF, 0x2060D8, 0.15f, 0.03f, 5,  1, 0.0f,    1.00f, 0.900f, 0f,  0.25f, 0.90f, false));
        // ---- earth: sand -> brown -> dark rock
        PROFILES.put("e_rock",  new CubeProfile(0xC8B089, 0x8A6B46, 0x4B3A28, 0.24f, 0.12f, 20, 8, 0.018f,  0.93f, 0.975f, 9f,  1f,    1.00f, false));
        PROFILES.put("e_big",   new CubeProfile(0xB9A07A, 0x7A6240, 0x453524, 0.60f, 0.25f, 24, 6, 0.014f,  0.94f, 0.985f, 5f,  1f,    1.00f, false));
        PROFILES.put("e_dust",  new CubeProfile(0xCDBA95, 0xA58F6A, 0x7A6A50, 0.34f, 0.14f, 22, 8, -0.001f, 0.93f, 1.006f, 5f,  1f,    0.35f, false));
        PROFILES.put("e_spark", new CubeProfile(0xFFE8B0, 0xE8A04A, 0x8A4A20, 0.08f, 0.03f, 18, 6, 0.012f,  0.95f, 0.980f, 30f, 1f,    0.95f, true));
        PROFILES.put("e_ring",  new CubeProfile(0xE3CFA4, 0xA3835A, 0x6A5236, 0.15f, 0.03f, 5,  1, 0.0f,    1.00f, 0.900f, 0f,  0.25f, 0.90f, false));
        // ---- air: white wisps (very light)
        PROFILES.put("a_wind",  new CubeProfile(0xFFFFFF, 0xE6F3FF, 0xBFD8EE, 0.16f, 0.07f, 14, 5, 0.0f,    0.93f, 0.960f, 22f, 1f,    0.55f, true));
        PROFILES.put("a_big",   new CubeProfile(0xFFFFFF, 0xDDEBFA, 0xB4CDE4, 0.50f, 0.20f, 12, 4, 0.0f,    0.90f, 0.940f, 8f,  1f,    0.30f, false));
        PROFILES.put("a_streak",new CubeProfile(0xFFFFFF, 0xEAF6FF, 0xC8E0F4, 0.12f, 0.03f, 8,  2, 0.0f,    0.97f, 0.930f, 0f,  0.35f, 0.90f, true));
        PROFILES.put("a_ring",  new CubeProfile(0xFFFFFF, 0xDDEFFF, 0xA8CBEA, 0.15f, 0.03f, 5,  1, 0.0f,    1.00f, 0.900f, 0f,  0.25f, 0.85f, false));
        // ---- blood-red threads and bright metal shards
        PROFILES.put("r_blood", new CubeProfile(0xFF7A7A, 0xB01818, 0x3A0808, 0.13f, 0.05f, 18, 6, 0.004f,  0.95f, 0.970f, 14f, 1f,    0.90f, true));
        PROFILES.put("m_metal", new CubeProfile(0xFFFFFF, 0xC4CEDA, 0x6A7484, 0.12f, 0.04f, 14, 4, 0.0f,    0.97f, 0.965f, 20f, 1f,    1.00f, false));
        // ---- lightning + avatar spirit
        PROFILES.put("l_bolt",  new CubeProfile(0xFFFFFF, 0xCFE8FF, 0x6FA8FF, 0.20f, 0.05f, 6,  2, 0.0f,    0.60f, 0.880f, 6f,  1f,    1.00f, true));
        PROFILES.put("s_gold",  new CubeProfile(0xFFFFFF, 0xFFE9A0, 0xFFB22E, 0.20f, 0.08f, 16, 6, -0.003f, 0.94f, 0.965f, 14f, 1f,    0.85f, true));
        PROFILES.put("s_core",  new CubeProfile(0xFFFFFF, 0xFFFFFF, 0xFFE08A, 0.45f, 0.10f, 6,  2, 0.0f,    0.80f, 0.900f, 12f, 1f,    1.00f, true));
        // ---- thunder: white -> yellow -> amber
        PROFILES.put("t_spark", new CubeProfile(0xFFFFFF, 0xFFF27A, 0xFFB81F, 0.16f, 0.06f, 9,  3, 0.0f,    0.80f, 0.930f, 25f, 1f,    1.00f, true));
        PROFILES.put("t_big",   new CubeProfile(0xFFFFFF, 0xFFEE70, 0xFFAA10, 0.50f, 0.18f, 8,  3, 0.0f,    0.82f, 0.900f, 12f, 1f,    0.60f, true));
        PROFILES.put("t_arc",   new CubeProfile(0xFFFFFF, 0xFFFAC0, 0xFFD23A, 0.18f, 0.04f, 5,  2, 0.0f,    0.55f, 0.880f, 8f,  1f,    1.00f, true));
        PROFILES.put("t_ring",  new CubeProfile(0xFFFFFF, 0xFFF08A, 0xFFC832, 0.15f, 0.03f, 5,  1, 0.0f,    1.00f, 0.900f, 0f,  0.25f, 0.95f, false));
        // ---- ice: white -> pale cyan -> frost blue
        PROFILES.put("i_flake", new CubeProfile(0xFFFFFF, 0xDDF8FF, 0x9FD8F5, 0.14f, 0.06f, 22, 8, 0.004f,  0.95f, 0.975f, 12f, 1f,    0.90f, true));
        PROFILES.put("i_big",   new CubeProfile(0xF4FDFF, 0xBDEBFF, 0x7CC4F0, 0.52f, 0.20f, 14, 5, 0.0f,    0.90f, 0.950f, 6f,  1f,    0.50f, true));
        PROFILES.put("i_shard", new CubeProfile(0xFFFFFF, 0xC8F1FF, 0x5FB0EC, 0.24f, 0.10f, 26, 8, 0.016f,  0.94f, 0.975f, 10f, 1f,    0.90f, true));
        PROFILES.put("i_ring",  new CubeProfile(0xF0FDFF, 0xA8E6FF, 0x6CC0F0, 0.15f, 0.03f, 5,  1, 0.0f,    1.00f, 0.900f, 0f,  0.25f, 0.90f, false));
        // ---- shadow: lilac -> violet -> near black
        PROFILES.put("d_wisp",  new CubeProfile(0xC9A6FF, 0x7A3FE0, 0x2A0F55, 0.20f, 0.08f, 18, 6, -0.002f, 0.93f, 0.965f, 14f, 1f,    0.85f, true));
        PROFILES.put("d_big",   new CubeProfile(0xB58CFF, 0x5E2BC4, 0x1E0A3C, 0.55f, 0.22f, 14, 5, 0.0f,    0.90f, 0.945f, 6f,  1f,    0.60f, true));
        PROFILES.put("d_smoke", new CubeProfile(0x4A2C70, 0x2B1745, 0x140A24, 0.38f, 0.16f, 28, 10, -0.002f, 0.94f, 1.008f, 5f,  1f,    0.45f, false));
        PROFILES.put("d_ring",  new CubeProfile(0xCBA9FF, 0x8446F0, 0x3A1A78, 0.15f, 0.03f, 5,  1, 0.0f,    1.00f, 0.900f, 0f,  0.25f, 0.90f, false));
        // ---- spirit: white -> mint -> teal
        PROFILES.put("p_wisp",  new CubeProfile(0xFFFFFF, 0xA8FFE2, 0x2FD6A4, 0.18f, 0.07f, 20, 7, -0.004f, 0.94f, 0.968f, 12f, 1f,    0.80f, true));
        PROFILES.put("p_big",   new CubeProfile(0xF2FFFA, 0x8CF5D0, 0x20B88C, 0.52f, 0.20f, 14, 5, -0.002f, 0.91f, 0.948f, 6f,  1f,    0.45f, true));
        PROFILES.put("p_mote",  new CubeProfile(0xFFFFFF, 0xC8FFF0, 0x5CF0C0, 0.10f, 0.04f, 26, 9, -0.006f, 0.96f, 0.980f, 20f, 1f,    0.95f, true));
        PROFILES.put("p_ring",  new CubeProfile(0xEFFFF9, 0x9CFFE0, 0x3CE0B0, 0.15f, 0.03f, 6,  1, 0.0f,    1.00f, 0.900f, 0f,  0.25f, 0.90f, false));
        PROFILES.put("s_ring",  new CubeProfile(0xFFFFFF, 0xFFE9A0, 0xFFB22E, 0.15f, 0.03f, 6,  1, 0.0f,    1.00f, 0.900f, 0f,  0.25f, 0.90f, false));
    }

    public static SimpleParticleType get(String name) {
        return TYPES.get(name);
    }

    public static void init() {
        for (String name : PROFILES.keySet()) {
            SimpleParticleType t = FabricParticleTypes.simple(true);
            Registry.register(Registries.PARTICLE_TYPE, Avatar.id(name), t);
            TYPES.put(name, t);
        }
    }
}
