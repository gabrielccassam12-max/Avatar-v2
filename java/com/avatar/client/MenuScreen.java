package com.avatar.client;

import com.avatar.core.AData;
import com.avatar.core.Element;
import com.avatar.core.Skill;
import com.avatar.net.SyncPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Progress overview: rank, mastery, secret techniques and their challenges, Avatar State (key U). */
public class MenuScreen extends Screen {
    public MenuScreen() {
        super(Text.translatable("screen.avatar.title"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        SyncPayload s = ClientState.last;
        int w = 300, x = (width - w) / 2, y = height / 2 - 112;
        g.fill(x - 8, y - 8, x + w + 8, y + 232, 0xD0080B12);
        Element el = s == null ? null : Element.of(s.element());
        int col = el == null ? 0xFFFFD35A : (0xFF000000 | el.color);
        g.drawBorder(x - 8, y - 8, w + 16, 240, col);
        g.drawTextWithShadow(textRenderer, title, x, y, col);
        if (s == null || el == null) {
            g.drawTextWithShadow(textRenderer, Text.translatable("message.avatar.choose_first"), x, y + 20, 0xFFFF7777);
            return;
        }
        int rank = AData.rankOf(s.xp());
        g.drawTextWithShadow(textRenderer, Text.empty().append(el.displayName()).append("  ·  ").append(Text.translatable("rank.avatar." + rank)), x, y + 14, 0xFFFFFFFF);
        String next = rank >= 5 ? "MAX" : s.xp() + " / " + AData.RANK_XP[rank + 1];
        g.drawTextWithShadow(textRenderer, Text.translatable("screen.avatar.mastery", next), x, y + 26, 0xFFFFE08A);

        g.drawTextWithShadow(textRenderer, Text.translatable("screen.avatar.secrets"), x, y + 44, col);
        int row = 0;
        for (Skill sk : Skill.values()) {
            if (!sk.isSecret() || sk.element != el) continue;
            int ry = y + 58 + row * 24;
            boolean has = (s.secrets() & (1L << sk.ordinal())) != 0;
            int have = s.stats().size() > sk.statIndex() ? s.stats().get(sk.statIndex()) : 0;
            g.drawTextWithShadow(textRenderer, sk.displayName(), x, ry, has ? 0xFF9CFF9C : 0xFFBBBBBB);
            Text info = has ? Text.translatable("screen.avatar.unlocked")
                    : Text.translatable("screen.avatar.challenge." + sk.name().toLowerCase(), Math.min(have, sk.statTarget()), sk.statTarget())
                    .append("  [").append(Text.translatable("rank.avatar." + sk.minRank)).append("]");
            g.drawTextWithShadow(textRenderer, info, x + 8, ry + 10, has ? 0xFF6FBF6F : 0xFF8890A0);
            row++;
        }
        int ay = y + 58 + row * 24 + 6;
        g.drawTextWithShadow(textRenderer, Text.translatable(s.avatarOk() ? "screen.avatar.avatar_ok" : "screen.avatar.avatar_locked"), x, ay, s.avatarOk() ? 0xFFFFE08A : 0xFFFF9A9A);
        g.drawTextWithShadow(textRenderer, Text.translatable("screen.avatar.keys"), x, ay + 14, 0xFF9AA6B4);
    }
}
