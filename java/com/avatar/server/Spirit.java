package com.avatar.server;

import com.avatar.core.Element;
import com.avatar.particle.Fx;
import com.avatar.util.Effects;
import com.avatar.util.Snd;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** SPIRIT: souls, light and healing. White, mint and teal cubes. */
public final class Spirit {
    private Spirit() {}

    private static final Random R = Fx.R;

    private static void glow(ServerWorld w, Vec3d at, int n) {
        Fx.burst(w, "p_wisp", at, n, 0.35, 0.3);
        Fx.burst(w, "p_mote", at, n / 2, 0.25, 0.4);
        Fx.vel(w, "p_big", at, Vec3d.ZERO);
    }

    private static void beam(ServerWorld w, Vec3d a, Vec3d b, double jag) {
        Vec3d d = b.subtract(a);
        int n = Math.max(3, (int) (d.length() * 2));
        for (int i = 0; i <= n; i++) {
            Vec3d q = a.add(d.multiply((double) i / n)).add(Fx.jitter(jag));
            Fx.vel(w, i % 3 == 0 ? "p_wisp" : "p_mote", q, Vec3d.ZERO);
        }
    }

    // ------------------------------------------------------------------ 1. Spirit Bolt
    public static boolean bolt(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Snd.play(w, c.hand(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
        Snd.play(w, c.hand(), SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1.0f, 1.2f);
        Fx.burst(w, "p_wisp", c.hand(), 8, 0.2, 0.1);
        Ops.projectile(w, p, c.hand(), c.look(), 1.5, 26, 0.7, false, (q, dir, t) -> {
            Fx.vel(w, "p_big", q, Vec3d.ZERO);
            Fx.vel(w, "p_wisp", q.add(Fx.jitter(0.15)), dir.multiply(-0.15));
            Fx.vel(w, "p_mote", q.add(Fx.jitter(0.25)), Fx.jitter(0.02));
        }, (q, target) -> {
            Snd.play(w, q, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.4f);
            glow(w, q, 22);
            Ops.blast(w, q, p, 2.0, 5.5f * m, 0.4, 0.2, e -> e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0)));
            Ops.shake(w, q, 8, 2, 4);
        });
        return true;
    }

