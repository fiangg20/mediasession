package dev.mediasession;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

final class Ui {
    static final int WHITE = 0xFFFFFFFF;
    static final int TEXT = 0xFFF4F4F8;
    static final int SUBTLE = 0xFFB6B6C2;
    static final int DIM = 0xFF7C7C8A;
    static final int DARK = 0xFF121216;
    static final int CARD = 0xEC111116;
    static final int BORDER = 0x28FFFFFF;
    static final int TRACK = 0x33FFFFFF;
    static final int DEFAULT_ACCENT = 0xFF8B7CF6;

    private Ui() {
    }

    static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    static int alpha(int argb, double multiplier) {
        int a = (int) Math.round(((argb >>> 24) & 0xFF) * clamp01(multiplier));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    static int mix(int from, int to, double t) {
        double k = clamp01(t);
        int a = lerp(from >>> 24, to >>> 24, k);
        int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, k);
        int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, k);
        int b = lerp(from & 0xFF, to & 0xFF, k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    static void hspan(GuiGraphicsExtractor g, int y, double left, double right, int color) {
        if (right - left <= 0.02) {
            return;
        }
        int li = (int) Math.ceil(left);
        int ri = (int) Math.floor(right);
        if (ri < li) {
            int px = (int) Math.floor(left);
            g.fill(px, y, px + 1, y + 1, alpha(color, right - left));
            return;
        }
        if (ri > li) {
            g.fill(li, y, ri, y + 1, color);
        }
        double lc = li - left;
        if (lc > 0.04) {
            g.fill(li - 1, y, li, y + 1, alpha(color, lc));
        }
        double rc = right - ri;
        if (rc > 0.04) {
            g.fill(ri, y, ri + 1, y + 1, alpha(color, rc));
        }
    }

    static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        if (h - 2 * r > 0) {
            g.fill(x, y + r, x + w, y + h - r, color);
        }
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            double inset = r - Math.sqrt(r * (double) r - dy * dy);
            hspan(g, y + row, x + inset, x + w - inset, color);
            hspan(g, y + h - 1 - row, x + inset, x + w - inset, color);
        }
    }

    static void circle(GuiGraphicsExtractor g, int cx, int cy, int diameter, int color) {
        roundRect(g, cx - diameter / 2, cy - diameter / 2, diameter, diameter, diameter / 2, color);
    }

    static void triangle(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean right, int color) {
        for (int row = 0; row < h; row++) {
            double t = (row + 0.5) / h;
            double span = w * (1.0 - Math.abs(2.0 * t - 1.0));
            double left = right ? x : x + w - span;
            double end = right ? x + span : x + w;
            hspan(g, y + row, left, end, color);
        }
    }

    static void play(GuiGraphicsExtractor g, int cx, int cy, int size, int color) {
        int w = (int) Math.round(size * 0.86);
        triangle(g, cx - w / 2 + Math.max(1, size / 10), cy - size / 2, w, size, true, color);
    }

    static void pause(GuiGraphicsExtractor g, int cx, int cy, int size, int color) {
        int bw = Math.max(2, (int) Math.round(size * 0.3));
        int gap = Math.max(2, (int) Math.round(size * 0.24));
        int left = cx - (bw * 2 + gap) / 2;
        roundRect(g, left, cy - size / 2, bw, size, 1, color);
        roundRect(g, left + bw + gap, cy - size / 2, bw, size, 1, color);
    }

    static void skip(GuiGraphicsExtractor g, int cx, int cy, int size, boolean forward, int color) {
        int tw = (int) Math.round(size * 0.78);
        int bw = Math.max(2, (int) Math.round(size * 0.18));
        int total = tw + 1 + bw;
        int left = cx - total / 2;
        if (forward) {
            triangle(g, left, cy - size / 2, tw, size, true, color);
            roundRect(g, left + tw + 1, cy - size / 2, bw, size, 1, color);
        } else {
            roundRect(g, left, cy - size / 2, bw, size, 1, color);
            triangle(g, left + bw + 1, cy - size / 2, tw, size, false, color);
        }
    }

    static void equalizer(GuiGraphicsExtractor g, int x, int baseY, int bars, int barWidth, int gap,
                          int maxHeight, int color, long nanos, boolean animate) {
        double t = nanos / 1.0e9;
        for (int i = 0; i < bars; i++) {
            double v = animate ? 0.5 + 0.5 * Math.sin(t * (3.1 + i * 1.7) + i * 1.9) : 0.35 + 0.12 * i;
            int h = Math.max(2, (int) Math.round(maxHeight * (0.25 + 0.75 * v)));
            int bx = x + i * (barWidth + gap);
            g.fill(bx, baseY - h, bx + barWidth, baseY, color);
        }
    }

    static void placeholder(GuiGraphicsExtractor g, int x, int y, int size, int accent) {
        roundRect(g, x, y, size, size, Math.max(4, size / 7), mix(accent, 0xFF0B0B10, 0.62));
        equalizer(g, x + size / 2 - 11, y + size / 2 + 8, 4, 4, 2, 18, alpha(WHITE, 0.55), 0L, false);
    }

    static Component boldText(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BOLD);
    }

    static int width(Font font, String s, boolean bold) {
        return bold ? font.width(boldText(s)) : font.width(s);
    }

    static String fit(Font font, String text, int max, boolean bold) {
        if (width(font, text, bold) <= max) {
            return text;
        }
        String s = text;
        while (!s.isEmpty() && width(font, s + "...", bold) > max) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }

    static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, boolean bold, boolean shadow) {
        if (bold) {
            g.text(font, boldText(s), x, y, color, shadow);
        } else {
            g.text(font, s, x, y, color, shadow);
        }
    }

    static String time(double seconds) {
        int t = (int) Math.max(0, Math.floor(seconds));
        int h = t / 3600;
        int m = (t % 3600) / 60;
        int s = t % 60;
        return h > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    static String prettySource(String id) {
        if (id == null || id.isBlank()) {
            return "Media";
        }
        String s = id;
        int bang = s.indexOf('!');
        if (bang > 0) {
            s = s.substring(0, bang);
        }
        s = s.replaceAll("(?i)\\.exe$", "");
        int underscore = s.indexOf('_');
        if (underscore > 0) {
            s = s.substring(0, underscore);
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0 && dot < s.length() - 1) {
            s = s.substring(dot + 1);
        }
        if (s.isEmpty()) {
            return "Media";
        }
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "zunemusic" -> "Media Player";
            case "msedge" -> "Edge";
            case "chrome" -> "Chrome";
            case "firefox" -> "Firefox";
            case "spotify" -> "Spotify";
            case "vlc" -> "VLC";
            default -> Character.toUpperCase(s.charAt(0)) + s.substring(1);
        };
    }

    static int accent(int rgb) {
        if (rgb == 0) {
            return DEFAULT_ACCENT;
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max < 24) {
            return DEFAULT_ACCENT;
        }
        double k = Math.min(3.2, 215.0 / max);
        double avg = (r + g + b) * k / 3.0;
        return 0xFF000000
                | channel(avg + (r * k - avg) * 1.35) << 16
                | channel(avg + (g * k - avg) * 1.35) << 8
                | channel(avg + (b * k - avg) * 1.35);
    }

    private static int channel(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}
