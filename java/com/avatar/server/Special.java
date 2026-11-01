package com.avatar.server;

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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Secret sub-bending, elemental combos and the supreme technique. */
public final class Special {
    private Special() {}

    private static final java.util.Random R = Fx.R;

    private static Vec3d flat(Vec3d v) {
        Vec3d f = new Vec3d(v.x, 0, v.z);
        return f.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : f.normalize();
    }

    // ================================================================== SECRETS
    // ------------------------------------------------------------------ Lightning storm
    public static boolean lightningStorm(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Snd.play(w, c.pos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.6f);
        Effects.add(t -> {
            if (t > 72 || !p.isAlive()) return false;
            Vec3d ctr = p.getPos();
            for (int i = 0; i < 12; i++)                                   // black storm cloud overhead
                Fx.vel(w, "f_smoke", ctr.x + R.nextGaussian() * 6, ctr.y + 20 + R.nextDouble() * 3, ctr.z + R.nextGaussian() * 6, R.nextGaussian() * 0.04, 0, R.nextGaussian() * 0.04);
            if (R.nextInt(3) == 0) Fx.vel(w, "l_bolt", ctr.x + R.nextGaussian() * 6, ctr.y + 19, ctr.z + R.nextGaussian() * 6, 0, 0, 0);
            if (t % 5 == 0) {
                List<LivingEntity> list = Ops.around(w, ctr, 20, p);
                Vec3d at = !list.isEmpty() && R.nextInt(10) < 7
                        ? list.get(R.nextInt(list.size())).getPos()
                        : ctr.add((R.nextDouble() - 0.5) * 24, 0, (R.nextDouble() - 0.5) * 24);
                BlockPos g = Ops.groundPos(w, at);
                Vec3d s = new Vec3d(at.x, g.getY(), at.z);
                Fire.boltPath(w, s.add(0, 22, 0), s, 0.7);
                Fire.flash(w, s);
                Fx.burst(w, "l_bolt", s.add(0, 0.5, 0), 30, 0.5, 0.4);
                Ops.ring(w, s.add(0, 0.1, 0), 3.5, 7, Element.AIR);
                Ops.blast(w, s, p, 3.2, 11f * c.mult(), 0.8, 0.5, e -> {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3));
                    e.setOnFireFor(3);
                });
                Fire.scorch(w, s, 2);
                Snd.play(w, s, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2f, 1.0f);
                Ops.shake(w, s, 22, 4, 6);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ Combustion: a focused beam that detonates
    public static boolean combustion(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        LivingEntity aim = Ops.aimEntity(w, p, 40, 0.97);
        Vec3d end = aim != null ? Ops.center(aim) : Ops.aimPoint(w, p, 40).add(0, 0.5, 0);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
        Effects.add(t -> {
            if (t > 14 || !p.isAlive()) return false;
            Vec3d from = p.getEyePos().add(p.getRotationVec(1f).multiply(0.8)).add(0, -0.3, 0);
            double size = 0.1 + t * 0.03;                                    // the "third-eye" energy gathers at the forehead / hand
            for (int i = 0; i < 4 + t; i++) Fx.vel(w, "s_core", from.add(Fx.jitter(size)), Vec3d.ZERO);
            Vec3d d = end.subtract(from);
            int n = (int) (d.length() * 2);
            for (int i = 0; i <= n; i++) Fx.vel(w, "l_bolt", from.add(d.multiply((double) i / Math.max(1, n))).add(Fx.jitter(0.04)), Vec3d.ZERO);
            Fx.burst(w, "f_core", end, 2 + t / 3, 0.1, 0.2);                 // marker at the target warms up
            if (t % 3 == 0) Snd.play(w, from, SoundEvents.ENTITY_BLAZE_SHOOT, 0.7f, 0.6f + t * 0.06f);
            if (t == 14) {
                Fire.explode(w, p, end, 7.0, 20f * c.mult(), 220);
                Fx.burst(w, "s_core", end, 30, 0.6, 0.3);
                Snd.play(w, end, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.5f, 0.6f);
                Earth.crater(w, end, 2.2);
                Ops.shake(w, end, 30, 8, 14);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ Bloodbending (night only)
    public static boolean bloodbending(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        long tod = w.getTimeOfDay() % 24000L;
        boolean night = tod >= 12800L && tod <= 23200L;
        if (com.avatar.core.AvatarConfig.INSTANCE.bloodbendingNightOnly && !night) {
            Bender.note(p, "message.avatar.night", Formatting.DARK_RED);
            return false;
        }
        LivingEntity target = Ops.aimEntity(w, p, 24, 0.92);
        if (target == null) return false;
        boolean full = (w.getTimeOfDay() / 24000L) % 8 == 0 && night;
        int dur = full ? 130 : 80;
        Snd.play(w, target.getPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.6f);
        Effects.add(t -> {
            if (t > dur || !target.isAlive() || !p.isAlive()) {
                if (target.isAlive() && p.isAlive()) {                      // let go: hurled forward
                    Vec3d look = p.getRotationVec(1f);
                    target.setVelocity(look.multiply(1.4).add(0, 0.2, 0));
                    target.velocityModified = true;
                    Ops.hurt(w, target, p, 6f * c.mult());
                    Fx.burst(w, "r_blood", Ops.center(target), 30, 0.4, 0.3);
                }
                return false;
            }
            Vec3d want = p.getEyePos().add(p.getRotationVec(1f).multiply(4.5));
            Vec3d to = want.subtract(Ops.center(target));
            Vec3d v = to.multiply(0.32);
            if (v.length() > 0.9) v = v.normalize().multiply(0.9);
            target.setVelocity(v);
            target.velocityModified = true;
            target.fallDistance = 0;
            Vec3d from = p.getEyePos().add(0, -0.4, 0), ctr = Ops.center(target);
            for (int k = 0; k < 3; k++) {                                   // blood threads between the hands and the body
                Vec3d dest = ctr.add(Fx.jitter(0.4));
                for (int i = 0; i <= 10; i++) Fx.vel(w, "r_blood", from.add(dest.subtract(from).multiply(i / 10.0)), Vec3d.ZERO);
            }
            Fx.sphere(w, "r_blood", ctr, 0.9, 6, Vec3d.ZERO);
            if (t % 20 == 0) Ops.hurt(w, target, p, 1.5f);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ Advanced healing
    public static boolean advancedHealing(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.2f);
        Snd.play(w, c.pos(), SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        Water.healBurst(w, p, 16f, true);
        for (ServerPlayerEntity o : w.getPlayers(q -> q != p && q.squaredDistanceTo(p) < 100)) {
            Water.healBurst(w, o, 14f, true);
            Fx.burst(w, "s_gold", o.getPos().add(0, 1, 0), 24, 0.2, 0.6);
        }
        Vec3d base = c.pos();
        Ops.ring(w, base.add(0, 0.1, 0), 9, 14, Element.WATER);
        Effects.add(t -> {
            if (t > 40 || !p.isAlive()) return false;
            for (int k = 0; k < 6; k++) {
                double a = t * 0.4 + k * 1.047, y = (t * 0.07 + k * 0.3) % 3.0;
                double r = 1.4 - y * 0.2;
                Fx.vel(w, k % 2 == 0 ? "w_drop" : "s_gold", base.x + Math.cos(a) * r, base.y + y, base.z + Math.sin(a) * r, 0, 0.04, 0);
            }
            if (t % 5 == 0) Snd.play(w, base, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.0f + t * 0.02f);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ Metal shrapnel
    public static boolean metalShrapnel(Cast c) {
        ServerWorld w = c.w();
        Vec3d look = c.look();
        Snd.play(w, c.pos(), SoundEvents.BLOCK_CHAIN_BREAK, 1.2f, 1.4f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_ANVIL_LAND, 0.5f, 1.8f);
        for (int k = 0; k < 9; k++) {
            Vec3d dir = look.add(Fx.jitter(0.1)).normalize();
            Ops.projectile(w, c.p(), c.eye().add(look.multiply(1.2)).add(Fx.jitter(0.3)), dir, 1.5, 22, 0.6, true,
                    (pos, d, t) -> {
                        Fx.vel(w, "m_metal", pos, Vec3d.ZERO);
                        Fx.vel(w, "m_metal", pos.subtract(d.multiply(0.4)), Vec3d.ZERO);
                    },
                    (pos, target) -> {
                        if (target != null) {
                            Ops.hurt(w, target, c.p(), 4.5f * c.mult());
                            Ops.push(target, dir, 0.3, 0.1);
                        }
                        Fx.burst(w, "m_metal", pos, 8, 0.2, 0.2);
                        Fx.burst(w, "e_spark", pos, 5, 0.15, 0.2);
                    });
        }
        return true;
    }

    // ------------------------------------------------------------------ Lavabending: a molten stream across the ground
    public static boolean lavabending(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d dir = flat(c.look()), right = Ops.rightOf(dir);
        TempBlocks.Budget budget = TempBlocks.Budget.make();
        Set<Integer> hit = new HashSet<>();
        Snd.play(w, c.pos(), SoundEvents.BLOCK_LAVA_POP, 1.5f, 0.6f);
        Snd.play(w, c.pos(), SoundEvents.ENTITY_GENERIC_EXPLODE, 0.6f, 0.5f);
        Effects.add(t -> {
            if (t > 26 || !p.isAlive()) return false;
            Vec3d head = p.getPos().add(dir.multiply(1.5 + t * 0.7));
            for (int side = -1; side <= 1; side++) {
                Vec3d q = head.add(right.multiply(side * 0.9 + Math.sin(t * 0.5) * 0.4));
                BlockPos g = Ops.groundPos(w, q);
                TempBlocks.convert(w, budget, g.down(), Blocks.MAGMA_BLOCK.getDefaultState(), 160);
                for (int i = 0; i < 4; i++) Fx.vel(w, "f_big", q.x + R.nextGaussian() * 0.3, g.getY() + 0.2, q.z + R.nextGaussian() * 0.3, 0, 0.08 + R.nextDouble() * 0.1, 0);
                Fx.vel(w, "f_core", q.x, g.getY() + 0.3, q.z, 0, 0.05, 0);
                Fx.vel(w, "e_rock", q.x, g.getY() + 0.3, q.z, R.nextGaussian() * 0.05, 0.2, R.nextGaussian() * 0.05);
                Fx.vel(w, "f_smoke", q.x, g.getY() + 0.5, q.z, 0, 0.06, 0);
            }
            for (LivingEntity e : Ops.around(w, head.add(0, 0.7, 0), 2.0, p)) {
                if (!hit.add(e.getId())) continue;
                Ops.hurt(w, e, p, 9f * c.mult());
                e.setOnFireFor(7);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
                Ops.push(e, dir, 0.4, 0.5);
            }
            if (t % 4 == 1) Snd.play(w, head, SoundEvents.BLOCK_LAVA_AMBIENT, 0.8f, 0.8f);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ Seismic sense
    public static boolean seismic(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Snd.play(w, c.pos(), SoundEvents.BLOCK_STONE_BREAK, 1.2f, 0.4f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 0.8f, 0.6f);
        Effects.add(t -> {
            if (t > 3) return false;
            Ops.ring(w, c.pos().add(0, 0.1, 0), 40, 28, Element.EARTH);
            return false;
        });
        List<LivingEntity> found = Ops.around(w, c.pos(), 40, p).stream().filter(e -> e.isOnGround()).toList();
        for (LivingEntity e : found) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0, true, false, false));
        }
        Effects.add(t -> {
            if (t > 120 || !p.isAlive()) return false;
            if (t % 6 == 0) {
                for (LivingEntity e : found) {
                    if (!e.isAlive()) continue;
                    for (int i = 0; i < 4; i++) Fx.vel(w, "e_spark", e.getX(), e.getY() + e.getHeight() + 0.4 + i * 0.25, e.getZ(), 0, 0.02, 0);
                    Fx.circle(w, "e_ring", e.getPos().add(0, 0.1, 0), 0.9, 10, t * 0.2, 0);
                }
            }
            return true;
        });
        p.sendMessage(Text.translatable("message.avatar.seismic", found.size()).formatted(Formatting.GOLD), true);
        return true;
    }

    // ------------------------------------------------------------------ Spirit projection
    public static boolean spirit(Cast c, PState s) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        long now = w.getTime();
        if (s.spiritUntil > now) {                                           // second press: return to the body
            s.spiritUntil = now;
            return true;
        }
        s.spiritUntil = now + 400;
        s.spiritOrigin = p.getPos();
        Vec3d origin = s.spiritOrigin;
        var ab = p.getAbilities();
        boolean grant = !ab.allowFlying;
        if (grant) ab.allowFlying = true;
        ab.flying = true;
        ab.setFlySpeed(0.045f);
        p.sendAbilitiesUpdate();
        Snd.play(w, c.pos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.5f);
        Fx.burst(w, "s_gold", c.pos().add(0, 1, 0), 40, 0.3, 0.3);
        Effects.add(t -> {
            boolean over = t > 400 || w.getTime() >= s.spiritUntil || !p.isAlive() || p.isRemoved();
            if (over) {
                s.spiritUntil = 0;
                if (p.isAlive()) {
                    Fx.burst(w, "s_gold", p.getPos().add(0, 1, 0), 30, 0.3, 0.3);
                    p.requestTeleport(origin.x, origin.y, origin.z);                 // the spirit snaps back into the body
                    Fx.burst(w, "s_core", origin.add(0, 1, 0), 20, 0.3, 0.3);
                    Ops.ring(w, origin.add(0, 0.1, 0), 4, 10, Element.AIR);
                    Snd.play(w, origin, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                    var a2 = p.getAbilities();
                    if (grant && !p.isCreative() && !p.isSpectator()) {
                        a2.allowFlying = false;
                        a2.flying = false;
                    }
                    a2.setFlySpeed(0.05f);
                    p.sendAbilitiesUpdate();
                    p.removeStatusEffect(StatusEffects.INVISIBILITY);
                }
                return false;
            }
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 40, 0, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 2, true, false, false));
            Vec3d at = p.getPos().add(0, 1, 0);
            for (int i = 0; i < 5; i++) Fx.vel(w, i % 2 == 0 ? "s_gold" : "a_wind", at.add(Fx.jitter(0.35)), Fx.jitter(0.02));
            if (t % 3 == 0) {                                                // the empty body glows where it was left
                for (int i = 0; i < 4; i++) Fx.vel(w, "s_gold", origin.x + R.nextGaussian() * 0.3, origin.y + R.nextDouble() * 1.8, origin.z + R.nextGaussian() * 0.3, 0, 0.02, 0);
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ Pressure: vacuum, then implosion
    public static boolean pressure(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        LivingEntity aim = Ops.aimEntity(w, p, 22, 0.93);
        Vec3d ctr = aim != null ? Ops.center(aim) : Ops.aimPoint(w, p, 18).add(0, 1.2, 0);
        Snd.play(w, ctr, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.4f);
        Effects.add(t -> {
            if (t > 30 || !p.isAlive()) return false;
            if (t <= 24) {
                for (int i = 0; i < 18; i++) {                                // air collapses towards the centre
                    double u = R.nextDouble() * 2 - 1, a = R.nextDouble() * 6.28, sq = Math.sqrt(1 - u * u), rad = 6.0;
                    Vec3d from = ctr.add(rad * sq * Math.cos(a), rad * u, rad * sq * Math.sin(a));
                    Vec3d v = ctr.subtract(from).multiply(0.14);
                    Fx.vel(w, i % 3 == 0 ? "a_big" : "a_wind", from, v);
                }
                Fx.sphere(w, "w_mist", ctr, 0.8 + 0.1 * Math.sin(t), 4, Vec3d.ZERO);
                Fx.vel(w, "s_core", ctr, Vec3d.ZERO);
                for (LivingEntity e : Ops.around(w, ctr, 9, p)) {
                    Vec3d pull = ctr.subtract(Ops.center(e)).multiply(0.07);
                    e.addVelocity(pull.x, pull.y, pull.z);
                    e.velocityModified = true;
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 60, 0, true, false, false));
                }
                if (t % 6 == 0) Snd.play(w, ctr, SoundEvents.BLOCK_BEACON_AMBIENT, 1.0f, 0.5f + t * 0.03f);
            } else if (t == 25) {
                Ops.blast(w, ctr, p, 8, 15f * c.mult(), 2.0, 0.7, e -> e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2)));
                Fx.burst(w, "a_wind", ctr, 90, 0.9, 0.1);
                Fx.burst(w, "s_core", ctr, 24, 0.5, 0.1);
                Ops.ring(w, ctr.add(0, -1, 0), 9, 10, Element.AIR);
                Snd.play(w, ctr, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.6f, 1.4f);
                Snd.play(w, ctr, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.9f, 1.4f);
                Ops.shake(w, ctr, 24, 6, 10);
            }
            return true;
        });
        return true;
    }

    // ================================================================== COMBOS (Avatar State)
    public static boolean flamingTornado(Cast c) {
        Vec3d at = Ops.aimPoint(c.w(), c.p(), 16.0);
        Vec3d drift = flat(c.look()).multiply(0.1);
        Air.tornadoAt(c.w(), c.p(), at, drift, 110, 3.5f * c.mult(), "f_fire", "f_big", true, false);
        Effects.add(t -> {                                                    // embers fly off the funnel
            if (t > 110) return false;
            Vec3d b = at.add(drift.multiply(t));
            Fx.vel(c.w(), "f_ember", b.x + R.nextGaussian() * 1.5, b.y + R.nextDouble() * 7, b.z + R.nextGaussian() * 1.5, R.nextGaussian() * 0.2, 0.05, R.nextGaussian() * 0.2);
            return true;
        });
        Fire.scorch(c.w(), at, 4);
        Ops.shake(c.w(), at, 20, 4, 14);
        return true;
    }

    public static boolean storm(Cast c) {
        ServerWorld w = c.w();
        Vec3d at = Ops.aimPoint(w, c.p(), 16.0);
        Air.tornadoAt(w, c.p(), at, flat(c.look()).multiply(0.06), 120, 2.5f * c.mult(), "w_drop", "w_big", false, true);
        Snd.play(w, at, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.7f);
        Effects.add(t -> {                                                    // heavy rain over the whole area
            if (t > 120) return false;
            for (int i = 0; i < 24; i++) Fx.vel(w, "w_drop", at.x + R.nextGaussian() * 5, at.y + 12 + R.nextDouble() * 2, at.z + R.nextGaussian() * 5, 0, -0.9, 0);
            for (int i = 0; i < 4; i++) Fx.vel(w, "f_smoke", at.x + R.nextGaussian() * 5, at.y + 13, at.z + R.nextGaussian() * 5, 0, 0, 0);
            for (LivingEntity e : Ops.around(w, at, 7, c.p())) e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 1, true, false, false));
            return true;
        });
        Ops.shake(w, at, 20, 3, 16);
        return true;
    }

    public static boolean eruption(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d at = Ops.aimPoint(w, p, 18.0);
        BlockPos g = Ops.groundPos(w, at);
        Vec3d base = new Vec3d(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
        TempBlocks.Budget budget = TempBlocks.Budget.make();
        Snd.play(w, base, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5f, 0.4f);
        Effects.add(t -> {
            if (t > 62 || !p.isAlive()) return false;
            if (t <= 16) {                                                    // the ground cracks open and glows
                Ops.shake(w, base, 26, 3 + t / 4, 3);
                for (int k = 0; k < 3; k++) {
                    double a = R.nextDouble() * 6.28, len = 1 + R.nextDouble() * 6;
                    for (double d = 0.5; d < len; d += 0.6)
                        Fx.vel(w, R.nextInt(3) == 0 ? "f_big" : "f_ember", base.x + Math.cos(a) * d, base.y + 0.15, base.z + Math.sin(a) * d, 0, 0.08, 0);
                }
                if (t % 5 == 1) Ops.ring(w, base.add(0, 0.1, 0), 7, 8, Element.FIRE);
                return true;
            }
            if (t == 17) {
                for (int dy = 0; dy < 4; dy++)                                // rock cone rises
                    for (int dx = -3; dx <= 3; dx++)
                        for (int dz = -3; dz <= 3; dz++)
                            if (dx * dx + dz * dz <= (3 - dy) * (3 - dy) + 1)
                                TempBlocks.place(w, budget, g.add(dx, dy, dz), (dy == 3 ? Blocks.MAGMA_BLOCK : Blocks.COBBLESTONE).getDefaultState(), 200);
                Ops.blast(w, base, p, 5.5, 16f * c.mult(), 1.6, 1.3, e -> e.setOnFireFor(8));
                Snd.play(w, base, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 2.5f, 0.5f);
                Fire.explode(w, p, base.add(0, 1, 0), 5.0, 8f * c.mult(), 160);
            }
            Vec3d top = base.add(0, 4.2, 0);
            for (int i = 0; i < 14; i++) {                                    // fountain of lava and rock
                double a = R.nextDouble() * 6.28, sp = R.nextDouble() * 0.25;
                Fx.vel(w, i % 3 == 0 ? "f_big" : "f_fire", top.x, top.y, top.z, Math.cos(a) * sp, 0.6 + R.nextDouble() * 0.7, Math.sin(a) * sp);
            }
            for (int i = 0; i < 3; i++) {
                double a = R.nextDouble() * 6.28, sp = 0.1 + R.nextDouble() * 0.3;
                Fx.vel(w, "e_big", top.x, top.y, top.z, Math.cos(a) * sp, 0.5 + R.nextDouble() * 0.7, Math.sin(a) * sp);
            }
            Fx.vel(w, "f_smoke", top.x, top.y + 1, top.z, R.nextGaussian() * 0.05, 0.2, R.nextGaussian() * 0.05);
            if (t % 5 == 0) {
                double a = R.nextDouble() * 6.28, d = 2 + R.nextDouble() * 5;
                Vec3d imp = base.add(Math.cos(a) * d, 0.5, Math.sin(a) * d);       // lava bombs landing around
                Fire.explode(w, p, imp, 3.0, 7f * c.mult(), 50);
            }
            if (t % 6 == 0) Ops.shake(w, base, 26, 4, 5);
            if (t == 40) Earth.crater(w, base.add(0, -1, 0), 3.0);
            return true;
        });
        return true;
    }

    public static boolean mudPrison(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        LivingEntity target = Ops.aimEntity(w, p, 22, 0.92);
        if (target == null) return false;
        Snd.play(w, target.getPos(), SoundEvents.BLOCK_GRAVEL_BREAK, 1.5f, 0.5f);
        Snd.play(w, target.getPos(), SoundEvents.ENTITY_GENERIC_SPLASH, 1.2f, 0.5f);
        Water.cage(w, target, new BlockState[]{Blocks.MUD.getDefaultState(), Blocks.PACKED_MUD.getDefaultState()}, "e_dust", 160);
        Ops.hurt(w, target, p, 4f * c.mult());
        Effects.add(t -> {
            if (t > 110 || !target.isAlive()) return false;
            Vec3d ctr = Ops.center(target);
            Fx.sphere(w, "e_dust", ctr, 1.3, 6, Vec3d.ZERO);
            Fx.sphere(w, "w_drop", ctr, 1.1, 4, Vec3d.ZERO);
            Fx.vel(w, "e_big", target.getX() + R.nextGaussian() * 0.5, target.getY() + 0.1, target.getZ() + R.nextGaussian() * 0.5, 0, 0.03, 0);
            target.setVelocity(0, Math.min(0, target.getVelocity().y), 0);       // sinks into the mud
            target.velocityModified = true;
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 6, 9, true, false, false));
            if (t % 25 == 0) Ops.hurt(w, target, p, 2.5f * c.mult());
            return true;
        });
        return true;
    }

    public static boolean steamBurst(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d at = Ops.aimPoint(w, p, 12.0).add(0, 1.0, 0);
        Snd.play(w, at, SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, 2.0f, 0.5f);
        Snd.play(w, at, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);
        Ops.shake(w, at, 22, 5, 8);
        Effects.add(t -> {
            if (t > 80 || !p.isAlive()) return false;
            double r = t < 10 ? 1.5 + t * 0.5 : 6.5;
            for (int i = 0; i < (t < 10 ? 36 : 14); i++) {
                double u = R.nextDouble() * 2 - 1, a = R.nextDouble() * 6.28, sq = Math.sqrt(1 - u * u), rr = r * (0.4 + 0.6 * R.nextDouble());
                Fx.vel(w, R.nextInt(3) == 0 ? "f_smoke" : "w_mist", at.x + rr * sq * Math.cos(a), at.y + rr * u * 0.8, at.z + rr * sq * Math.sin(a), R.nextGaussian() * 0.03, 0.03, R.nextGaussian() * 0.03);
            }
            if (t == 1) Ops.blast(w, at, p, 7, 11f * c.mult(), 1.4, 0.6, null);
            if (t % 8 == 2) {
                for (LivingEntity e : Ops.around(w, at, r + 0.5, p)) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
                    Ops.hurt(w, e, p, 2.5f * c.mult());
                }
                Snd.play(w, at, SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.8f, 0.7f);
            }
            return true;
        });
        return true;
    }

    // ================================================================== SUPREME: ELEMENTAL COLLAPSE
    private static final String[] PART = {"f_core", "w_ice", "e_big", "a_wind"};
    private static final String[] RING = {"f_ring", "w_ring", "e_ring", "a_ring"};

    public static double collapseCharge(PState s, long now) {
        return Math.min(1.0, (now - s.collapseStart) / 110.0);
    }

    /** Key pressed: the Avatar rises and the four elements gather into a sphere. Runs until the key is released. */
    public static void collapseStart(Cast c, PState s, int skillIdx) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        s.collapsing = true;
        s.collapseStart = w.getTime();
        double startY = p.getY();
        Snd.play(w, c.pos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);
        Snd.play(w, c.pos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
        Effects.add(t -> {
            boolean released = !s.holding[skillIdx];
            if (!p.isAlive() || p.isRemoved() || !s.avatarOn || released || t > 600) {
                p.setNoGravity(false);
                s.collapsing = false;
                if (!released) s.holding[skillIdx] = false;
                return false;
            }
            double ch = collapseCharge(s, w.getTime());
            p.setNoGravity(true);
            double hover = startY + 3.2 * Math.min(1.0, t / 25.0);
            p.setVelocity(0, (hover - p.getY()) * 0.3, 0);
            p.velocityModified = true;
            p.fallDistance = 0;
            Vec3d ctr = p.getPos().add(0, 3.6 + ch * 1.6, 0);
            double rad = 0.7 + ch * 3.4;
            for (int i = 0; i < 6 + (int) (14 * ch); i++) {                        // the four elements, each on its own side of the sphere
                int el = R.nextInt(4);
                double a = R.nextDouble() * 6.28, u = R.nextDouble() * 2 - 1, sq = Math.sqrt(1 - u * u);
                double rr = rad * (0.5 + 0.5 * R.nextDouble());
                Fx.vel(w, PART[el], ctr.x + rr * sq * Math.cos(a), ctr.y + rr * u, ctr.z + rr * sq * Math.sin(a), -sq * Math.sin(a) * 0.05, 0, sq * Math.cos(a) * 0.05);
            }
            for (int i = 0; i < 3; i++) Fx.vel(w, "s_core", ctr.add(Fx.jitter(rad * 0.35)), Vec3d.ZERO);
            Fx.circle(w, "s_gold", ctr.add(0, Math.sin(t * 0.2) * rad * 0.5, 0), rad * 1.15, 10, t * 0.35, 0);
            for (int i = 0; i < 4; i++) {                                           // energy from the world flows in
                double a = R.nextDouble() * 6.28, d = 7 + R.nextDouble() * 6;
                Vec3d from = p.getPos().add(Math.cos(a) * d, R.nextDouble() * 3, Math.sin(a) * d);
                Fx.vel(w, PART[R.nextInt(4)], from, ctr.subtract(from).multiply(0.1));
            }
            if (t % 8 == 0) Snd.play(w, ctr, SoundEvents.BLOCK_BEACON_AMBIENT, 1.5f, 0.5f + (float) ch);
            if (t % 12 == 0) Ops.shake(w, p.getPos(), 20, 2 + (int) (ch * 4), 8);
            if (t % 20 == 0) Ops.ring(w, p.getPos().add(0, -3.0, 0), 5 + ch * 8, 16, Element.values()[(t / 20) % 4]);
            return true;
        });
    }

    /** Key released: the sphere crashes into the ground. */
    public static boolean collapse(Cast c, PState s) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        double ch = Math.max(0.15, c.charge());
        p.setNoGravity(false);
        s.collapsing = false;
        Vec3d from = p.getPos().add(0, 3.6 + ch * 1.6, 0);
        Snd.play(w, from, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.6f);
        Snd.play(w, from, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.6f);
        Ops.shake(w, from, 40, 6, 12);
        Effects.add(t -> {                                                           // the sphere dives to the ground
            if (!p.isAlive()) return false;
            double y = from.y - (t * 1.7);
            Vec3d ground = Ops.groundPos(w, new Vec3d(from.x, p.getY(), from.z)).toCenterPos();
            double gy = ground.y - 0.5;
            boolean land = y <= gy + 0.5 || t > 16;
            Vec3d at = new Vec3d(from.x, Math.max(y, gy + 0.3), from.z);
            double rad = 0.7 + ch * 3.4;
            for (int i = 0; i < 40; i++) {
                double a = R.nextDouble() * 6.28, u = R.nextDouble() * 2 - 1, sq = Math.sqrt(1 - u * u);
                Fx.vel(w, PART[R.nextInt(4)], at.x + rad * sq * Math.cos(a), at.y + rad * u, at.z + rad * sq * Math.sin(a), 0, 0.1, 0);
            }
            p.setVelocity(0, -1.2, 0);
            p.velocityModified = true;
            p.fallDistance = 0;
            if (!land) return true;
            // ---------------- impact
            double R0 = 7 + 11 * ch;
            Vec3d hit = at;
            Snd.play(w, hit, SoundEvents.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);
            Snd.play(w, hit, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 0.5f);
            Snd.play(w, hit, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 3.0f, 0.4f);
            Ops.shake(w, hit, 60, 10, 30);
            Ops.blast(w, hit, p, R0, (28f + 42f * (float) ch) * c.mult(), 2.4, 1.2, e -> {
                e.setOnFireFor(6);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 3));
            });
            for (int el = 0; el < 4; el++) {
                int idx = el;
                Scheduler.later(el * 3, () -> {
                    Effects.groundRing(w, hit.add(0, 0.05, 0), R0 * (0.7 + 0.1 * idx), 16, RING[idx], PART[idx]);
                    Fx.burst(w, PART[idx], hit.add(0, 0.5, 0), 70, 0.9 + 0.2 * ch, 0.6);
                });
            }
            Fx.fireBlast(w, hit.add(0, 0.4, 0), 120, 0.9);
            Earth.debris(w, hit, 110, 1.0);
            for (int i = 0; i < 25; i++) {                                           // boulders hurled into the sky
                double a = R.nextDouble() * 6.28, sp = 0.2 + R.nextDouble() * 0.7;
                Fx.vel(w, "e_big", hit.x, hit.y + 0.5, hit.z, Math.cos(a) * sp, 0.9 + R.nextDouble() * 1.0, Math.sin(a) * sp);
            }
            Fx.burst(w, "s_core", hit.add(0, 0.5, 0), 60, 0.8, 0.4);
            Fx.burst(w, "s_gold", hit.add(0, 0.5, 0), 80, 1.0, 0.5);
            Earth.crater(w, hit, 3.0 + 5.0 * ch);
            Fire.scorch(w, hit, R0 * 0.5);
            Effects.add(q -> {                                                       // the shock wave sweeps outwards
                if (q > 18) return false;
                double r = R0 * q / 18.0;
                for (int i = 0; i < 40; i++) {
                    double a = Math.PI * 2 * i / 40;
                    BlockPos g = Ops.groundPos(w, hit.add(Math.cos(a) * r, 0, Math.sin(a) * r));
                    Fx.vel(w, "e_dust", hit.x + Math.cos(a) * r, g.getY() + 0.2, hit.z + Math.sin(a) * r, 0, 0.12, 0);
                }
                return true;
            });
            return false;
        });
        return true;
    }
}
