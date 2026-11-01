package com.avatar.server;

import com.avatar.core.Element;
import com.avatar.particle.Fx;
import com.avatar.util.Effects;
import com.avatar.util.Scheduler;
import com.avatar.util.Snd;
import com.avatar.util.TempBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** EARTH: heavy hits, walls, spikes and terrain control. */
public final class Earth {
    private Earth() {}

    private static final java.util.Random R = Fx.R;
    public static final BlockState[] ROCK = {Blocks.COBBLESTONE.getDefaultState(), Blocks.STONE.getDefaultState(), Blocks.ANDESITE.getDefaultState()};

    /** Debris flying out of an impact (they fall back down). */
    public static void debris(ServerWorld w, Vec3d at, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double a = R.nextDouble() * Math.PI * 2, sp = speed * (0.3 + R.nextDouble());
            Fx.vel(w, i % 3 == 0 ? "e_big" : "e_rock", at.x, at.y + 0.2, at.z, Math.cos(a) * sp, 0.25 + R.nextDouble() * 0.5, Math.sin(a) * sp);
        }
        for (int i = 0; i < count / 2; i++) Fx.vel(w, "e_dust", at.x + R.nextGaussian() * 0.6, at.y + 0.2, at.z + R.nextGaussian() * 0.6, R.nextGaussian() * speed * 0.3, 0.04, R.nextGaussian() * speed * 0.3);
    }

    /** Digs a temporary bowl into the ground. */
    public static void crater(ServerWorld w, Vec3d at, double radius) {
        TempBlocks.Budget b = TempBlocks.Budget.make();
        BlockPos o = BlockPos.ofFloored(at);
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int dy = -r; dy <= 0; dy++) {
                    if (dx * dx + dz * dz + dy * dy * 2 > radius * radius) continue;
                    TempBlocks.dig(w, b, o.add(dx, dy - 1, dz), 0);
                }
    }

    // ------------------------------------------------------------------ 1. Rock Punch
    public static boolean rockPunch(Cast c) {
        ServerWorld w = c.w();
        Vec3d look = c.look();
        Snd.play(w, c.pos(), SoundEvents.BLOCK_GRAVEL_BREAK, 1.3f, 0.6f);
        Snd.play(w, c.pos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.6f);
        Fx.burst(w, "e_rock", c.pos().add(0, 0.2, 0), 14, 0.2, 0.8);                 // the rock tears out of the ground
        Fx.burst(w, "e_dust", c.pos().add(look.multiply(0.8)).add(0, 0.3, 0), 10, 0.1, 0.3);
        Ops.projectile(w, c.p(), c.eye().add(look.multiply(1.4)).add(0, -0.4, 0), look, 1.05, 26, 1.0, true,
                (pos, d, t) -> {
                    for (int i = 0; i < 5; i++) {
                        double a = t * 0.7 + i * 1.26;
                        Fx.vel(w, "e_big", pos.add(Math.cos(a) * 0.25, Math.sin(a) * 0.25, Math.sin(a * 0.5) * 0.25), Vec3d.ZERO);
                    }
                    Fx.vel(w, "e_dust", pos, d.multiply(-0.05));
                },
                (pos, target) -> {
                    Ops.blast(w, pos, c.p(), 2.6, 9f * c.mult(), 1.0, 0.35, null);
                    debris(w, pos, 28, 0.4);
                    Snd.play(w, pos, SoundEvents.BLOCK_STONE_BREAK, 1.6f, 0.6f);
                    Snd.play(w, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
                    Ops.shake(w, pos, 12, 3, 6);
                });
        return true;
    }

    // ------------------------------------------------------------------ 2. Earth Wall
    public static boolean wall(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d at = Ops.aimPoint(w, p, 7.0);
        Vec3d look = c.look();
        Vec3d dir = new Vec3d(look.x, 0, look.z).lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : new Vec3d(look.x, 0, look.z).normalize();
        Vec3d side = Ops.rightOf(dir);
        TempBlocks.Budget budget = TempBlocks.Budget.make();
        Snd.play(w, at, SoundEvents.BLOCK_STONE_BREAK, 1.6f, 0.5f);
        Snd.play(w, at, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.5f);
        Ops.shake(w, at, 14, 3, 8);
        for (int i = -3; i <= 3; i++) {
            Vec3d col = at.add(side.multiply(i));
            BlockPos g = Ops.groundPos(w, col);
            int dist = Math.abs(i);
            for (int h = 0; h < 4 - (dist > 2 ? 1 : 0); h++) {
                BlockPos q = g.up(h);
                Scheduler.later(1 + h * 2 + dist, () -> {
                    if (TempBlocks.place(w, budget, q, ROCK[R.nextInt(ROCK.length)], 0)) {
                        for (LivingEntity e : Ops.targets(w, new net.minecraft.util.math.Box(q), null)) {
                            e.addVelocity(0, 0.7, 0);
                            e.velocityModified = true;
                        }
                    }
                    Fx.burst(w, "e_dust", new Vec3d(q.getX() + 0.5, q.getY() + 0.2, q.getZ() + 0.5), 3, 0.08, 0.3);
                    Fx.vel(w, "e_rock", q.getX() + 0.5, q.getY() + 1.0, q.getZ() + 0.5, R.nextGaussian() * 0.05, 0.15, R.nextGaussian() * 0.05);
                });
            }
        }
        if (!com.avatar.core.AvatarConfig.INSTANCE.modifyBlocks) {              // no block changes: a wall of cubes instead
            Effects.add(t -> {
                if (t > 50) return false;
                for (int i = -3; i <= 3; i++)
                    for (int h = 0; h < 4; h++)
                        Fx.vel(w, "e_big", at.add(side.multiply(i)).add(0, h * 0.9 + 0.5, 0), Vec3d.ZERO);
                return true;
            });
        }
        return true;
    }

    // ------------------------------------------------------------------ 3. Earth Spikes
    public static boolean spikes(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d eye = c.eye(), look = c.look();
        TempBlocks.Budget budget = TempBlocks.Budget.make();
        Snd.play(w, c.pos(), SoundEvents.BLOCK_STONE_BREAK, 1.4f, 0.5f);
        List<LivingEntity> list = Ops.targets(w, new net.minecraft.util.math.Box(eye, eye.add(look.multiply(14))).expand(5), p).stream()
                .filter(e -> Ops.center(e).subtract(eye).normalize().dotProduct(look) > 0.75).limit(6).toList();
        if (list.isEmpty()) {
            Vec3d dir = new Vec3d(look.x, 0, look.z).lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : new Vec3d(look.x, 0, look.z).normalize();
            Effects.add(t -> {
                if (t > 10) return false;
                Vec3d at = p.getPos().add(dir.multiply(1.5 + t * 1.2));
                Water.spikeColumn(w, p, at, 3, ROCK, "e_rock", budget, 0);
                debris(w, at, 6, 0.2);
                for (LivingEntity e : Ops.around(w, at.add(0, 1, 0), 1.6, p)) {
                    Ops.hurt(w, e, p, 6f * c.mult());
                    e.setVelocity(e.getVelocity().add(0, 0.8, 0));
                    e.velocityModified = true;
                }
                return true;
            });
            return true;
        }
        for (LivingEntity e : list) {
            Vec3d at = e.getPos();
            for (int k = 0; k < 5; k++) {
                double a = k * 1.2566;
                Vec3d q = at.add(Math.cos(a) * 0.9, 0, Math.sin(a) * 0.9);
                Scheduler.later(1 + k, () -> {
                    Water.spikeColumn(w, p, q, 2 + R.nextInt(2), ROCK, "e_rock", budget, 0);
                    debris(w, q, 4, 0.15);
                });
            }
            Scheduler.later(2, () -> {
                if (!e.isAlive()) return;
                Ops.hurt(w, e, p, 8f * c.mult());
                e.setVelocity(e.getVelocity().add(0, 1.0, 0));
                e.velocityModified = true;
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
                Snd.play(w, e.getPos(), SoundEvents.BLOCK_STONE_BREAK, 1.2f, 0.7f);
            });
        }
        Ops.shake(w, c.pos(), 12, 3, 8);
        return true;
    }

    // ------------------------------------------------------------------ 4. Boulder Throw (hold to lift, release to throw)
    public static Vec3d boulderPos(Cast c, float ch) {
        Vec3d look = c.look();
        Vec3d flat = new Vec3d(look.x, 0, look.z).lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : new Vec3d(look.x, 0, look.z).normalize();
        return c.pos().add(flat.multiply(1.6)).add(0, 1.6 + 1.0 * ch, 0);
    }

    /** While the key is held: the rock rips out of the ground and grows above the player. */
    public static void chargeFx(Cast c, float ch) {
        ServerWorld w = c.w();
        Vec3d at = boulderPos(c, ch);
        double r = 0.45 + 0.75 * ch;
        for (int i = 0; i < 4 + (int) (6 * ch); i++) {
            double u = R.nextDouble() * 2 - 1, a = R.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u), rr = r * (0.4 + 0.6 * R.nextDouble());
            Fx.vel(w, "e_big", at.x + rr * s * Math.cos(a), at.y + rr * u, at.z + rr * s * Math.sin(a), 0, 0, 0);
        }
        if (R.nextInt(3) == 0) Fx.vel(w, "e_rock", c.pos().x + R.nextGaussian() * 0.8, c.pos().y + 0.1, c.pos().z + R.nextGaussian() * 0.8, 0, 0.18 + 0.1 * ch, 0);
        if (R.nextInt(5) == 0) Fx.vel(w, "e_dust", c.pos().x + R.nextGaussian() * 0.9, c.pos().y + 0.1, c.pos().z + R.nextGaussian() * 0.9, 0, 0.03, 0);
    }

    public static boolean boulder(Cast c) {
        ServerWorld w = c.w();
        float ch = Math.max(0.3f, c.charge());
        Vec3d look = c.look();
        Vec3d start = boulderPos(c, ch);
        double r = 0.45 + 0.75 * ch;
        Snd.play(w, c.pos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.4f, 0.5f);
        Snd.play(w, c.pos(), SoundEvents.BLOCK_STONE_BREAK, 1.2f, 0.5f);
        Ops.shake(w, c.pos(), 10, 3, 6);
        Vec3d aim = Ops.aimPoint(w, c.p(), 40).add(0, 0.5, 0).subtract(start);
        Vec3d dir = (c.p().isSneaking() ? look : (aim.lengthSquared() < 4 ? look : aim.normalize().add(look).normalize())).normalize();
        Ops.projectile(w, c.p(), start, dir, 0.95, 60, 0.8 + r, false,
                (pos, d, t) -> {
                    for (int i = 0; i < 6 + (int) (6 * ch); i++) {
                        double u = R.nextDouble() * 2 - 1, a = R.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
                        Fx.vel(w, "e_big", pos.x + r * s * Math.cos(a), pos.y + r * u, pos.z + r * s * Math.sin(a), 0, 0, 0);
                    }
                    Fx.vel(w, "e_dust", pos, d.multiply(-0.05));
                    if (t % 3 == 0) Fx.vel(w, "e_rock", pos.add(Fx.jitter(r)), Fx.jitter(0.05).add(0, -0.05, 0));
                },
                (pos, target) -> {
                    double rad = 3.2 + 2.2 * ch;
                    Ops.blast(w, pos, c.p(), rad, (11f + 15f * ch) * c.mult(), 1.4, 0.6, e -> e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 1)));
                    debris(w, pos, 60 + (int) (60 * ch), 0.6);
                    Ops.ring(w, pos.add(0, -0.4, 0), rad, 10, Element.EARTH);
                    Snd.play(w, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
                    Snd.play(w, pos, SoundEvents.BLOCK_STONE_BREAK, 2.0f, 0.5f);
                    Ops.shake(w, pos, 24, 5, 10);
                    crater(w, pos, 1.5 + ch * 1.5);
                });
        return true;
    }

    // ------------------------------------------------------------------ 5. Earthquake
    public static boolean earthquake(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d ctr = c.pos();
        TempBlocks.Budget budget = new TempBlocks.Budget(40);
        double maxR = 13;
        Snd.play(w, ctr, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.5f);
        Snd.play(w, ctr, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5f, 0.4f);
        Effects.add(t -> {
            if (t > 40 || !p.isAlive()) return false;
            if (t % 10 == 1) Ops.ring(w, ctr.add(0, -0.3, 0), maxR, 12, Element.EARTH);
            if (t % 2 == 0) Ops.shake(w, ctr, 28, 6, 5);
            if (t % 5 == 1) Snd.play(w, ctr, SoundEvents.BLOCK_STONE_BREAK, 2.0f, 0.4f);
            double reach = Math.min(maxR, t * 0.5 + 3);
            for (int k = 0; k < 3; k++) {                                // cracks racing out across the ground
                double a = R.nextDouble() * Math.PI * 2;
                double len = 4 + R.nextDouble() * (reach - 3);
                for (double d = 1.5; d < len; d += 0.7) {
                    Vec3d q = ctr.add(Math.cos(a) * d + R.nextGaussian() * 0.15, 0, Math.sin(a) * d + R.nextGaussian() * 0.15);
                    BlockPos g = Ops.groundPos(w, q);
                    Fx.vel(w, "e_dust", q.x, g.getY() + 0.1, q.z, 0, 0.07, 0);
                    if (R.nextInt(3) == 0) Fx.vel(w, "e_rock", q.x, g.getY() + 0.2, q.z, R.nextGaussian() * 0.04, 0.2 + R.nextDouble() * 0.2, R.nextGaussian() * 0.04);
                }
            }
            if (t % 4 == 0) {
                for (int i = 0; i < 2; i++) {
                    double a = R.nextDouble() * Math.PI * 2, d = 2 + R.nextDouble() * (reach - 2);
                    BlockPos g = Ops.groundPos(w, ctr.add(Math.cos(a) * d, 0, Math.sin(a) * d));
                    TempBlocks.dig(w, budget, g.down(), 220);
                }
            }
            if (t % 6 == 0) {
                for (LivingEntity e : Ops.around(w, ctr, reach, p)) {
                    if (!e.isOnGround()) continue;
                    e.setVelocity(e.getVelocity().add(R.nextGaussian() * 0.12, 0.38, R.nextGaussian() * 0.12));
                    e.velocityModified = true;
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2));
                    if (t % 12 == 0) Ops.hurt(w, e, p, 3f * c.mult());
                }
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 6. Rock Armor
    public static boolean armor(Cast c, PState s) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        s.rockArmorUntil = w.getTime() + 320;
        Snd.play(w, c.pos(), SoundEvents.BLOCK_STONE_BREAK, 1.5f, 0.6f);
        Snd.play(w, c.pos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.5f);
        debris(w, c.pos(), 20, 0.15);
        Effects.add(t -> {
            if (t > 320 || !p.isAlive() || p.isRemoved() || w.getTime() > s.rockArmorUntil) {
                if (p.isAlive()) {
                    Fx.burst(w, "e_rock", p.getPos().add(0, 1, 0), 24, 0.25, 0.4);
                    Snd.play(w, p.getPos(), SoundEvents.BLOCK_STONE_BREAK, 1.0f, 0.8f);
                }
                return false;
            }
            Vec3d c0 = p.getPos();
            if (t % 20 == 1) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 60, 1, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0, true, false, false));
            }
            double yaw = Math.toRadians(p.getBodyYaw());
            double[][] plates = {{0, 1.75, 0.0}, {0, 1.15, 0.38}, {0, 1.15, -0.38}, {0.5, 1.5, 0}, {-0.5, 1.5, 0}, {0.3, 0.45, 0}, {-0.3, 0.45, 0}, {0, 1.45, 0.3}};
            for (double[] pl : plates) {
                double x = pl[0] * Math.cos(yaw) - pl[2] * Math.sin(yaw), z = pl[0] * Math.sin(yaw) + pl[2] * Math.cos(yaw);
                Fx.vel(w, "e_big", c0.x + x, c0.y + pl[1], c0.z + z, 0, 0, 0);
            }
            if (t % 5 == 0) Fx.vel(w, "e_dust", c0.x + R.nextGaussian() * 0.4, c0.y + R.nextDouble() * 1.8, c0.z + R.nextGaussian() * 0.4, 0, 0.02, 0);
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 7. Metalbending
    static boolean metalItem(ItemStack st) {
        if (st == null || st.isEmpty()) return false;
        String id = Registries.ITEM.getId(st.getItem()).getPath();
        return id.contains("iron") || id.contains("chainmail") || id.contains("golden") || id.contains("netherite") || id.contains("copper");
    }

    static boolean metalBlock(BlockState bs) {
        String id = Registries.BLOCK.getId(bs.getBlock()).getPath();
        return id.contains("iron") || id.contains("chain") || id.contains("anvil") || id.contains("rail") || id.contains("netherite")
                || id.contains("gold_block") || id.contains("copper") || id.contains("bars") || id.contains("lantern") || id.contains("bell");
    }

    public static boolean metal(Cast c) {
        ServerWorld w = c.w();
        ServerPlayerEntity p = c.p();
        Vec3d eye = c.eye(), look = c.look();
        BlockHitResult bh = w.raycast(new RaycastContext(eye, eye.add(look.multiply(32)), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
        if (bh.getType() != HitResult.Type.MISS && metalBlock(w.getBlockState(bh.getBlockPos()))) {
            // metal cable: swing towards the metal block
            Vec3d anchor = Vec3d.ofCenter(bh.getBlockPos());
            Snd.play(w, c.pos(), SoundEvents.BLOCK_CHAIN_BREAK, 1.2f, 1.2f);
            Effects.add(t -> {
                if (t > 28 || !p.isAlive() || p.isRemoved()) return false;
                Vec3d to = anchor.subtract(p.getPos().add(0, 1, 0));
                double d = to.length();
                if (d < 2.0) return false;
                p.setVelocity(to.normalize().multiply(Math.min(1.1, 0.4 + t * 0.08)));
                p.velocityModified = true;
                p.fallDistance = 0;
                Vec3d from = p.getPos().add(0, 1.2, 0);
                for (int i = 0; i <= 14; i++) Fx.vel(w, "m_metal", from.add(anchor.subtract(from).multiply(i / 14.0)), Vec3d.ZERO);
                return true;
            });
            return true;
        }
        LivingEntity t0 = Ops.aimEntity(w, p, 26, 0.92);
        if (t0 == null) return false;
        int pieces = 0;
        for (EquipmentSlot sl : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            if (metalItem(t0.getEquippedStack(sl))) pieces++;
        ItemStack hand = t0.getMainHandStack();
        boolean tool = metalItem(hand);
        if (pieces == 0 && !tool) return false;
        Snd.play(w, t0.getPos(), SoundEvents.BLOCK_CHAIN_BREAK, 1.2f, 0.8f);
        Snd.play(w, t0.getPos(), SoundEvents.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
        if (tool) {
            ItemStack drop = hand.copy();
            t0.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            t0.dropStack(drop);                                         // the weapon is ripped out of their hand
        }
        int pcs = pieces;
        Effects.add(t -> {
            if (t > 14 || !t0.isAlive() || !p.isAlive()) return false;
            Vec3d from = p.getEyePos().add(0, -0.4, 0), to = Ops.center(t0);
            for (int i = 0; i <= 14; i++) Fx.vel(w, "m_metal", from.add(to.subtract(from).multiply(i / 14.0)), Vec3d.ZERO);
            Vec3d pull = p.getPos().subtract(t0.getPos());
            Ops.push(t0, pull, 0.3, 0.05);
            return true;
        });
        Ops.hurt(w, t0, p, (4f + 2.5f * pcs) * c.mult());
        Fx.burst(w, "m_metal", Ops.center(t0), 30, 0.4, 0.2);
        return true;
    }
}
