package android.os;
import java.util.ArrayList;
import java.util.List;
/** Test fake: delayed runnables are collected; the test runs them explicitly. */
public class Handler {
    public final List<Runnable> delayed = new ArrayList<>();
    public Handler(Looper l) {}
    public boolean postDelayed(Runnable r, long ms) { delayed.add(r); return true; }
    public boolean post(Runnable r) { r.run(); return true; }
    public void removeCallbacks(Runnable r) { delayed.removeIf(x -> x == r); }
    /** Runs everything queued so far (time has passed). */
    public void runDelayed() { List<Runnable> now = new ArrayList<>(delayed); delayed.clear(); for (Runnable r : now) { r.run(); } }
}
