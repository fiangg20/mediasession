package dev.mediasession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.sounds.SoundSource;

final class MusicMute {
    private static final int RELEASE_DELAY_TICKS = 30;

    private static boolean enabled = true;
    private static boolean muted;
    private static boolean overridden;
    private static boolean checked;
    private static double saved;
    private static int idle;
    private static Path settingsFile;
    private static Path leftoverFile;

    private MusicMute() {
    }

    static void init() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("cache").resolve("mediasession");
        settingsFile = dir.resolve("settings.txt");
        leftoverFile = dir.resolve("music.txt");
        try {
            if (Files.exists(settingsFile)) {
                enabled = !Files.readString(settingsFile).trim().equals("mute=false");
            }
        } catch (IOException ignored) {
        }
    }

    static boolean enabled() {
        return enabled;
    }

    static void setEnabled(boolean value) {
        enabled = value;
        try {
            Files.createDirectories(settingsFile.getParent());
            Files.writeString(settingsFile, "mute=" + value);
        } catch (IOException ignored) {
        }
        if (!value) {
            release(Minecraft.getInstance());
        }
    }

    static void tick(Minecraft mc) {
        if (mc.options == null) {
            return;
        }
        if (!checked) {
            checked = true;
            restoreLeftover(mc);
        }
        MediaState s = MediaBridge.get().state();
        boolean playing = s.active() && s.playing();
        OptionInstance<Double> music = mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC);
        if (enabled && playing) {
            idle = 0;
            if (!muted && !overridden) {
                double current = music.get();
                if (current > 0.0) {
                    saved = current;
                    remember(current);
                    music.set(0.0);
                    muted = true;
                }
            } else if (muted && music.get() > 0.0) {
                muted = false;
                overridden = true;
                forget();
            }
        } else if (muted) {
            if (++idle >= RELEASE_DELAY_TICKS) {
                release(mc);
            }
        } else {
            overridden = false;
        }
    }

    static void shutdown() {
        release(Minecraft.getInstance());
    }

    private static void release(Minecraft mc) {
        if (muted && mc != null && mc.options != null) {
            mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(saved);
        }
        muted = false;
        overridden = false;
        idle = 0;
        forget();
    }

    private static void remember(double value) {
        try {
            Files.createDirectories(leftoverFile.getParent());
            Files.writeString(leftoverFile, Double.toString(value));
        } catch (IOException ignored) {
        }
    }

    private static void forget() {
        try {
            Files.deleteIfExists(leftoverFile);
        } catch (IOException ignored) {
        }
    }

    private static void restoreLeftover(Minecraft mc) {
        try {
            if (Files.exists(leftoverFile)) {
                double value = Double.parseDouble(Files.readString(leftoverFile).trim());
                if (value > 0.0 && mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get() == 0.0) {
                    mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(value);
                }
                Files.deleteIfExists(leftoverFile);
            }
        } catch (Exception ignored) {
        }
    }
}
