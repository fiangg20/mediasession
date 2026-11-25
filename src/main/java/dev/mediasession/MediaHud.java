package dev.mediasession;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class MediaHud {
    private static final int W = 240;
    private static final int H = 56;

    private static long visibleUntil;
    private static long last = System.nanoTime();
    private static float appear;
    private static boolean seen;
    private static String lastKey = "";

    private MediaHud() {
    }

    static void poke() {
        visibleUntil = System.nanoTime() + 3_500_000_000L;
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        MediaState s = MediaBridge.get().state();
        long now = System.nanoTime();

        String key = s.trackKey();
        if (!seen) {
            if (s.received() != 0L) {
                seen = true;
                lastKey = key;
            }
        } else if (!key.equals(lastKey)) {
            lastKey = key;
            if (s.active() && !s.title().isEmpty()) {
                visibleUntil = now + 4_000_000_000L;
            }
        }

        double dt = Math.min(0.1, (now - last) / 1.0e9);
        last = now;
        boolean want = now < visibleUntil && s.active() && !(mc.gui.screen() instanceof MediaScreen);
        appear += (float) (((want ? 1 : 0) - appear) * (1 - Math.exp(-dt * 10)));
        if (!want && appear < 0.02f) {
            appear = 0;
            return;
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int x = sw - W - 12 + Math.round((1 - appear) * (W + 24));
        int y = 12;
        int accent = Ui.accent(s.accent());
        Identifier art = ArtTexture.update(s);

        g.fill(x, y, x + W, y + H, Ui.PANEL);
        if (art != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, art, x, y, 0, 0, H, H, 256, 256, 256, 256);
        } else {
            g.fill(x, y, x + H, y + H, Ui.COVER_BG);
            String letter = Ui.initial(s.title());
            int lw = Ui.width(mc.font, letter, true) * 3;
            Ui.scaledText(g, mc.font, letter, x + (H - lw) / 2, y + (H - 27) / 2, 3.0f, 0xFF34343A, true);
        }
        g.outline(x, y, W, H, Ui.BORDER);

        int tx = x + H + 12;
        int tw = x + W - 12 - tx;
        if (s.playing()) {
            Ui.dot(g, x + W - 17, y + 10, true, Ui.alpha(accent, 0.65 + 0.35 * Math.sin(now / 1.0e9 * 4.0)));
        } else {
            Ui.dot(g, x + W - 17, y + 10, false, Ui.DIM);
        }
        String title = s.title().isEmpty() ? "Unknown title" : s.title();
        Ui.marquee(g, mc.font, title, tx, y + 10, tw - 14, 1.0f, Ui.TEXT, true, now);
        String sub = s.artist().isEmpty() ? Ui.prettySource(s.id()) : s.artist();
        g.text(mc.font, Ui.fit(mc.font, sub, tw, false), tx, y + 24, Ui.SUBTLE, false);

        int bx = x + H;
        int bw = W - H;
        g.fill(bx, y + H - 2, bx + bw, y + H, Ui.TRACK);
        double frac = s.duration() > 0 ? Ui.clamp01(s.live() / s.duration()) : 0;
        int fw = (int) Math.round(bw * frac);
        if (fw > 0) {
            g.fill(bx, y + H - 2, bx + fw, y + H, accent);
        }
    }
}
