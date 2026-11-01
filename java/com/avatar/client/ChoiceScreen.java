package com.avatar.client;

import com.avatar.core.Element;
import com.avatar.net.ChoosePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** "Choose your element" - shown on the first join. */
public class ChoiceScreen extends Screen {
    public ChoiceScreen() {
        super(Text.translatable("screen.avatar.choose"));
    }

    @Override
    protected void init() {
        int w = 140, h = 20, gap = 10, y = height / 2 - 40;
        for (Element e : Element.values()) {
            int col = e.ordinal() / 4, row = e.ordinal() % 4;
            int x = width / 2 - w - gap / 2 + col * (w + gap);
            addDrawableChild(ButtonWidget.builder(e.displayName().copy().styled(st -> st.withColor(e.color)), b -> {
                ClientPlayNetworking.send(new ChoosePayload(e.ordinal()));
                close();
            }).dimensions(x, y + row * 26, w, h).build());
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 78, 0xFFFFD35A);
        g.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.avatar.choose_hint"), width / 2, height / 2 - 62, 0xFFB8C4D0);
    }
}
