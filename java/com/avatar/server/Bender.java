package com.avatar.server;

import com.avatar.Avatar;
import com.avatar.core.AData;
import com.avatar.core.AvatarConfig;
import com.avatar.core.Element;
import com.avatar.core.Skill;
import com.avatar.net.OpenChoicePayload;
import com.avatar.net.PosePayload;
import com.avatar.net.StatePayload;
import com.avatar.net.SyncPayload;
import com.avatar.particle.Fx;
import com.avatar.util.Effects;
import com.avatar.util.Scheduler;
import com.avatar.util.Snd;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server brain: saved data, ranks and unlocks, energy, casting rules, Avatar State and syncing. */
public final class Bender {
    private Bender() {}

    public static final AttachmentType<AData> DATA = AttachmentRegistry.<AData>builder()
            .persistent(AData.CODEC)
            .copyOnDeath()
            .buildAndRegister(Avatar.id("bender"));

    private static final Map<UUID, PState> STATES = new HashMap<>();
    private static boolean GUARD = false;
    private static final java.util.Random R = Fx.R;

    public static AData get(ServerPlayerEntity p) {
        return ((AttachmentTarget) p).getAttachedOrElse(DATA, AData.DEFAULT);
    }

    public static void set(ServerPlayerEntity p, AData d) {
        ((AttachmentTarget) p).setAttached(DATA, d);
    }

    public static PState st(ServerPlayerEntity p) {
        PState s = STATES.computeIfAbsent(p.getUuid(), k -> new PState());
        if (s.active == null) {
            Element e = get(p).el();
            if (e != null) s.active = e;
        }
        return s;
    }

    public static void note(ServerPlayerEntity p, String key, Formatting f) {
        p.sendMessage(Text.translatable(key).formatted(f), true);
    }

    private static ServerWorld world(ServerPlayerEntity p) {
        return (ServerWorld) p.getWorld();
    }

    private static int maxEnergy(ServerPlayerEntity p, AData d) {
        return d.maxEnergy(st(p).avatarOn);
    }

    private static float damageMult(AData d, PState s) {
        return (float) (AvatarConfig.INSTANCE.damageMultiplier * (1.0 + 0.12 * d.rank()) * (s.avatarOn ? 1.6 : 1.0));
    }

    // ================================================================== init
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTicks();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                AData d = get(p);
                if (d.element() < 0) continue;
                PState s = st(p);
                if (!p.isAlive()) {
                    reset(p, s);
                    continue;
                }
                ServerWorld w = world(p);
                long now = w.getTime();
                boolean dirty = false;
                int max = maxEnergy(p, d);
                if (s.avatarOn) {
                    if (tick % 6 == 0) {
                        d = d.withEnergy(d.energy() - 1, max);
                        dirty = true;
                        if (d.energy() <= 0) {
                            set(p, d);
                            endAvatar(p, s);
                            d = get(p);
                        }
                    }
                    if (s.avatarOn) avatarTick(p, s, w, tick);
                } else if (tick % 8 == 0 && d.energy() < max) {
                    d = d.withEnergy(d.energy() + (int) Math.max(1, Math.round((1 + d.rank() / 2.0) * AvatarConfig.INSTANCE.energyRegenMultiplier)), max);
                    dirty = true;
                }
                if (s.flying) {
                    if (tick % 8 == 0) {
                        d = d.withEnergy(d.energy() - 1, max);
                        dirty = true;
                    }
                    if (tick % 10 == 0) d = d.addStat(AData.ST_FLIGHT, 10);
                    if (d.energy() <= 0) stopFlight(p, s);
                    if (R.nextInt(2) == 0) Fx.vel(w, "a_wind", p.getX() + R.nextGaussian() * 0.3, p.getY() + 0.8, p.getZ() + R.nextGaussian() * 0.3, 0, -0.02, 0);
                    dirty = true;
                }
                holdFx(p, s, w, now);
                if (dirty) set(p, d);
                if (dirty) checkUnlocks(p);
                if (dirty || tick % 10 == 0) sync(p);
                int hash = (s.avatarOn ? 1 : 0) | ((s.active == null ? 0 : s.active.ordinal()) << 1);
                if (hash != s.lastSentHash || tick % 100 == 0) {
                    s.lastSentHash = hash;
                    broadcastState(p);
                }
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            sync(p);
            broadcastState(p);
            if (get(p).element() < 0) Scheduler.later(60, () -> {
                if (!p.isRemoved() && get(p).element() < 0 && ServerPlayNetworking.canSend(p, OpenChoicePayload.ID))
                    ServerPlayNetworking.send(p, new OpenChoicePayload());
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            PState s = STATES.get(p.getUuid());
            if (s != null) reset(p, s);
            STATES.remove(p.getUuid());
        });

