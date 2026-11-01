package com.avatar.server;

import com.avatar.core.Element;
import com.avatar.particle.Fx;
import com.avatar.util.Effects;
import com.avatar.util.Scheduler;
import com.avatar.util.Snd;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** THUNDER: fast, electric, stuns. Yellow-white cubes and jagged arcs. */
public final class Thunder {
    private Thunder() {}

    private static final Random R = Fx.R;

    /** A jagged electric arc between two points. */
    public static void path(ServerWorld w, Vec3d a, Vec3d b, double jag) {
        Vec3d d = b.subtract(a);
        int seg = Math.max(3, (int) (d.length() * 1.4));
        Vec3d prev = a;
        for (int i = 1; i <= seg; i++) {
            Vec3d q = a.add(d.multiply((double) i / seg));
            if (i < seg) q = q.add(Fx.jitter(jag));
            for (int k = 0; k < 3; k++) Fx.vel(w, "t_arc", prev.add(q.subtract(prev).multiply(k / 3.0)), Vec3d.ZERO);
            if (i % 3 == 0) Fx.vel(w, "t_spark", q, Fx.jitter(0.04));
            prev = q;
        }
    }

    /** Shock: damage + stun (heavy slowness and weakness). */
    private static void zap(ServerWorld w, ServerPlayerEntity owner, LivingEntity e, float dmg, int stun) {
        Ops.hurt(w, e, owner, dmg);
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, stun, 3));
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, stun + 20, 0));
        Fx.burst(w, "t_spark", Ops.center(e), 12, 0.3, 0.3);
    }

    private static void impact(ServerWorld w, ServerPlayerEntity owner, Vec3d at, double radius, float dmg, int shake) {
        Snd.play(w, at, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9f, 1.7f);
        Fx.burst(w, "t_spark", at, 24, 0.4, 0.2);
        Fx.burst(w, "t_arc", at, 8, 0.3, 0.2);
        Fx.vel(w, "t_big", at, Vec3d.ZERO);
        Ops.blast(w, at, owner, radius, dmg, 0.5, 0.2, e -> e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 2)));
        Ops.shake(w, at, 10, shake, 5);
    }

    // ------------------------------------------------------------------ 1. Spark Shot
    public static boolean spark(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Snd.play(w, c.hand(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 2.0f);
        Snd.play(w, c.hand(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.8f);
        Fx.burst(w, "t_spark", c.hand(), 8, 0.2, 0.1);
        Ops.projectile(w, p, c.hand(), c.look(), 1.7, 22, 0.7, false, (q, dir, t) -> {
            Fx.vel(w, "t_arc", q.add(Fx.jitter(0.12)), Vec3d.ZERO);
            Fx.vel(w, "t_big", q, Vec3d.ZERO);
            if (t % 2 == 0) Fx.vel(w, "t_spark", q.add(Fx.jitter(0.2)), Fx.jitter(0.05));
        }, (q, target) -> impact(w, p, q, 2.0, 5.5f * m, 2));
        return true;
    }

    // ------------------------------------------------------------------ 2. Arc Slash: short electric cone
    public static boolean arc(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d look = c.look(), from = c.hand();
        Snd.play(w, from, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.5f);
        Snd.play(w, from, SoundEvents.ITEM_TRIDENT_THUNDER, 0.5f, 1.8f);
        for (int i = 0; i < 4; i++) {
            Vec3d end = c.eye().add(look.add(Fx.jitter(0.22)).normalize().multiply(7 + R.nextDouble() * 2));
            path(w, from, end, 0.3);
        }
        int n = 0;
        for (LivingEntity e : Ops.around(w, c.eye().add(look.multiply(4)), 5.2, p)) {
            Vec3d to = Ops.center(e).subtract(c.eye());
            if (to.length() > 9 || to.normalize().dotProduct(look) < 0.55) continue;
            path(w, from, Ops.center(e), 0.25);
            zap(w, p, e, 7f * c.mult(), 40);
            Ops.push(e, look, 0.5, 0.1);
            n++;
        }
        if (n > 0) Ops.shake(w, from, 8, 2, 4);
        return true;
    }

    // ------------------------------------------------------------------ 3. Static Field
    public static boolean field(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);
        Snd.play(w, c.pos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.2f);
        Ops.ring(w, c.pos().add(0, 0.1, 0), 4.5, 8, Element.THUNDER);
        Effects.add(t -> {
            if (t > 100 || !p.isAlive()) return false;
            Vec3d ctr = p.getPos();
            for (int i = 0; i < 6; i++) {
                double a = R.nextDouble() * Math.PI * 2;
                Fx.vel(w, "t_spark", ctr.x + Math.cos(a) * 4.5, ctr.y + 0.1 + R.nextDouble() * 1.5, ctr.z + Math.sin(a) * 4.5, 0, 0.04, 0);
            }
            if (t % 5 == 0) {
                double a = R.nextDouble() * Math.PI * 2, rr = 2 + R.nextDouble() * 2.5;
                path(w, ctr.add(0, 1, 0), ctr.add(Math.cos(a) * rr, 0.2 + R.nextDouble(), Math.sin(a) * rr), 0.25);
            }
            if (t % 10 == 0) {
                Fx.circle(w, "t_ring", ctr.add(0, 0.1, 0), 4.5, 40, R.nextDouble(), 0);
                for (LivingEntity e : Ops.around(w, ctr, 4.5, p)) {
                    path(w, ctr.add(0, 1, 0), Ops.center(e), 0.2);
                    zap(w, p, e, 3f * m, 25);
                }
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 4. Blink Strike: teleport leaving a bolt
    public static boolean blink(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d start = p.getPos(), dir = Ops.flat(c.look());
        Vec3d end = Ops.walkFree(w, start, start.add(dir.multiply(11)));
        if (end.distanceTo(start) < 1.5) return false;
        Snd.play(w, start, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 2.0f);
        path(w, start.add(0, 1, 0), end.add(0, 1, 0), 0.4);
        path(w, start.add(0, 0.4, 0), end.add(0, 0.4, 0), 0.3);
        Fx.burst(w, "t_big", start.add(0, 1, 0), 4, 0.1, 0.1);
        Fx.burst(w, "t_spark", start.add(0, 1, 0), 30, 0.5, 0.2);
        Set<Integer> hit = new HashSet<>();
        int steps = (int) Math.ceil(start.distanceTo(end));
        for (int i = 0; i <= steps; i++) {
            Vec3d q = start.add(end.subtract(start).multiply((double) i / Math.max(1, steps))).add(0, 1, 0);
            for (LivingEntity e : Ops.around(w, q, 1.8, p)) if (hit.add(e.getId())) zap(w, p, e, 6f * c.mult(), 40);
        }
        p.requestTeleport(end.x, end.y, end.z);
        p.fallDistance = 0;
        Fx.burst(w, "t_spark", end.add(0, 1, 0), 30, 0.5, 0.2);
        Fx.burst(w, "t_big", end.add(0, 1, 0), 4, 0.1, 0.1);
        Snd.play(w, end, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.8f);
        Ops.shake(w, end, 6, 2, 4);
        return true;
    }

    // ------------------------------------------------------------------ 5. Thunder Clap
    public static boolean clap(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d at = c.pos();
        Snd.play(w, at, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.0f);
        Snd.play(w, at, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
        Fire.flash(w, at);
        Ops.ring(w, at.add(0, 0.1, 0), 6.5, 10, Element.THUNDER);
        Effects.groundRing(w, at.add(0, 0.5, 0), 6.5, 14, "t_ring", "t_arc");
        Fx.burst(w, "t_spark", at.add(0, 1, 0), 70, 0.7, 0.2);
        Fx.burst(w, "t_big", at.add(0, 1, 0), 8, 0.3, 0.1);
        for (int i = 0; i < 5; i++) path(w, at.add(0, 1, 0), at.add(Fx.jitter(5)).add(0, 0.2, 0), 0.4);
        Ops.blast(w, at, p, 6.5, 9f * c.mult(), 1.3, 0.5, e -> {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 2));
        });
        Ops.shake(w, at, 24, 8, 12);
        return true;
    }

    // ------------------------------------------------------------------ 6. Chain Storm
    public static boolean chain(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        LivingEntity first = Ops.aimEntity(w, p, 18, 0.92);
        if (first == null) {
            double best = 1e9;
            for (LivingEntity e : Ops.around(w, c.eye(), 14, p)) {
                double d = e.squaredDistanceTo(p);
                if (d < best) {
                    best = d;
                    first = e;
                }
            }
        }
        if (first == null) return false;
        List<LivingEntity> order = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        LivingEntity cur = first;
        while (cur != null && order.size() < 8) {
            order.add(cur);
            seen.add(cur.getId());
            LivingEntity next = null;
            double best = 81;
            for (LivingEntity e : Ops.around(w, Ops.center(cur), 9, p)) {
                if (seen.contains(e.getId())) continue;
                double d = Ops.center(e).squaredDistanceTo(Ops.center(cur));
                if (d < best) {
                    best = d;
                    next = e;
                }
            }
            cur = next;
        }
        Snd.play(w, c.pos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.6f);
        Vec3d[] prev = {c.hand()};
        for (int i = 0; i < order.size(); i++) {
            LivingEntity e = order.get(i);
            float dmg = 8f * c.mult() * (float) Math.pow(0.92, i);
            Scheduler.later(i * 2, () -> {
                if (!e.isAlive()) return;
                Vec3d q = Ops.center(e);
                path(w, prev[0], q, 0.3);
                path(w, prev[0], q.add(Fx.jitter(0.5)), 0.45);
                zap(w, p, e, dmg, 50);
                Snd.play(w, q, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.9f);
                prev[0] = q;
            });
        }
        Ops.shake(w, c.pos(), 10, 3, 6);
        return true;
    }

    // ------------------------------------------------------------------ 7. Sky Judgment
    public static boolean judgment(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d ctr = Ops.aimPoint(w, p, 28);
        float m = c.mult();
        Snd.play(w, ctr, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.7f);
        Ops.ring(w, ctr.add(0, 0.1, 0), 7.5, 20, Element.THUNDER);
        Effects.groundRing(w, ctr.add(0, 0.2, 0), 7.5, 20, "t_ring", "t_arc");
        Effects.add(t -> {
            if (t > 42 || !p.isAlive()) return false;
            if (t % 14 == 1) Fx.circle(w, "t_ring", ctr.add(0, 0.15, 0), 7.5, 60, R.nextDouble(), 0);
            if (t % 3 == 0) {
                double a = R.nextDouble() * Math.PI * 2, rr = R.nextDouble() * 7;
                Vec3d spot = ctr.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
                Vec3d ground = Vec3d.ofBottomCenter(Ops.groundPos(w, spot));
                path(w, ground.add(R.nextGaussian() * 2, 22, R.nextGaussian() * 2), ground, 0.8);
                if (t % 6 == 0) Fire.flash(w, ground);
                impact(w, p, ground.add(0, 0.3, 0), 2.6, 9f * m, 4);
                Snd.play(w, ground, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f + R.nextFloat() * 0.5f);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ Secret: Overload
    public static boolean overload(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Snd.play(w, c.pos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.4f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.5f, 2.0f);
        Ops.ring(w, c.pos().add(0, 0.1, 0), 5, 10, Element.THUNDER);
        Ops.shake(w, c.pos(), 16, 5, 10);
        Effects.add(t -> {
            if (t > 100 || !p.isAlive()) return false;
            if (t % 10 == 1) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 30, 3, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 30, 1, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 30, 1, true, false, false));
            }
            p.fallDistance = 0;
            Vec3d b = p.getPos();
            for (int i = 0; i < 5; i++) Fx.vel(w, "t_spark", b.add(R.nextGaussian() * 0.4, 0.1 + R.nextDouble() * 1.8, R.nextGaussian() * 0.4), Fx.jitter(0.06));
            if (t % 3 == 0) path(w, b.add(0, 0.2 + R.nextDouble() * 1.5, 0), b.add(Fx.jitter(1.6)).add(0, 1, 0), 0.25);
            if (t % 4 == 0) {
                for (LivingEntity e : Ops.around(w, b.add(0, 1, 0), 6, p)) {
                    path(w, b.add(0, 1.2, 0), Ops.center(e), 0.25);
                    zap(w, p, e, 3.5f * m, 20);
                    break;
                }
            }
            return true;
        });
        return true;
    }
}
