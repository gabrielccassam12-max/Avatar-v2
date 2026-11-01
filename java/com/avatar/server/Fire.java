package com.avatar.server;

import com.avatar.core.AvatarConfig;
import com.avatar.core.Element;
import com.avatar.particle.Fx;
import com.avatar.util.Effects;
import com.avatar.util.Snd;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

import java.util.HashSet;
import java.util.Set;

/** FIRE: aggressive, fast, explosive. Everything is drawn with yellow -> orange -> red cubes. */
public final class Fire {
    private Fire() {}

    private static final java.util.Random R = Fx.R;

    /** Sets a few blocks on fire around a point (respects config + gamerules). */
    public static void scorch(ServerWorld w, Vec3d c, double radius) {
        if (!AvatarConfig.INSTANCE.fireIgnites || !AvatarConfig.INSTANCE.modifyBlocks) return;
        if (!w.getGameRules().getBoolean(GameRules.DO_FIRE_TICK) || !w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) return;
        int tries = (int) (radius * 4);
        for (int i = 0; i < tries; i++) {
            BlockPos p = BlockPos.ofFloored(c.x + R.nextGaussian() * radius * 0.45, c.y + R.nextInt(3) - 1, c.z + R.nextGaussian() * radius * 0.45);
            if (w.getBlockState(p).isAir() && Blocks.FIRE.getDefaultState().canPlaceAt(w, p)) w.setBlockState(p, Blocks.FIRE.getDefaultState());
        }
    }

