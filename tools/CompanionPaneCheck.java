import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import name.osher.gil.minivmac.CompanionPane;
import name.osher.gil.minivmac.LiveMapView;
import name.osher.gil.minivmac.MapStackLayout;
import name.osher.gil.minivmac.R;

/**
 * Focused actual Android View checks, detached from the guest/activity UI.
 * Reuses PartyPaneRenderCheck's system-font initialization and software Canvas.
 * Requires the matching newly built APK installed for its string resources.
 * Does NOT verify fragment polling, reference dialog placement, physical keys
 * reaching the guest, attached AccessibilityNodeInfo projection, hardware-GPU/
 * e-ink rendering, or device interaction. The emulator owner's live accessibility
 * tree must separately show Info selected=true / Map selected=false and the
 * inverse after returning to Map.
 *
 * After the integration owner generates the current R.jar, from the repo root:
 *   check_dir=$(mktemp -d scratch/companion-pane-check.XXXXXX)
 *   source_dir=android/minivmac/src/main/java/name/osher/gil/minivmac
 *   r_jar=android/minivmac/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/macIIDebug/processMacIIDebugResources/R.jar
 *   javac --release 8 -cp "/usr/lib/android-sdk/platforms/android-34/android.jar:$r_jar" \
 *     -d "$check_dir/classes" tools/CompanionPaneCheck.java \
 *     "$source_dir/CompanionPane.java" "$source_dir/MapStackLayout.java" "$source_dir/CompanionGeometry.java" \
 *     "$source_dir/LiveMapView.java" "$source_dir/MapArtwork.java" \
 *     "$source_dir/notebook/NoteIcon.java" "$source_dir/notebook/ExplorationTrail.java" \
 *     "$source_dir/mapper/PartyState.java" \
 *     "$source_dir/mapper/PartyPaneLayout.java" "$source_dir/mapper/MapViewport.java" \
 *     "$source_dir/mapper/PoolRadState.java" "$source_dir/mapper/GeoMap.java" \
 *     "$source_dir/mapper/MapObservation.java" "$source_dir/mapper/MapMode.java" \
 *     "$source_dir/mapper/AreaIdentity.java"
 *   jar cf "$check_dir/classes.jar" -C "$check_dir/classes" .
 *   /usr/lib/android-sdk/build-tools/34.0.0/d8 --min-api 21 \
 *     --lib /usr/lib/android-sdk/platforms/android-34/android.jar \
 *     --output "$check_dir/companion-pane-check.zip" "$check_dir/classes.jar" "$r_jar"
 *
 * Only the emulator owner should run:
 *   adb -s emulator-5584 push "$check_dir/companion-pane-check.zip" /data/local/tmp/
 *   adb -s emulator-5584 shell \
 *     'CLASSPATH=/data/local/tmp/companion-pane-check.zip app_process /system/bin CompanionPaneCheck'
 */
public final class CompanionPaneCheck {
    private static Context context;
    private static int passed;
    private static final int[] TOOL_IDS = {
            R.id.companion_tool_options, R.id.companion_tool_message_log, R.id.companion_tool_exploration,
            R.id.companion_tool_journal, R.id.companion_tool_note_index,
            R.id.companion_tool_levels, R.id.companion_tool_spells, R.id.companion_tool_equipment,
            R.id.companion_tool_money, R.id.companion_tool_wheel, R.id.companion_tool_legend};

    public static void main(String[] args) {
        try { checks(args.length == 0 ? "com.hunterdavis.poolradmacmaps.ii" : args[0]); }
        catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
    }

    private static void checks(String packageName) throws Exception {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        if (Typeface.DEFAULT == null)
            Typeface.class.getMethod("loadPreinstalledSystemFontMap").invoke(null);
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context system = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        Context app = system.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY);
        context = new ContextThemeWrapper(app, android.R.style.Theme_Material_Light_NoActionBar);

