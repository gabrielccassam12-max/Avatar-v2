package com.avatar.client;

import com.avatar.Avatar;
import com.avatar.core.Element;
import com.avatar.core.Skill;
import com.avatar.net.ActionPayload;
import com.avatar.net.OpenChoicePayload;
import com.avatar.net.PosePayload;
import com.avatar.net.ShakePayload;
import com.avatar.net.StatePayload;
import com.avatar.net.SyncPayload;
import com.avatar.particle.CubeProfile;
import com.avatar.particle.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public class AvatarClient implements ClientModInitializer {
    private static final String CAT = "key.categories.avatar";
    /** The seven basic techniques of the element in use. */
    public static final KeyBinding[] SLOT = new KeyBinding[7];
    public static KeyBinding SPECIAL, NEXT, AVATAR, ELEMENT, MENU;
    private static final boolean[] down = new boolean[8];

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.avatar." + name, InputUtil.Type.KEYSYM, code, CAT));
    }

    @Override
    public void onInitializeClient() {
        for (Map.Entry<String, SimpleParticleType> en : ModParticles.TYPES.entrySet()) {
            CubeProfile pf = ModParticles.PROFILES.get(en.getKey());
            ParticleFactoryRegistry.getInstance().register(en.getValue(), sprites ->
                    (ParticleFactory<SimpleParticleType>) (type, world, x, y, z, vx, vy, vz) ->
                            new CubeParticle(world, x, y, z, vx, vy, vz, sprites, pf));
        }

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer r) helper.register(new AvatarFeature(r));
        });

        int[] codes = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M};
        for (int i = 0; i < SLOT.length; i++) SLOT[i] = key("slot" + (i + 1), codes[i]);
        SPECIAL = key("special", GLFW.GLFW_KEY_G);
        NEXT = key("next", GLFW.GLFW_KEY_R);
        AVATAR = key("avatar", GLFW.GLFW_KEY_H);
        ELEMENT = key("element", GLFW.GLFW_KEY_Y);
        MENU = key("menu", GLFW.GLFW_KEY_U);

        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) -> ClientState.set(payload));
        ClientPlayNetworking.registerGlobalReceiver(PosePayload.ID, (payload, context) -> {
            ClientState.startPose(payload.entityId(), payload.pose(), payload.ticks());
            MinecraftClient mc = context.client();
            if (mc.player != null && payload.entityId() == mc.player.getId()) {
                Element el = currentElement();
                ClientState.cast(payload.pose(), payload.ticks(), el == null ? 0xFFFFFF : el.color);
                handBurst(mc, el, 14);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.ID, (payload, context) ->
                ClientState.setState(payload.entityId(), payload.avatar(), payload.element()));
        ClientPlayNetworking.registerGlobalReceiver(ShakePayload.ID, (payload, context) ->
                ClientState.shake(payload.strength(), payload.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(OpenChoicePayload.ID, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(new ChoiceScreen())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.clear());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientState.tick(client);
            if (client.player == null) return;
            boolean free = client.currentScreen == null;
            for (int i = 0; i < SLOT.length; i++) edge(i, free && SLOT[i].isPressed(), 0, i);
            edge(7, free && SPECIAL.isPressed(), 1, 0);
            while (NEXT.wasPressed()) if (free) ClientPlayNetworking.send(new ActionPayload(3, 0, 0));
            while (AVATAR.wasPressed()) if (free) ClientPlayNetworking.send(new ActionPayload(2, 0, 0));
            while (ELEMENT.wasPressed()) if (free) ClientPlayNetworking.send(new ActionPayload(4, 0, 0));
            while (MENU.wasPressed()) if (free) client.setScreen(new MenuScreen());
            for (KeyBinding k : SLOT) while (k.wasPressed()) { }
            while (SPECIAL.wasPressed()) { }
            chargeFeel(client);
        });

        HudRenderCallback.EVENT.register(AvatarHud::render);
    }

    private static final int[] heldFor = new int[8];

    /** The element whose powers are on the keys right now (the chosen one, or the active one in Avatar State). */
    private static Element currentElement() {
        SyncPayload s = ClientState.last;
        if (s == null || s.element() < 0) return null;
        return Element.of(s.avatarOn() && s.active() >= 0 ? s.active() : s.element());
    }

    /** Zoom in and gather cubes at the hand while a chargeable technique is held. */
    private static void chargeFeel(MinecraftClient mc) {
        Element el = currentElement();
        float target = 0f;
        for (int i = 0; i < 8; i++) heldFor[i] = down[i] ? heldFor[i] + 1 : 0;
        if (el != null && mc.currentScreen == null) {
            for (int i = 0; i < 7; i++) {
                Skill sk = Skill.basic(el, i);
                if (down[i] && sk != null && sk.chargeable) target = Math.max(target, Math.min(1f, heldFor[i] / 30f));
            }
            SyncPayload s = ClientState.last;
            if (down[7] && s != null && s.avatarOn() && heldFor[7] > 3) target = Math.max(target, Math.min(1f, heldFor[7] / 60f));
        }
        ClientState.chargeTarget = target;
        if (target > 0.05f && el != null && mc.player != null && mc.world != null && (mc.player.age & 1) == 0) {
            Vec3d hand = handPos(mc);
            SimpleParticleType t = ModParticles.get(el.small);
            if (t == null) return;
            int n = 1 + (int) (target * 3);
            for (int i = 0; i < n; i++) {
                double r = 0.9 - target * 0.4;
                Vec3d o = hand.add((Math.random() - 0.5) * 2 * r, (Math.random() - 0.5) * 2 * r, (Math.random() - 0.5) * 2 * r);
                Vec3d v = hand.subtract(o).multiply(0.12);
                mc.world.addParticle(t, o.x, o.y, o.z, v.x, v.y, v.z);
            }
        }
    }

    private static Vec3d handPos(MinecraftClient mc) {
        Vec3d look = mc.player.getRotationVec(1f);
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        right = right.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : right.normalize();
        return mc.player.getEyePos().add(look.multiply(0.8)).add(right.multiply(0.35)).add(0, -0.35, 0);
    }

    /** A puff of cubes leaving the hand when a technique is cast. */
    private static void handBurst(MinecraftClient mc, Element el, int n) {
        if (el == null || mc.world == null || mc.player == null) return;
        SimpleParticleType t = ModParticles.get(el.small), t2 = ModParticles.get(el.accent);
        Vec3d hand = handPos(mc), look = mc.player.getRotationVec(1f);
        for (int i = 0; i < n; i++) {
            SimpleParticleType pt = i % 3 == 0 && t2 != null ? t2 : t;
            if (pt == null) continue;
            Vec3d v = look.multiply(0.12 + Math.random() * 0.12).add((Math.random() - 0.5) * 0.08, (Math.random() - 0.5) * 0.08, (Math.random() - 0.5) * 0.08);
            mc.world.addParticle(pt, hand.x, hand.y, hand.z, v.x, v.y, v.z);
        }
    }

    /** Sends "pressed" and "released" when the key changes state. */
    private static void edge(int idx, boolean now, int kind, int slot) {
        if (now && !down[idx]) ClientPlayNetworking.send(new ActionPayload(kind, slot, 0));
        if (!now && down[idx]) ClientPlayNetworking.send(new ActionPayload(kind, slot, 1));
        down[idx] = now;
    }
}
