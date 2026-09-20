import android.content.Context;
import android.content.res.Configuration;
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
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import java.util.Arrays;
import java.util.Collections;
import name.osher.gil.minivmac.NoteEditorLayout;
import name.osher.gil.minivmac.notebook.InkNote;
import name.osher.gil.minivmac.notebook.InkSheetLayout;

/**
 * Actual detached Android View layout/input checks, not dialog/lifecycle/disk or physical-stylus acceptance.
 * Root separately checks the real upper-pane dialog and its autosave controls in the installed app.
 * Reuses InkInputCheck's framework initialization and normalized synthetic pointer input.
 *
 * From repository root (no Gradle or APK resources required):
 *   check_dir=$(mktemp -d scratch/note-editor-check.XXXXXX)
 *   source_dir=android/minivmac/src/main/java/name/osher/gil/minivmac
 *   javac --release 8 -cp /usr/lib/android-sdk/platforms/android-34/android.jar \
 *     -d "$check_dir/classes" tools/NoteEditorLayoutCheck.java \
 *     "$source_dir"/{NoteEditorLayout,InkSheetView,MapArtwork}.java \
 *     "$source_dir"/notebook/{InkNote,InkHistory,InkSheetLayout,InkViewport,NoteIcon,ExplorationTrail}.java \
 *     "$source_dir"/mapper/{PoolRadState,GeoMap,AreaIdentity}.java
 *   jar cf "$check_dir/classes.jar" -C "$check_dir/classes" .
 *   /usr/lib/android-sdk/build-tools/34.0.0/d8 --min-api 21 \
 *     --lib /usr/lib/android-sdk/platforms/android-34/android.jar \
 *     --output "$check_dir/note-editor-check.zip" "$check_dir/classes.jar"
 * Only the emulator owner runs:
 *   adb -s emulator-5584 push "$check_dir/note-editor-check.zip" /data/local/tmp/
 *   adb -s emulator-5584 shell \
 *     'CLASSPATH=/data/local/tmp/note-editor-check.zip app_process /system/bin NoteEditorLayoutCheck'
 */
public final class NoteEditorLayoutCheck {
    private static Context context;
    private static float density;
    private static int passed;

    public static void main(String[] args) {
        try { checks(); } catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
    }

