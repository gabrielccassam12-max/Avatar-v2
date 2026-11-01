package com.avatar.client;

import com.avatar.core.AData;
import com.avatar.core.Element;
import com.avatar.core.Skill;
import com.avatar.net.SyncPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.List;

/** Element, rank, energy and mastery bars, the seven techniques with cooldowns, the special slot and the Avatar State glow. */
public final class AvatarHud {
    private AvatarHud() {}

    private static int argb(int rgb, int a) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static void render(DrawContext g, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SyncPayload s = ClientState.last;
        if (mc.player == null || mc.options.hudHidden || s == null || s.element() < 0) return;
        long t = ClientState.time();
        Element own = Element.of(s.element());
        Element act = s.active() >= 0 ? Element.of(s.active()) : own;
        int col = act.color;
        int sw = g.getScaledWindowWidth(), sh = g.getScaledWindowHeight();

        if (ClientState.flash > 0.02f) {                              // colour flash at the screen edges when a technique fires
            int a = (int) (ClientState.flash * 150), edge = 36 + (int) (ClientState.flash * 40);
            int fc = ClientState.flashColor;
            g.fillGradient(0, 0, sw, edge, argb(fc, a), argb(fc, 0));
            g.fillGradient(0, sh - edge, sw, sh, argb(fc, 0), argb(fc, a));
        }
        if (ClientState.chargeTarget > 0.05f) {                       // tightening frame while charging
            int a = (int) (ClientState.chargeTarget * 110), e2 = (int) (10 + ClientState.chargeTarget * 26);
            g.fillGradient(0, 0, e2, sh, argb(col, a), argb(col, 0));
            g.fillGradient(sw - e2, 0, sw, sh, argb(col, 0), argb(col, a));
        }
        if (s.avatarOn()) {                                           // cinematic bars + golden edge glow
            int bar = 10 + (int) (2 * Math.sin(t * 0.1));
            g.fill(0, 0, sw, bar, argb(0x000000, 0xB0));
            g.fill(0, sh - bar, sw, sh, argb(0x000000, 0xB0));
            g.fillGradient(0, bar, sw, bar + 14, argb(0xFFD35A, 0x40), argb(0xFFD35A, 0x00));
            g.fillGradient(0, sh - bar - 14, sw, sh - bar, argb(0xFFD35A, 0x00), argb(0xFFD35A, 0x40));
        }

        ClientState.shownEnergy += (s.energy() - ClientState.shownEnergy) * 0.2f;
        float pct = Math.max(0f, Math.min(1f, ClientState.shownEnergy / Math.max(1, s.max())));

        int w = 138, x = sw - w - 12, rows = 7;
        int y = sh - 30 - rows * 12 - 68;
        int rank = AData.rankOf(s.xp());

        Text head = Text.empty().append(act.displayName()).append(" · ").append(Text.translatable("rank.avatar." + rank));
        g.drawTextWithShadow(mc.textRenderer, head, x, y, argb(col, 255));
        if (s.avatarOn() && (t / 5) % 2 == 0) g.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.avatar.state"), x, y - 11, 0xFFFFE08A);

        // energy bar
        int by = y + 12;
        g.fill(x - 3, by - 3, x + w + 3, by + 11, argb(0x0B0F14, 0xB4));
        g.drawBorder(x - 3, by - 3, w + 6, 14, argb(col, 0xFF));
        int fw = (int) (w * pct);
        if (fw > 0) {
            g.fillGradient(x, by, x + fw, by + 4, argb(0xFFFFFF, 0xE0), argb(col, 0xFF));
            g.fillGradient(x, by + 4, x + fw, by + 8, argb(col, 0xFF), argb(col, 0x80));
            int gx = x + (int) ((t * 2) % Math.max(1, fw));
            g.fill(gx, by, Math.min(gx + 3, x + fw), by + 8, argb(0xFFFFFF, 0x50));
        }
        String en = s.energy() + "/" + s.max();
        g.drawTextWithShadow(mc.textRenderer, en, x + w - mc.textRenderer.getWidth(en), by - 12, 0xFFC8D8E8);

        // mastery bar
        int my = by + 14;
        float mp = rank >= 5 ? 1f : (float) (s.xp() - AData.RANK_XP[rank]) / (AData.RANK_XP[rank + 1] - AData.RANK_XP[rank]);
        g.fill(x, my, x + w, my + 3, argb(0x0B0F14, 0xB4));
        g.fill(x, my, x + (int) (w * Math.max(0f, Math.min(1f, mp))), my + 3, argb(0xFFD35A, 0xFF));

        // the seven techniques
        int ay = my + 8;
        for (int i = 0; i < rows; i++) {
            Skill sk = Skill.basic(s.avatarOn() ? act : own, i);
            if (sk == null) continue;
            int ry = ay + i * 12;
            long left = Math.max(0, ClientState.cdEnd[sk.ordinal()] - t);
            boolean locked = !s.avatarOn() && rank < sk.minRank;
            boolean afford = s.energy() >= sk.cost * (s.avatarOn() ? 0.6 : 1.0);
            int tc2 = locked ? 0xFF666E7A : left > 0 ? 0xFF8A94A0 : afford ? 0xFFFFFFFF : 0xFFFF7777;
            g.fill(x - 3, ry - 1, x + w + 3, ry + 10, argb(0x0B0F14, 0x88));
            g.drawTextWithShadow(mc.textRenderer, Text.literal("[").append(AvatarClient.SLOT[i].getBoundKeyLocalizedText()).append("] ").append(sk.displayName()), x, ry + 1, tc2);
            String right = locked ? Text.translatable("rank.avatar." + sk.minRank).getString() : String.valueOf(sk.cost);
            g.drawTextWithShadow(mc.textRenderer, right, x + w - mc.textRenderer.getWidth(right), ry + 1, locked ? 0xFF666E7A : argb(col, 255));
            if (left > 0) {
                int total = Math.max(1, sk.cooldown);
                int cw = (int) (w * (1f - Math.min(1f, (float) left / total)));
                g.fill(x, ry + 9, x + w, ry + 10, argb(0x000000, 0x90));
                g.fill(x, ry + 9, x + cw, ry + 10, argb(col, 0xFF));
            }
        }

        // special slot (secret techniques, combos, supreme)
        int sy = ay + rows * 12 + 3;
        List<Skill> list = Skill.specials(act, s.secrets(), s.avatarOn());
        g.fill(x - 3, sy - 1, x + w + 3, sy + 10, argb(0x2A1A40, 0xA0));
        if (list.isEmpty()) {
            g.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.avatar.no_special"), x, sy + 1, 0xFF7A7A8A);
        } else {
            Skill sp = list.get(Math.floorMod(s.selected(), list.size()));
            long left = Math.max(0, ClientState.cdEnd[sp.ordinal()] - t);
            g.drawTextWithShadow(mc.textRenderer, Text.literal("[").append(AvatarClient.SPECIAL.getBoundKeyLocalizedText()).append("] ").append(sp.displayName()), x, sy + 1, left > 0 ? 0xFF9A8AB0 : 0xFFE0C8FF);
            String c2 = (s.selected() % list.size() + list.size()) % list.size() + 1 + "/" + list.size();
            g.drawTextWithShadow(mc.textRenderer, c2, x + w - mc.textRenderer.getWidth(c2), sy + 1, 0xFFB090E0);
            if (left > 0) {
                int cw = (int) (w * (1f - Math.min(1f, (float) left / Math.max(1, sp.cooldown))));
                g.fill(x, sy + 9, x + cw, sy + 10, 0xFFB090E0);
            }
        }
        if (s.avatarOk() && !s.avatarOn()) {
            g.drawTextWithShadow(mc.textRenderer, Text.literal("[").append(AvatarClient.AVATAR.getBoundKeyLocalizedText()).append("] ").append(Text.translatable("hud.avatar.ready")), x, sy + 13, 0xFFFFE08A);
        }
    }
}
