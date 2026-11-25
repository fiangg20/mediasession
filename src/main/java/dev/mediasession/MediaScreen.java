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
    private static final int W = 348;
    private static final int H = 116;
    private static final int COVER = 116;
    private static final int PREV = 0;
    private static final int PLAY = 1;
    private static final int NEXT = 2;
    private static final int BAR = 3;
    private static final int SESSION = 4;
    private static final int MUTE = 5;

    private final long opened = System.nanoTime();
    private final float[] hover = new float[6];
    private long last = opened;
    private boolean dragging;
    private double dragFraction;
    private int x;
    private int y;
    private int sessionX;
    private int sessionW;
    private int muteX;
    private int muteW;

    MediaScreen() {
        super(Component.literal("Media"));
    }

    @Override
    protected void init() {
        x = (this.width - W) / 2;
        y = (this.height - H) / 2;
    }

    private int x0() {
        return x + COVER + 14;
    }

    private int cw() {
        return W - COVER - 28;
    }

    private int prevX() {
        return x + W - 78;
    }

    private int nextX() {
        return x + W - 20;
    }

    private int playL() {
        return x + W - 62;
    }

    private int ctrlY() {
        return y + 91;
    }

    private String sessionLabel(MediaState s) {
        int index = Math.max(0, s.sessions().indexOf(s.id())) + 1;
        return index + "/" + s.sessions().size() + " >";
    }

    private void layoutChips(MediaState s) {
        int right = x + W - 14;
        if (s.active() && s.sessions().size() > 1) {
            sessionW = this.font.width(sessionLabel(s));
            sessionX = right - sessionW;
            right = sessionX - 14;
        } else {
            sessionW = 0;
            sessionX = 0;
        }
        muteW = 10 + this.font.width("MUTE MUSIC");
        muteX = right - muteW;
    }

    private boolean overRow(double mx, double my, int cx, int cw) {
        return cw > 0 && mx >= cx - 3 && mx <= cx + cw + 3 && my >= y + 7 && my <= y + 22;
    }

    private boolean overPlay(double mx, double my) {
        return mx >= playL() && mx < playL() + 26 && my >= y + 78 && my < y + 104;
    }

    private boolean overSkip(double mx, double my, int cx) {
        return Math.abs(mx - cx) <= 12 && Math.abs(my - ctrlY()) <= 13;
    }

    private boolean overBar(double mx, double my) {
        return mx >= x + COVER && mx <= x + W && my >= y + H - 10 && my <= y + H;
    }

    private boolean overPanel(double mx, double my) {
        return mx >= x && mx <= x + W && my >= y && my <= y + H;
    }

    private double fraction(double mx) {
        return Ui.clamp01((mx - (x + COVER)) / (double) (W - COVER));
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
        double open = Math.min(1.0, (now - opened) / 320.0e6);
        double e = 1 - Math.pow(1 - open, 4);

        MediaState s = MediaBridge.get().state();
        Identifier art = ArtTexture.update(s);
        int accent = Ui.accent(s.accent());
        layoutChips(s);

        boolean[] over = new boolean[6];
        over[MUTE] = overRow(mouseX, mouseY, muteX, muteW);
        if (s.active()) {
            over[PREV] = overSkip(mouseX, mouseY, prevX());
            over[PLAY] = overPlay(mouseX, mouseY);
            over[NEXT] = overSkip(mouseX, mouseY, nextX());
            over[BAR] = overBar(mouseX, mouseY) || dragging;
            over[SESSION] = overRow(mouseX, mouseY, sessionX, sessionW);
        }
        double ease = 1 - Math.exp(-dt * 18);
        for (int i = 0; i < hover.length; i++) {
            hover[i] += (float) (((over[i] ? 1 : 0) - hover[i]) * ease);
        }

        boolean wipe = open < 1.0;
        if (wipe) {
            g.enableScissor(x - 2, y - 2, x - 2 + (int) Math.round((W + 4) * e), y + H + 2);
        }
        drawPanel(g, s, art, accent, now);
        if (wipe) {
            g.disableScissor();
        }
    }

    private void drawPanel(GuiGraphicsExtractor g, MediaState s, Identifier art, int accent, long now) {
        g.fill(x, y, x + W, y + H, Ui.PANEL);

        if (s.active() && art != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, art, x, y, 0, 0, COVER, COVER, 256, 256, 256, 256);
        } else {
            g.fill(x, y, x + COVER, y + COVER, Ui.COVER_BG);
            String letter = s.active() ? Ui.initial(s.title()) : "-";
            int lw = Ui.width(this.font, letter, true) * 4;
            Ui.scaledText(g, this.font, letter, x + (COVER - lw) / 2, y + (COVER - 36) / 2, 4.0f, 0xFF34343A, true);
        }
        g.outline(x, y, W, H, Ui.BORDER);

        int x0 = x0();
        int cw = cw();

        String source = s.active() ? Ui.prettySource(s.id()).toUpperCase(Locale.ROOT) : "MEDIA";
        boolean playing = s.active() && s.playing();
        if (playing) {
            Ui.dot(g, x0, y + 12, true, Ui.alpha(accent, 0.65 + 0.35 * Math.sin(now / 1.0e9 * 4.0)));
        } else {
            Ui.dot(g, x0, y + 12, false, Ui.DIM);
        }
        int sourceMax = muteX - 12 - (x0 + 10);
        Ui.text(g, this.font, Ui.fit(this.font, source, sourceMax, false), x0 + 10, y + 10, Ui.DIM, false);
        drawChips(g, s);

        if (!s.active()) {
            Ui.marquee(g, this.font, "Nothing playing", x0, y + 25, cw, 2.0f, Ui.TEXT, true, now);
            String sub = MediaBridge.get().available()
                    ? "Start something in any media app"
                    : "Needs Windows with PowerShell";
            g.text(this.font, sub, x0, y + 51, Ui.SUBTLE, false);
            return;
        }

        String title = s.title().isEmpty() ? "Unknown title" : s.title();
        Ui.marquee(g, this.font, title, x0, y + 25, cw, 2.0f, Ui.TEXT, true, now);
        if (!s.artist().isEmpty()) {
            g.text(this.font, Ui.fit(this.font, s.artist(), cw, false), x0, y + 49, Ui.SUBTLE, false);
        }
        if (!s.album().isEmpty()) {
            g.text(this.font, Ui.fit(this.font, s.album(), cw, false), x0, y + 61, Ui.DIM, false);
        }

        double dur = s.duration();
        double shown = dragging ? dragFraction * dur : s.live();
        double frac = dur > 0 ? Ui.clamp01(shown / dur) : 0;

        String cur = Ui.time(shown);
        String total = " / " + (dur > 0 ? Ui.time(dur) : "--:--");
        g.text(this.font, cur, x0, y + 87, Ui.TEXT, false);
        g.text(this.font, total, x0 + this.font.width(cur), y + 87, Ui.DIM, false);

        drawSkip(g, prevX(), false, s.canPrevious(), hover[PREV]);
        drawSkip(g, nextX(), true, s.canNext(), hover[NEXT]);
        drawPlay(g, s, accent, hover[PLAY]);

        int bx = x + COVER;
        int bw = W - COVER;
        int bh = 2 + Math.round(2 * hover[BAR]);
        g.fill(bx, y + H - bh, bx + bw, y + H, Ui.TRACK);
        int fw = (int) Math.round(bw * frac);
        if (fw > 0) {
            g.fill(bx, y + H - bh, bx + fw, y + H, accent);
        }
        if (hover[BAR] > 0.05f && dur > 0) {
            g.fill(bx + fw - 1, y + H - 9, bx + fw + 1, y + H, Ui.alpha(Ui.PURE, hover[BAR]));
        }
    }

    private void drawChips(GuiGraphicsExtractor g, MediaState s) {
        boolean on = MusicMute.enabled();
        int color = Ui.mix(on ? Ui.SUBTLE : Ui.DIM, Ui.TEXT, hover[MUTE]);
        g.outline(muteX, y + 12, 5, 5, color);
        if (on) {
            g.fill(muteX + 1, y + 13, muteX + 4, y + 16, color);
        }
        g.text(this.font, "MUTE MUSIC", muteX + 10, y + 10, color, false);
        if (sessionW > 0) {
            g.text(this.font, sessionLabel(s), sessionX, y + 10, Ui.mix(Ui.DIM, Ui.TEXT, hover[SESSION]), false);
        }
    }

    private void drawSkip(GuiGraphicsExtractor g, int cx, boolean forward, boolean enabled, float hv) {
        int color = enabled ? Ui.mix(Ui.SUBTLE, Ui.PURE, hv) : Ui.alpha(Ui.DIM, 0.7);
        Ui.skip(g, cx, ctrlY(), 12, forward, color);
    }

    private void drawPlay(GuiGraphicsExtractor g, MediaState s, int accent, float hv) {
        int left = playL();
        int top = y + 78;
        int fill = s.canToggle() ? Ui.mix(Ui.TEXT, accent, hv * 0.85) : Ui.alpha(Ui.TEXT, 0.35);
        g.fill(left, top, left + 26, top + 26, fill);
        int cx = left + 13;
        int cy = top + 13;
        if (s.playing()) {
            Ui.pause(g, cx, cy, 12, Ui.DARK);
        } else {
            Ui.play(g, cx, cy, 12, Ui.DARK);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            MediaBridge bridge = MediaBridge.get();
            MediaState s = bridge.state();
            double mx = event.x();
            double my = event.y();
            layoutChips(s);
            if (!overPanel(mx, my)) {
                this.onClose();
                return true;
            }
            if (overRow(mx, my, muteX, muteW)) {
                click();
                MusicMute.setEnabled(!MusicMute.enabled());
                return true;
            }
            if (s.active()) {
                if (overSkip(mx, my, prevX()) && s.canPrevious()) {
                    click();
                    bridge.previous();
                    return true;
                }
                if (overSkip(mx, my, nextX()) && s.canNext()) {
                    click();
                    bridge.next();
                    return true;
                }
                if (overPlay(mx, my) && s.canToggle()) {
                    click();
                    bridge.toggle();
                    return true;
                }
                if (overRow(mx, my, sessionX, sessionW)) {
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