    private static void checks() throws Exception {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        if (Typeface.DEFAULT == null) Typeface.class.getMethod("loadPreinstalledSystemFontMap").invoke(null);
        Class<?> type = Class.forName("android.app.ActivityThread");
        Object thread = type.getMethod("systemMain").invoke(null);
        Context system = (Context) type.getMethod("getSystemContext").invoke(thread);
        context = new ContextThemeWrapper(system, android.R.style.Theme_Material_Light_NoActionBar);
        density = context.getResources().getDisplayMetrics().density;

        run("one compact header doubles the old tablet sketch allocation", () -> {
            NoteEditorLayout editor = create(context, dp(960), dp(416));
            check(editor.getChildCount() == 2, "Expected only one header and the existing paper View");
            check(editor.sheet.getTop() <= dp(56), "Extra title, hint, status or footer consumed sketch height");
            float previous = 213 * density / 1.25f;
            check(editor.sheet.getHeight() >= previous * 2, "Sketch is not twice the measured old 213px allocation");
            InkSheetLayout paper = paper(editor);
            check(Math.abs(paper.width / paper.height - 8f / 3f) < .0001f, "Composite page was stretched");
            System.out.println("METRICS density=" + density + " pane=" + editor.getWidth() + "x" + editor.getHeight()
                    + " header=" + (editor.sheet.getTop() - editor.getPaddingTop())
                    + " sheet=" + editor.sheet.getWidth() + "x" + editor.sheet.getHeight()
                    + " paper=" + paper.width + "x" + paper.height + " priorSheet=" + previous);
            visibleClose(editor); checkSimpleControls(editor);
        });

        run("narrow tools scroll while Close remains fixed and visible", () -> {
            NoteEditorLayout editor = create(context, dp(320), dp(300));
            HorizontalScrollView tools = scroll(editor); check(tools != null, "Missing bounded tool strip");
            check(tools.getChildAt(0).getWidth() > tools.getWidth(), "Narrow tools were shrunk instead of scrollable");
            Rect before = bounds(editor, editor.close);
            tools.scrollTo(tools.getChildAt(0).getWidth(), 0);
            check(tools.getScrollX() > 0, "Tool strip cannot reach its trailing actions");
            check(before.equals(bounds(editor, editor.close)), "Close moved with the scrolling tools");
            visibleClose(editor); checkSimpleControls(editor);
            check(editor.sheet.getHeight() >= dp(240), "A second control row shrank the narrow sheet");
        });

        run("the journal link action joins the scrolling tools without taking sketch height", () -> {
            NoteEditorLayout wide = create(context, dp(960), dp(416));
            int reference = wide.sheet.getHeight();
            check(wide.journal.getText().toString().equals("Journal"), "Journal action is missing its label");
            check(bounds(wide, wide.journal).height() >= dp(48), "Journal target is smaller than the other tools");
            HorizontalScrollView tools = scroll(wide);
            check(tools != null && contains(tools, wide.journal), "Journal escaped the bounded scrolling tool strip");
            check(!contains(wide.sheet, wide.journal), "Journal was placed over the paper");
            check(reference >= 213 * density / 1.25f * 2, "Journal cost the sketch its 0.13.0 allocation");

            // A linked count must not push Close away or add a second row.
            Rect close = bounds(wide, wide.close);
            wide.journal.setText("Journal 8");
            resize(wide, dp(960), dp(416));
            check(wide.sheet.getHeight() == reference, "A linked count changed the drawing allocation");
            check(close.equals(bounds(wide, wide.close)), "A linked count moved Close & save");
            visibleClose(wide); checkSimpleControls(wide);

            NoteEditorLayout narrow = create(context, dp(320), dp(300));
            narrow.journal.setText("Journal 8"); resize(narrow, dp(320), dp(300));
            check(narrow.sheet.getHeight() >= dp(240), "Journal shrank the narrow sheet");
            visibleClose(narrow); checkSimpleControls(narrow);
        });

        run("template action stays in the compact scrolling toolbar", () -> {
            for(int width:new int[]{320,960}) {
                NoteEditorLayout editor=create(context,dp(width),dp(416));
                check(editor.template.getText().toString().equals("Template"),"Missing template action");
                check(contains(scroll(editor),editor.template),"Template escaped toolbar");
                check(editor.sheet.getTop()<=dp(56),"Template shrank writing height");
                visibleClose(editor);checkSimpleControls(editor);
            }
        });

        run("large text retains a complete Close target without growing another toolbar", () -> {
            Configuration config = new Configuration(context.getResources().getConfiguration()); config.fontScale = 1.6f;
            Context enlarged = new ContextThemeWrapper(context.createConfigurationContext(config),
                    android.R.style.Theme_Material_Light_NoActionBar);
            NoteEditorLayout editor = create(enlarged, dp(320), dp(300));
            visibleClose(editor); check(editor.sheet.getHeight() > dp(190), "Large text starved the drawing sheet");
            check(editor.close.getPaint().measureText(editor.close.getText().toString())
                    <= editor.close.getWidth() - editor.close.getPaddingLeft() - editor.close.getPaddingRight(),
                    "Close text is clipped at larger font scale");
        });

        run("resize, status changes and Fit preserve existing normalized handwriting", () -> {
            NoteEditorLayout editor = create(context, dp(960), dp(416));
            InkNote original = new InkNote(Collections.singletonList(
                    new InkNote.Stroke(false, .006f, new float[]{.55f,.3f,.72f,.6f,.88f,.4f})));
            editor.sheet.setNote(original); final int[] changes = {0}; editor.sheet.setOnChangeListener(() -> changes[0]++);
            editor.status.setText("Save failed. Your ink is still here; retry Close & save.");
            resize(editor, dp(320), dp(300)); editor.sheet.fitPage(); resize(editor, dp(960), dp(416));
            same(original, editor.sheet.getNote()); check(changes[0] == 0, "Layout/navigation emitted an autosave edit");
            check(editor.status.getText().toString().startsWith("Save failed"), "Compaction discarded save-failure feedback");
            check(editor.status.getVisibility() == View.VISIBLE, "Save status is hidden at normal tablet width");
        });

        run("finger drawing stays enabled and interrupted strokes remain canceled", () -> {
            NoteEditorLayout editor = create(context, dp(960), dp(416));
            check(!editor.sheet.isPenOnly(), "The retired setting silently disabled finger drawing");
            final int[] changes = {0}; editor.sheet.setOnChangeListener(() -> changes[0]++);
            touch(editor, MotionEvent.ACTION_DOWN, .6f, .4f); touch(editor, MotionEvent.ACTION_MOVE, .8f, .6f);
            touch(editor, MotionEvent.ACTION_UP, .8f, .6f);
            check(changes[0] == 1 && editor.sheet.getNote().strokes().size() == 1, "Finger ink was lost or duplicated");
            InkNote saved = editor.sheet.getNote();
            touch(editor, MotionEvent.ACTION_DOWN, .6f, .7f); touch(editor, MotionEvent.ACTION_MOVE, .8f, .7f);
            resize(editor, dp(320), dp(300)); touch(editor, MotionEvent.ACTION_UP, .8f, .7f);
            same(saved, editor.sheet.getNote()); check(changes[0] == 1, "Resize committed an unfinished stroke");
        });
        System.out.println("PASS " + passed + " compact editor actual Android View checks; no dialog/autosave-disk, journal-store or physical acceptance.");
    }

