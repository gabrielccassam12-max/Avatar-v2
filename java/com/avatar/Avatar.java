package com.avatar;

import com.avatar.core.AvatarConfig;
import com.avatar.item.ModItems;
import com.avatar.net.ActionPayload;
import com.avatar.net.ChoosePayload;
import com.avatar.net.OpenChoicePayload;
import com.avatar.net.PosePayload;
import com.avatar.net.ShakePayload;
import com.avatar.net.StatePayload;
import com.avatar.net.SyncPayload;
import com.avatar.particle.ModParticles;
import com.avatar.server.Bender;
import com.avatar.util.Effects;
import com.avatar.util.Scheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Avatar implements ModInitializer {
    public static final String ID = "avatar";
    public static final Logger LOG = LoggerFactory.getLogger(ID);

    public static Identifier id(String path) {
        return Identifier.of(ID, path);
    }

    @Override
    public void onInitialize() {
        AvatarConfig.load();
        ModParticles.init();
        ModItems.init();
        Effects.init();
        Scheduler.init();
        Bender.init();

        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChoosePayload.ID, ChoosePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PosePayload.ID, PosePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShakePayload.ID, ShakePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenChoicePayload.ID, OpenChoicePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) ->
                Bender.action(context.player(), payload.kind(), payload.slot(), payload.phase()));
        ServerPlayNetworking.registerGlobalReceiver(ChoosePayload.ID, (payload, context) ->
                Bender.choose(context.player(), payload.element()));
        LOG.info("Avatar: the four elements are awake.");
    }
}
