import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import name.osher.gil.minivmac.InkSheetView;
import name.osher.gil.minivmac.MapArtwork;
import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.notebook.InkNote;
import name.osher.gil.minivmac.notebook.InkSheetLayout;
import name.osher.gil.minivmac.notebook.NoteIcon;

/** Synthetic Android software-Canvas/View checks, run with app_process, not an app or guest fixture. */
public final class CompositeSheetRenderCheck {
    private static int passed;
    private static Context context;
    private static final PoolRadState SNAPSHOT = snapshot();
    private static final Map<Integer, NoteIcon> ICONS = new HashMap<>();
    private static final java.util.List<Bitmap> BITMAPS = new ArrayList<>();

    public static void main(String[] args) {
        try { runChecks(); }
        catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
    }

    private static void runChecks() throws Exception {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        ICONS.put(3 * 16 + 4, NoteIcon.TEMPLE);
        run("wide paper keeps the map left and right writing area blank", () -> {
            InkSheetView view = view(InkNote.empty(), 900, 450);
            InkSheetLayout layout = layout(900, 450);
            Bitmap image = render(view);
            int darkLeft = 0;
            for (int y = (int) layout.mapTop; y < layout.mapTop + layout.mapSize; y++)
                for (int x = (int) layout.mapLeft; x < layout.mapLeft + layout.mapSize; x++)
                    if (image.getPixel(x, y) == Color.BLACK) darkLeft++;
            check(darkLeft > 200, "Map did not render on left half");
            check(image.getPixel((int) layout.toX(.75f), (int) layout.toY(.5f)) == Color.WHITE,
                    "Right writing half is not blank white paper");
        });
        run("one stroke crosses map and writing; eraser restores the exact composite base", () -> {
            InkSheetView view = view(InkNote.empty(), 900, 450);
            Bitmap base = render(view);
            view.setNote(note(line(false, .05f)));
            Bitmap ink = render(view);
            check(differences(base, ink) > 1000, "Cross-sheet stroke is missing");
            InkSheetLayout layout = layout(900, 450);
            check(ink.getPixel((int) layout.toX(.75f), (int) layout.toY(.5f)) == Color.BLACK,
                    "Stroke did not reach writing half");
            view.setNote(note(line(false, .05f), line(true, .15f)));
            equal(base, render(view), "Eraser altered geometry, paper, symbols or party marker");
        });
        run("symbol changes retain the flag's committed handwriting", () -> {
            InkNote ink = note(line(false, .04f));
            InkSheetView view = view(ink, 900, 450);
            Map<Integer, NoteIcon> changed = new HashMap<>(ICONS);
            changed.put(3 * 16 + 4, NoteIcon.MONSTER);
            Bitmap before = render(view);
            view.setMap(SNAPSHOT, changed, 4, 3);
            check(view.getNote().strokes().size() == 1, "Changing symbol reset note ink");
            check(differences(before, render(view)) > 5, "Changing symbol did not change its appearance");
        });
        run("all nine 26-pixel manual symbols are visible and distinct", () -> {
            java.util.Set<Integer> fingerprints = new java.util.HashSet<>();
            for (NoteIcon icon : NoteIcon.values()) {
                Bitmap image = bitmap(40, 40);
                Map<Integer, NoteIcon> marker = new HashMap<>(); marker.put(0, icon);
                new MapArtwork().drawMarkers(new Canvas(image), marker, null, false, 7, 7, 26, 1);
                int[] pixels = pixels(image); int dark = 0;
                for (int pixel : pixels) if (pixel == Color.BLACK) dark++;
                check(dark > 5, "Invisible symbol " + icon);
                check(fingerprints.add(Arrays.hashCode(pixels)), "Duplicate symbol " + icon);
            }
            check(fingerprints.size() == 9, "Expected nine symbols");
        });
        run("cancelled and extra-pointer strokes never enter autosave ink", () -> {
            InkSheetView view = view(InkNote.empty(), 900, 450);
            InkSheetLayout layout = layout(900, 450);
            Bitmap base = render(view);
            event(view, MotionEvent.ACTION_DOWN, layout.toX(.15f), layout.toY(.65f));
            event(view, MotionEvent.ACTION_MOVE, layout.toX(.85f), layout.toY(.65f));
            check(differences(base, render(view)) > 200, "Active stroke preview is missing");
            event(view, MotionEvent.ACTION_CANCEL, layout.toX(.85f), layout.toY(.65f));
            check(view.getNote().strokes().isEmpty(), "Cancelled stroke was committed");
            equal(base, render(view), "Cancelled preview was retained");
            event(view, MotionEvent.ACTION_DOWN, layout.toX(.65f), layout.toY(.65f));
            event(view, MotionEvent.ACTION_POINTER_DOWN, layout.toX(.7f), layout.toY(.7f));
            event(view, MotionEvent.ACTION_UP, layout.toX(.75f), layout.toY(.75f));
            check(view.getNote().strokes().isEmpty(), "Extra pointer committed unfinished ink");
        });
        run("resize preserves tile-anchored ink and fixed half proportions", () -> {
            InkSheetLayout original = layout(900, 450);
            float x = original.normalX(original.mapTileX(7.5f));
            float y = original.normalY(original.mapTileY(11.5f));
            InkNote dot = note(new InkNote.Stroke(false, .055f, new float[]{x, y}));
            InkSheetView view = view(dot, 900, 450);
            Bitmap before = render(view);
            check(before.getPixel((int) original.mapTileX(7.5f), (int) original.mapTileY(11.5f)) == Color.BLACK,
                    "Original tile anchor misplaced");
            resize(view, 600, 500);
            InkSheetLayout resized = layout(600, 500);
            check(render(view).getPixel((int) resized.mapTileX(7.5f), (int) resized.mapTileY(11.5f)) == Color.BLACK,
                    "Tile-anchored ink moved after resize");
            check(view.getNote().strokes().size() == 1, "Resize changed saved ink");
        });
        System.out.println("PASS " + passed + " composite-sheet Android software checks; synthetic data, no GPU/e-ink/stylus acceptance.");
    }

