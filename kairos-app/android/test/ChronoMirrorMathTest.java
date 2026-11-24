package app.kairos.productivity;

/** Small JVM smoke test; no Android runtime required. */
public final class ChronoMirrorMathTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        check(ChronoMirrorMath.remaining(true, 15000, 99, 10000) == 5000);
        check(ChronoMirrorMath.remaining(false, 15000, 7, 20000) == 7000);
        check(ChronoMirrorMath.remaining(true, 15000, 0, 20000) == 0);
        check(ChronoMirrorMath.elapsed(true, 10000, 70, 13500) == 73500);
        check(ChronoMirrorMath.elapsed(false, 10000, 70, 13500) == 70000);
        check(ChronoMirrorMath.elapsed(true, 15000, 70, 10000) == 70000);
        check(ChronoMirrorMath.progress(100, 25000) == 75);
        check(ChronoMirrorMath.progress(100, 150000) == 0);
        check(ChronoMirrorMath.progress(100, -1) == 100);
        check(ChronoMirrorMath.progress(0, 0) == -1);
        check(ChronoMirrorMath.clock(73000).equals("۱:۱۳"));
        check(ChronoMirrorMath.clock(5000).equals("۰:۰۵"));
        System.out.println("ChronoMirrorMath: 12 checks passed");
    }
}
