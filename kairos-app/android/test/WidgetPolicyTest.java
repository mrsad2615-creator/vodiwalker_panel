package app.kairos.productivity;

public final class WidgetPolicyTest {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Widget policy failed");
    }
    public static void main(String[] args) {
        check(WidgetPolicy.dailyRows(0) == 0);
        check(WidgetPolicy.dailyRows(159) == 0);
        check(WidgetPolicy.dailyRows(160) == 1);
        check(WidgetPolicy.dailyRows(204) == 1);
        check(WidgetPolicy.dailyRows(205) == 2);
        check(WidgetPolicy.dailyRows(255) == 3);
        check(WidgetPolicy.fresh("2026-11-20", "2026-11-20"));
        check(!WidgetPolicy.fresh("2026-11-19", "2026-11-20"));
        check(!WidgetPolicy.fresh(null, "2026-11-20"));
        check(WidgetPolicy.safeTaskId("tsk-1_a"));
        check(!WidgetPolicy.safeTaskId("../task?delete=1"));
        check(!WidgetPolicy.safeTaskId(""));
        check(!WidgetPolicy.safeTaskId(null));
    }
}