        run("Map is default; one map instance and its notebook survive every tab change", () -> {
            CompanionPane pane = pane(context, 480, 320);
            View original = pane.findViewById(R.id.live_map);
            check(original instanceof LiveMapView && countMaps(pane) == 1, "Expected one mounted LiveMapView");
            ((LiveMapView) original).showNotebook("Synthetic retained notebook", Collections.emptyMap());
            check(pane.isMapSelected() && CompanionPane.MAP.equals(pane.selectedTab()), "Map is not default");
            List<String> changes = new ArrayList<>();
            pane.setOnTabSelectedListener(changes::add);
            pane.setTab(CompanionPane.INFO);
            check(original.getVisibility() == View.GONE, "Map must be hidden on Info");
            check(pane.findViewById(R.id.companion_info).getVisibility() == View.VISIBLE, "Info is not visible");
            pane.setTab(CompanionPane.INFO);
            pane.setTab("obsolete-tab");
            pane.setTab(null);
            check(changes.equals(Arrays.asList("info", "map")), "Selection callback duplicated or failed fallback");
            check(pane.findViewById(R.id.live_map) == original && countMaps(pane) == 1, "Tab replaced map");
            check(original.getContentDescription().toString().contains("Synthetic retained notebook"), "Map state reset");
            pane.setOnTabSelectedListener(null);
            pane.setTab(CompanionPane.INFO);
            check(changes.size() == 2, "Cleared listener was still called");
        });

        run("Tab selection/accessibility metadata is correct and buttons do not claim keyboard focus", () -> {
            CompanionPane pane = pane(context, 360, 240);
            Button map = pane.findViewById(R.id.companion_tab_map);
            Button info = pane.findViewById(R.id.companion_tab_info);
            check(map.isSelected() && !info.isSelected(), "Default selected state is wrong");
            check(map.getHeight() == dp(context, 48), "Tab hit height is not 48dp");
            info.performClick();
            check(info.isSelected() && !map.isSelected() && !pane.isMapSelected(), "Info click did not select");
            // AOSP View.onInitializeAccessibilityNodeInfoInternal returns before
            // populating a node when mAttachInfo is null. A detached node cannot
            // establish what an attached accessibility service receives.
            check(!info.isAttachedToWindow(), "This check is intended for detached View metadata");
            for (Button tab : new Button[]{map, info}) {
                check(tab.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_YES,
                        "Tab is not marked important for accessibility");
                check(tab.getAccessibilityClassName().toString().equals("android.widget.Button"),
                        "Tab lost its actionable accessibility role");
                check(tab.isEnabled() && tab.isClickable(), "Tab cannot be activated");
            }
            check(info.getContentDescription().toString().equals(
                    context.getString(R.string.companion_tab_accessibility,
                            context.getString(R.string.companion_tab_info))), "Accessible Info label is missing");
            map.performClick();
            check(map.isSelected() && !info.isSelected(), "Returning to Map left stale selection");
            check(!pane.requestFocus() && !pane.hasFocus(), "Companion stole keyboard focus");
            assertNotFocusable(pane);
        });

        run("Options and reference tool buttons deliver distinct enum callbacks, and no maintenance action", () -> {
            CompanionPane pane = pane(context, 480, 360);
            pane.setTab(CompanionPane.INFO);
            List<CompanionPane.Tool> tools = new ArrayList<>();
            pane.setOnToolSelectedListener(tools::add);
            for (int id : TOOL_IDS) {
                Button button = pane.findViewById(id);
                check(button != null && button.isEnabled() && button.length() > 0, "Tool entry missing");
                button.performClick();
            }
            check(tools.equals(Arrays.asList(CompanionPane.Tool.OPTIONS, CompanionPane.Tool.MESSAGE_LOG, CompanionPane.Tool.EXPLORATION, CompanionPane.Tool.JOURNAL, CompanionPane.Tool.NOTE_INDEX,
                    CompanionPane.Tool.LEVELS, CompanionPane.Tool.SPELLS, CompanionPane.Tool.EQUIPMENT,
                    CompanionPane.Tool.MONEY, CompanionPane.Tool.WHEEL, CompanionPane.Tool.LEGEND)), "Incorrect or duplicate tool routing");
            check(CompanionPane.INFO.equals(pane.selectedTab()), "Tool click changed underlying Info tab");
            pane.setOnToolSelectedListener(null);
            pane.findViewById(TOOL_IDS[0]).performClick();
            check(tools.size() == TOOL_IDS.length, "Cleared tool listener retained callback");
        });

