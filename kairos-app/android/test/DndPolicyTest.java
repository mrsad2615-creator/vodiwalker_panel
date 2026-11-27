package app.kairos.productivity;

public final class DndPolicyTest {
    public static void main(String[] args) {
        final int all = 1, priority = 2, none = 3, alarms = 4;
        check(DndPolicy.shouldAcquire(false, all, all), "acquire when all interruptions were allowed");
        check(!DndPolicy.shouldAcquire(true, all, all), "do not reacquire while already owned");
        check(!DndPolicy.shouldAcquire(false, priority, all), "do not own a user priority filter");
        check(!DndPolicy.shouldAcquire(false, none, all), "never weaken silence");
        check(!DndPolicy.shouldAcquire(false, alarms, all), "never weaken alarms-only");
        check(DndPolicy.shouldRestore(true, priority, priority), "restore an unchanged owned filter");
        check(!DndPolicy.shouldRestore(false, priority, priority), "do not touch a user-owned filter");
        check(!DndPolicy.shouldRestore(true, none, priority), "preserve a later user change");
        System.out.println("DndPolicyTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
