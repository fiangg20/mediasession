package dev.mediasession;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class MediaHud {
    private static final int W = 214;
    private static final int H = 48;

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
        appear += (float) (((want ? 1 : 0) - appear) * (1 - Math.exp(-dt * 9)));
        if (!want && appear < 0.02f) {
            appear = 0;
            return;
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int x = sw - W - 10 + Math.round((1 - appear) * (W + 20));
        int y = 10;
        int accent = Ui.accent(s.accent());
        Identifier art = ArtTexture.update(s);

        for (int i = 1; i <= 5; i++) {
            Ui.roundRect(g, x - i, y - i + 2, W + 2 * i, H + 2 * i, 12 + i, Ui.alpha(0xFF000000, 0.06));
        }
        Ui.roundRect(g, x - 1, y - 1, W + 2, H + 2, 13, Ui.BORDER);
        Ui.roundRect(g, x, y, W, H, 12, Ui.CARD);

        int size = 34;
        int ax = x + 7;
        int ay = y + 7;
        if (art != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, art, ax, ay, 0, 0, size, size, 256, 256, 256, 256);
        } else {
            Ui.placeholder(g, ax, ay, size, accent);
        }

        int tx = ax + size + 9;
        int tw = x + W - 34 - tx;
        String title = s.title().isEmpty() ? "Unknown title" : s.title();
        Ui.text(g, mc.font, Ui.fit(mc.font, title, tw, true), tx, y + 10, Ui.TEXT, true, false);
        String sub = s.artist().isEmpty() ? Ui.prettySource(s.id()) : s.artist();
        g.text(mc.font, Ui.fit(mc.font, sub, tw, false), tx, y + 23, Ui.SUBTLE, false);

        if (s.playing()) {
            Ui.equalizer(g, x + W - 26, y + 24, 4, 3, 1, 12, accent, now, true);
        } else {
            Ui.pause(g, x + W - 19, y + 18, 10, Ui.DIM);
        }

        int bx = x + 14;
        int bw = W - 28;
        int by = y + H - 8;
        Ui.roundRect(g, bx, by, bw, 2, 1, Ui.TRACK);
        double frac = s.duration() > 0 ? Ui.clamp01(s.live() / s.duration()) : 0;
        int fw = (int) Math.round(bw * frac);
        if (fw > 0) {
            Ui.roundRect(g, bx, by, Math.max(fw, 2), 2, 1, accent);
        }
    }
}
