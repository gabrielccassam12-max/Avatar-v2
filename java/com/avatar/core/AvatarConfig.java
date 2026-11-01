package com.avatar.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** config/avatar.json - balance, block damage and performance. Read once at startup. */
public final class AvatarConfig {
    // ---- balance
    public double costMultiplier = 1.0;
    public double cooldownMultiplier = 1.0;
    public double damageMultiplier = 1.0;
    public double energyRegenMultiplier = 1.0;
    public boolean allowPvp = true;
    // ---- world interaction
    /** false = bending never touches blocks (particles only). */
    public boolean modifyBlocks = true;
    /** Temporary structures (walls, ice, spikes, craters) disappear after this many ticks. */
    public int restoreTicks = 500;
    /** Collapse, Earthquake and Eruption may dig temporary craters. */
    public boolean craters = true;
    /** Fire may set blocks on fire. */
    public boolean fireIgnites = true;
    /** Never touch blocks inside this many blocks of the world spawn. */
    public int spawnProtectionRadius = 0;
    /** Upper limit of blocks one technique may change. */
    public int maxBlocksPerCast = 400;
    /** Bloodbending only works at night (true to the show). */
    public boolean bloodbendingNightOnly = true;
    // ---- permissions (vanilla permission level, 0 = everyone)
    public int secretsPermissionLevel = 0;
    public int avatarStatePermissionLevel = 0;
    // ---- performance
    public double particleDensity = 1.0;
    public boolean cameraShake = true;

    public static AvatarConfig INSTANCE = new AvatarConfig();
    /** Mirrors INSTANCE.particleDensity for quick access. */
    public static double DENSITY = 1.0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("avatar.json");
        try {
            if (Files.exists(p)) {
                AvatarConfig c = GSON.fromJson(Files.readString(p), AvatarConfig.class);
                if (c != null) INSTANCE = c;
            }
            INSTANCE.particleDensity = Math.max(0.1, Math.min(3.0, INSTANCE.particleDensity));
            Files.writeString(p, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            e.printStackTrace();
        }
        DENSITY = INSTANCE.particleDensity;
    }

    public static int cost(int base) {
        return Math.max(0, (int) Math.round(base * INSTANCE.costMultiplier));
    }

    public static int cooldown(int base) {
        return Math.max(1, (int) Math.round(base * INSTANCE.cooldownMultiplier));
    }
}
