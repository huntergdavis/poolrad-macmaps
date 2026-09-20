import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import name.osher.gil.minivmac.R;
import name.osher.gil.minivmac.WorldView;
import name.osher.gil.minivmac.mapper.ExploredMap;
import name.osher.gil.minivmac.mapper.WorldLayout;
import name.osher.gil.minivmac.notebook.AreaConnections;
import name.osher.gil.minivmac.notebook.ExplorationTrail;

/**
 * Actual detached Android View checks of the World tab: stitched placement on the
 * board, chip row, focus animation targets, touch panning, zoom controls and
 * accessibility selection. Not a live-game or notebook-storage test.
 *
 *   tools/render-check.sh WorldViewCheck
 */
public final class WorldViewCheck {
    private static Context context;
    private static float density;
    private static int passed;

    public static void main(String[] args) {
        try { checks(); } catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
    }

    private static AreaConnections.Edge edge(int from, int fx, int fy, int to, int tx, int ty) {
        return new AreaConnections.Edge(from, fy * 16 + fx, to, ty * 16 + tx);
    }
    private static AreaConnections history(AreaConnections.Edge... edges) throws Exception {
        AreaConnections h = new AreaConnections();
        for (AreaConnections.Edge e : edges) h = h.add(e);
        return h;
    }
    private static Map<Integer, WorldView.Sheet> sheets(int... areas) {
        Map<Integer, WorldView.Sheet> map = new HashMap<>();
        for (int a : areas) {
            ExplorationTrail trail = ExplorationTrail.empty();
            for (int i = 0; i < 40; i++) trail = trail.record(64 + i, i == 0 ? -1 : 63 + i);
            map.put(a, new WorldView.Sheet(new ExploredMap().geometry(a, trail), trail));
        }
        return map;
    }

