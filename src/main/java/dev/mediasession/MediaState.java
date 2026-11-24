package dev.mediasession;

import java.util.List;

record MediaState(
        boolean active,
        String id,
        String status,
        String title,
        String artist,
        String album,
        double position,
        double duration,
        boolean canToggle,
        boolean canNext,
        boolean canPrevious,
        boolean canSeek,
        String art,
        int accent,
        List<String> sessions,
        long received) {

    static final MediaState EMPTY = new MediaState(false, "", "Closed", "", "", "", 0, 0,
            false, false, false, false, "", 0, List.of(), 0L);

    boolean playing() {
        return "Playing".equals(status);
    }

    double live() {
        if (!playing() || received == 0L) {
            return position;
        }
        double value = position + (System.nanoTime() - received) / 1.0e9;
        return duration > 0 ? Math.min(value, duration) : value;
    }

    String trackKey() {
        return active ? id + "|" + title + "|" + artist : "";
    }

    MediaState withStatus(String newStatus) {
        return new MediaState(active, id, newStatus, title, artist, album, live(), duration,
                canToggle, canNext, canPrevious, canSeek, art, accent, sessions, System.nanoTime());
    }

    MediaState withPosition(double newPosition) {
        return new MediaState(active, id, status, title, artist, album, newPosition, duration,
                canToggle, canNext, canPrevious, canSeek, art, accent, sessions, System.nanoTime());
    }
}