        run("Short landscape keeps Info scrolling inside the existing pane and retains its position", () -> {
            CompanionPane pane = pane(context, 640, 160);
            pane.setTab(CompanionPane.INFO);
            layout(pane, context, 640, 160);
            ScrollView scroll = pane.findViewById(R.id.companion_info);
            check(scroll.getChildAt(0).getHeight() > scroll.getHeight(), "Short Info list should scroll");
            scroll.scrollTo(0, Integer.MAX_VALUE);
            int position = scroll.getScrollY();
            check(position > 0, "Last tool cannot be reached");
            check(pane.getHeight() == dp(context, 160), "Info enlarged its allocated pane");
            pane.setTab(CompanionPane.MAP);
            layout(pane, context, 640, 160);
            pane.setTab(CompanionPane.INFO);
            layout(pane, context, 640, 160);
            check(scroll.getScrollY() == position, "Switching tabs reset the retained scroll position");
            check(scroll.getTop() == 0 && scroll.getHeight() == dp(context, 160) - dp(context, 48),
                    "Info escaped the content allocation");
        });

        run("Large text wraps tool labels and the final tool remains reachable", () -> {
            Configuration configuration = new Configuration(context.getResources().getConfiguration());
            configuration.fontScale = 1.8f;
            Context larger = new ContextThemeWrapper(context.createConfigurationContext(configuration),
                    android.R.style.Theme_Material_Light_NoActionBar);
            CompanionPane pane = pane(larger, 240, 240);
            pane.setTab(CompanionPane.INFO);
            layout(pane, larger, 240, 240);
            ScrollView scroll = pane.findViewById(R.id.companion_info);
            scroll.scrollTo(0, Integer.MAX_VALUE);
            Button last = pane.findViewById(R.id.companion_tool_legend);
            ViewGroup tools = (ViewGroup) scroll.getChildAt(0);
            for (int id : TOOL_IDS)
                check(pane.findViewById(id).getHeight() >= dp(larger, 48), "A large-text tool lost its hit height");
            check(last.getBottom() - scroll.getScrollY() <= scroll.getHeight(), "Final tool is clipped at scroll end");
            check(tools.getHeight() > scroll.getHeight(), "Large-text controls unexpectedly compressed");
        });

        run("Tab bounds and selected black/white contrast survive repeated layout changes", () -> {
            CompanionPane pane = pane(context, 480, 260);
            for (String tab : new String[]{"map", "info", "map"}) {
                pane.setTab(tab);
                layout(pane, context, 480, 260);
                check(pane.getHeight() == dp(context, 260), "Tab changed allocated height");
                Bitmap bitmap = Bitmap.createBitmap(pane.getWidth(), pane.getHeight(), Bitmap.Config.ARGB_8888);
                try {
                    pane.draw(new Canvas(bitmap));
                    int left = bitmap.getPixel(dp(context, 8), dp(context, 6));
                    int right = bitmap.getPixel(pane.getWidth() / 2 + dp(context, 8), dp(context, 6));
                    check(left == (pane.isMapSelected() ? Color.BLACK : Color.WHITE), "Map tab contrast mismatch");
                    check(right == (pane.isMapSelected() ? Color.WHITE : Color.BLACK), "Info tab contrast mismatch");
                } finally { bitmap.recycle(); }
            }
            layout(pane, context, 240, 100);
            check(pane.getHeight() == dp(context, 100), "Narrow pane exceeded its budget");
        });