        // defences
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (GUARD || !(entity instanceof ServerPlayerEntity sp)) return true;
            PState s = STATES.get(sp.getUuid());
            if (s == null) return true;
            long now = sp.getWorld().getTime();
            if (s.spiritUntil > now) return false;
            float m = 1f;
            if (s.waterShieldUntil > now) m *= 0.35f;
            if (s.airShieldUntil > now) m *= source.getSource() instanceof ProjectileEntity ? 0f : 0.75f;
            if (s.rockArmorUntil > now) m *= 0.5f;
            if (s.iceArmorUntil > now) m *= 0.5f;
            if (s.shadowUntil > now) m *= 0.4f;
            if (s.etherealUntil > now) return false;
            if (s.avatarOn) m *= 0.7f;
            if (m >= 0.999f) return true;
            if (m <= 0.001f) return false;
            GUARD = true;
            try {
                sp.damage(source, amount * m);
            } finally {
                GUARD = false;
            }
            return false;
        });

        // kills feed mastery and the challenges of the secret techniques
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killed) -> {
            if (!(entity instanceof ServerPlayerEntity sp)) return;
            AData d = get(sp);
            PState s = STATES.get(sp.getUuid());
            if (d.element() < 0 || s == null || s.active == null) return;
            if (world.getTime() - s.lastCast > 80) return;                    // only kills made with bending count
            addXp(sp, 10);
            stat(sp, AData.killStat(s.active), 1);
        });

        registerCommands();
    }

    // ================================================================== casting
    public static void action(ServerPlayerEntity p, int kind, int slot, int phase) {
        if (!p.isAlive() || p.isSpectator()) return;
        AData d = get(p);
        if (d.element() < 0) {
            if (ServerPlayNetworking.canSend(p, OpenChoicePayload.ID)) ServerPlayNetworking.send(p, new OpenChoicePayload());
            return;
        }
        PState s = st(p);
        switch (kind) {
            case 0 -> {
                Skill sk = Skill.basic(s.avatarOn ? s.active : d.el(), slot);
                if (sk != null) press(p, d, s, sk, phase);
            }
            case 1 -> {
                List<Skill> list = Skill.specials(s.active, d.secrets(), s.avatarOn);
                if (list.isEmpty()) {
                    if (phase == 0) note(p, "message.avatar.no_special", Formatting.GRAY);
                    return;
                }
                press(p, d, s, list.get(Math.floorMod(s.sel, list.size())), phase);
            }
            case 2 -> {
                if (phase == 0) toggleAvatar(p);
            }
            case 3 -> {
                if (phase == 0) {
                    s.sel++;
                    sync(p);
                }
            }
            case 4 -> {
                if (phase == 0 && s.avatarOn) {
                    s.active = Element.of((s.active.ordinal() + 1) % Element.values().length);
                    s.sel = 0;
                    Snd.play(world(p), p.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
                    Fx.burst(world(p), s.active.accent, p.getPos().add(0, 1, 0), 24, 0.25, 0.3);
                    broadcastState(p);
                    sync(p);
                }
            }
            default -> { }
        }
    }

    private static void press(ServerPlayerEntity p, AData d, PState s, Skill sk, int phase) {
        int o = sk.ordinal();
        ServerWorld w = world(p);
        long now = w.getTime();
        if (!sk.chargeable) {
            if (phase == 0) fire(p, sk, 0f);
            return;
        }
        if (sk == Skill.ELEMENTAL_COLLAPSE) {
            if (phase == 0) {
                if (!canStart(p, d, s, sk)) return;
                s.pressAt[o] = now;
                s.holding[o] = true;
                broadcastPose(p, sk.pose, 140);
                Special.collapseStart(new Cast(w, p, damageMult(d, s), 0f, true), s, o);
            } else if (s.holding[o]) {
                s.holding[o] = false;
                if (s.collapsing) fire(p, sk, (float) Special.collapseCharge(s, now));
            }
            return;
        }
        if (phase == 0) {
            if (!canStart(p, d, s, sk)) return;
            s.pressAt[o] = now;
            s.holding[o] = true;
            broadcastPose(p, sk.pose, 40);
        } else if (s.holding[o]) {
            s.holding[o] = false;
            float full = sk == Skill.LIGHTNING ? 40f : 30f;
            fire(p, sk, Math.max(0f, Math.min(1f, (now - s.pressAt[o]) / full)));
        }
    }

    private static int costOf(Skill sk, PState s) {
        double c = AvatarConfig.cost(sk.cost) * (s.avatarOn ? 0.6 : 1.0);
        return Math.max(1, (int) Math.round(c));
    }

    /** Availability checks shared by instant and charged techniques; tells the player why not. */
    private static boolean canStart(ServerPlayerEntity p, AData d, PState s, Skill sk) {
        long now = world(p).getTime();
        if (sk.kind == 0 && !s.avatarOn && sk.element != d.el()) return false;
        if (sk.kind == 0 && !s.avatarOn && d.rank() < sk.minRank) {
            p.sendMessage(Text.translatable("message.avatar.rank_needed", Text.translatable("rank.avatar." + sk.minRank)).formatted(Formatting.RED), true);
            return false;
        }
        if (sk.kind == 1) {
            if (!d.has(sk)) return false;
            if (!p.hasPermissionLevel(AvatarConfig.INSTANCE.secretsPermissionLevel)) {
                note(p, "message.avatar.no_permission", Formatting.RED);
                return false;
            }
        }
        if (sk.kind >= 2 && !s.avatarOn) {
            note(p, "message.avatar.need_avatar", Formatting.RED);
            return false;
        }
        if (now < s.cd[sk.ordinal()]) {
            note(p, "message.avatar.cooldown", Formatting.GRAY);
            return false;
        }
        if (d.energy() < costOf(sk, s)) {
            note(p, "message.avatar.energy", Formatting.RED);
            return false;
        }
        return true;
    }

    private static void fire(ServerPlayerEntity p, Skill sk, float charge) {
        AData d = get(p);
        PState s = st(p);
        ServerWorld w = world(p);
        long now = w.getTime();
        boolean toggleOff = (sk == Skill.FLIGHT && s.flying) || (sk == Skill.SPIRIT_PROJECTION && s.spiritUntil > now);
        if (!toggleOff && !canStart(p, d, s, sk)) return;

        // chain attacks: use another technique soon after one -> stronger
        int chain = (now - s.lastCast <= 40 && s.combo > 0) ? s.combo + 1 : 1;
        float mult = damageMult(d, s) * (1f + 0.07f * Math.min(chain - 1, 6));
        if (s.sustained != null && sk != Skill.AIR_SCOOTER && sk != Skill.FLIGHT) {      // a new move cancels the old stance
            s.sustained.dead = true;
            s.sustained = null;
        }
        Cast c = new Cast(w, p, mult, charge, s.avatarOn);
        boolean ok;
        try {
            ok = dispatch(sk, c, s);
        } catch (RuntimeException e) {
            Avatar.LOG.error("technique " + sk + " failed", e);
            ok = false;
        }
        if (!ok) {
            if (!toggleOff) note(p, "message.avatar.fail", Formatting.GRAY);
            return;
        }
        int cost = toggleOff ? 0 : costOf(sk, s);
        d = get(p);                                                       // techniques may have changed the data (healing)
        d = d.withEnergy(d.energy() - cost, maxEnergy(p, d)).addStat(AData.ST_CASTS, 1);
        switch (sk) {
            case LIGHTNING, LIGHTNING_STORM -> d = d.addStat(AData.ST_LIGHTNING, 1);
            case METALBENDING -> d = d.addStat(AData.ST_METAL, 1);
            case EARTHQUAKE -> d = d.addStat(AData.ST_QUAKE, 1);
            case FLAMING_TORNADO, STORM, ERUPTION, MUD_PRISON, STEAM_BURST, ELEMENTAL_COLLAPSE -> d = d.addStat(AData.ST_COMBO, 1);
            default -> { }
        }
        set(p, d);
        s.cd[sk.ordinal()] = now + Math.max(1, (long) (AvatarConfig.cooldown(sk.cooldown) * (s.avatarOn ? 0.7 : 1.0)));
        s.combo = chain;
        s.lastCast = now;
        if (chain >= 3) p.sendMessage(Text.translatable("message.avatar.combo", chain).formatted(Formatting.GOLD), true);
        if (sk.pose > 0) broadcastPose(p, sk.pose, sk.poseTicks);
        addXp(p, 2 + sk.cost / 8);
        checkUnlocks(p);
        sync(p);
    }

    private static boolean dispatch(Skill sk, Cast c, PState s) {
        return switch (sk) {
            case FIRE_PUNCH -> Fire.punch(c);
            case FLAME_KICK -> Fire.kick(c);
            case FIREBALL -> Fire.fireball(c);
            case FIRE_BLAST -> Fire.blast(c);
            case JET_PROPULSION -> Fire.jet(c);
            case DRAGON_BREATH -> Fire.dragonBreath(c);
            case LIGHTNING -> Fire.lightning(c);
            case WATER_WHIP -> Water.whip(c);
            case WATER_BLADES -> Water.blades(c);
            case WATER_SHIELD -> Water.shield(c, s);
            case WATER_PRISON -> Water.prison(c);
            case ICE_SPIKES -> Water.iceSpikes(c);
            case ICE_CAGE -> Water.iceCage(c);
            case HEALING -> Water.healing(c);
            case ROCK_PUNCH -> Earth.rockPunch(c);
            case EARTH_WALL -> Earth.wall(c);
            case EARTH_SPIKES -> Earth.spikes(c);
            case BOULDER_THROW -> Earth.boulder(c);
            case EARTHQUAKE -> Earth.earthquake(c);
            case ROCK_ARMOR -> Earth.armor(c, s);
            case METALBENDING -> Earth.metal(c);
            case AIR_PUNCH -> Air.punch(c);
            case AIR_BLAST -> Air.blast(c);
            case AIR_SCOOTER -> Air.scooter(c, s);
            case TORNADO -> Air.tornado(c);
            case AIR_SHIELD -> Air.shield(c, s);
            case AIR_DASH -> Air.dash(c);
            case FLIGHT -> Air.flight(c, s);
            case LIGHTNING_STORM -> Special.lightningStorm(c);
            case COMBUSTION -> Special.combustion(c);
            case BLOODBENDING -> Special.bloodbending(c);
            case ADV_HEALING -> Special.advancedHealing(c);
            case METAL_SHRAPNEL -> Special.metalShrapnel(c);
            case LAVABENDING -> Special.lavabending(c);
            case SEISMIC_SENSE -> Special.seismic(c);
            case SPIRIT_PROJECTION -> Special.spirit(c, s);
            case PRESSURE -> Special.pressure(c);
            case FLAMING_TORNADO -> Special.flamingTornado(c);
            case STORM -> Special.storm(c);
            case ERUPTION -> Special.eruption(c);
            case MUD_PRISON -> Special.mudPrison(c);
            case STEAM_BURST -> Special.steamBurst(c);
            case ELEMENTAL_COLLAPSE -> Special.collapse(c, s);
            case SPARK_SHOT -> Thunder.spark(c);
            case ARC_SLASH -> Thunder.arc(c);
            case STATIC_FIELD -> Thunder.field(c);
            case BLINK_STRIKE -> Thunder.blink(c);
            case THUNDER_CLAP -> Thunder.clap(c);
            case CHAIN_STORM -> Thunder.chain(c);
            case SKY_JUDGMENT -> Thunder.judgment(c);
            case OVERLOAD -> Thunder.overload(c);
            case FROST_SHARD -> Ice.shard(c);
            case FROST_BREATH -> Ice.breath(c);
            case ICE_ARMOR -> Ice.armor(c, s);
            case GLACIER_WALL -> Ice.wall(c);
            case HAIL_STORM -> Ice.hail(c);
            case FLASH_FREEZE -> Ice.flash(c);
            case GLACIAL_COMET -> Ice.comet(c);
            case ABSOLUTE_ZERO -> Ice.absoluteZero(c);
            case SHADOW_BOLT -> Shadow.bolt(c);
            case TENDRILS -> Shadow.tendrils(c);
            case SHADOW_STEP -> Shadow.step(c, s);
            case SHADOW_CLOAK -> Shadow.cloak(c, s);
            case VOID_ORB -> Shadow.voidOrb(c);
            case NIGHTMARE -> Shadow.nightmare(c);
            case ECLIPSE -> Shadow.eclipse(c);
            case SHADOW_CLONES -> Shadow.clones(c);
            case SPIRIT_BOLT -> Spirit.bolt(c);
            case MENDING_LIGHT -> Spirit.mending(c);
            case GUARDIAN_SPIRIT -> Spirit.guardian(c);
            case ETHEREAL_FORM -> Spirit.ethereal(c, s);
            case SOUL_DRAIN -> Spirit.drain(c);
            case WRAITH_WAVE -> Spirit.wraith(c);
            case ASCENSION -> Spirit.ascension(c);
            case SPIRIT_AWAKENING -> Spirit.awakening(c);
        };
    }

    /** Visual feedback while a charged technique's key is held. */
    private static void holdFx(ServerPlayerEntity p, PState s, ServerWorld w, long now) {
        for (int i = 0; i < s.holding.length; i++) {
            if (!s.holding[i]) continue;
            Skill sk = Skill.values()[i];
            if (now - s.pressAt[i] > 600 || !p.isAlive()) {
                s.holding[i] = false;
                continue;
            }
            if (sk == Skill.ELEMENTAL_COLLAPSE) continue;
            float ch = Math.min(1f, (now - s.pressAt[i]) / (sk == Skill.LIGHTNING ? 40f : 30f));
            Cast c = new Cast(w, p, 1f, ch, s.avatarOn);
            switch (sk) {
                case FIREBALL -> {
                    Vec3d h = c.hand().add(c.look().multiply(0.4));
                    double sz = 0.1 + 0.3 * ch;
                    for (int k = 0; k < 3 + (int) (6 * ch); k++) Fx.vel(w, "f_core", h.add(Fx.jitter(sz)), Vec3d.ZERO);
                    Fx.vel(w, "f_fire", h.add(Fx.jitter(sz * 2)), Fx.jitter(0.02).add(0, 0.03, 0));
                    if (now % 6 == 0) Snd.play(w, h, SoundEvents.ITEM_FIRECHARGE_USE, 0.4f, 0.6f + ch);
                }
                case LIGHTNING -> {
                    Vec3d h = c.hand().add(c.look().multiply(0.4));
                    for (int k = 0; k < 2 + (int) (5 * ch); k++) {            // sparks crawl between the hands
                        Vec3d a = h.add(Fx.jitter(0.25 + 0.2 * ch));
                        Fx.vel(w, "l_bolt", a, Vec3d.ZERO);
                    }
                    if (R.nextInt(3) == 0) Fire.boltPath(w, h.add(Fx.jitter(0.3)), h.add(Fx.jitter(0.5)), 0.12);
                    if (now % 4 == 0) Snd.play(w, h, SoundEvents.BLOCK_BEACON_AMBIENT, 0.4f, 1.0f + ch);
                }
                case BOULDER_THROW -> {
                    Earth.chargeFx(c, ch);
                    if (now % 7 == 0) Snd.play(w, p.getPos(), SoundEvents.BLOCK_GRAVEL_BREAK, 0.8f, 0.5f + ch * 0.5f);
                }
                default -> { }
            }
        }
    }

    // ================================================================== avatar state
    public static void toggleAvatar(ServerPlayerEntity p) {
        AData d = get(p);
        PState s = st(p);
        if (!d.avatarOk()) {
            note(p, "message.avatar.avatar_locked", Formatting.RED);
            return;
        }
        if (!p.hasPermissionLevel(AvatarConfig.INSTANCE.avatarStatePermissionLevel)) {
            note(p, "message.avatar.no_permission", Formatting.RED);
            return;
        }
        if (s.avatarOn) {
            endAvatar(p, s);
            return;
        }
        if (d.energy() < 30) {
            note(p, "message.avatar.energy", Formatting.RED);
            return;
        }
        ServerWorld w = world(p);
        s.avatarOn = true;
        s.active = d.el();
        s.sel = 0;
        set(p, d.withEnergy(d.maxEnergy(true), d.maxEnergy(true)));
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.7f);
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.2f);
        Ops.shake(w, p.getPos(), 40, 6, 24);
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 20));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("message.avatar.avatar_sub").formatted(Formatting.GOLD)));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("message.avatar.avatar_on").formatted(Formatting.WHITE, Formatting.BOLD)));
        Vec3d base = p.getPos();
        for (Element e : Element.values()) Effects.groundRing(w, base.add(0, 0.05, 0), 8 + e.ordinal() * 1.2, 16, e.ring, e.small);
        Effects.groundRing(w, base.add(0, 0.05, 0), 12, 20, "s_ring", "s_gold");
        Effects.add(t -> {                                                    // column of light
            if (t > 36 || !p.isAlive()) return false;
            for (int i = 0; i < 18; i++) {
                double a = R.nextDouble() * 6.28, rr = R.nextDouble() * 1.4;
                Fx.vel(w, i % 3 == 0 ? "s_core" : "s_gold", base.x + Math.cos(a) * rr, base.y + R.nextDouble() * 2, base.z + Math.sin(a) * rr, 0, 0.3 + R.nextDouble() * 0.4, 0);
            }
            return true;
        });
        broadcastState(p);
        sync(p);
    }

    public static void endAvatar(ServerPlayerEntity p, PState s) {
        if (!s.avatarOn) return;
        s.avatarOn = false;
        AData d = get(p);
        s.active = d.el();
        s.sel = 0;
        for (int i = 0; i < s.holding.length; i++) s.holding[i] = false;
        p.setNoGravity(false);
        s.collapsing = false;
        set(p, d.withEnergy(d.energy(), d.maxEnergy(false)));
        ServerWorld w = world(p);
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.6f);
        Fx.burst(w, "s_gold", p.getPos().add(0, 1, 0), 60, 0.4, 0.3);
        Effects.groundRing(w, p.getPos().add(0, 0.05, 0), 6, 12, "s_ring", "s_gold");
        note(p, "message.avatar.avatar_off", Formatting.GRAY);
        broadcastState(p);
        sync(p);
    }

    private static void avatarTick(ServerPlayerEntity p, PState s, ServerWorld w, int tick) {
        if (tick % 20 == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 1, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 1, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, 1, true, false, false));
        }
        Vec3d c = p.getPos();
        double ang = tick * 0.12;
        int ne = Element.values().length;
        for (int i = 0; i < ne; i++) {                                        // all the elements circle the Avatar
            Element e = Element.values()[i];
            double rad = i < 4 ? 1.35 : 1.75;
            double a = (i < 4 ? ang : -ang * 0.8) + i * Math.PI / 2, y = 1.0 + Math.sin(tick * 0.08 + i * 1.7) * 0.7;
            Fx.vel(w, e.accent, c.x + Math.cos(a) * rad, c.y + y, c.z + Math.sin(a) * rad, 0, 0, 0);
            Fx.vel(w, e.small, c.x + Math.cos(a) * rad, c.y + y, c.z + Math.sin(a) * rad, 0, 0.01, 0);
        }
        if (tick % 2 == 0) Fx.vel(w, "s_gold", c.x + R.nextGaussian() * 0.4, c.y + R.nextDouble() * 2, c.z + R.nextGaussian() * 0.4, 0, 0.06, 0);
        if (tick % 4 == 0) {                                                  // eyes: two white-gold sparks
            Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f), r = Ops.rightOf(look);
            for (int sd = -1; sd <= 1; sd += 2) Fx.vel(w, "s_core", eye.add(look.multiply(0.3)).add(r.multiply(0.16 * sd)), Vec3d.ZERO);
        }
        if (tick % 60 == 0) Ops.shake(w, c, 6, 1, 6);
    }

    // ================================================================== state helpers
    private static void reset(ServerPlayerEntity p, PState s) {
        if (s.avatarOn) endAvatarQuiet(p, s);
        for (int i = 0; i < s.holding.length; i++) s.holding[i] = false;
        if (s.flying) stopFlight(p, s);
        if (s.sustained != null) {
            s.sustained.dead = true;
            s.sustained = null;
        }
        s.collapsing = false;
        s.spiritUntil = 0;
        s.waterShieldUntil = s.airShieldUntil = s.rockArmorUntil = 0;
        p.setNoGravity(false);
    }

    private static void endAvatarQuiet(ServerPlayerEntity p, PState s) {
        s.avatarOn = false;
        AData d = get(p);
        s.active = d.el();
        set(p, d.withEnergy(Math.min(d.energy(), d.maxEnergy(false)), d.maxEnergy(false)));
    }

    public static void stopFlight(ServerPlayerEntity p, PState s) {
        if (!s.flying) return;
        s.flying = false;
        var ab = p.getAbilities();
        if (s.grantedFlight && !p.isCreative() && !p.isSpectator()) {
            ab.allowFlying = false;
            ab.flying = false;
        }
        ab.setFlySpeed(0.05f);
        s.grantedFlight = false;
        p.sendAbilitiesUpdate();
        p.fallDistance = 0;
        Fx.burst(world(p), "a_wind", p.getPos().add(0, 0.5, 0), 16, 0.2, 0.2);
    }

    // ================================================================== progression
    public static void addXp(ServerPlayerEntity p, int n) {
        AData d = get(p);
        if (d.element() < 0) return;
        int before = d.rank();
        d = d.withXp(d.xp() + n);
        set(p, d);
        if (d.rank() > before) {
            ServerWorld w = world(p);
            p.sendMessage(Text.literal("★ ").formatted(Formatting.GOLD).append(Text.translatable("rank.avatar." + d.rank()).formatted(Formatting.GOLD, Formatting.BOLD)), false);
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 15));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("message.avatar.rank_up").formatted(Formatting.YELLOW)));
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("rank.avatar." + d.rank()).formatted(Formatting.GOLD, Formatting.BOLD)));
            Snd.play(w, p.getPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
            Fx.burst(w, d.el().small, p.getPos().add(0, 1, 0), 40, 0.35, 0.4);
            Effects.groundRing(w, p.getPos().add(0, 0.05, 0), 5, 10, d.el().ring, d.el().small);
        }
    }

    public static void stat(ServerPlayerEntity p, int idx, int n) {
        AData d = get(p);
        if (d.element() < 0) return;
        set(p, d.addStat(idx, n));
        checkUnlocks(p);
    }

    /** Secret techniques unlock when the challenge counter and the rank are reached. */
    public static void checkUnlocks(ServerPlayerEntity p) {
        AData d = get(p);
        if (d.element() < 0) return;
        for (Skill sk : Skill.values()) {
            if (!sk.isSecret() || sk.element != d.el() || d.has(sk)) continue;
            if (d.rank() >= sk.minRank && d.stat(sk.statIndex()) >= sk.statTarget()) unlock(p, sk);
        }
    }

    public static void unlock(ServerPlayerEntity p, Skill sk) {
        AData d = get(p);
        if (d.has(sk)) return;
        set(p, d.withSecrets(d.secrets() | (1L << sk.bit())));
        ServerWorld w = world(p);
        p.sendMessage(Text.translatable("message.avatar.secret_unlocked", sk.displayName()).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 50, 20));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(sk.displayName().copy().formatted(Formatting.LIGHT_PURPLE)));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("message.avatar.secret_title").formatted(Formatting.WHITE, Formatting.BOLD)));
        Snd.play(w, p.getPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        Fx.burst(w, "s_gold", p.getPos().add(0, 1, 0), 50, 0.35, 0.4);
        sync(p);
    }

    /** Scroll: mastery + the next secret technique of your element, challenge or not. */
    public static boolean useScroll(ServerPlayerEntity p) {
        AData d = get(p);
        if (d.element() < 0) {
            note(p, "message.avatar.choose_first", Formatting.RED);
            return false;
        }
        addXp(p, 300);
        d = get(p);
        for (Skill sk : Skill.values()) {
            if (sk.isSecret() && sk.element == d.el() && !d.has(sk)) {
                unlock(p, sk);
                break;
            }
        }
        sync(p);
        return true;
    }

    /** Avatar gem: opens the Avatar State, but only for a Legend. */
    public static boolean useGem(ServerPlayerEntity p) {
        AData d = get(p);
        if (d.element() < 0) {
            note(p, "message.avatar.choose_first", Formatting.RED);
            return false;
        }
        if (d.avatarOk()) {
            note(p, "message.avatar.already", Formatting.YELLOW);
            return false;
        }
        if (d.rank() < 5) {
            p.sendMessage(Text.translatable("message.avatar.gem_rank", Text.translatable("rank.avatar.5")).formatted(Formatting.RED), true);
            return false;
        }
        set(p, d.withAvatarOk(true));
        ServerWorld w = world(p);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 1.0f);
        Fx.burst(w, "s_core", p.getPos().add(0, 1, 0), 80, 0.6, 0.3);
        Effects.groundRing(w, p.getPos().add(0, 0.05, 0), 10, 16, "s_ring", "s_gold");
        p.sendMessage(Text.translatable("message.avatar.gem_ok").formatted(Formatting.GOLD, Formatting.BOLD), false);
        sync(p);
        return true;
    }

    public static void choose(ServerPlayerEntity p, int element) {
        AData d = get(p);
        Element e = Element.of(element);
        if (e == null || d.element() >= 0) return;
        d = d.withElement(element);
        set(p, d.withEnergy(d.maxEnergy(false), d.maxEnergy(false)));
        PState s = st(p);
        s.active = e;
        ServerWorld w = world(p);
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.0f);
        Fx.burst(w, e.small, p.getPos().add(0, 1, 0), 60, 0.4, 0.4);
        Effects.groundRing(w, p.getPos().add(0, 0.05, 0), 6, 12, e.ring, e.small);
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 50, 20));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("message.avatar.chosen_sub").formatted(Formatting.YELLOW)));
        p.networkHandler.sendPacket(new TitleS2CPacket(e.displayName().copy().formatted(Formatting.BOLD)));
        broadcastState(p);
        sync(p);
    }

    // ================================================================== network
    public static void sync(ServerPlayerEntity p) {
        AData d = get(p);
        PState s = st(p);
        long now = world(p).getTime();
        int[] cd = new int[Skill.values().length];
        for (int i = 0; i < cd.length; i++) cd[i] = (int) Math.max(0, s.cd[i] - now);
        if (!ServerPlayNetworking.canSend(p, SyncPayload.ID)) return;
        ServerPlayNetworking.send(p, new SyncPayload(d.element(), d.xp(), d.energy(), d.maxEnergy(s.avatarOn), d.secrets(), d.avatarOk(),
                s.avatarOn, s.active == null ? -1 : s.active.ordinal(), s.sel, cd, d.stats()));
    }

    public static void broadcastState(ServerPlayerEntity p) {
        PState s = st(p);
        StatePayload pl = new StatePayload(p.getId(), s.avatarOn, s.active == null ? -1 : s.active.ordinal());
        if (ServerPlayNetworking.canSend(p, StatePayload.ID)) ServerPlayNetworking.send(p, pl);
        for (ServerPlayerEntity o : PlayerLookup.tracking(p)) if (ServerPlayNetworking.canSend(o, StatePayload.ID)) ServerPlayNetworking.send(o, pl);
    }

    public static void broadcastPose(ServerPlayerEntity p, int pose, int ticks) {
        if (pose <= 0) return;
        PosePayload pl = new PosePayload(p.getId(), pose, ticks);
        if (ServerPlayNetworking.canSend(p, PosePayload.ID)) ServerPlayNetworking.send(p, pl);
        for (ServerPlayerEntity o : PlayerLookup.tracking(p)) if (ServerPlayNetworking.canSend(o, PosePayload.ID)) ServerPlayNetworking.send(o, pl);
    }

    // ================================================================== commands
    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("avatar")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("element").then(CommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            String n = StringArgumentType.getString(ctx, "name").toLowerCase();
                            int idx = switch (n) {
                                case "fire", "fogo" -> 0;
                                case "water", "agua", "água" -> 1;
                                case "earth", "terra" -> 2;
                                case "air", "ar" -> 3;
                                case "thunder", "raio", "lightning" -> 4;
                                case "ice", "gelo" -> 5;
                                case "shadow", "sombra" -> 6;
                                case "spirit", "espirito", "espírito" -> 7;
                                default -> -1;
                            };
                            if (idx < 0) return 0;
                            AData d = get(p).withElement(idx);
                            set(p, d.withEnergy(d.maxEnergy(false), d.maxEnergy(false)));
                            st(p).active = Element.of(idx);
                            st(p).avatarOn = false;
                            sync(p);
                            broadcastState(p);
                            return 1;
                        })))
                        .then(CommandManager.literal("choose").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            set(p, get(p).withElement(-1));
                            if (ServerPlayNetworking.canSend(p, OpenChoicePayload.ID)) ServerPlayNetworking.send(p, new OpenChoicePayload());
                            return 1;
                        }))
                        .then(CommandManager.literal("rank").then(CommandManager.argument("n", IntegerArgumentType.integer(0, 5)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            AData d = get(p).withXp(AData.RANK_XP[IntegerArgumentType.getInteger(ctx, "n")]);
                            set(p, d.withEnergy(d.maxEnergy(st(p).avatarOn), d.maxEnergy(st(p).avatarOn)));
                            checkUnlocks(p);
                            sync(p);
                            return 1;
                        })))
                        .then(CommandManager.literal("master").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            AData d = get(p);
                            if (d.element() < 0) d = d.withElement(0);
                            d = d.withXp(AData.RANK_XP[5]).withAvatarOk(true);
                            long all = d.secrets();
                            for (Skill sk : Skill.values()) if (sk.isSecret()) all |= 1L << sk.bit();
                            d = d.withSecrets(all);
                            set(p, d.withEnergy(d.maxEnergy(false), d.maxEnergy(false)));
                            st(p).active = d.el();
                            sync(p);
                            broadcastState(p);
                            return 1;
                        }))
                        .then(CommandManager.literal("unlock").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            AData d = get(p);
                            long all = d.secrets();
                            for (Skill sk : Skill.values()) if (sk.isSecret()) all |= 1L << sk.bit();
                            set(p, d.withSecrets(all));
                            sync(p);
                            return 1;
                        }))
                        .then(CommandManager.literal("avatar").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            set(p, get(p).withAvatarOk(true));
                            sync(p);
                            return 1;
                        }))
                        .then(CommandManager.literal("energy").then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 1000)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            AData d = get(p);
                            set(p, d.withEnergy(IntegerArgumentType.getInteger(ctx, "amount"), maxEnergy(p, d)));
                            sync(p);
                            return 1;
                        })))
                        .then(CommandManager.literal("reset").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            reset(p, st(p));
                            set(p, AData.DEFAULT);
                            STATES.remove(p.getUuid());
                            sync(p);
                            broadcastState(p);
                            if (ServerPlayNetworking.canSend(p, OpenChoicePayload.ID)) ServerPlayNetworking.send(p, new OpenChoicePayload());
                            return 1;
                        }))));
    }
}
