package app.kairos.productivity;

/** Pure policy shared by widget renderers; never writes application data. */
final class WidgetPolicy {
    private WidgetPolicy() {}
    static int dailyRows(int minHeightDp) {
        if (minHeightDp >= 255) return 3;
        if (minHeightDp >= 205) return 2;
        if (minHeightDp >= 160) return 1;
        return 0;
    }
    static boolean fresh(String snapshotDay, String today) {
        return snapshotDay != null && today != null && today.equals(snapshotDay);
    }
    static boolean safeTaskId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,100}");
    }
}
