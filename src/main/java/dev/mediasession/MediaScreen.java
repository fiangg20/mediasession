package dev.mediasession;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

final class MediaScreen extends Screen {
    private static final int W = 300;
    private static final int H = 152;
    private static final int PAD = 14;
    private static final int ART = 84;
    private static final int PREV = 0;
    private static final int PLAY = 1;
    private static final int NEXT = 2;
    private static final int BAR = 3;
    private static final int CYCLE = 4;

    private final long opened = System.nanoTime();
    private final float[] hover = new float[5];
    private long last = opened;
    private float knob;
    private boolean dragging;
    private double dragFraction;
    private int x;
    private int y;

    MediaScreen() {
        super(Component.literal("Media"));
    }

    @Override
    protected void init() {
        x = (this.width - W) / 2;
        y = (this.height - H) / 2;
    }

    private int barX() {
        return x + PAD + 36;
    }

    private int barW() {
        return W - 2 * PAD - 72;
    }

    private int ctrlX() {
        return x + W / 2;
    }

    private int ctrlY() {
        return y + 131;
    }

    private int cycleX() {
        return x + W - PAD - 36;
    }

    private static boolean near(double mx, double my, int cx, int cy, int r) {
        double dx = mx - cx;
        double dy = my - cy;
        return dx * dx + dy * dy <= (double) r * r;
    }

    private boolean overBar(double mx, double my) {
        return mx >= barX() - 4 && mx <= barX() + barW() + 4 && my >= y + 101 && my <= y + 119;
    }

    private boolean overCycle(double mx, double my) {
        return mx >= cycleX() && mx <= cycleX() + 36 && my >= y + 12 && my <= y + 26;
    }

    private boolean overCard(double mx, double my) {
        return mx >= x && mx <= x + W && my >= y && my <= y + H;
    }

    private double fraction(double mx) {
        return Ui.clamp01((mx - barX()) / barW());
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1.0e9);
        last = now;
        double open = Math.min(1.0, (now - opened) / 260.0e6);
        double e = 1 - Math.pow(1 - open, 3);
        int top = y + (int) Math.round((1 - e) * 16);

        MediaBridge bridge = MediaBridge.get();
        MediaState s = bridge.state();
        Identifier art = ArtTexture.update(s);
        int accent = Ui.accent(s.accent());

        g.fillGradient(0, 0, this.width, this.height, Ui.alpha(accent, 0.22 * e), Ui.alpha(0xFF000000, 0.35 * e));

        boolean[] over = new boolean[5];
        if (s.active()) {
            over[PREV] = near(mouseX, mouseY, ctrlX() - 54, ctrlY(), 17);
            over[PLAY] = near(mouseX, mouseY, ctrlX(), ctrlY(), 17);
            over[NEXT] = near(mouseX, mouseY, ctrlX() + 54, ctrlY(), 17);
            over[BAR] = overBar(mouseX, mouseY);
            over[CYCLE] = overCycle(mouseX, mouseY) && s.sessions().size() > 1;
        }
        double ease = 1 - Math.exp(-dt * 16);
        for (int i = 0; i < hover.length; i++) {
            hover[i] += (float) (((over[i] ? 1 : 0) - hover[i]) * ease);
        }
        knob += (float) ((((over[BAR] || dragging) ? 1 : 0) - knob) * ease);

        for (int i = 1; i <= 8; i++) {
            Ui.roundRect(g, x - i, top - i + 4, W + 2 * i, H + 2 * i, 16 + i, Ui.alpha(0xFF000000, 0.045));
        }
        Ui.roundRect(g, x - 1, top - 1, W + 2, H + 2, 17, Ui.BORDER);
        Ui.roundRect(g, x, top, W, H, 16, Ui.CARD);
        Ui.roundRect(g, x + 6, top + 6, W - 12, 98, 12, Ui.alpha(accent, 0.10));

        if (!s.active()) {
            drawIdle(g, top, accent, now, bridge.available());
        } else {
            drawActive(g, top, s, art, accent, now);
        }