    private static NoteEditorLayout create(Context owner, int width, int height) {
        NoteEditorLayout editor = new NoteEditorLayout(owner, "11, 2 · A long authenticated local area name");
        FrameLayout parent = new FrameLayout(owner); parent.addView(editor);
        resize(editor, width, height); return editor;
    }
    private static void resize(NoteEditorLayout editor, int width, int height) {
        editor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        editor.layout(0, 0, width, height);
    }
    private static InkSheetLayout paper(NoteEditorLayout editor) {
        return new InkSheetLayout(editor.sheet.getWidth(), editor.sheet.getHeight(), 6 * density);
    }
    private static Rect bounds(NoteEditorLayout editor, View child) {
        Rect bounds = new Rect(); child.getDrawingRect(bounds); editor.offsetDescendantRectToMyCoords(child, bounds); return bounds;
    }
    private static void visibleClose(NoteEditorLayout editor) {
        Rect close = bounds(editor, editor.close);
        check(close.left >= 0 && close.right <= editor.getWidth() && close.top >= 0
                && close.bottom <= editor.sheet.getTop(), "Close is clipped, scrolled away or overlapping the paper");
        check(editor.close.getHeight() >= dp(48) && !editor.close.isFocusable(), "Close lost its touch target or stole key focus");
    }
    private static void checkSimpleControls(View view) {
        check(!(view instanceof CheckBox), "Pen-only checkbox returned");
        if (view instanceof TextView) {
            String label = ((TextView) view).getText().toString();
            check(!label.contains("Pen only") && !label.contains("Save PNG"), "Removed control returned: " + label);
        }
        if (view instanceof Button) check(view.getHeight() >= dp(48) && !view.isFocusable(), "Tool target/key focus regressed");
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            checkSimpleControls(((ViewGroup) view).getChildAt(i));
    }
    private static boolean contains(View parent, View wanted) {
        if (parent == wanted) return true;
        if (parent instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) parent).getChildCount(); i++)
            if (contains(((ViewGroup) parent).getChildAt(i), wanted)) return true;
        return false;
    }
    private static HorizontalScrollView scroll(View view) {
        if (view instanceof HorizontalScrollView) return (HorizontalScrollView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            HorizontalScrollView found = scroll(((ViewGroup) view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private static void touch(NoteEditorLayout editor, int action, float x, float y) {
        InkSheetLayout paper = paper(editor); MotionEvent.PointerProperties property = new MotionEvent.PointerProperties();
        property.id = 7; property.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords point = new MotionEvent.PointerCoords();
        point.x = paper.toX(x); point.y = paper.toY(y); point.pressure = 1; point.size = 1;
        long now = SystemClock.uptimeMillis(); MotionEvent event = MotionEvent.obtain(now, now, action, 1,
                new MotionEvent.PointerProperties[]{property}, new MotionEvent.PointerCoords[]{point},
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try { check(editor.sheet.onTouchEvent(event), "Sheet touch escaped toward the guest"); } finally { event.recycle(); }
    }
    private static void same(InkNote first, InkNote second) {
        check(first.strokes().size() == second.strokes().size(), "Stored stroke count changed");
        for (int i = 0; i < first.strokes().size(); i++) {
            InkNote.Stroke a = first.strokes().get(i), b = second.strokes().get(i);
            check(a.eraser() == b.eraser() && a.width() == b.width() && Arrays.equals(a.points(), b.points()), "Stored stroke changed");
        }
    }
    private static int dp(int value) { return Math.round(value * density); }
    private static void check(boolean test, String message) { if (!test) throw new AssertionError(message); }
    private static void run(String name, Runnable check) { check.run(); passed++; System.out.println("PASS " + passed + ": " + name); }
}
