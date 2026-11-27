package app.kairos.productivity;

/** Pure policy: never weaken an existing system interruption filter. */
final class DndPolicy {
    private DndPolicy() {}

    static boolean shouldAcquire(boolean alreadyOwned, int current, int all) {
        return !alreadyOwned && current == all;
    }

    static boolean shouldRestore(boolean owned, int current, int priority) {
        return owned && current == priority;
    }
}
