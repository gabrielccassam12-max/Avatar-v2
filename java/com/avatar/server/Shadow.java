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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/** SHADOW: darkness, fear, pulling and vanishing. Violet and near-black cubes. */
public final class Shadow {
    private Shadow() {}

    private static final Random R = Fx.R;

    private static void poof(ServerWorld w, Vec3d at, int n) {
        Fx.burst(w, "d_smoke", at, n, 0.25, 0.2);
        Fx.burst(w, "d_wisp", at, n, 0.35, 0.3);
        Fx.vel(w, "d_big", at, Vec3d.ZERO);
    }

    /** A dark thread between two points. */
    private static void thread(ServerWorld w, Vec3d a, Vec3d b) {
        Vec3d d = b.subtract(a);
        int n = Math.max(3, (int) (d.length() * 2));
        for (int i = 0; i <= n; i++) {
            Vec3d q = a.add(d.multiply((double) i / n)).add(Fx.jitter(0.08));
            Fx.vel(w, i % 3 == 0 ? "d_big" : "d_wisp", q, Vec3d.ZERO);
        }
    }

    // ------------------------------------------------------------------ 1. Shadow Bolt
    public static boolean bolt(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Snd.play(w, c.hand(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.6f);
        Snd.play(w, c.hand(), SoundEvents.ENTITY_PHANTOM_SWOOP, 0.7f, 1.4f);
        Fx.burst(w, "d_wisp", c.hand(), 8, 0.2, 0.1);
        Ops.projectile(w, p, c.hand(), c.look(), 1.4, 26, 0.7, false, (q, dir, t) -> {
            Fx.vel(w, "d_big", q, Vec3d.ZERO);
            Fx.vel(w, "d_wisp", q.add(Fx.jitter(0.15)), dir.multiply(-0.15));
            if (t % 2 == 0) Fx.vel(w, "d_smoke", q.add(Fx.jitter(0.2)), Vec3d.ZERO);
        }, (q, target) -> {
            Snd.play(w, q, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, 0.4f, 1.8f);
            poof(w, q, 20);
            Ops.blast(w, q, p, 2.0, 5f * m, 0.4, 0.2, e -> {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 50, 0));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
            });
            Ops.shake(w, q, 8, 2, 4);
        });
        return true;
    }

    // ------------------------------------------------------------------ 2. Tendrils
    public static boolean tendrils(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        LivingEntity aim = Ops.aimEntity(w, p, 16, 0.9);
        Vec3d at = aim != null ? aim.getPos() : Ops.aimPoint(w, p, 12);
        float m = c.mult();
        Snd.play(w, at, SoundEvents.ENTITY_WITHER_AMBIENT, 0.7f, 1.8f);
        Ops.ring(w, at.add(0, 0.1, 0), 3.4, 8, Element.SHADOW);
        Effects.add(t -> {
            if (t > 32 || !p.isAlive()) return false;
            for (int k = 0; k < 7; k++) {
                double a = k * Math.PI * 2 / 7 + t * 0.25, rr = 3.0 - (t % 12) * 0.12;
                double h = (t % 12) * 0.2;
                Fx.vel(w, "d_wisp", at.x + Math.cos(a) * rr, at.y + h, at.z + Math.sin(a) * rr, 0, 0.06, 0);
                Fx.vel(w, "d_big", at.x + Math.cos(a) * rr * 0.7, at.y + h * 0.7, at.z + Math.sin(a) * rr * 0.7, 0, 0.03, 0);
            }
            if (t % 4 == 0) {
                for (LivingEntity e : Ops.around(w, at.add(0, 1, 0), 3.4, p)) {
                    Vec3d to = at.subtract(e.getPos());
                    Ops.push(e, to, 0.28, 0.05);
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, 4));
                    if (t % 8 == 0) {
                        Ops.hurt(w, e, p, 2.2f * m);
                        thread(w, at.add(0, 0.1, 0), Ops.center(e));
                    }
                }
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 3. Shadow Step
    public static boolean step(Cast c, PState s) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d start = p.getPos();
        LivingEntity aim = Ops.aimEntity(w, p, 18, 0.85);
        Vec3d want;
        if (aim != null) {
            Vec3d back = Ops.flat(aim.getRotationVec(1f)).multiply(-1.6);
            want = aim.getPos().add(back);
        } else {
            want = start.add(Ops.flat(c.look()).multiply(8));
        }
        Vec3d dest = Ops.walkFree(w, start, want);
        if (dest.distanceTo(start) < 1.5) return false;
        Snd.play(w, start, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);
        poof(w, start.add(0, 1, 0), 30);
        thread(w, start.add(0, 1, 0), dest.add(0, 1, 0));
        p.requestTeleport(dest.x, dest.y, dest.z);
        p.fallDistance = 0;
        s.shadowUntil = w.getTime() + 50;
        Snd.play(w, dest, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        poof(w, dest.add(0, 1, 0), 30);
        if (aim != null && aim.isAlive() && aim.getPos().distanceTo(dest) < 4) {
            Ops.hurt(w, aim, p, 7f * c.mult());
            aim.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
            Fx.burst(w, "d_big", Ops.center(aim), 6, 0.3, 0.3);
        }
        Ops.shake(w, dest, 6, 2, 4);
        return true;
    }

    // ------------------------------------------------------------------ 4. Shadow Cloak
    public static boolean cloak(Cast c, PState s) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        long until = w.getTime() + 160;
        s.shadowUntil = until;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 160, 0, true, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 160, 1, true, false, false));
        Snd.play(w, c.pos(), SoundEvents.ENTITY_PHANTOM_AMBIENT, 1.0f, 0.6f);
        Ops.ring(w, c.pos().add(0, 0.1, 0), 3, 8, Element.SHADOW);
        poof(w, c.pos().add(0, 1, 0), 50);
        Effects.add(t -> {
            if (w.getTime() >= until || !p.isAlive()) {
                if (p.isAlive()) poof(w, p.getPos().add(0, 1, 0), 24);
                return false;
            }
            Vec3d b = p.getPos();
            if (t % 2 == 0) Fx.vel(w, "d_smoke", b.add(R.nextGaussian() * 0.3, 0.1 + R.nextDouble() * 1.6, R.nextGaussian() * 0.3), new Vec3d(0, 0.02, 0));
            if (t % 5 == 0) Fx.vel(w, "d_wisp", b.add(R.nextGaussian() * 0.25, 0.3 + R.nextDouble() * 1.4, R.nextGaussian() * 0.25), Fx.jitter(0.03));
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 5. Void Orb
    public static boolean voidOrb(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Vec3d dir = c.look().normalize();
        Vec3d[] pos = {c.hand()};
        Snd.play(w, pos[0], SoundEvents.ENTITY_WITHER_SHOOT, 0.8f, 1.8f);
        Effects.add(t -> {
            if (!p.isAlive()) return false;
            Vec3d next = pos[0].add(dir.multiply(0.45));
            BlockPos bp = BlockPos.ofFloored(next);
            boolean blocked = !w.getBlockState(bp).getCollisionShape(w, bp).isEmpty();
            if (!blocked && t <= 34) pos[0] = next;
            Vec3d o = pos[0];
            Fx.sphere(w, "d_big", o, 0.8, 5, Vec3d.ZERO);
            Fx.sphere(w, "d_smoke", o, 1.1, 4, Vec3d.ZERO);
            for (int i = 0; i < 4; i++) {
                double a = t * 0.5 + i * Math.PI / 2;
                Fx.vel(w, "d_wisp", o.x + Math.cos(a) * 2.2, o.y + Math.sin(a * 1.3) * 0.6, o.z + Math.sin(a) * 2.2, 0, 0, 0);
            }
            if (t % 2 == 0) {
                for (LivingEntity e : Ops.around(w, o, 4.5, p)) {
                    Ops.push(e, o.subtract(e.getPos()), 0.3, 0.02);
                    if (t % 6 == 0) Ops.hurt(w, e, p, 1.4f * m);
                }
            }
            if (t < 34 && !blocked) return true;
            Snd.play(w, o, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
            poof(w, o, 60);
            Ops.ring(w, o.add(0, -0.5, 0), 5, 10, Element.SHADOW);
            Ops.blast(w, o, p, 5.0, 12f * m, 1.1, 0.5, e -> e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 1)));
            Ops.shake(w, o, 20, 6, 10);
            return false;
        });
        return true;
    }

    // ------------------------------------------------------------------ 6. Nightmare
    public static boolean nightmare(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d at = c.pos();
        Snd.play(w, at, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.6f);
        Snd.play(w, at, SoundEvents.ENTITY_GHAST_SCREAM, 1.2f, 0.5f);
        Ops.ring(w, at.add(0, 0.1, 0), 8.5, 14, Element.SHADOW);
        Effects.groundRing(w, at.add(0, 0.5, 0), 8.5, 18, "d_ring", "d_smoke");
        poof(w, at.add(0, 1, 0), 70);
        Ops.blast(w, at, p, 8.5, 5f * c.mult(), 1.0, 0.4, e -> {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 1));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0));
            Fx.burst(w, "d_big", Ops.center(e), 6, 0.2, 0.3);
        });
        Ops.shake(w, at, 20, 5, 12);
        return true;
    }

    // ------------------------------------------------------------------ 7. Eclipse
    public static boolean eclipse(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d at = c.pos();
        float m = c.mult();
        Snd.play(w, at, SoundEvents.ENTITY_WITHER_SPAWN, 1.5f, 1.5f);
        Snd.play(w, at, SoundEvents.BLOCK_BEACON_DEACTIVATE, 2.0f, 0.4f);
        Ops.ring(w, at.add(0, 0.1, 0), 10, 16, Element.SHADOW);
        Ops.shake(w, at, 24, 5, 16);
        Effects.add(t -> {
            if (t > 100 || !p.isAlive()) return false;
            Fx.sphere(w, "d_smoke", at.add(0, 0.5, 0), 10, 16, Vec3d.ZERO);
            Fx.sphere(w, "d_big", at.add(0, 0.5, 0), 10, 5, Vec3d.ZERO);
            if (t % 6 == 0) Fx.circle(w, "d_ring", at.add(0, 0.15, 0), 10, 70, R.nextDouble(), 0);
            if (t % 10 == 0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 30, 1, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 30, 1, true, false, false));
                for (LivingEntity e : Ops.around(w, at.add(0, 1, 0), 10, p)) {
                    Ops.hurt(w, e, p, 2f * m);
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 40, 1));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
                    thread(w, at.add(0, 1, 0), Ops.center(e));
                }
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ Secret: Shadow Clones
    public static boolean clones(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float m = c.mult();
        Snd.play(w, c.pos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.5f);
        Snd.play(w, c.pos(), SoundEvents.ENTITY_PHANTOM_AMBIENT, 1.5f, 0.5f);
        Ops.ring(w, c.pos().add(0, 0.1, 0), 4, 10, Element.SHADOW);
        poof(w, c.pos().add(0, 1, 0), 40);
        Effects.add(t -> {
            if (t > 160 || !p.isAlive()) return false;
            Vec3d b = p.getPos();
            for (int i = 0; i < 3; i++) {
                double a = t * 0.12 + i * Math.PI * 2 / 3;
                Vec3d cp = b.add(Math.cos(a) * 2.4, 0, Math.sin(a) * 2.4);
                Fx.vel(w, "d_big", cp.x, cp.y + 1.65, cp.z, 0, 0, 0);                    // head
                Fx.vel(w, "d_wisp", cp.x + R.nextGaussian() * 0.12, cp.y + 0.5 + R.nextDouble() * 0.9, cp.z + R.nextGaussian() * 0.12, 0, 0, 0);
                Fx.vel(w, "d_smoke", cp.x + R.nextGaussian() * 0.2, cp.y + 0.1 + R.nextDouble() * 1.2, cp.z + R.nextGaussian() * 0.2, 0, 0.02, 0);
                Fx.vel(w, "s_core", cp.x + 0.12, cp.y + 1.7, cp.z, 0, 0, 0);
                if ((t + i * 4) % 12 == 0) {
                    for (LivingEntity e : Ops.around(w, cp.add(0, 1, 0), 10, p)) {
                        thread(w, cp.add(0, 1, 0), Ops.center(e));
                        Ops.hurt(w, e, p, 4.5f * m);
                        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1));
                        Fx.burst(w, "d_wisp", Ops.center(e), 8, 0.25, 0.3);
                        break;
                    }
                }
            }
            return true;
        });
        return true;
    }
}
