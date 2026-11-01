package com.avatar.client;

import com.avatar.Avatar;
import com.avatar.core.Element;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Avatar State on a player: glowing white eyes, the airbender arrows in the colour of the element, and a soft halo. */
public class AvatarFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier WHITE = Avatar.id("textures/misc/white.png");

    public AvatarFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
        super(ctx);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity entity,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        ClientState.Vis v = ClientState.VIS.get(entity.getId());
        if (v == null || !v.avatar || entity.isInvisible()) return;
        java.util.function.Supplier<VertexConsumer> vc = () -> vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        PlayerEntityModel<AbstractClientPlayerEntity> m = getContextModel();
        Element el = Element.of(v.element);
        int tat = el == null ? 0x5AB8FF : el.color;
        float age = entity.age + tickDelta;
        int pulse = (int) (215 + 40 * Math.sin(age * 0.2));

        matrices.push();
        m.head.rotate(matrices);
        MatrixStack.Entry e = matrices.peek();
        // eyes (white-gold) with a halo
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side > 0 ? 1.0f : -3.2f, x1 = side > 0 ? 3.2f : -1.0f;
            Boxes.box(e, vc, x0, -4.7f, -4.12f, x1, -3.1f, -4.0f, 0xFFF4C8, 255, Boxes.FULL_LIGHT);
            Boxes.box(e, vc, x0 - 0.6f, -5.3f, -4.35f, x1 + 0.6f, -2.5f, -4.12f, 0xFFE9A0, 90, Boxes.FULL_LIGHT);
        }
        // arrow on the forehead
        Boxes.box(e, vc, -0.5f, -8.1f, -4.1f, 0.5f, -5.6f, -4.0f, tat, pulse, Boxes.FULL_LIGHT);
        Boxes.box(e, vc, -1.8f, -7.0f, -4.1f, -0.5f, -6.3f, -4.0f, tat, pulse, Boxes.FULL_LIGHT);
        Boxes.box(e, vc, 0.5f, -7.0f, -4.1f, 1.8f, -6.3f, -4.0f, tat, pulse, Boxes.FULL_LIGHT);
        matrices.pop();

        // arrows along both arms
        for (int side = 0; side < 2; side++) {
            ModelPart arm = side == 0 ? m.rightArm : m.leftArm;
            float cx = side == 0 ? -1f : 1f;
            matrices.push();
            arm.rotate(matrices);
            MatrixStack.Entry a = matrices.peek();
            Boxes.box(a, vc, cx - 0.35f, 0.4f, -2.34f, cx + 0.35f, 9.5f, -2.2f, tat, pulse, Boxes.FULL_LIGHT);
            Boxes.box(a, vc, cx - 0.35f, 0.4f, 2.2f, cx + 0.35f, 9.5f, 2.34f, tat, pulse, Boxes.FULL_LIGHT);
            matrices.pop();
        }
    }
}