    private static PoolRadState snapshot() {
        byte[] data = new byte[1200]; data[0]='P'; data[1]='R'; data[2]='M'; data[3]='1';
        data[130]=12; data[131]=9; data[132]=2;
        for (int tile=0; tile<256; tile++) data[176+tile]=(byte)((tile%16%4==0?1:0)|(tile/16%3==0?0x10:0));
        return PoolRadState.parse(data);
    }
    private static InkSheetLayout layout(int w, int h) {
        return new InkSheetLayout(w, h, 6 * context.getResources().getDisplayMetrics().density);
    }
    private static InkSheetView view(InkNote note, int w, int h) {
        InkSheetView view = new InkSheetView(context); view.setMap(SNAPSHOT, ICONS, 4, 3); view.setNote(note);
        resize(view, w, h); return view;
    }
    private static void resize(InkSheetView view, int w, int h) {
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
    }
    private static Bitmap render(InkSheetView view) {
        Bitmap image = bitmap(view.getWidth(), view.getHeight());
        view.draw(new Canvas(image)); return image;
    }
    private static Bitmap bitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        BITMAPS.add(bitmap); return bitmap;
    }
    private static void event(InkSheetView view, int action, float x, float y) {
        long now = SystemClock.uptimeMillis(); MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        check(view.onTouchEvent(event), "Ink event escaped the view"); event.recycle();
    }
    private static InkNote.Stroke line(boolean erase, float width) {
        return new InkNote.Stroke(erase, width, new float[]{.08f, .5f, .92f, .5f});
    }
    private static InkNote note(InkNote.Stroke... strokes) { return new InkNote(Arrays.asList(strokes)); }
    private static int[] pixels(Bitmap image) {
        int[] result = new int[image.getWidth()*image.getHeight()];
        image.getPixels(result, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight()); return result;
    }
    private static int differences(Bitmap a, Bitmap b) {
        int[] aa=pixels(a), bb=pixels(b); check(aa.length==bb.length, "Different image sizes");
        int changed=0; for(int i=0;i<aa.length;i++) if(aa[i]!=bb[i]) changed++; return changed;
    }
    private static void equal(Bitmap a, Bitmap b, String message) { check(differences(a,b)==0, message); }
    private static void check(boolean okay, String message) { if(!okay) throw new AssertionError(message); }
    private static void run(String name, Runnable test) {
        try { test.run(); passed++; System.out.println("PASS "+passed+": "+name); }
        finally { for (Bitmap bitmap : BITMAPS) bitmap.recycle(); BITMAPS.clear(); }
    }
}