    // ------------------------------------------------------------------ 2. Mending Light
    public static boolean mending(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.8f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.0f);
        float amount = 8f * Math.min(1.6f, c.mult());
        Water.healBurst(w, p, amount, true);
        for (ServerPlayerEntity o : w.getPlayers(q -> q != p && q.squaredDistanceTo(p) < 49)) Water.healBurst(w, o, amount * 0.8f, true);
        Ops.ring(w, c.pos().add(0, 0.1, 0), 6, 12, Element.SPIRIT);
        Effects.add(t -> {
            if (t > 30 || !p.isAlive()) return false;
            Vec3d b = p.getPos();
            for (int k = 0; k < 4; k++) {
                double a = t * 0.45 + k * Math.PI / 2, y = ((t * 3 + k * 8) % 36) / 36.0 * 2.2;
                Fx.vel(w, "p_wisp", b.x + Math.cos(a) * 0.9, b.y + y, b.z + Math.sin(a) * 0.9, 0, 0.03, 0);
                Fx.vel(w, "p_mote", b.x + Math.cos(a + Math.PI) * 0.6, b.y + y, b.z + Math.sin(a + Math.PI) * 0.6, 0, 0.04, 0);
            }
            if (t % 8 == 1) Fx.circle(w, "p_ring", b.add(0, 0.1, 0), 0.6 + t * 0.15, 24, 0, 0);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 3. Guardian Spirit
    public static boolean guardian(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 240, 1));
        Snd.play(w, c.pos(), SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, 1.5f, 0.8f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);
        Ops.ring(w, c.pos().add(0, 0.1, 0), 3.5, 8, Element.SPIRIT);
        glow(w, c.pos().add(0, 1.5, 0), 30);
        Effects.add(t -> {
            if (t > 240 || !p.isAlive()) {
                if (p.isAlive()) glow(w, p.getPos().add(0, 1.8, 0), 20);
                return false;
            }
            Vec3d b = p.getPos();
            double a = t * 0.1;
            Vec3d sp = b.add(Math.cos(a) * 1.9, 1.5 + Math.sin(t * 0.07) * 0.4, Math.sin(a) * 1.9);
            Fx.sphere(w, "p_big", sp, 0.35, 3, Vec3d.ZERO);                       // the spirit: a glowing head and a trailing tail
            Fx.vel(w, "p_wisp", sp.add(Fx.jitter(0.15)), new Vec3d(Math.sin(a) * 0.05, -0.01, -Math.cos(a) * 0.05));
            Fx.vel(w, "p_mote", sp.add(Fx.jitter(0.3)), Fx.jitter(0.02));
            Fx.vel(w, "s_core", sp.add(0.12, 0.05, 0), Vec3d.ZERO);
            if (t % 12 == 0) {
                for (LivingEntity e : Ops.around(w, b.add(0, 1, 0), 5, p)) {
                    beam(w, sp, Ops.center(e), 0.1);
                    Ops.hurt(w, e, p, 4f * m);
                    Fx.burst(w, "p_wisp", Ops.center(e), 8, 0.25, 0.3);
                    break;
                }
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 4. Ethereal Form
    public static boolean ethereal(Cast c, PState s) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        long until = w.getTime() + 70;
        s.etherealUntil = until;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 70, 2, true, false, false));
        Snd.play(w, c.pos(), SoundEvents.ENTITY_ALLAY_DEATH, 1.2f, 0.9f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0f, 2.0f);
        glow(w, c.pos().add(0, 1, 0), 40);
        Effects.add(t -> {
            if (w.getTime() >= until || !p.isAlive()) {
                if (p.isAlive()) glow(w, p.getPos().add(0, 1, 0), 20);
                return false;
            }
            Vec3d b = p.getPos();
            for (int i = 0; i < 6; i++) Fx.vel(w, i % 2 == 0 ? "p_wisp" : "p_mote", b.add(R.nextGaussian() * 0.35, 0.1 + R.nextDouble() * 1.8, R.nextGaussian() * 0.35), new Vec3d(0, 0.03, 0));
            if (t % 4 == 0) Fx.vel(w, "p_big", b.add(R.nextGaussian() * 0.25, 0.4 + R.nextDouble() * 1.2, R.nextGaussian() * 0.25), Vec3d.ZERO);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 5. Soul Drain
    public static boolean drain(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        LivingEntity target = Ops.aimEntity(w, p, 16, 0.9);
        if (target == null) return false;
        float m = c.mult();
        Snd.play(w, target.getPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 1.8f);
        Effects.add(t -> {
            if (t > 30 || !p.isAlive() || !target.isAlive() || target.squaredDistanceTo(p) > 400) return false;
            Vec3d from = Ops.center(target), to = p.getEyePos().add(0, -0.5, 0);
            Vec3d d = to.subtract(from);
            int n = Math.max(4, (int) (d.length() * 2));
            for (int i = 0; i < n; i++) {                                           // souls flow from the victim to you
                double k = ((i + t * 0.6) % n) / n;
                Fx.vel(w, i % 3 == 0 ? "p_big" : "p_wisp", from.add(d.multiply(k)).add(Fx.jitter(0.06)), Vec3d.ZERO);
            }
            Fx.vel(w, "p_mote", from.add(Fx.jitter(0.4)), new Vec3d(0, 0.05, 0));
            if (t % 5 == 0) {
                Ops.hurt(w, target, p, 2.4f * m);
                p.heal(1.4f);
                Snd.play(w, from, SoundEvents.ENTITY_ALLAY_HURT, 0.6f, 1.6f);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 6. Wraith Wave
    public static boolean wraith(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Vec3d look = c.look().normalize(), start = c.eye().add(0, -0.3, 0), side = Ops.rightOf(look);
        Snd.play(w, start, SoundEvents.ENTITY_GHAST_SCREAM, 0.8f, 1.6f);
        Snd.play(w, start, SoundEvents.ENTITY_ALLAY_DEATH, 1.5f, 0.6f);
        Set<Integer> hit = new HashSet<>();
        Effects.add(t -> {
            if (t > 14 || !p.isAlive()) return false;
            Vec3d f = start.add(look.multiply(1.0 + t * 1.1));
            for (int i = -3; i <= 3; i++) {
                Vec3d q = f.add(side.multiply(i * 0.55)).add(0, Math.sin(t * 0.7 + i) * 0.4, 0);
                Fx.vel(w, i % 2 == 0 ? "p_big" : "p_wisp", q, look.multiply(0.2));
                Fx.vel(w, "p_wisp", q.add(0, 0.5, 0), look.multiply(0.1));
                Fx.vel(w, "p_mote", q.add(Fx.jitter(0.3)), look.multiply(0.15));
            }
            for (LivingEntity e : Ops.around(w, f, 2.6, p)) {
                if (!hit.add(e.getId())) continue;
                Ops.hurt(w, e, p, 8f * m);
                Ops.push(e, look, 0.8, 0.45);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 25, 0));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 1));
                glow(w, Ops.center(e), 12);
            }
            return true;
        });
        Ops.shake(w, c.pos(), 10, 3, 8);
        return true;
    }

    // ------------------------------------------------------------------ 7. Ascension
    public static boolean ascension(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Vec3d ctr = c.pos();
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 22, 2, true, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 160, 0, true, false, false));
        Snd.play(w, ctr, SoundEvents.BLOCK_BEACON_ACTIVATE, 2.0f, 1.6f);
        Snd.play(w, ctr, SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 2.0f, 0.5f);
        Ops.ring(w, ctr.add(0, 0.1, 0), 9, 24, Element.SPIRIT);
        Effects.add(t -> {
            if (t > 40 || !p.isAlive()) return false;
            for (int i = 0; i < 12; i++) {                                           // pillar of light
                double a = R.nextDouble() * Math.PI * 2, rr = R.nextDouble() * 1.4;
                Fx.vel(w, i % 3 == 0 ? "s_core" : "p_wisp", ctr.x + Math.cos(a) * rr, ctr.y + R.nextDouble() * 4, ctr.z + Math.sin(a) * rr, 0, 0.5, 0);
            }
            Fx.circle(w, "p_ring", ctr.add(0, 0.2 + (t % 10) * 0.4, 0), 1.6, 18, t * 0.3, 0.1);
            if (t == 30) {
                Vec3d b = p.getPos();
                Snd.play(w, b, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);
                Snd.play(w, b, SoundEvents.BLOCK_BEACON_POWER_SELECT, 2.0f, 2.0f);
                glow(w, b.add(0, 1, 0), 80);
                Effects.groundRing(w, ctr.add(0, 0.3, 0), 9, 16, "p_ring", "p_mote");
                Ops.blast(w, b, p, 9, 14f * m, 1.2, 0.6, e -> e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 120, 0)));
                for (ServerPlayerEntity o : w.getPlayers(q -> q != p && q.squaredDistanceTo(p) < 81)) Water.healBurst(w, o, 10f, true);
                Water.healBurst(w, p, 10f, true);
                Ops.shake(w, b, 30, 7, 12);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ Secret: Spirit Awakening
    public static boolean awakening(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Vec3d b = c.pos();
        Snd.play(w, b, SoundEvents.BLOCK_BEACON_POWER_SELECT, 2.0f, 2.0f);
        Snd.play(w, b, SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, 2.0f, 0.6f);
        Snd.play(w, b, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.8f);
        p.setHealth(p.getMaxHealth());
        Water.healBurst(w, p, 4f, true);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 300, 1));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 300, 1));
        for (ServerPlayerEntity o : w.getPlayers(q -> q != p && q.squaredDistanceTo(p) < 144)) {
            Water.healBurst(w, o, 14f, true);
            o.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
            o.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0));
        }
        Ops.ring(w, b.add(0, 0.1, 0), 12, 20, Element.SPIRIT);
        Effects.groundRing(w, b.add(0, 0.4, 0), 12, 24, "p_ring", "p_mote");
        glow(w, b.add(0, 1, 0), 120);
        Ops.blast(w, b, p, 12, 10f * m, 1.4, 0.6, e -> {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 160, 1));
        });
        Effects.add(t -> {
            if (t > 40 || !p.isAlive()) return false;
            Vec3d q = p.getPos();
            for (int i = 0; i < 8; i++) {
                double a = R.nextDouble() * Math.PI * 2, rr = 0.4 + R.nextDouble() * 1.2;
                Fx.vel(w, i % 2 == 0 ? "p_wisp" : "s_gold", q.x + Math.cos(a) * rr, q.y + R.nextDouble() * 0.3, q.z + Math.sin(a) * rr, 0, 0.25 + R.nextDouble() * 0.3, 0);
            }
            return true;
        });
        Ops.shake(w, b, 30, 7, 14);
        return true;
    }
}