    private static void checks() throws Exception {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        if (Typeface.DEFAULT == null) Typeface.class.getMethod("loadPreinstalledSystemFontMap").invoke(null);
        Class<?> type = Class.forName("android.app.ActivityThread");
        Object thread = type.getMethod("systemMain").invoke(null);
        Context system = (Context) type.getMethod("getSystemContext").invoke(thread);
        context = new ContextThemeWrapper(system, android.R.style.Theme_Material_Light_NoActionBar);
        density = context.getResources().getDisplayMetrics().density;
        final AreaConnections gate = history(edge(0, 0, 4, 20, 15, 4), edge(20, 15, 4, 0, 0, 4), edge(0, 5, 5, 21, 8, 8));

        run("an empty notebook shows the hint, no chips and disabled zoom", () -> {
            WorldView world = create(dp(600), dp(500));
            world.show("Notebook 1", new AreaConnections(), -1, null, new HashMap<>(), "");
            resize(world, dp(600), dp(500));
            check(world.layout().isEmpty(), "Empty history produced places");
            check(world.findViewById(R.id.companion_world_chips).getVisibility() == View.GONE, "Chip row shown with nothing to show");
            check(!world.findViewById(R.id.companion_world_zoom_in).isEnabled(), "Zoom enabled with no world");
            check(((TextView) world.findViewById(R.id.companion_world_footer)).getText().toString().contains("No areas discovered"), "Footer hint missing");
        });

        run("the gate stitches the Slums beside New Phlan and Sokal Keep is an island with a link", () -> {
            WorldView world = create(dp(600), dp(500));
            world.show("Notebook 1", gate, 0, null, sheets(0, 20, 21), "");
            resize(world, dp(600), dp(500));
            WorldLayout layout = world.layout();
            check(layout.places.size() == 2, "Expected the city and one island, got " + layout.places.size());
            check(layout.places.get(0).name.equals("New Phlan & Slums of Phlan"), "City name: " + layout.places.get(0).name);
            check(layout.find(20).x + WorldLayout.SIDE == layout.find(0).x && layout.find(20).y == layout.find(0).y, "Slums not stitched west of New Phlan");
            check(layout.links.size() == 1 && layout.links.get(0).toArea == 21, "The boat crossing is not the one link");
            check(chipCount(world) == 4, "Expected Whole world, Current area and two place chips, got " + chipCount(world));
            check(world.findViewById(R.id.companion_world_chip_all).isSelected(), "Whole world is not the initial focus");
            float[] fit = transform(world);
            check(fit[0] > 0, "Board never fitted");
            float worldWidthPx = layout.width * fit[0], worldHeightPx = layout.height * fit[0];
            View board = world.board();
            check(worldWidthPx <= board.getWidth() && worldHeightPx <= board.getHeight(), "Whole world does not fit the board");
            check(fit[1] >= -1 && fit[1] + worldWidthPx <= board.getWidth() + 1, "Whole world is not inside the board horizontally");
        });

        run("chips, Current area and the Fit control change the board's target and stay 48dp tall", () -> {
            WorldView world = create(dp(600), dp(500));
            world.show("Notebook 1", gate, 21, null, sheets(0, 20, 21), "");
            resize(world, dp(600), dp(500));
            float[] whole = transform(world);
            // The party's island leads the chip row; the two-district city is the second place.
            check(world.layout().places.get(0).name.equals("Sokal Keep"), "Party's island does not lead: " + world.layout().places.get(0).name);
            world.focusPlace(1); finish(world);
            check(world.focus().equals("place:1"), "Place focus not recorded");
            float[] city = transform(world);
            check(city[0] > whole[0], "Focusing the city did not zoom in");
            world.focusHere(); finish(world);
            check(world.focus().equals(WorldView.FOCUS_HERE) && world.selectedArea() == 21, "Current area focus did not select Sokal Keep");
            float[] here = transform(world);
            check(here[0] > city[0], "Framing one area is not closer than framing the two-district city");
            world.findViewById(R.id.companion_world_fit).performClick(); finish(world);
            check(world.focus().equals(WorldView.FOCUS_ALL), "Fit did not return to the whole world");
            float[] again = transform(world);
            check(Math.abs(again[0] - whole[0]) < .01f, "Fit did not restore the whole-world scale");
            for (int id : new int[]{R.id.companion_world_zoom_out, R.id.companion_world_zoom_in, R.id.companion_world_fit, R.id.companion_world_chip_all, R.id.companion_world_chip_here}) {
                View control = world.findViewById(id);
                check(control.getHeight() >= dp(40) && control.getWidth() >= dp(40), "Control smaller than a finger: " + id);
                check(!control.isFocusable(), "Control claims keyboard focus: " + id);
            }
            check(world.findViewById(R.id.companion_world_chips) instanceof HorizontalScrollView, "Chip row is not a scrolling strip");
            check(world.findViewById(R.id.companion_world_chips).getHeight() == dp(WorldView.ROW_DP), "Chip row height changed");
        });

        run("zoom buttons scale about the centre and stay within limits; dragging pans", () -> {
            WorldView world = create(dp(600), dp(500));
            world.show("Notebook 1", gate, 0, null, sheets(0, 20, 21), "");
            resize(world, dp(600), dp(500));
            float[] start = transform(world);
            world.findViewById(R.id.companion_world_zoom_in).performClick();
            float[] in = transform(world);
            check(Math.abs(in[0] - start[0] * 1.5f) < .01f, "Zoom in is not 1.5x");
            for (int i = 0; i < 20; i++) world.findViewById(R.id.companion_world_zoom_in).performClick();
            check(transform(world)[0] <= dp(48) + .01f, "Zoom exceeded 48dp per tile");
            for (int i = 0; i < 40; i++) world.findViewById(R.id.companion_world_zoom_out).performClick();
            check(transform(world)[0] >= density - .01f, "Zoom went below the floor");
            world.findViewById(R.id.companion_world_fit).performClick(); finish(world);
            float[] before = transform(world);
            View board = world.board();
            long now = SystemClock.uptimeMillis();
            touch(board, MotionEvent.ACTION_DOWN, board.getWidth() / 2f, board.getHeight() / 2f, now, now);
            touch(board, MotionEvent.ACTION_MOVE, board.getWidth() / 2f + dp(60), board.getHeight() / 2f + dp(20), now, now + 30);
            touch(board, MotionEvent.ACTION_UP, board.getWidth() / 2f + dp(60), board.getHeight() / 2f + dp(20), now, now + 60);
            float[] after = transform(world);
            check(Math.abs(after[1] - before[1] - dp(60)) <= dp(2) && Math.abs(after[2] - before[2] - dp(20)) <= dp(2), "Drag did not pan the board by the finger's travel");
            check(Math.abs(after[0] - before[0]) < .01f, "Drag changed the zoom");
        });

        run("selecting an area lists its crossings and the party marker follows the current area", () -> {
            WorldView world = create(dp(600), dp(500));
            world.show("Notebook 1", gate, 0, null, sheets(0, 20, 21), "");
            resize(world, dp(600), dp(500));
            check(world.board().performAccessibilityAction(0x04000000 + 20, null), "Accessible selection refused");
            check(world.selectedArea() == 20, "Slums not selected");
            String footer = ((TextView) world.findViewById(R.id.companion_world_footer)).getText().toString();
            check(footer.startsWith("Slums of Phlan") && footer.contains("New Phlan 0,4 → Slums of Phlan 15,4") && footer.contains("40 of 256 squares walked"), "Footer: " + footer);
            world.selectArea(0);
            footer = ((TextView) world.findViewById(R.id.companion_world_footer)).getText().toString();
            check(footer.contains("you are here") && footer.contains("Sokal Keep 8,8"), "Current-area footer: " + footer);
            String description = world.board().getContentDescription().toString();
            check(description.contains("3 areas in 2 places") && description.contains("Sokal Keep"), "Board description: " + description);
            // Growing the world by one crossing keeps the selection and refits.
            AreaConnections grown = gate.add(edge(20, 3, 3, 29, 9, 9));
            world.show("Notebook 1", grown, 0, null, sheets(0, 20, 21, 29), "");
            check(world.layout().places.size() == 3 && world.selectedArea() == 0, "Growth lost the selection or a place");
            check(chipCount(world) == 5, "Chip row did not grow");
            // A different notebook resets selection and focus.
            world.show("Notebook 2", new AreaConnections(), 20, null, new HashMap<>(), "");
            check(world.selectedArea() == -1 && world.focus().equals(WorldView.FOCUS_ALL) && world.layout().places.size() == 1, "Notebook change did not reset the view");
        });

        run("a narrow phone pane keeps every control and the board", () -> {
            WorldView world = create(dp(320), dp(400));
            world.show("Notebook 1", gate, 0, null, sheets(0, 20, 21), "");
            resize(world, dp(320), dp(400));
            check(world.board().getHeight() >= dp(400 - 48 - 48 - 60), "Board lost its height on a phone");
            Rect fit = bounds(world, world.findViewById(R.id.companion_world_fit));
            check(fit.right <= dp(320) && fit.height() >= dp(40), "Fit control clipped on a phone");
            float[] t = transform(world);
            check(t[0] > 0 && world.layout().width * t[0] <= world.board().getWidth() + 1, "Whole world does not fit a phone board");
        });

        System.out.println("PASS " + passed + " World tab actual Android View checks; no notebook-storage, travel-recording or live-game acceptance.");
    }

