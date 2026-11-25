package dev.mediasession;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

final class Ui {
    static final int PURE = 0xFFFFFFFF;
    static final int TEXT = 0xFFF2F2F2;
    static final int SUBTLE = 0xFF9C9CA3;
    static final int DIM = 0xFF5F5F66;
    static final int DARK = 0xFF0A0A0B;
    static final int PANEL = 0xF20A0A0B;
    static final int BORDER = 0x33FFFFFF;
    static final int TRACK = 0x2EFFFFFF;
    static final int COVER_BG = 0xFF161618;

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
        g.fill(left, cy - size / 2, left + bw, cy + size / 2, color);
        g.fill(left + bw + gap, cy - size / 2, left + bw * 2 + gap, cy + size / 2, color);
    }

    static void skip(GuiGraphicsExtractor g, int cx, int cy, int size, boolean forward, int color) {
        int tw = (int) Math.round(size * 0.78);
        int bw = Math.max(2, (int) Math.round(size * 0.18));
        int total = tw + 1 + bw;
        int left = cx - total / 2;
        if (forward) {
            triangle(g, left, cy - size / 2, tw, size, true, color);
            g.fill(left + tw + 1, cy - size / 2, left + tw + 1 + bw, cy + size / 2, color);
        } else {
            g.fill(left, cy - size / 2, left + bw, cy + size / 2, color);
            triangle(g, left + bw + 1, cy - size / 2, tw, size, false, color);
        }
    }

    static void dot(GuiGraphicsExtractor g, int x, int y, boolean filled, int color) {
        if (filled) {
            g.fill(x, y, x + 5, y + 5, color);
        } else {
            g.outline(x, y, 5, 5, color);
        }
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

    static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, boolean bold) {
        if (bold) {
            g.text(font, boldText(s), x, y, color, false);
        } else {
            g.text(font, s, x, y, color, false);
        }
    }

    static void scaledText(GuiGraphicsExtractor g, Font font, String s, int x, int y, float scale, int color, boolean bold) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale, scale);
        text(g, font, s, 0, 0, color, bold);
        g.pose().popMatrix();
    }

    static void marquee(GuiGraphicsExtractor g, Font font, String s, int x, int y, int w, float scale,
                        int color, boolean bold, long nanos) {
        int tw = (int) Math.ceil(width(font, s, bold) * scale);
        if (tw <= w) {
            scaledText(g, font, s, x, y, scale, color, bold);
            return;
        }
        double over = tw - w;
        double speed = 32.0;
        double pause = 1.4;
        double run = over / speed;
        double total = 2 * (run + pause);
        double m = (nanos / 1.0e9) % total;
        double off;
        if (m < pause) {
            off = 0;
        } else if (m < pause + run) {
            off = (m - pause) * speed;
        } else if (m < 2 * pause + run) {
            off = over;
        } else {
            off = over - (m - 2 * pause - run) * speed;
        }
        g.enableScissor(x, y - 1, x + w, y + (int) Math.ceil(font.lineHeight * scale) + 2);
        scaledText(g, font, s, (int) Math.round(x - off), y, scale, color, bold);
        g.disableScissor();
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

    static String initial(String title) {
        if (title == null || title.isBlank()) {
            return "-";
        }
        return new String(Character.toChars(Character.toUpperCase(title.codePointAt(0))));
    }

    static int accent(int rgb) {
        if (rgb == 0) {
            return TEXT;
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max < 12) {
            return TEXT;
        }
        double k = max < 170 ? Math.min(3.0, 170.0 / max) : 1.0;
        return 0xFF000000 | channel(r * k) << 16 | channel(g * k) << 8 | channel(b * k);
    }

    private static int channel(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}