        String hint = "Space  play / pause    Left / Right  seek 5s    Esc  close";
        g.text(this.font, hint, x + (W - this.font.width(hint)) / 2, top + H + 10, 0x66FFFFFF, false);
    }

    private void drawIdle(GuiGraphicsExtractor g, int top, int accent, long now, boolean available) {
        int cx = x + W / 2;
        Ui.equalizer(g, cx - 14, top + 52, 5, 4, 2, 26, Ui.alpha(accent, 0.55), now, false);
        String title = available ? "Nothing playing" : "Media bridge offline";
        String sub = available ? "Start something in any media app" : "Windows with PowerShell is required";
        Ui.text(g, this.font, title, cx - Ui.width(this.font, title, true) / 2, top + 76, Ui.TEXT, true, false);
        g.text(this.font, sub, cx - this.font.width(sub) / 2, top + 92, Ui.DIM, false);
    }

    private void drawActive(GuiGraphicsExtractor g, int top, MediaState s, Identifier art, int accent, long now) {
        int ax = x + PAD;
        int ay = top + PAD;
        Ui.roundRect(g, ax - 2, ay - 2, ART + 4, ART + 4, 16, Ui.alpha(accent, 0.18));
        if (art != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, art, ax, ay, 0, 0, ART, ART, 256, 256, 256, 256);
        } else {
            Ui.placeholder(g, ax, ay, ART, accent);
        }

        int tx = ax + ART + 12;
        int tw = x + W - PAD - tx;
        String source = Ui.prettySource(s.id()).toUpperCase(Locale.ROOT);
        Ui.text(g, this.font, source, tx, top + 17, accent, false, false);
        if (s.playing()) {
            Ui.equalizer(g, tx + this.font.width(source) + 6, top + 25, 3, 2, 1, 8, accent, now, true);
        }
        if (s.sessions().size() > 1) {
            drawCycle(g, top, s);
        }

        String title = s.title().isEmpty() ? "Unknown title" : s.title();
        Ui.text(g, this.font, Ui.fit(this.font, title, tw, true), tx, top + 31, Ui.TEXT, true, false);
        if (!s.artist().isEmpty()) {
            g.text(this.font, Ui.fit(this.font, s.artist(), tw, false), tx, top + 45, Ui.SUBTLE, false);
        }
        if (!s.album().isEmpty()) {
            g.text(this.font, Ui.fit(this.font, s.album(), tw, false), tx, top + 58, Ui.DIM, false);
        }

        double dur = s.duration();
        double shown = dragging ? dragFraction * dur : s.live();
        double frac = dur > 0 ? Ui.clamp01(shown / dur) : 0;
        int bx = barX();
        int bw = barW();
        int by = top + 108;
        Ui.roundRect(g, bx, by, bw, 4, 2, Ui.TRACK);
        int fw = (int) Math.round(bw * frac);
        if (fw > 0) {
            Ui.roundRect(g, bx, by, Math.max(fw, 4), 4, 2, accent);
        }
        int kd = 2 * Math.round(knob * 3.5f);
        if (kd >= 2 && dur > 0) {
            Ui.circle(g, bx + fw, by + 2, kd, Ui.WHITE);
        }
        String left = Ui.time(shown);
        String right = dur > 0 ? Ui.time(dur) : "--:--";
        g.text(this.font, left, x + PAD, by - 2, Ui.SUBTLE, false);
        g.text(this.font, right, x + W - PAD - this.font.width(right), by - 2, Ui.SUBTLE, false);

        int cx = ctrlX();
        int cy = top + 131;
        drawSkip(g, cx - 54, cy, false, s.canPrevious(), hover[PREV]);
        drawSkip(g, cx + 54, cy, true, s.canNext(), hover[NEXT]);
        drawPlay(g, cx, cy, s, accent, hover[PLAY]);
    }

    private void drawCycle(GuiGraphicsExtractor g, int top, MediaState s) {
        int px = cycleX();
        int py = top + 12;
        float hv = hover[CYCLE];
        Ui.roundRect(g, px, py, 36, 14, 7, Ui.alpha(Ui.WHITE, 0.07 + 0.10 * hv));
        int index = Math.max(0, s.sessions().indexOf(s.id())) + 1;
        String label = index + "/" + s.sessions().size();
        g.text(this.font, label, px + 7, py + 3, Ui.mix(Ui.SUBTLE, Ui.WHITE, hv), false);
        Ui.triangle(g, px + 27, py + 4, 4, 6, true, Ui.mix(Ui.DIM, Ui.WHITE, hv));
    }

    private void drawSkip(GuiGraphicsExtractor g, int cx, int cy, boolean forward, boolean enabled, float hv) {
        if (hv > 0.01f && enabled) {
            Ui.circle(g, cx, cy, 32, Ui.alpha(Ui.WHITE, 0.10 * hv));
        }
        int color = enabled ? Ui.mix(Ui.SUBTLE, Ui.WHITE, hv) : Ui.alpha(Ui.DIM, 0.55);
        Ui.skip(g, cx, cy, 12, forward, color);
    }

    private void drawPlay(GuiGraphicsExtractor g, int cx, int cy, MediaState s, int accent, float hv) {
        if (hv > 0.01f && s.canToggle()) {
            Ui.circle(g, cx, cy, 38, Ui.alpha(accent, 0.22 * hv));
        }
        int fill = s.canToggle() ? Ui.mix(0xFFE6E6EE, Ui.WHITE, hv) : Ui.alpha(Ui.WHITE, 0.35);
        Ui.circle(g, cx, cy, 32, fill);
        if (s.playing()) {
            Ui.pause(g, cx, cy, 12, Ui.DARK);
        } else {
            Ui.play(g, cx, cy, 14, Ui.DARK);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            MediaBridge bridge = MediaBridge.get();
            MediaState s = bridge.state();
            double mx = event.x();
            double my = event.y();
            if (!overCard(mx, my)) {
                this.onClose();
                return true;
            }
            if (s.active()) {
                if (near(mx, my, ctrlX() - 54, ctrlY(), 17) && s.canPrevious()) {
                    click();
                    bridge.previous();
                    return true;
                }
                if (near(mx, my, ctrlX() + 54, ctrlY(), 17) && s.canNext()) {
                    click();
                    bridge.next();
                    return true;
                }
                if (near(mx, my, ctrlX(), ctrlY(), 17) && s.canToggle()) {
                    click();
                    bridge.toggle();
                    return true;
                }
                if (overCycle(mx, my) && s.sessions().size() > 1) {
                    click();
                    bridge.cycleSession();
                    return true;
                }
                if (overBar(mx, my) && s.canSeek() && s.duration() > 0) {
                    dragging = true;
                    dragFraction = fraction(mx);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            dragFraction = fraction(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = false;
            MediaState s = MediaBridge.get().state();
            MediaBridge.get().seek(dragFraction * s.duration());
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (MediaSessionClient.OPEN.matches(event)) {
            this.onClose();
            return true;
        }
        MediaBridge bridge = MediaBridge.get();
        int key = event.key();
        if (key == InputConstants.KEY_SPACE) {
            bridge.toggle();
            return true;
        }
        if (key == InputConstants.KEY_LEFT) {
            bridge.seekBy(-5);
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            bridge.seekBy(5);
            return true;
        }
        return super.keyPressed(event);
    }
}