    private static WorldView create(int width, int height) {
        WorldView world = new WorldView(context);
        FrameLayout parent = new FrameLayout(context); parent.addView(world);
        resize(world, width, height); return world;
    }
    private static void resize(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
    /** Ends any running focus animation at its target. */
    private static void finish(WorldView world) throws RuntimeException {
        try {
            Field field = world.board().getClass().getDeclaredField("animator"); field.setAccessible(true);
            Object animator = field.get(world.board());
            if (animator != null) animator.getClass().getMethod("end").invoke(animator);
        } catch (Exception failure) { throw new RuntimeException(failure); }
    }
    /** Scale (px per tile), offsetX and offsetY of the board's transform. */
    private static float[] transform(WorldView world) {
        try {
            float[] values = new float[3]; String[] names = {"scale", "offsetX", "offsetY"};
            for (int i = 0; i < 3; i++) { Field f = world.board().getClass().getDeclaredField(names[i]); f.setAccessible(true); values[i] = f.getFloat(world.board()); }
            return values;
        } catch (Exception failure) { throw new RuntimeException(failure); }
    }
    private static int chipCount(WorldView world) {
        return ((ViewGroup) ((HorizontalScrollView) world.findViewById(R.id.companion_world_chips)).getChildAt(0)).getChildCount();
    }
    private static Rect bounds(ViewGroup root, View child) {
        Rect bounds = new Rect(); child.getDrawingRect(bounds); root.offsetDescendantRectToMyCoords(child, bounds); return bounds;
    }
    private static void touch(View view, int action, float x, float y, long down, long when) {
        MotionEvent.PointerProperties property = new MotionEvent.PointerProperties();
        property.id = 3; property.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords point = new MotionEvent.PointerCoords();
        point.x = x; point.y = y; point.pressure = 1; point.size = 1;
        MotionEvent event = MotionEvent.obtain(down, when, action, 1, new MotionEvent.PointerProperties[]{property},
                new MotionEvent.PointerCoords[]{point}, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { check(view.onTouchEvent(event), "Board touch escaped toward the guest"); } finally { event.recycle(); }
    }
    private static int dp(int value) { return Math.round(value * density); }
    private static void check(boolean test, String message) { if (!test) throw new AssertionError(message); }
    private interface Check { void run() throws Exception; }
    private static void run(String name, Check check) throws Exception { check.run(); passed++; System.out.println("PASS " + passed + ": " + name); }
}