    public static void explode(ServerWorld w, ServerPlayerEntity p, Vec3d at, double radius, float dmg, int cubes) {
        Ops.blast(w, at, p, radius, dmg, 1.2, 0.45, e -> e.setOnFireFor(6));
        Fx.fireBlast(w, at, cubes, radius * 0.11);
        Ops.ring(w, at.add(0, -0.3, 0), radius * 1.1, 10, Element.FIRE);
        Snd.play(w, at, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5f, 0.9f);
        Snd.play(w, at, SoundEvents.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
        Ops.shake(w, at, 24, (int) Math.min(8, 2 + radius), 10);
        scorch(w, at, radius);
    }

    // ------------------------------------------------------------------ 1. Fire Punch
    public static boolean punch(Cast c) {
        Vec3d look = c.look(), o = c.eye().add(look.multiply(1.6)).add(0, -0.4, 0);
        Snd.play(c.w(), o, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
        Snd.play(c.w(), o, SoundEvents.ITEM_FIRECHARGE_USE, 1.0f, 1.2f);
        Fx.burst(c.w(), "f_fire", o, 22, 0.25, 0.1);
        for (int i = 0; i < 6; i++) Fx.vel(c.w(), "f_core", o.add(Fx.jitter(0.2)), look.multiply(0.15));
        Fx.circle(c.w(), "f_ring", o.add(0, -0.3, 0), 0.7, 14, 0, 0);
        int hit = 0;
        for (LivingEntity e : Ops.around(c.w(), c.eye().add(look.multiply(1.8)), 2.3, c.p())) {
            Ops.hurt(c.w(), e, c.p(), 6f * c.mult());
            e.setOnFireFor(4);
            Ops.push(e, look, 0.9, 0.3);
            Fx.burst(c.w(), "f_ember", Ops.center(e), 10, 0.25, 0.5);
            hit++;
        }
        if (hit > 0) {
            Snd.play(c.w(), o, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
            Ops.shake(c.w(), o, 8, 2, 4);
        }
        return true;
    }

    // ------------------------------------------------------------------ 2. Flame Kick: crescent wave of fire along the ground
    public static boolean kick(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d look = c.look();
        Vec3d dir = new Vec3d(look.x, 0, look.z);
        dir = dir.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : dir.normalize();
        Vec3d right = Ops.rightOf(dir), d = dir;
        Vec3d start = p.getPos().add(dir.multiply(1.0)).add(0, 0.3, 0);
        Vec3d[] pos = {start};
        Set<Integer> hit = new HashSet<>();
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_BLAZE_SHOOT, 1.0f, 0.9f);
        Effects.add(t -> {
            if (t > 14 || !p.isAlive()) return false;
            Vec3d at = pos[0] = pos[0].add(d.multiply(0.75));
            int n = 15;
            for (int i = -n; i <= n; i++) {                 // crescent: the edges trail behind the middle
                double u = (double) i / n, lag = u * u * 1.8;
                Vec3d q = at.add(right.multiply(u * 2.3)).subtract(d.multiply(lag));
                Fx.vel(w, "f_fire", q, new Vec3d(0, 0.02 + R.nextDouble() * 0.04, 0));
                if (i % 3 == 0) Fx.vel(w, "f_big", q.add(0, 0.1, 0), new Vec3d(0, 0.05, 0));
            }
            Fx.vel(w, "f_core", at, Vec3d.ZERO);
            for (LivingEntity e : Ops.around(w, at.add(0, 0.5, 0), 2.4, p)) {
                if (!hit.add(e.getId())) continue;
                Ops.hurt(w, e, p, 7f * c.mult());
                e.setOnFireFor(5);
                Ops.push(e, d, 0.7, 0.45);
            }
            if (t % 3 == 0) scorch(w, at, 1.4);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 3. Fireball (charge it)
    public static boolean fireball(Cast c) {
        ServerWorld w = c.w();
        float ch = Math.max(0.25f, c.charge());
        Vec3d look = c.look();
        double radius = 2.2 + 2.4 * ch;
        float dmg = (6f + 9f * ch) * c.mult();
        double size = 0.15 + 0.2 * ch;
        Snd.play(w, c.eye(), SoundEvents.ENTITY_BLAZE_SHOOT, 1.2f, 0.9f - 0.3f * ch);
        Ops.projectile(w, c.p(), c.eye().add(look.multiply(1.2)).add(0, -0.25, 0), look, 1.15, 70, 0.8 + ch * 0.5, false,
                (pos, d, t) -> {
                    for (int i = 0; i < 4 + (int) (6 * ch); i++) Fx.vel(w, "f_core", pos.add(Fx.jitter(size)), Vec3d.ZERO);
                    for (int i = 0; i < 8; i++) Fx.vel(w, "f_fire", pos.add(Fx.jitter(size * 1.6)), d.multiply(-0.12).add(Fx.jitter(0.03)));
                    Fx.vel(w, "f_big", pos.add(Fx.jitter(size)), d.multiply(-0.05));
                    Fx.vel(w, "f_ember", pos, Fx.jitter(0.07).add(0, 0.03, 0));
                },
                (pos, target) -> explode(w, c.p(), pos, radius, dmg, 60 + (int) (90 * ch)));
        return true;
    }

    // ------------------------------------------------------------------ 4. Fire Blast: nova around the body
    public static boolean blast(Cast c) {
        ServerWorld w = c.w();
        Vec3d at = c.pos();
        double maxR = 8.0;
        Fx.fireBlast(w, at.add(0, 0.3, 0), 90, 0.6);
        Ops.ring(w, at.add(0, -0.3, 0), maxR, 14, Element.FIRE);
        Snd.play(w, at, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 1.3f);
        Snd.play(w, at, SoundEvents.ENTITY_BLAZE_SHOOT, 1.3f, 0.5f);
        Ops.shake(w, at, 18, 4, 8);
        Set<Integer> hit = new HashSet<>();
        Effects.add(t -> {
            if (t > 14) return false;
            double r = Effects.ringRadius(maxR, t, 14);
            for (LivingEntity e : Ops.around(w, at.add(0, 0.5, 0), r + 1.5, c.p())) {
                double dx = e.getX() - at.x, dz = e.getZ() - at.z, dist = Math.sqrt(dx * dx + dz * dz);
                if (hit.contains(e.getId()) || Math.abs(dist - r) > 1.8) continue;
                hit.add(e.getId());
                Ops.hurt(w, e, c.p(), 8f * c.mult());
                e.setOnFireFor(6);
                Ops.blow(e, at, 1.3, 0.5);
            }
            if (t == 3) scorch(w, at, 5);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 5. Jet Propulsion
    public static boolean jet(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Set<Integer> hit = new HashSet<>();
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_BLAZE_SHOOT, 1.4f, 0.6f);
        Effects.add(t -> {
            if (t > 22 || !p.isAlive() || p.isRemoved()) {
                if (p.isAlive()) p.fallDistance = 0;
                return false;
            }
            Vec3d look = p.getRotationVec(1f);
            Vec3d dir = new Vec3d(look.x, look.y * 0.9 + 0.08, look.z).normalize();
            p.setVelocity(dir.multiply(Math.min(1.0, 0.35 + t * 0.13)));
            p.velocityModified = true;
            p.fallDistance = 0;
            Vec3d back = dir.multiply(-1), right = Ops.rightOf(dir), up = right.crossProduct(dir);
            Vec3d base = p.getPos().add(0, 0.45, 0);
            for (int side = -1; side <= 1; side += 2) {
                Vec3d o = base.add(right.multiply(0.22 * side)).add(back.multiply(0.2));
                for (int i = 0; i < 9; i++)
                    Fx.vel(w, "f_fire", o, back.multiply(0.3 + R.nextDouble() * 0.45).add(right.multiply(R.nextGaussian() * 0.05)).add(up.multiply(R.nextGaussian() * 0.05)));
                Fx.vel(w, "f_ember", o, back.multiply(0.35).add(Fx.jitter(0.08)));
            }
            Fx.vel(w, "f_big", base.add(back.multiply(0.5)), back.multiply(0.25).add(Fx.jitter(0.03)));
            Fx.vel(w, "f_core", base.add(back.multiply(0.3)), back.multiply(0.1));
            if (t % 6 == 1) Snd.play(w, p.getPos(), SoundEvents.ITEM_FIRECHARGE_USE, 0.8f, 0.6f);
            for (LivingEntity e : Ops.targets(w, p.getBoundingBox().expand(1.4), p)) {
                if (!hit.add(e.getId())) continue;
                Ops.hurt(w, e, p, 5f * c.mult());
                e.setOnFireFor(5);
                Ops.push(e, dir, 0.8, 0.3);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 6. Dragon Breath
    public static boolean dragonBreath(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Snd.play(w, p.getEyePos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.4f);
        Effects.add(t -> {
            if (t > 44 || !p.isAlive() || p.isRemoved()) return false;
            Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f), right = Ops.rightOf(look), up = right.crossProduct(look);
            Vec3d o = eye.add(look.multiply(0.9)).add(right.multiply(0.2)).add(up.multiply(-0.35));
            for (int i = 0; i < 18; i++) {
                Vec3d v = look.multiply(0.55 + R.nextDouble() * 0.4).add(right.multiply(R.nextGaussian() * 0.07)).add(up.multiply(R.nextGaussian() * 0.07));
                Fx.vel(w, "f_fire", o, v);
            }
            for (int k = 0; k < 2; k++) {                    // corkscrew: the breath twists like a dragon's
                double a = t * 0.9 + k * Math.PI;
                Vec3d off = right.multiply(Math.cos(a) * 0.5).add(up.multiply(Math.sin(a) * 0.5));
                Fx.vel(w, "f_core", o.add(off), look.multiply(0.55).add(off.multiply(0.25)));
            }
            if (t % 2 == 0) Fx.vel(w, "f_big", o.add(look.multiply(0.6)), look.multiply(0.5).add(Fx.jitter(0.04)));
            if (t % 3 == 0) Fx.vel(w, "f_smoke", o.add(look.multiply(5)), look.multiply(0.1).add(0, 0.05, 0));
            if (t % 4 == 1) Snd.play(w, eye, SoundEvents.ITEM_FIRECHARGE_USE, 0.7f, 0.6f);
            if (t % 3 == 0) {
                for (LivingEntity e : Ops.targets(w, new net.minecraft.util.math.Box(eye.subtract(12, 12, 12), eye.add(12, 12, 12)), p)) {
                    Vec3d to = Ops.center(e).subtract(eye);
                    double d = to.length();
                    if (d < 0.5 || d > 11 || to.normalize().dotProduct(look) < 0.9) continue;
                    Ops.hurt(w, e, p, 2.8f * c.mult());
                    e.setOnFireFor(5);
                }
            }
            if (t % 8 == 0) scorch(w, eye.add(look.multiply(7)), 2.5);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 7. Lightning (charge it)
    /** Draws a jagged bolt and returns nothing; the strike itself is dealt by the caller. */
    public static void boltPath(ServerWorld w, Vec3d a, Vec3d b, double jag) {
        Vec3d d = b.subtract(a);
        int seg = Math.max(4, (int) (d.length() * 1.6));
        Vec3d prev = a;
        for (int i = 1; i <= seg; i++) {
            Vec3d q = a.add(d.multiply((double) i / seg));
            if (i < seg) q = q.add(Fx.jitter(jag));
            int steps = 4;
            for (int k = 0; k < steps; k++) Fx.vel(w, "l_bolt", prev.add(q.subtract(prev).multiply((double) k / steps)), Vec3d.ZERO);
            if (i % 3 == 0) Fx.vel(w, "s_core", q, Vec3d.ZERO);
            prev = q;
        }
    }

    public static void flash(ServerWorld w, Vec3d at) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
        if (bolt != null) {
            bolt.setPosition(at.x, at.y, at.z);
            bolt.setCosmetic(true);
            w.spawnEntity(bolt);
        }
    }

    public static boolean lightning(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        float ch = Math.max(0.2f, c.charge());
        Vec3d eye = c.eye(), look = c.look();
        LivingEntity aim = Ops.aimEntity(w, p, 40, 0.97);
        Vec3d end = aim != null ? Ops.center(aim) : Ops.aimPoint(w, p, 40).add(0, 0.2, 0);
        Vec3d from = c.hand();
        boltPath(w, from, end, 0.35);
        for (int i = 0; i < 2; i++) boltPath(w, from, end.add(Fx.jitter(0.9)), 0.5);          // forks
        flash(w, end);
        Snd.play(w, end, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4f, 1.2f);
        Snd.play(w, from, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.6f);
        Fx.burst(w, "l_bolt", end, 40, 0.5, 0.3);
        Fx.burst(w, "s_core", end, 10, 0.2, 0.2);
        Ops.ring(w, end.add(0, -0.2, 0), 3.5, 8, Element.AIR);
        Ops.shake(w, end, 20, 5, 8);
        float dmg = (9f + 12f * ch) * c.mult();
        Set<Integer> hit = new HashSet<>();
        // main strike + chain lightning to nearby enemies
        Vec3d cur = end;
        for (int hop = 0; hop < 3 + (int) (3 * ch); hop++) {
            LivingEntity next = null;
            double best = hop == 0 ? 3.0 : 7.0;
            for (LivingEntity e : Ops.around(w, cur, hop == 0 ? 3.0 : 7.0, p)) {
                if (hit.contains(e.getId())) continue;
                double d = Ops.center(e).distanceTo(cur);
                if (d <= best) {
                    best = d;
                    next = e;
                }
            }
            if (next == null) break;
            hit.add(next.getId());
            Vec3d q = Ops.center(next);
            if (hop > 0) boltPath(w, cur, q, 0.3);
            Ops.hurt(w, next, p, dmg * (hop == 0 ? 1f : 0.65f));
            next.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 3));
            next.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 1));
            next.setOnFireFor(2);
            Fx.burst(w, "l_bolt", q, 14, 0.3, 0.3);
            cur = q;
        }
        scorch(w, end, 2);
        return true;
    }
}
