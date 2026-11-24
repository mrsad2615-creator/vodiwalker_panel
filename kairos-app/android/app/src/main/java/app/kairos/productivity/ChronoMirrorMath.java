package app.kairos.productivity;

/** Pure calculations shared by the widget and service. Times are milliseconds. */
final class ChronoMirrorMath {
    private ChronoMirrorMath() {}

    static long remaining(boolean running, long endAt, int remainingSeconds, long now) {
        return running ? Math.max(0L, endAt - now) : Math.max(0L, (long) remainingSeconds * 1000L);
    }

    static long elapsed(boolean running, long anchor, int elapsedSeconds, long now) {
        long base = Math.max(0L, (long) elapsedSeconds * 1000L);
        return base + (running && anchor > 0 ? Math.max(0L, now - anchor) : 0L);
    }

    static int progress(int totalSeconds, long remainingMs) {
        if (totalSeconds <= 0) return -1;
        long total = (long) totalSeconds * 1000L;
        long remaining = Math.max(0L, Math.min(total, remainingMs));
        return (int) ((total - remaining) * 100L / total);
    }

    static String clock(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        long minute = seconds / 60L;
        long second = seconds % 60L;
        return persian(minute) + ":" + (second < 10 ? "۰" : "") + persian(second);
    }

    private static String persian(long number) {
        String digits = "۰۱۲۳۴۵۶۷۸۹";
        String latin = String.valueOf(number);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < latin.length(); i++) {
            char c = latin.charAt(i);
            out.append(c >= '0' && c <= '9' ? digits.charAt(c - '0') : c);
        }
        return out.toString();
    }
}
