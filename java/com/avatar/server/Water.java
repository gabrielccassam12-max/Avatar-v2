package com.avatar.server;

import com.avatar.core.AData;
import com.avatar.core.Element;
import com.avatar.particle.Fx;
import com.avatar.util.Effects;
import com.avatar.util.Scheduler;
import com.avatar.util.Snd;
import com.avatar.util.TempBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

/** WATER: control of space. Whips, blades, shields, prisons, ice and healing. */
public final class Water {
    private Water() {}

    private static final java.util.Random R = Fx.R;

    private static BlockState iceBlock() {
        int r = R.nextInt(3);
        return (r == 0 ? Blocks.BLUE_ICE : r == 1 ? Blocks.PACKED_ICE : Blocks.ICE).getDefaultState();
    }

    // ------------------------------------------------------------------ 1. Water Whip
    public static boolean whip(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Snd.play(w, c.eye(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.3f);
        Snd.play(w, c.eye(), SoundEvents.ENTITY_PLAYER_SPLASH, 0.8f, 1.2f);
        Set<Integer> hit = new HashSet<>();
        Vec3d look0 = c.look();
        Vec3d flat0 = new Vec3d(look0.x, 0, look0.z).lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : new Vec3d(look0.x, 0, look0.z).normalize();
        Effects.add(t -> {
            if (t > 8 || !p.isAlive()) return false;
            Vec3d look = p.getRotationVec(1f);
            Vec3d right = Ops.rightOf(look), up = right.crossProduct(look);
            double sweep = -1.2 + 2.4 * t / 8.0;                  // the whip swings from right to left
            Vec3d o = p.getEyePos().add(look.multiply(0.5)).add(right.multiply(0.4)).add(0, -0.4, 0);
            double len = 8.5;
            Vec3d tip = o;
            int n = 22;
            for (int i = 0; i <= n; i++) {
                double u = (double) i / n;
                double lateral = sweep * u * u * 2.8 + Math.sin(u * 7 - t * 1.4) * 0.35 * (1 - u);
                double lift = Math.sin(u * Math.PI) * 0.7 - 0.2 * u;
                Vec3d q = o.add(look.multiply(len * u)).add(right.multiply(lateral)).add(up.multiply(lift));
                Fx.vel(w, i % 2 == 0 ? "w_drop" : "w_big", q, Vec3d.ZERO);
                if (i % 3 == 0) Fx.vel(w, "w_ice", q, Fx.jitter(0.02));
                tip = q;
                if (i > 6) {
                    for (LivingEntity e : Ops.around(w, q, 1.3, p)) {
                        if (!hit.add(e.getId())) continue;
                        Ops.hurt(w, e, p, 6f * c.mult());
                        Vec3d pull = p.getPos().subtract(e.getPos());
                        Ops.push(e, pull, 0.5, 0.35);                    // lashes the enemy towards you
                        Fx.burst(w, "w_drop", Ops.center(e), 12, 0.25, 0.4);
                        Snd.play(w, q, SoundEvents.ENTITY_GENERIC_SPLASH, 0.8f, 1.4f);
                    }
                }
            }
            Fx.burst(w, "w_drop", tip, 3, 0.1, 0.3);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 2. Water Blades (3 piercing crescents)
    public static boolean blades(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d look = c.look();
        Snd.play(w, c.eye(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.6f);
        Snd.play(w, c.eye(), SoundEvents.BLOCK_GLASS_BREAK, 0.5f, 1.8f);
        for (int k = -1; k <= 1; k++) {
            double yaw = k * 0.28;
            Vec3d dir = new Vec3d(look.x * Math.cos(yaw) - look.z * Math.sin(yaw), look.y, look.x * Math.sin(yaw) + look.z * Math.cos(yaw)).normalize();
            Vec3d right = Ops.rightOf(dir), up = right.crossProduct(dir);
            Vec3d[] pos = {c.eye().add(dir.multiply(1.0)).add(0, -0.3, 0)};
            Set<Integer> hit = new HashSet<>();
            Effects.add(t -> {
                if (t > 16 || !p.isAlive()) return false;
                Vec3d next = pos[0].add(dir.multiply(1.0));
                if (!w.getBlockState(BlockPos.ofFloored(next)).getCollisionShape(w, BlockPos.ofFloored(next)).isEmpty()) return false;
                pos[0] = next;
                for (int i = -6; i <= 6; i++) {                      // a thin, slightly curved blade facing sideways
                    double u = i / 6.0;
                    Vec3d q = next.add(right.multiply(u * 0.9)).subtract(dir.multiply(u * u * 0.35));
                    Fx.vel(w, i % 2 == 0 ? "w_ice" : "w_drop", q, Vec3d.ZERO);
                }
                Fx.vel(w, "w_big", next, dir.multiply(-0.05));
                for (LivingEntity e : Ops.around(w, next, 1.3, p)) {
                    if (!hit.add(e.getId())) continue;
                    Ops.hurt(w, e, p, 5f * c.mult());
                    Ops.push(e, dir, 0.35, 0.15);
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1));
                    Fx.burst(w, "w_ice", Ops.center(e), 12, 0.3, 0.3);
                }
                return true;
            });
        }
        return true;
    }

    // ------------------------------------------------------------------ 3. Water Shield (a swirling disc in front + damage cut)
    public static boolean shield(Cast c, PState s) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        s.waterShieldUntil = w.getTime() + 110;
        Snd.play(w, c.pos(), SoundEvents.ENTITY_PLAYER_SPLASH, 1.0f, 0.8f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);
        Effects.add(t -> {
            if (t > 110 || !p.isAlive() || p.isRemoved() || w.getTime() > s.waterShieldUntil) return false;
            Vec3d look = p.getRotationVec(1f);
            Vec3d flat = new Vec3d(look.x, 0, look.z);
            flat = flat.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
            Vec3d right = Ops.rightOf(flat), up = new Vec3d(0, 1, 0);
            Vec3d ctr = p.getPos().add(0, 1.15, 0).add(flat.multiply(1.5));
            double spin = t * 0.35;
            for (int i = 0; i < 26; i++) {
                double a = spin + Math.PI * 2 * i / 26;
                double rr = 1.5 + Math.sin(a * 3 + t * 0.3) * 0.08;
                Vec3d q = ctr.add(right.multiply(Math.cos(a) * rr)).add(up.multiply(Math.sin(a) * rr));
                Fx.vel(w, i % 2 == 0 ? "w_drop" : "w_ice", q, Vec3d.ZERO);
            }
            for (int i = 0; i < 8; i++) {                          // inner swirl
                double a = -spin * 1.5 + Math.PI * 2 * i / 8, rr = 0.5 + 0.5 * ((t * 0.07 + i * 0.13) % 1.0);
                Fx.vel(w, "w_big", ctr.add(right.multiply(Math.cos(a) * rr)).add(up.multiply(Math.sin(a) * rr)), Vec3d.ZERO);
            }
            if (p.isOnFire()) p.extinguish();
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 4. Water Prison (sphere of water, holds the enemy)
    public static boolean prison(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        LivingEntity target = Ops.aimEntity(w, p, 20, 0.93);
        if (target == null) return false;
        Snd.play(w, target.getPos(), SoundEvents.ENTITY_GENERIC_SPLASH, 1.4f, 0.6f);
        Snd.play(w, target.getPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        int dur = 90;
        Effects.add(t -> {
            if (t > dur || !target.isAlive() || target.isRemoved() || !p.isAlive()) {
                if (target.isAlive()) {
                    Fx.burst(w, "w_drop", Ops.center(target), 50, 0.5, 0.4);
                    Fx.burst(w, "w_big", Ops.center(target), 10, 0.3, 0.4);
                    Snd.play(w, target.getPos(), SoundEvents.ENTITY_GENERIC_SPLASH, 1.5f, 0.8f);
                    Ops.push(target, new Vec3d(0, 0, 0), 0, 0.2);
                }
                return false;
            }
            Vec3d ctr = Ops.center(target);
            double r = 1.1 + target.getHeight() * 0.45;
            Fx.sphere(w, "w_drop", ctr, r, 14, Vec3d.ZERO);
            Fx.sphere(w, "w_big", ctr, r * 0.9, 2, Vec3d.ZERO);
            Fx.circle(w, "w_ice", ctr.add(0, Math.sin(t * 0.2) * r * 0.6, 0), r * 0.8, 6, t * 0.4, 0);
            target.setVelocity(0, 0.02 + Math.sin(t * 0.25) * 0.03, 0);          // floats helplessly
            target.velocityModified = true;
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 6, 9, true, false, false));
            if (t % 15 == 0) Ops.hurt(w, target, p, 2.5f * c.mult());               // drowning
            if (t % 20 == 1) Snd.play(w, ctr, SoundEvents.ENTITY_PLAYER_SPLASH, 0.5f, 0.7f);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 5. Ice Spikes (line of ice erupting from the ground)
    public static void spikeColumn(ServerWorld w, ServerPlayerEntity p, Vec3d at, int height, BlockState[] blocks, String part, TempBlocks.Budget b, int ticks) {
        BlockPos g = Ops.groundPos(w, at);
        for (int h = 0; h < height; h++) {
            BlockPos q = g.up(h);
            TempBlocks.place(w, b, q, blocks[Math.min(blocks.length - 1, R.nextInt(blocks.length))], ticks);
            for (int i = 0; i < 5; i++) Fx.vel(w, part, q.getX() + 0.5 + R.nextGaussian() * 0.25, q.getY() + 0.5, q.getZ() + 0.5 + R.nextGaussian() * 0.25, 0, 0.05, 0);
        }
    }

    public static boolean iceSpikes(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d look = c.look();
        Vec3d dir = new Vec3d(look.x, 0, look.z).lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : new Vec3d(look.x, 0, look.z).normalize();
        Vec3d right = Ops.rightOf(dir);
        TempBlocks.Budget budget = TempBlocks.Budget.make();
        BlockState[] ice = {Blocks.PACKED_ICE.getDefaultState(), Blocks.BLUE_ICE.getDefaultState(), Blocks.ICE.getDefaultState()};
        Snd.play(w, c.pos(), SoundEvents.BLOCK_GLASS_BREAK, 1.2f, 0.6f);
        Set<Integer> hit = new HashSet<>();
        Effects.add(t -> {
            if (t > 12 || !p.isAlive()) return false;
            double dist = 1.6 + t * 1.15;
            for (int side = -1; side <= 1; side++) {
                if (side != 0 && t % 2 == 1) continue;
                Vec3d at = p.getPos().add(dir.multiply(dist)).add(right.multiply(side * (0.6 + t * 0.12)));
                spikeColumn(w, p, at, side == 0 ? 3 : 2, ice, "w_ice", budget, 0);
                Fx.burst(w, "w_ice", at.add(0, 0.5, 0), 6, 0.2, 0.8);
                for (LivingEntity e : Ops.around(w, at.add(0, 1, 0), 1.7, p)) {
                    if (!hit.add(e.getId())) continue;
                    Ops.hurt(w, e, p, 7f * c.mult());
                    e.setVelocity(e.getVelocity().add(0, 0.85, 0));
                    e.velocityModified = true;
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 70, 2));
                    e.setFrozenTicks(120);
                }
            }
            if (t % 3 == 1) Snd.play(w, p.getPos().add(dir.multiply(dist)), SoundEvents.BLOCK_GLASS_BREAK, 0.7f, 0.7f + t * 0.04f);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ cage (ice / mud): a shell of temporary blocks around a target
    public static void cage(ServerWorld w, LivingEntity target, BlockState[] blocks, String part, int ticks) {
        TempBlocks.Budget b = TempBlocks.Budget.make();
        BlockPos o = target.getBlockPos();
        int hgt = Math.max(2, (int) Math.ceil(target.getHeight()));
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = -1; dy <= hgt; dy++) {
                    boolean shell = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == -1 || dy == hgt;
                    if (!shell) continue;
                    BlockPos q = o.add(dx, dy, dz);
                    if (TempBlocks.place(w, b, q, blocks[R.nextInt(blocks.length)], ticks)) {
                        for (int i = 0; i < 3; i++) Fx.vel(w, part, q.getX() + 0.5 + R.nextGaussian() * 0.3, q.getY() + 0.5, q.getZ() + 0.5 + R.nextGaussian() * 0.3, 0, 0.03, 0);
                    }
                }
    }

    // ------------------------------------------------------------------ 6. Ice Cage
    public static boolean iceCage(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        LivingEntity target = Ops.aimEntity(w, p, 20, 0.93);
        if (target == null) return false;
        Snd.play(w, target.getPos(), SoundEvents.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
        Snd.play(w, target.getPos(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.0f);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 140, 6));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 140, 1));
        target.setFrozenTicks(260);
        Ops.hurt(w, target, p, 4f * c.mult());
        cage(w, target, new BlockState[]{Blocks.ICE.getDefaultState(), Blocks.PACKED_ICE.getDefaultState()}, "w_ice", 140);
        Vec3d ctr = Ops.center(target);
        Effects.add(t -> {                                         // even without blocks the target is held in a cage of ice cubes
            if (t > 100 || !target.isAlive()) return false;
            Fx.sphere(w, "w_ice", ctr, 1.5, 6, Vec3d.ZERO);
            if (t < 100 && !AvatarCfgBlocks()) {
                target.setVelocity(0, target.getVelocity().y, 0);
                target.velocityModified = true;
            }
            return true;
        });
        Fx.burst(w, "w_ice", ctr, 50, 0.4, 0.2);
        return true;
    }

    private static boolean AvatarCfgBlocks() {
        return com.avatar.core.AvatarConfig.INSTANCE.modifyBlocks;
    }

    // ------------------------------------------------------------------ 7. Healing
    public static void healBurst(ServerWorld w, LivingEntity e, float amount, boolean cleanse) {
        float before = e.getHealth();
        e.heal(amount);
        if (cleanse) {
            for (StatusEffectInstance inst : new java.util.ArrayList<>(e.getStatusEffects())) {
                if (!inst.getEffectType().value().isBeneficial()) e.removeStatusEffect(inst.getEffectType());
            }
            e.extinguish();
        }
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, cleanse ? 2 : 1));
        if (e instanceof ServerPlayerEntity sp) {
            float healed = Math.max(0f, e.getHealth() - before);
            Bender.stat(sp, AData.ST_HEAL, (int) Math.ceil(healed));
        }
    }

    public static boolean healing(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.6f);
        Snd.play(w, c.pos(), SoundEvents.ENTITY_PLAYER_SPLASH, 0.8f, 1.5f);
        healBurst(w, p, 7f * Math.min(1.6f, c.mult()), false);
        for (ServerPlayerEntity o : w.getPlayers(q -> q != p && q.squaredDistanceTo(p) < 36)) healBurst(w, o, 6f, false);
        Effects.add(t -> {
            if (t > 34 || !p.isAlive()) return false;
            Vec3d ctr = p.getPos();
            for (int k = 0; k < 3; k++) {                            // two glowing water rings climbing the body
                double a = t * 0.5 + k * 2.094, y = ((t * 3 + k * 11) % 36) / 36.0 * 2.0;
                Fx.vel(w, "w_drop", ctr.x + Math.cos(a) * 0.9, ctr.y + y, ctr.z + Math.sin(a) * 0.9, 0, 0.02, 0);
                Fx.vel(w, "s_gold", ctr.x + Math.cos(a + 3.14) * 0.7, ctr.y + y, ctr.z + Math.sin(a + 3.14) * 0.7, 0, 0.03, 0);
            }
            if (t % 6 == 1) {
                Fx.circle(w, "w_ring", ctr.add(0, 0.1, 0), 0.6 + t * 0.08, 20, 0, 0);
                Snd.play(w, ctr, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.2f + t * 0.02f);
            }
            return true;
        });
        return true;
    }
}
