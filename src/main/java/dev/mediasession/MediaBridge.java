package dev.mediasession;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MediaBridge {
    private static final Logger LOG = LoggerFactory.getLogger("mediasession");
    private static final MediaBridge INSTANCE = new MediaBridge();

    private final Object writeLock = new Object();
    private volatile MediaState state = MediaState.EMPTY;
    private volatile Process process;
    private volatile boolean stopped;
    private Path dir;

    static MediaBridge get() {
        return INSTANCE;
    }

    static void start() {
        INSTANCE.launch();
    }

    MediaState state() {
        return state;
    }

    boolean available() {
        return process != null;
    }

    void toggle() {
        MediaState current = state;
        if (current.active()) {
            state = current.withStatus(current.playing() ? "Paused" : "Playing");
        }
        send("toggle");
    }

    void next() {
        send("next");
    }

    void previous() {
        send("prev");
    }

    void seek(double seconds) {
        MediaState current = state;
        double bounded = Math.max(0, current.duration() > 0 ? Math.min(seconds, current.duration()) : seconds);
        state = current.withPosition(bounded);
        send("seek\t" + bounded);
    }

    void seekBy(double delta) {
        MediaState current = state;
        if (current.active() && current.canSeek()) {
            seek(current.live() + delta);
        }
    }

    void cycleSession() {
        MediaState current = state;
        List<String> list = current.sessions();
        if (list.size() < 2) {
            return;
        }
        int index = list.indexOf(current.id());
        send("select\t" + list.get((index + 1) % list.size()));
    }

    void shutdown() {
        stopped = true;
        Process p = process;
        if (p != null) {
            p.destroy();
        }
    }

    private void launch() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            LOG.info("Media bridge disabled, Windows is required");
            return;
        }
        dir = FabricLoader.getInstance().getGameDir().resolve("cache").resolve("mediasession");
        Thread thread = new Thread(this::run, "mediasession-bridge");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        int failures = 0;
        while (!stopped && failures < 4) {
            long began = System.nanoTime();
            try {
                Files.createDirectories(dir);
                Path script = dir.resolve("bridge.ps1");
                try (InputStream in = MediaBridge.class.getResourceAsStream("/assets/mediasession/bridge.ps1")) {
                    Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
                }
                ProcessBuilder builder = new ProcessBuilder(
                        "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                        "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
                        "-File", script.toString(), "-OutDir", dir.toString());
                builder.redirectError(ProcessBuilder.Redirect.DISCARD);
                Process p = builder.start();
                process = p;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        accept(line);
                    }
                }
                p.waitFor();
            } catch (Exception e) {
                LOG.warn("Media bridge error: {}", e.toString());
            }
            process = null;
            state = MediaState.EMPTY;
            if (stopped) {
                break;
            }
            failures = (System.nanoTime() - began) > 10_000_000_000L ? 0 : failures + 1;
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void send(String command) {
        Process p = process;
        if (p == null) {
            return;
        }
        synchronized (writeLock) {
            try {
                OutputStream out = p.getOutputStream();
                out.write((command + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {
            }
        }
    }

    private void accept(String line) {
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            List<String> sessions = new ArrayList<>();
            if (o.has("sessions") && !o.get("sessions").isJsonNull()) {
                JsonElement e = o.get("sessions");
                if (e.isJsonArray()) {
                    for (JsonElement item : e.getAsJsonArray()) {
                        sessions.add(item.getAsString());
                    }
                } else if (e.isJsonPrimitive()) {
                    sessions.add(e.getAsString());
                }
            }
            state = new MediaState(
                    flag(o, "active"),
                    text(o, "id"),
                    text(o, "status"),
                    text(o, "title"),
                    text(o, "artist"),
                    text(o, "album"),
                    number(o, "position"),
                    number(o, "duration"),
                    flag(o, "canToggle"),
                    flag(o, "canNext"),
                    flag(o, "canPrev"),
                    flag(o, "canSeek"),
                    text(o, "art"),
                    (int) number(o, "accent"),
                    List.copyOf(sessions),
                    System.nanoTime());
        } catch (Exception ignored) {
        }
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static double number(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : 0;
    }

    private static boolean flag(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
    }
}
