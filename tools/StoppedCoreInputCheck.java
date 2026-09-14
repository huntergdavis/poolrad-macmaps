import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

/**
 * Focused JVM regression for the actual EmulatorFragment input binding bodies.
 * Extracts those methods unchanged, then compiles them with a recording Core
 * and the same three-method mouse-listener interface. No copied guard logic,
 * Android framework, native emulator, guest input or hardware acceptance.
 *
 * Run from the repository root with JDK 17:
 *   java tools/StoppedCoreInputCheck.java
 */
public final class StoppedCoreInputCheck {
    public static void main(String[] args) throws Exception {
        Path fragment = Path.of("android/minivmac/src/main/java/name/osher/gil/minivmac/EmulatorFragment.java");
        String source = Files.readString(fragment);
        require(source.contains("mScreenView.setOnMouseEventListener(mouseInput);"), "Screen must use the checked binding");
        require(source.contains("mTrackPadView.setOnMouseEventListener(mouseInput);"), "Trackpad must use the checked binding");
        String listener = method(source, "private ScreenView.OnMouseEventListener createMouseInputListener()");
        String release = method(source, "private void releaseAutomaticKey()");
        String probe = "public final class StoppedCoreProbe {\n" + FIXTURE + listener + "\n" + release + "\n" + CHECKS + "\n}";
        Path directory = Files.createTempDirectory("poolrad-stopped-core-check-");
        Path java = directory.resolve("StoppedCoreProbe.java");
        Files.writeString(java, probe, StandardCharsets.UTF_8);
        require(ToolProvider.getSystemJavaCompiler() != null, "A full JDK is required");
        require(ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", directory.toString(), java.toString()) == 0, "Actual callback fixture did not compile");
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()})) {
            try { loader.loadClass("StoppedCoreProbe").getMethod("run").invoke(null); }
            catch (InvocationTargetException failure) { throw new AssertionError("Actual input callback failed", failure.getCause()); }
        }
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        require(start >= 0 && source.indexOf(signature, start + 1) < 0, "Expected one actual method: " + signature);
        int opening = source.indexOf('{', start), depth = 0;
        for (int at = opening; at < source.length(); at++) {
            char c = source.charAt(at);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(start, at + 1);
            }
        }
        throw new AssertionError("Unterminated actual method: " + signature);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final String FIXTURE = """
        private volatile Core mCore;
        private int mAutomaticKey = -1;
        private Core mAutomaticKeyCore;
        private int cancellations;
        private boolean stopOnCancel;
        private void cancelAutomaticWheel() {
            cancellations++;
            releaseAutomaticKey();
            if (stopOnCancel) mCore = null;
        }
        private static final class ScreenView {
            interface OnMouseEventListener {
                void onMousePosition(int x, int y);
                void onMouseMove(int dx, int dy);
                void onMouseClick(boolean down);
            }
        }
        private static final class Core {
            boolean ready;
            int positions, moves, downs, ups, keyUps, x, y, dx, dy;
            Core(boolean ready) { this.ready = ready; }
            boolean isReady() { return ready; }
            void setMousePosition(int x, int y) { positions++; this.x=x; this.y=y; }
            void setMoveMouse(int dx, int dy) { moves++; this.dx=dx; this.dy=dy; }
            void setMouseBtn(Boolean down) { if (down) downs++; else ups++; }
            void keyUp(int key) { keyUps++; }
            int mouseCalls() { return positions + moves + downs + ups; }
        }
        private static void check(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
        }
        private static void gesture(ScreenView.OnMouseEventListener listener) {
            listener.onMousePosition(12, 34);
            listener.onMouseMove(-3, 4);
            listener.onMouseClick(true);
            listener.onMouseClick(false);
        }
        """;

    private static final String CHECKS = """
        public static void run() {
            StoppedCoreProbe fixture = new StoppedCoreProbe();
            ScreenView.OnMouseEventListener listener = fixture.createMouseInputListener();
            gesture(listener);
            check(fixture.cancellations == 1, "Stopped input must safely cancel automatic typing");

            Core cold = new Core(false); fixture.mCore = cold;
            gesture(listener);
            check(cold.mouseCalls() == 0, "Input reached an unready/stopped Core");

            Core live = new Core(true); fixture.mCore = live;
            gesture(listener);
            check(live.positions == 1 && live.moves == 1 && live.downs == 1 && live.ups == 1,
                    "Live mouse input must still be forwarded exactly once");
            check(live.x == 12 && live.y == 34 && live.dx == -3 && live.dy == 4,
                    "Live input coordinates changed");

            fixture.mCore = null;
            gesture(listener);
            check(live.mouseCalls() == 4, "Late shutdown events reached the previous Core");

            fixture.mCore = live; fixture.stopOnCancel = true;
            listener.onMouseClick(true);
            check(fixture.mCore == null && live.mouseCalls() == 4,
                    "Core stopping during mouse-down cancellation was dereferenced");
            fixture.stopOnCancel = false;

            fixture.mAutomaticKey = 7; fixture.mAutomaticKeyCore = live;
            listener.onMouseClick(true);
            check(live.keyUps == 0 && fixture.mAutomaticKey == -1 && fixture.mAutomaticKeyCore == null,
                    "Automatic-key cleanup touched a stopped Core or retained stale state");

            Core restarted = new Core(true); fixture.mCore = restarted;
            fixture.mAutomaticKey = 8; fixture.mAutomaticKeyCore = live;
            listener.onMouseClick(true);
            check(live.keyUps == 0 && restarted.keyUps == 0 && restarted.downs == 1,
                    "Stale automatic release crossed into a replacement Core");
            fixture.mAutomaticKey = 9; fixture.mAutomaticKeyCore = restarted;
            listener.onMouseClick(true);
            check(restarted.keyUps == 1 && fixture.mAutomaticKey == -1,
                    "Matching live automatic key must still release");
            System.out.println("PASS 8 actual mouse-binding/key-release checks; recording Core only, no device acceptance");
        }
        """;
}
