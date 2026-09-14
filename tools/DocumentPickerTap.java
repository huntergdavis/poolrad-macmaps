import android.os.Build;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

/** Emulator-only UI helper, not app code. Some API 30 DocumentsUI rows ignore
 * plain `adb shell input tap`; use an explicit finger event for the chosen row.
 * Run via app_process as the ADB shell, never inside the game/application.
 * Build/run commands are in docs/LOCAL_TESTING.md. */
public final class DocumentPickerTap {
    public static void main(String[] args) throws Exception {
        if (!"ranchu".equals(Build.HARDWARE) && !"goldfish".equals(Build.HARDWARE))
            throw new IllegalStateException("Only the Android test emulator is supported");
        if (args.length != 2) throw new IllegalArgumentException("Expected screen x y");
        float x = Float.parseFloat(args[0]), y = Float.parseFloat(args[1]);
        if (Float.isNaN(x) || Float.isInfinite(x) || Float.isNaN(y) || Float.isInfinite(y)
                || x < 0 || y < 0) throw new IllegalArgumentException("Invalid screen coordinates");
        Class<?> manager = Class.forName("android.hardware.input.InputManager");
        Object instance = manager.getMethod("getInstance").invoke(null);
        java.lang.reflect.Method inject = manager.getMethod("injectInputEvent", InputEvent.class, int.class);
        MotionEvent.PointerProperties p = new MotionEvent.PointerProperties();
        p.id = 0; p.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
        c.x = x; c.y = y; c.pressure = 1; c.size = 1;
        long down = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
                    new MotionEvent.PointerProperties[]{p}, new MotionEvent.PointerCoords[]{c},
                    0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
            try {
                if (!Boolean.TRUE.equals(inject.invoke(instance, event, 2)))
                    throw new IllegalStateException("Android declined injected input");
            } finally { event.recycle(); }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(100);
        }
        System.out.println("Injected one emulator finger tap");
    }
}
