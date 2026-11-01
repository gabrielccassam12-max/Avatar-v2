package com.avatar.server;

import com.avatar.core.AvatarConfig;
import com.avatar.core.Element;
import com.avatar.particle.Fx;
import com.avatar.util.Effects;
import com.avatar.util.Scheduler;
import com.avatar.util.Snd;
import com.avatar.util.TempBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/** ICE: freezes, slows and shatters. White and pale blue cubes. */
public final class Ice {
    private Ice() {}

    private static final Random R = Fx.R;

    private static void freeze(LivingEntity e, int ticks, int slowAmp) {
        e.setFrozenTicks(Math.max(e.getFrozenTicks(), ticks));
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, Math.min(ticks, 200), slowAmp));
    }

    private static void shatter(ServerWorld w, Vec3d at, int n) {
        Snd.play(w, at, SoundEvents.BLOCK_GLASS_BREAK, 1.0f, 1.3f);
        Fx.burst(w, "i_shard", at, n, 0.45, 0.3);
        Fx.burst(w, "i_flake", at, n / 2, 0.3, 0.3);
        Fx.vel(w, "i_big", at, Vec3d.ZERO);
    }

    // ------------------------------------------------------------------ 1. Frost Shard
    public static boolean shard(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Snd.play(w, c.hand(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.6f);
        Snd.play(w, c.hand(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.8f);
        Fx.burst(w, "i_flake", c.hand(), 8, 0.2, 0.1);
        Ops.projectile(w, p, c.hand(), c.look(), 1.5, 24, 0.7, false, (q, dir, t) -> {
            Fx.vel(w, "i_shard", q, Vec3d.ZERO);
            Fx.vel(w, "i_big", q, Vec3d.ZERO);
            Fx.vel(w, "i_flake", q.add(Fx.jitter(0.15)), dir.multiply(-0.2));
        }, (q, target) -> {
            shatter(w, q, 26);
            Ops.blast(w, q, p, 2.0, 5f * m, 0.4, 0.2, e -> freeze(e, 100, 1));
            Ops.shake(w, q, 8, 2, 4);
        });
        return true;
    }

    // ------------------------------------------------------------------ 2. Frost Breath
    public static boolean breath(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Snd.play(w, c.hand(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.8f);
        Effects.add(t -> {
            if (t > 22 || !p.isAlive()) return false;
            Vec3d look = p.getRotationVec(1f), from = p.getEyePos().add(look.multiply(0.8)).add(0, -0.35, 0);
            for (int i = 0; i < 7; i++) Fx.vel(w, i % 3 == 0 ? "i_shard" : "i_flake", from, look.multiply(0.55 + R.nextDouble() * 0.3).add(Fx.jitter(0.1)));
            Fx.vel(w, "i_big", from.add(look), look.multiply(0.5).add(Fx.jitter(0.04)));
            if (t % 3 == 0) {
                Snd.play(w, from, SoundEvents.BLOCK_POWDER_SNOW_STEP, 0.8f, 0.7f);
                for (LivingEntity e : Ops.around(w, from.add(look.multiply(4.5)), 5.0, p)) {
                    Vec3d to = Ops.center(e).subtract(from);
                    if (to.length() > 10 || to.normalize().dotProduct(look) < 0.78) continue;
                    Ops.hurt(w, e, p, 2.2f * m);
                    freeze(e, 140, 2);
                    Fx.burst(w, "i_flake", Ops.center(e), 6, 0.2, 0.3);
                }
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 3. Ice Armor
    public static boolean armor(Cast c, PState s) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        long until = w.getTime() + 240;
        s.iceArmorUntil = until;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 240, 1));
        Snd.play(w, c.pos(), SoundEvents.BLOCK_GLASS_BREAK, 1.0f, 0.6f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.8f);
        Ops.ring(w, c.pos().add(0, 0.1, 0), 3.2, 8, Element.ICE);
        Fx.burst(w, "i_shard", c.pos().add(0, 1, 0), 40, 0.4, 0.2);
        Effects.add(t -> {
            if (w.getTime() >= until || !p.isAlive()) {
                if (p.isAlive()) shatter(w, p.getPos().add(0, 1, 0), 30);
                return false;
            }
            Vec3d b = p.getPos();
            if (t % 2 == 0) {
                for (int k = 0; k < 3; k++) {
                    double a = t * 0.15 + k * 2.094, y = 0.2 + ((t + k * 9) % 24) / 24.0 * 1.6;
                    Fx.vel(w, "i_shard", b.x + Math.cos(a) * 0.7, b.y + y, b.z + Math.sin(a) * 0.7, 0, 0, 0);
                }
            }
            if (t % 8 == 0) Fx.vel(w, "i_big", b.x + R.nextGaussian() * 0.3, b.y + 0.3 + R.nextDouble() * 1.4, b.z + R.nextGaussian() * 0.3, 0, 0, 0);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 4. Glacier Wall
    public static boolean wall(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d at = Ops.aimPoint(w, p, 7.0);
        Vec3d dir = Ops.flat(c.look()), side = Ops.rightOf(dir);
        TempBlocks.Budget budget = TempBlocks.Budget.make();
        Snd.play(w, at, SoundEvents.BLOCK_GLASS_BREAK, 1.4f, 0.5f);
        Snd.play(w, at, SoundEvents.BLOCK_POWDER_SNOW_PLACE, 1.5f, 0.6f);
        Ops.shake(w, at, 12, 3, 8);
        for (int i = -3; i <= 3; i++) {
            Vec3d col = at.add(side.multiply(i));
            BlockPos g = Ops.groundPos(w, col);
            int dist = Math.abs(i);
            for (int h = 0; h < 4 - (dist > 2 ? 1 : 0); h++) {
                BlockPos q = g.up(h);
                Scheduler.later(1 + h * 2 + dist, () -> {
                    if (TempBlocks.place(w, budget, q, Blocks.PACKED_ICE.getDefaultState(), 300)) {
                        for (LivingEntity e : Ops.targets(w, new net.minecraft.util.math.Box(q), null)) {
                            e.addVelocity(0, 0.6, 0);
                            e.velocityModified = true;
                            freeze(e, 80, 1);
                        }
                    }
                    Vec3d top = new Vec3d(q.getX() + 0.5, q.getY() + 0.6, q.getZ() + 0.5);
                    Fx.burst(w, "i_flake", top, 4, 0.1, 0.4);
                    Fx.vel(w, "i_shard", top.x, top.y + 0.5, top.z, R.nextGaussian() * 0.04, 0.12, R.nextGaussian() * 0.04);
                });
            }
        }
        if (!AvatarConfig.INSTANCE.modifyBlocks) {                              // no block changes: a wall of cubes instead
            Effects.add(t -> {
                if (t > 60) return false;
                for (int i = -3; i <= 3; i++)
                    for (int h = 0; h < 4; h++)
                        Fx.vel(w, "i_big", at.add(side.multiply(i)).add(0, h * 0.9 + 0.5, 0), Vec3d.ZERO);
                return true;
            });
        }
        return true;
    }

    // ------------------------------------------------------------------ 5. Hail Storm
    public static boolean hail(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d ctr = Ops.aimPoint(w, p, 22);
        float m = c.mult();
        Snd.play(w, ctr, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 1.5f, 0.6f);
        Ops.ring(w, ctr.add(0, 0.1, 0), 5.5, 14, Element.ICE);
        Effects.add(t -> {
            if (t > 32 || !p.isAlive()) return false;
            for (int k = 0; k < 2; k++) {
                double a = R.nextDouble() * Math.PI * 2, rr = Math.sqrt(R.nextDouble()) * 5.5;
                Vec3d spot = ctr.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
                double gy = Ops.groundPos(w, spot).getY();
                for (int s = 0; s < 8; s++) Fx.vel(w, s % 2 == 0 ? "i_shard" : "i_flake", spot.x, gy + 10 - s * 0.25, spot.z, 0, -0.9, 0);
                Scheduler.later(9, () -> {
                    Vec3d hit = new Vec3d(spot.x, gy + 0.2, spot.z);
                    shatter(w, hit, 14);
                    Ops.blast(w, hit, p, 1.8, 4f * m, 0.3, 0.2, e -> freeze(e, 80, 1));
                });
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 6. Flash Freeze
    public static boolean flash(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d at = c.pos();
        Snd.play(w, at, SoundEvents.BLOCK_GLASS_BREAK, 1.6f, 0.4f);
        Snd.play(w, at, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 1.5f, 0.5f);
        Ops.ring(w, at.add(0, 0.1, 0), 7.5, 12, Element.ICE);
        Effects.groundRing(w, at.add(0, 0.4, 0), 7.5, 16, "i_ring", "i_shard");
        Fx.burst(w, "i_flake", at.add(0, 1, 0), 80, 0.7, 0.2);
        Fx.burst(w, "i_big", at.add(0, 1, 0), 10, 0.3, 0.2);
        Ops.blast(w, at, p, 7.5, 6f * c.mult(), 0.6, 0.3, e -> {
            freeze(e, 240, 5);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 5));
        });
        if (AvatarConfig.INSTANCE.modifyBlocks) {                                // water around you turns to ice for a while
            TempBlocks.Budget budget = TempBlocks.Budget.make();
            BlockPos b = BlockPos.ofFloored(at);
            for (int dx = -5; dx <= 5; dx++)
                for (int dz = -5; dz <= 5; dz++)
                    for (int dy = -1; dy <= 0; dy++) {
                        BlockPos q = b.add(dx, dy, dz);
                        if (w.getBlockState(q).isOf(Blocks.WATER) && w.getBlockState(q.up()).isAir())
                            TempBlocks.place(w, budget, q, Blocks.ICE.getDefaultState(), 200);
                    }
        }
        Ops.shake(w, at, 18, 5, 8);
        return true;
    }

    // ------------------------------------------------------------------ 7. Glacial Comet
    public static boolean comet(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d target = Ops.aimPoint(w, p, 30);
        float m = c.mult();
        Vec3d start = target.add(R.nextGaussian() * 2, 24, R.nextGaussian() * 2);
        Snd.play(w, target, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 2.0f, 0.5f);
        Ops.ring(w, target.add(0, 0.1, 0), 6.5, 26, Element.ICE);
        int fall = 26;
        Effects.add(t -> {
            if (!p.isAlive()) return false;
            double k = Math.min(1.0, (double) t / fall);
            Vec3d pos = start.add(target.subtract(start).multiply(k * k));
            Fx.sphere(w, "i_big", pos, 1.5, 6, Vec3d.ZERO);
            Fx.sphere(w, "i_shard", pos, 1.6, 8, Vec3d.ZERO);
            Fx.vel(w, "i_flake", pos.add(Fx.jitter(0.5)), new Vec3d(0, 0.25, 0));
            if (t % 5 == 0) Snd.play(w, pos, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
            if (t < fall) return true;
            Snd.play(w, target, SoundEvents.ENTITY_GENERIC_EXPLODE, 2.0f, 1.3f);
            Snd.play(w, target, SoundEvents.BLOCK_GLASS_BREAK, 2.0f, 0.5f);
            shatter(w, target.add(0, 0.5, 0), 90);
            Fx.burst(w, "i_big", target.add(0, 0.8, 0), 14, 0.5, 0.4);
            Effects.groundRing(w, target.add(0, 0.3, 0), 8, 14, "i_ring", "i_shard");
            Ops.blast(w, target, p, 6.5, 20f * m, 1.6, 0.8, e -> freeze(e, 200, 3));
            Ops.shake(w, target, 30, 9, 14);
            return false;
        });
        return true;
    }

    // ------------------------------------------------------------------ Secret: Absolute Zero
    public static boolean absoluteZero(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d at = c.pos();
        float m = c.mult();
        Snd.play(w, at, SoundEvents.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);
        Snd.play(w, at, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 2.0f, 0.4f);
        Ops.ring(w, at.add(0, 0.1, 0), 12, 24, Element.ICE);
        Effects.groundRing(w, at.add(0, 0.3, 0), 12, 30, "i_ring", "i_shard");
        Ops.shake(w, at, 24, 6, 20);
        Effects.add(t -> {
            if (t > 100 || !p.isAlive()) return false;
            for (int i = 0; i < 14; i++) {                                         // snow falling over the whole field
                double a = R.nextDouble() * Math.PI * 2, rr = Math.sqrt(R.nextDouble()) * 12;
                Fx.vel(w, "i_flake", at.x + Math.cos(a) * rr, at.y + 5 + R.nextDouble() * 3, at.z + Math.sin(a) * rr, R.nextGaussian() * 0.03, -0.18, R.nextGaussian() * 0.03);
            }
            if (t % 4 == 0) Fx.circle(w, "i_ring", at.add(0, 0.15 + R.nextDouble() * 2.5, 0), 12, 70, R.nextDouble(), 0);
            if (t % 10 == 0) {
                Snd.play(w, at, SoundEvents.BLOCK_POWDER_SNOW_STEP, 1.5f, 0.5f);
                for (LivingEntity e : Ops.around(w, at, 12, p)) {
                    Ops.hurt(w, e, p, 3f * m);
                    freeze(e, 300, 4);
                    Fx.burst(w, "i_shard", Ops.center(e), 8, 0.2, 0.3);
                }
            }
            return true;
        });
        return true;
    }
}