        run("Unused companion space consumes touch without taking focus or changing selection", () -> {
            CompanionPane pane = pane(context, 480, 240);
            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 2, 2, 0);
            MotionEvent cancel = MotionEvent.obtain(now, now + 1, MotionEvent.ACTION_CANCEL, 2, 2, 0);
            try {
                check(pane.onTouchEvent(down), "Unused pane touch was not consumed");
                check(pane.onTouchEvent(cancel), "Canceled pane touch was not consumed");
                check(pane.isMapSelected() && !pane.hasFocus(), "Background touch affected navigation/focus");
            } finally { down.recycle(); cancel.recycle(); }
        });
        run("Actual MapStackLayout keeps guest/keyboard bounds stable across tabs and hide/show", () -> {
            MapStackLayout stack = new MapStackLayout(context, null);
            stack.setOrientation(LinearLayout.VERTICAL);
            CompanionPane pane = new CompanionPane(context);
            pane.setId(R.id.companion_pane);
            stack.addView(pane, new LinearLayout.LayoutParams(-1, 0));
            FrameLayout guest = new FrameLayout(context);
            View screen = new View(context);
            screen.setId(R.id.screen);
            guest.addView(screen, new FrameLayout.LayoutParams(-1, -1));
            stack.addView(guest, new LinearLayout.LayoutParams(-1, 0, 1));
            View keyboard = new View(context);
            keyboard.setId(R.id.keyboard);
            stack.addView(keyboard, new LinearLayout.LayoutParams(-1, dp(context, 144)));
            check(pane.findViewById(R.id.live_map).getLayoutParams() instanceof FrameLayout.LayoutParams,
                    "Map is not a retained frame child");
            for (int[] size : new int[][]{{480, 900}, {900, 480}, {360, 640}}) {
                for (boolean keys : new boolean[]{false, true}) {
                    keyboard.setVisibility(keys ? View.VISIBLE : View.GONE);
                    pane.setVisibility(View.VISIBLE);
                    pane.setTab(CompanionPane.MAP);
                    layout(stack, context, size[0], size[1]);
                    Rect guestBefore = bounds(guest), keyboardBefore = bounds(keyboard), companionBefore = bounds(pane);
                    pane.setTab(CompanionPane.INFO);
                    layout(stack, context, size[0], size[1]);
                    check(bounds(guest).equals(guestBefore), "Info changed the guest rectangle");
                    check(bounds(keyboard).equals(keyboardBefore), "Info changed the keyboard rectangle");
                    check(bounds(pane).equals(companionBefore), "Info changed companion allocation");
                    check(pane.getBottom() <= guest.getTop(), "Companion overlaps the guest");
                    check(screen.getMeasuredHeight() == guest.getMeasuredHeight(), "Guest frame did not fill its budget");
                    if (keys) check(keyboard.getTop() == guest.getBottom(), "Keyboard is not below the guest");
                    pane.setVisibility(View.GONE);
                    layout(stack, context, size[0], size[1]);
                    check(guest.getHeight() >= guestBefore.height(), "Hiding companion shrank the guest");
                    pane.setVisibility(View.VISIBLE);
                    layout(stack, context, size[0], size[1]);
                    check(bounds(guest).equals(guestBefore), "Showing companion did not restore the guest rectangle");
                    check(bounds(keyboard).equals(keyboardBefore), "Hide/show moved the keyboard");
                    check(CompanionPane.INFO.equals(pane.selectedTab()), "Hide/show lost the selected tab");
                }
            }
        });
        System.out.println("PASS " + passed + " companion checks (detached software Views only)");
    }

    private static CompanionPane pane(Context context, int width, int height) {
        CompanionPane result = new CompanionPane(context);
        layout(result, context, width, height);
        return result;
    }

    private static void layout(View view, Context context, int width, int height) {
        int w = dp(context, width), h = dp(context, height);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int countMaps(View view) {
        int count = view instanceof LiveMapView ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += countMaps(group.getChildAt(i));
        }
        return count;
    }

    private static Rect bounds(View view) {
        return new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
    }

    private static void assertNotFocusable(View view) {
        check(!view.isFocusable() && !view.isFocusableInTouchMode(), "A companion control accepts hardware focus");
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) assertNotFocusable(group.getChildAt(i));
        }
    }

    private interface Check { void run() throws Exception; }
    private static void run(String label, Check check) throws Exception {
        check.run(); passed++; System.out.println("PASS " + label);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
