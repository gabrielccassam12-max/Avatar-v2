package com.avatar.server;

import com.avatar.core.Element;
import com.avatar.particle.Fx;
import com.avatar.util.Effects;
import com.avatar.util.Snd;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

/** AIR: agile, pushes and pulls, never stands still. */
public final class Air {
    private Air() {}

    private static final java.util.Random R = Fx.R;

    // ------------------------------------------------------------------ 1. Air Punch
    public static boolean punch(Cast c) {
        ServerWorld w = c.w();
        Vec3d look = c.look();
        Snd.play(w, c.eye(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.8f);
        Snd.play(w, c.eye(), SoundEvents.ENTITY_PHANTOM_FLAP, 0.7f, 1.8f);
        Vec3d right = Ops.rightOf(look), up = right.crossProduct(look);
        Ops.projectile(w, c.p(), c.eye().add(look.multiply(1.2)).add(0, -0.3, 0), look, 1.7, 14, 1.1, true,
                (pos, d, t) -> {
                    for (int i = 0; i < 10; i++) {
                        double a = i * 0.628 + t * 0.6;
                        Fx.vel(w, "a_wind", pos.add(right.multiply(Math.cos(a) * 0.45)).add(up.multiply(Math.sin(a) * 0.45)), d.multiply(-0.3));
                    }
                    Fx.vel(w, "a_streak", pos, d.multiply(-0.2));
                    Fx.vel(w, "a_big", pos, Vec3d.ZERO);
                },
                (pos, target) -> {
                    Ops.blast(w, pos, c.p(), 2.4, 5f * c.mult(), 1.6, 0.35, null);
                    Fx.burst(w, "a_wind", pos, 30, 0.45, 0.2);
                    Fx.circle(w, "a_ring", pos, 0.9, 20, 0, 0);
                    Snd.play(w, pos, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.2f);
                });
        return true;
    }

    // ------------------------------------------------------------------ 2. Air Blast: cone shockwave, also swats projectiles
    public static boolean blast(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d eye = c.eye(), look = c.look(), right = Ops.rightOf(look), up = right.crossProduct(look);
        Snd.play(w, eye, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 1.4f);
        Snd.play(w, eye, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
        Ops.shake(w, eye, 10, 3, 6);
        Effects.add(t -> {                                              // expanding cone of rings
            if (t > 7) return false;
            double d = 1.2 + t * 1.0, rad = 0.4 + t * 0.55;
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                Vec3d q = eye.add(look.multiply(d)).add(right.multiply(Math.cos(a) * rad)).add(up.multiply(Math.sin(a) * rad));
                Fx.vel(w, "a_wind", q, look.multiply(0.3));
            }
            Fx.vel(w, "a_big", eye.add(look.multiply(d)), look.multiply(0.3));
            return true;
        });
        for (LivingEntity e : Ops.targets(w, new Box(eye, eye.add(look.multiply(8))).expand(5), p)) {
            Vec3d to = Ops.center(e).subtract(eye);
            double d = to.length();
            if (d > 8 || d < 0.2 || to.normalize().dotProduct(look) < 0.55) continue;
            Ops.hurt(w, e, p, 3f * c.mult());
            Ops.push(e, look, 1.9 * (1.0 - d / 12), 0.55);
        }
        for (Entity e : w.getOtherEntities(p, new Box(eye, eye.add(look.multiply(8))).expand(4), q -> q instanceof ProjectileEntity)) {
            e.setVelocity(look.multiply(1.6));
            e.velocityModified = true;
        }
        return true;
    }

    // ------------------------------------------------------------------ 3. Air Scooter (sphere of wind to ride)
    public static boolean scooter(Cast c, PState s) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Snd.play(w, c.pos(), SoundEvents.ENTITY_PHANTOM_FLAP, 1.0f, 1.2f);
        Effects.Handle h = new Effects.Handle();
        if (s.sustained != null) s.sustained.dead = true;
        s.sustained = h;
        Effects.add(t -> {
            if (h.dead || t > 140 || !p.isAlive() || p.isRemoved() || p.isSneaking()) {
                if (p.isAlive()) {
                    p.fallDistance = 0;
                    Fx.burst(w, "a_wind", p.getPos().add(0, 0.5, 0), 20, 0.3, 0.2);
                }
                h.dead = true;
                return false;
            }
            Vec3d look = p.getRotationVec(1f);
            Vec3d flat = new Vec3d(look.x, 0, look.z);
            flat = flat.lengthSquared() < 1e-4 ? Vec3d.ZERO : flat.normalize();
            double vy = p.getVelocity().y;
            if (p.horizontalCollision) vy = 0.45;                         // hops over steps
            p.setVelocity(flat.x * 0.72, p.isOnGround() ? Math.max(vy, -0.05) : Math.max(vy, -0.35), flat.z * 0.72);
            p.velocityModified = true;
            p.fallDistance = 0;
            Vec3d ctr = p.getPos().add(0, 0.35, 0);
            for (int i = 0; i < 14; i++) {                                  // the spinning ball under the feet
                double u = R.nextDouble() * 2 - 1, a = t * 0.9 + R.nextDouble() * 6.28, sq = Math.sqrt(1 - u * u);
                Fx.vel(w, i % 3 == 0 ? "a_big" : "a_wind", ctr.x + 0.7 * sq * Math.cos(a), ctr.y + 0.55 * u, ctr.z + 0.7 * sq * Math.sin(a), 0, 0, 0);
            }
            Fx.vel(w, "a_streak", ctr.subtract(flat.multiply(0.5)), flat.multiply(-0.3));
            if (t % 10 == 1) Snd.play(w, ctr, SoundEvents.ENTITY_PHANTOM_FLAP, 0.4f, 1.6f);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 4. Tornado
    /** Generic funnel: part = visible particle, hot = fire tornado, storm = lightning inside. Used by combos too. */
    public static void tornadoAt(ServerWorld w, ServerPlayerEntity owner, Vec3d base, Vec3d drift, int ticks, float dmg, String part, String part2, boolean fire, boolean storm) {
        Vec3d[] pos = {base};
        Snd.play(w, base, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.5f);
        Effects.add(t -> {
            if (t > ticks || !owner.isAlive()) {
                Fx.burst(w, part, pos[0].add(0, 2, 0), 30, 0.5, 0.3);
                return false;
            }
            Vec3d b = pos[0] = pos[0].add(drift);
            double height = 7.5;
            for (int i = 0; i < 26; i++) {                                   // spiral funnel, wider at the top
                double hh = R.nextDouble() * height, rad = 0.5 + hh * 0.38, a = t * 0.55 + hh * 1.3 + R.nextDouble() * 0.6;
                Fx.vel(w, R.nextInt(4) == 0 ? part2 : part, b.x + Math.cos(a) * rad, b.y + hh, b.z + Math.sin(a) * rad, -Math.sin(a) * 0.12, 0.02, Math.cos(a) * 0.12);
            }
            for (int i = 0; i < 3; i++) Fx.vel(w, "e_dust", b.x + R.nextGaussian() * 0.8, b.y + 0.1, b.z + R.nextGaussian() * 0.8, 0, 0.12, 0);
            if (t % 12 == 1) Snd.play(w, b, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.4f, 0.5f);
            for (LivingEntity e : Ops.targets(w, new Box(b.x - 4.5, b.y - 1, b.z - 4.5, b.x + 4.5, b.y + height, b.z + 4.5), owner)) {
                Vec3d rel = e.getPos().subtract(b);
                double hd = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
                if (hd > 4.2) continue;
                Vec3d tang = new Vec3d(-rel.z, 0, rel.x).normalize().multiply(0.35);
                Vec3d in = hd > 0.4 ? new Vec3d(-rel.x, 0, -rel.z).normalize().multiply(0.12) : Vec3d.ZERO;
                double lift = e.getY() - b.y < 6 ? 0.2 : 0.0;
                e.setVelocity(tang.x + in.x, Math.max(e.getVelocity().y * 0.5, lift), tang.z + in.z);
                e.velocityModified = true;
                e.fallDistance = 0;
                if (t % 10 == 0) {
                    Ops.hurt(w, e, owner, dmg);
                    if (fire) e.setOnFireFor(4);
                }
            }
            if (storm && t % 9 == 0) {
                Vec3d s = b.add(R.nextGaussian() * 2, 0, R.nextGaussian() * 2);
                Fire.boltPath(w, s.add(0, height + 3, 0), s, 0.5);
                Fire.flash(w, s);
                Ops.blast(w, s, owner, 2.5, dmg * 1.5f, 0.6, 0.3, null);
            }
            return true;
        });
    }

    public static boolean tornado(Cast c) {
        Vec3d at = Ops.aimPoint(c.w(), c.p(), 14.0);
        Vec3d look = c.look();
        Vec3d drift = new Vec3d(look.x, 0, look.z).lengthSquared() < 1e-4 ? Vec3d.ZERO : new Vec3d(look.x, 0, look.z).normalize().multiply(0.08);
        tornadoAt(c.w(), c.p(), at, drift, 90, 2.0f * c.mult(), "a_wind", "a_big", false, false);
        return true;
    }

    // ------------------------------------------------------------------ 5. Air Shield (sphere that deflects projectiles)
    public static boolean shield(Cast c, PState s) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        s.airShieldUntil = w.getTime() + 120;
        Snd.play(w, c.pos(), SoundEvents.ENTITY_PHANTOM_FLAP, 1.0f, 0.7f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 0.5f, 2.0f);
        Effects.add(t -> {
            if (t > 120 || !p.isAlive() || p.isRemoved() || w.getTime() > s.airShieldUntil) return false;
            Vec3d ctr = p.getPos().add(0, 1.0, 0);
            for (int i = 0; i < 16; i++) {
                double u = R.nextDouble() * 2 - 1, a = t * 0.5 + R.nextDouble() * 6.28, sq = Math.sqrt(1 - u * u);
                Fx.vel(w, i % 4 == 0 ? "a_big" : "a_wind", ctr.x + 2.2 * sq * Math.cos(a), ctr.y + 2.2 * u, ctr.z + 2.2 * sq * Math.sin(a), -Math.sin(a) * 0.1, 0, Math.cos(a) * 0.1);
            }
            for (Entity e : w.getOtherEntities(p, new Box(ctr, ctr).expand(3.0), q -> q instanceof ProjectileEntity)) {
                Vec3d out = e.getPos().subtract(ctr).normalize().multiply(1.1);          // projectiles bounce away
                e.setVelocity(out);
                e.velocityModified = true;
                Fx.burst(w, "a_wind", e.getPos(), 8, 0.2, 0.2);
            }
            for (LivingEntity e : Ops.around(w, ctr, 2.6, p)) {
                Ops.blow(e, ctr, 0.5, 0.12);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 6. Air Dash
    public static boolean dash(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d look = c.look();
        Vec3d dir = new Vec3d(look.x, Math.max(-0.2, Math.min(0.35, look.y * 0.5 + 0.05)), look.z).normalize();
        Snd.play(w, c.pos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 1.8f);
        Snd.play(w, c.pos(), SoundEvents.ITEM_ELYTRA_FLYING, 0.6f, 1.4f);
        Fx.circle(w, "a_ring", c.pos().add(0, 0.2, 0), 0.8, 18, 0, 0);
        Set<Integer> hit = new HashSet<>();
        Effects.add(t -> {
            if (t > 7 || !p.isAlive()) {
                if (p.isAlive()) p.setVelocity(p.getVelocity().multiply(0.4));
                return false;
            }
            p.setVelocity(dir.multiply(1.55));
            p.velocityModified = true;
            p.fallDistance = 0;
            Vec3d b = p.getPos().add(0, 0.9, 0);
            for (int i = 0; i < 12; i++) Fx.vel(w, i % 3 == 0 ? "a_streak" : "a_wind", b.add(Fx.jitter(0.35)), dir.multiply(-0.35));
            Fx.vel(w, "a_big", b, dir.multiply(-0.1));
            for (LivingEntity e : Ops.around(w, b, 1.6, p)) {
                if (!hit.add(e.getId())) continue;
                Ops.hurt(w, e, p, 3f * c.mult());
                Ops.push(e, dir, 0.9, 0.3);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 7. Flight (toggle)
    public static boolean flight(Cast c, PState s) {
        ServerPlayerEntity p = c.p();
        if (s.flying) {
            Bender.stopFlight(p, s);
            return true;
        }
        var ab = p.getAbilities();
        if (!ab.allowFlying) {
            ab.allowFlying = true;
            s.grantedFlight = true;
        }
        ab.setFlySpeed(0.07f);
        ab.flying = true;
        p.sendAbilitiesUpdate();
        p.setVelocity(0, 0.55, 0);
        p.velocityModified = true;
        s.flying = true;
        Snd.play(c.w(), c.pos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 1.4f);
        Fx.burst(c.w(), "a_wind", c.pos().add(0, 0.2, 0), 30, 0.35, 0.3);
        Fx.circle(c.w(), "a_ring", c.pos().add(0, 0.2, 0), 1.0, 20, 0, 0);
        return true;
    }
}
