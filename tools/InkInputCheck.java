import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import name.osher.gil.minivmac.InkSheetView;
import name.osher.gil.minivmac.notebook.InkNote;
import name.osher.gil.minivmac.notebook.InkSheetLayout;

/**
 * Focused, synthetic actual-Android-View pen/navigation checks. No app window or device input.
 * Reuses CompositeSheetRenderCheck's detached View/software Canvas pattern and the
 * PartyPaneRenderCheck font bootstrap. No physical stylus, palm, GPU or e-ink acceptance.
 *
 * Compile from the repository root using Bash (no Gradle/app installation required):
 *   check_dir=$(mktemp -d scratch/ink-input-check.XXXXXX)
 *   source_dir=android/minivmac/src/main/java/name/osher/gil/minivmac
 *   javac --release 8 -cp /usr/lib/android-sdk/platforms/android-34/android.jar \
 *     -d "$check_dir/classes" tools/InkInputCheck.java \
 *     "$source_dir"/{InkSheetView,MapArtwork}.java \
 *     "$source_dir"/notebook/{InkNote,InkHistory,InkSheetLayout,InkViewport,NoteIcon}.java \
 *     "$source_dir"/mapper/{PoolRadState,GeoMap,AreaIdentity}.java
 *   jar cf "$check_dir/classes.jar" -C "$check_dir/classes" .
 *   /usr/lib/android-sdk/build-tools/34.0.0/d8 --min-api 21 \
 *     --lib /usr/lib/android-sdk/platforms/android-34/android.jar \
 *     --output "$check_dir/ink-input-check.zip" "$check_dir/classes.jar"
 * Only the emulator owner runs:
 *   adb -s emulator-5580 push "$check_dir/ink-input-check.zip" /data/local/tmp/
 *   adb -s emulator-5580 shell \
 *     'CLASSPATH=/data/local/tmp/ink-input-check.zip app_process /system/bin InkInputCheck'
 *
 * Event contract: https://developer.android.com/reference/android/view/MotionEvent
 * FLAG_CANCELED applies to the pointer lifting on UP/POINTER_UP; an unrelated rejected
 * palm must not cancel the active stylus. IDs are stable while pointer indices can change.
 * https://developer.android.com/develop/ui/views/touch-and-input/gestures/scale
 */
public final class InkInputCheck {
    private static final int FINGER = MotionEvent.TOOL_TYPE_FINGER;
    private static final int PEN = MotionEvent.TOOL_TYPE_STYLUS;
    private static final int ERASER = MotionEvent.TOOL_TYPE_ERASER;
    private static final int WIDTH = 900, HEIGHT = 450;
    private static final List<Bitmap> BITMAPS = new ArrayList<>();
    private static Context context;
    private static int passed;

    public static void main(String[] args) {
        try { runChecks(); }
        catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
    }

    private static void runChecks() throws Exception {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        if (Typeface.DEFAULT == null)
            Typeface.class.getMethod("loadPreinstalledSystemFontMap").invoke(null);
        check(Typeface.DEFAULT != null, "Default Android font map is unavailable");
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        System.out.println("Actual InkSheetView input checks; synthetic pointer tools and software Canvas");

        run("default finger drawing remains available; pen-only rejects finger ink", () -> {
            Fixture f = new Fixture(InkNote.empty());
            check(!f.view.isPenOnly(), "New notes unexpectedly require a stylus");
            f.stroke(7, FINGER, .3f, .4f, .6f, .4f);
            check(f.view.getNote().strokes().size() == 1 && f.changes == 1, "Finger stroke was lost or double-saved");
            f.view.setPenOnly(true);
            InkNote before = f.view.getNote();
            f.stroke(13, FINGER, .3f, .6f, .6f, .6f);
            sameNote(before, f.view.getNote(), "Pen-only finger gesture left ink");
            check(f.changes == 1 && f.view.isPenOnly(), "Pen-only navigation caused an edit or changed mode");
            f.view.fitPage();
            f.stroke(21, PEN, .4f, .7f, .7f, .7f);
            check(f.view.getNote().strokes().size() == 2 && f.changes == 2, "Pen-only rejected the actual stylus");
        });

        run("built-in eraser clears only ink and retains undo/redo", () -> {
            Fixture f = new Fixture(InkNote.empty()); f.view.setPenOnly(true);
            Bitmap blank = render(f.view);
            f.stroke(7, PEN, .3f, .5f, .7f, .5f);
            Bitmap ink = render(f.view);
            check(differences(blank, ink) > 100, "Stylus ink preview did not become visible ink");
            f.stroke(13, ERASER, .25f, .5f, .75f, .5f);
            check(f.view.getNote().strokes().get(1).eraser(), "Physical eraser tool was recorded as a pen");
            equal(blank, render(f.view), "Built-in eraser failed to restore blank paper");
            f.view.undo(); equal(ink, render(f.view), "Undo did not restore erased ink");
            f.view.redo(); equal(blank, render(f.view), "Redo did not restore the erasure");
            check(f.changes == 4, "Pen/eraser/undo/redo did not emit exactly one edit each");
        });

        run("stylus survives palm entry, reordered indices and rejected palm release", () -> {
            Fixture f = new Fixture(InkNote.empty()); f.view.setPenOnly(true);
            f.send(MotionEvent.ACTION_DOWN, 0, 0, f.p(21,PEN,.2f,.4f));
            f.send(MotionEvent.ACTION_MOVE, 0, 0, f.p(21,PEN,.3f,.4f));
            f.send(MotionEvent.ACTION_POINTER_DOWN, 1, 0, f.p(21,PEN,.3f,.4f), f.p(7,FINGER,.8f,.8f));
            // Stable IDs, deliberately different array indices. The palm is not the pen.
            f.send(MotionEvent.ACTION_MOVE, 0, 0, f.p(7,FINGER,.9f,.9f), f.p(21,PEN,.45f,.4f));
            f.send(MotionEvent.ACTION_POINTER_DOWN, 2, 0,
                    f.p(7,FINGER,.9f,.9f), f.p(21,PEN,.45f,.4f), f.p(13,FINGER,.1f,.8f));
            f.send(MotionEvent.ACTION_MOVE, 0, 0,
                    f.p(7,FINGER,.95f,.95f), f.p(21,PEN,.6f,.4f), f.p(13,FINGER,.05f,.8f));
            f.send(MotionEvent.ACTION_POINTER_UP, 0, MotionEvent.FLAG_CANCELED,
                    f.p(7,FINGER,.95f,.95f), f.p(21,PEN,.6f,.4f), f.p(13,FINGER,.05f,.8f));
            f.send(MotionEvent.ACTION_POINTER_UP, 1, 0, f.p(21,PEN,.6f,.4f), f.p(13,FINGER,.05f,.8f));
            f.send(MotionEvent.ACTION_UP, 0, 0, f.p(21,PEN,.75f,.4f));
            check(f.changes == 1 && f.view.getNote().strokes().size() == 1, "Palm interrupted or duplicated the stylus stroke");
            InkNote.Stroke stroke = f.view.getNote().strokes().get(0);
            checkEndpoints(stroke, .2f,.4f,.75f,.4f);
            for (int i=1; i<stroke.points().length; i+=2)
                near(stroke.points()[i], .4f, "A palm coordinate entered the stylus path");
        });

        run("stylus takes over tentative finger ink and survives the original pointer lifting", () -> {
            Fixture f = new Fixture(InkNote.empty());
            f.send(MotionEvent.ACTION_DOWN, 0, 0, f.p(7,FINGER,.15f,.8f));
            f.send(MotionEvent.ACTION_MOVE, 0, 0, f.p(7,FINGER,.3f,.8f));
            f.send(MotionEvent.ACTION_POINTER_DOWN, 1, 0, f.p(7,FINGER,.3f,.8f), f.p(21,PEN,.4f,.3f));
            f.send(MotionEvent.ACTION_MOVE, 0, 0, f.p(7,FINGER,.8f,.9f), f.p(21,PEN,.5f,.3f));
            f.send(MotionEvent.ACTION_POINTER_UP, 0, 0, f.p(7,FINGER,.8f,.9f), f.p(21,PEN,.5f,.3f));
            f.send(MotionEvent.ACTION_MOVE, 0, 0, f.p(21,PEN,.6f,.3f));
            f.send(MotionEvent.ACTION_UP, 0, 0, f.p(21,PEN,.7f,.3f));
            check(f.changes == 1 && f.view.getNote().strokes().size() == 1, "Tentative finger ink survived stylus takeover");
            checkEndpoints(f.view.getNote().strokes().get(0), .4f,.3f,.7f,.3f);
        });

        run("active stylus POINTER_UP commits once while the remaining palm cannot ink", () -> {
            Fixture f = new Fixture(InkNote.empty());
            f.send(MotionEvent.ACTION_DOWN, 0, 0, f.p(21,PEN,.2f,.5f));
            f.send(MotionEvent.ACTION_POINTER_DOWN, 1, 0, f.p(21,PEN,.2f,.5f), f.p(7,FINGER,.8f,.8f));
            f.send(MotionEvent.ACTION_MOVE, 0, 0, f.p(7,FINGER,.8f,.8f), f.p(21,PEN,.5f,.5f));
            f.send(MotionEvent.ACTION_POINTER_UP, 1, 0, f.p(7,FINGER,.8f,.8f), f.p(21,PEN,.6f,.5f));
            check(f.changes == 1, "Stylus lifting before palm did not finish the stroke");
            f.send(MotionEvent.ACTION_MOVE, 0, 0, f.p(7,FINGER,.7f,.8f));
            f.send(MotionEvent.ACTION_UP, 0, 0, f.p(7,FINGER,.6f,.8f));
            check(f.changes == 1 && f.view.getNote().strokes().size() == 1, "Remaining palm began or duplicated ink");
            checkEndpoints(f.view.getNote().strokes().get(0), .2f,.5f,.6f,.5f);
        });

        cancellationChecks();
        navigationChecks();
        System.out.println("PASS " + passed + " actual InkSheetView checks; synthetic events/software Canvas only, "
                + "not physical stylus/palm/GPU/e-ink acceptance.");
    }

    private static void cancellationChecks() {
        run("CANCEL and canceled active UP/POINTER_UP discard preview without saving", () -> {
            for (int mode=0; mode<3; mode++) {
                Fixture f = new Fixture(InkNote.empty()); f.view.setPenOnly(true);
                Bitmap before = render(f.view);
                f.send(MotionEvent.ACTION_DOWN, 0, 0, f.p(21,PEN,.2f,.4f));
                f.send(MotionEvent.ACTION_MOVE, 0, 0, f.p(21,PEN,.65f,.4f));
                check(differences(before, render(f.view)) > 100, "No active stylus preview to cancel");
                if (mode == 0) f.send(MotionEvent.ACTION_CANCEL, 0, 0, f.p(21,PEN,.65f,.4f));
                else if (mode == 1)
                    f.send(MotionEvent.ACTION_UP, 0, MotionEvent.FLAG_CANCELED, f.p(21,PEN,.65f,.4f));
                else {
                    f.send(MotionEvent.ACTION_POINTER_DOWN, 1, 0, f.p(21,PEN,.65f,.4f), f.p(7,FINGER,.8f,.8f));
                    f.send(MotionEvent.ACTION_POINTER_UP, 1, MotionEvent.FLAG_CANCELED,
                            f.p(7,FINGER,.8f,.8f), f.p(21,PEN,.65f,.4f));
                    f.send(MotionEvent.ACTION_UP, 0, 0, f.p(7,FINGER,.8f,.8f));
                }
                check(f.changes == 0 && f.view.getNote().strokes().isEmpty(), "Canceled active pointer saved ink: " + mode);
                equal(before, render(f.view), "Canceled stroke preview remained visible: " + mode);
            }
        });

        run("focus loss, resize, mode/tool change and Fit discard unfinished ink", () -> {
            for (int mode=0; mode<5; mode++) {
                Fixture f = new Fixture(InkNote.empty());
                Bitmap before = render(f.view);
                f.send(MotionEvent.ACTION_DOWN, 0, 0, f.p(21,PEN,.2f,.4f));
                f.send(MotionEvent.ACTION_MOVE, 0, 0, f.p(21,PEN,.65f,.4f));
                if (mode == 0) { f.view.onWindowFocusChanged(false); f.view.onWindowFocusChanged(true); }
                if (mode == 1) { resize(f.view, 600, 500); resize(f.view, WIDTH, HEIGHT); }
                if (mode == 2) f.view.setPenOnly(true);
                if (mode == 3) f.view.setEraser(true);
                if (mode == 4) f.view.fitPage();
                f.send(MotionEvent.ACTION_UP, 0, 0, f.p(21,PEN,.65f,.4f));
                check(f.changes == 0 && f.view.getNote().strokes().isEmpty(), "Lifecycle/control cancellation saved ink: " + mode);
                equal(before, render(f.view), "Lifecycle/control cancellation retained preview: " + mode);
            }
        });

        run("batched stylus motion preserves historical points for a nonzero pointer ID", () -> {
            Fixture f = new Fixture(InkNote.empty()); f.view.setPenOnly(true);
            f.send(MotionEvent.ACTION_DOWN, 0, 0, f.p(21,PEN,.2f,.3f));
            f.batchedMove(f.p(21,PEN,.3f,.4f), f.p(21,PEN,.4f,.6f));
            f.send(MotionEvent.ACTION_UP, 0, 0, f.p(21,PEN,.6f,.3f));
            check(f.changes == 1, "Batched move did not commit exactly once");
            float[] points = f.view.getNote().strokes().get(0).points();
            check(hasPoint(points,.3f,.4f) && hasPoint(points,.4f,.6f), "Historical stylus coordinates were dropped");
            checkEndpoints(f.view.getNote().strokes().get(0), .2f,.3f,.6f,.3f);
        });

        run("pen hover cancels tentative finger ink and suppresses palm drawing until exit", () -> {
            Fixture f = new Fixture(InkNote.empty()); Bitmap blank = render(f.view);
            f.send(MotionEvent.ACTION_DOWN,0,0,f.p(7,FINGER,.2f,.4f));
            f.send(MotionEvent.ACTION_MOVE,0,0,f.p(7,FINGER,.6f,.4f));
            f.hover(MotionEvent.ACTION_HOVER_ENTER,f.p(21,PEN,.5f,.5f));
            f.send(MotionEvent.ACTION_UP,0,0,f.p(7,FINGER,.6f,.4f));
            f.stroke(7,FINGER,.2f,.6f,.6f,.6f);
            check(f.changes==0 && f.view.getNote().strokes().isEmpty(),"A hovering pen failed to suppress palm ink");
            equal(blank,render(f.view),"Hover left canceled finger preview visible");
            f.hover(MotionEvent.ACTION_HOVER_EXIT,f.p(21,PEN,.5f,.5f));
            f.stroke(7,FINGER,.2f,.6f,.6f,.6f);
            check(f.changes==1,"Finger drawing did not recover after pen hover exited");
        });
    }

    private static void navigationChecks() {
        run("two-finger pinch in either mode preserves ink, redo and edit notifications", () -> {
            for (boolean penOnly : new boolean[]{false,true}) {
                Fixture f = new Fixture(seed()); f.view.undo(); f.changes = 0;
                f.view.setPenOnly(penOnly);
                InkNote before = f.view.getNote(); Bitmap fit = render(f.view);
                f.pinch();
                check(differences(fit, render(f.view)) > 100, "Two-finger pinch did not enlarge the page: " + penOnly);
                sameNote(before, f.view.getNote(), "Pinch mutated the note: " + penOnly);
                check(f.changes == 0 && f.view.canRedo(), "Pinch saved tentative finger ink or destroyed redo");
                f.view.fitPage();
                equal(fit, render(f.view), "Fit did not restore the exact page framing");
                check(f.changes == 0 && f.view.canRedo(), "Fit unexpectedly edited history");
            }
        });

        run("pen-only finger pan navigates a zoomed sheet without saving or dropping redo", () -> {
            Fixture f = new Fixture(seed()); f.view.undo(); f.changes = 0; f.view.setPenOnly(true);
            InkNote before = f.view.getNote(); Bitmap fit = render(f.view);
            f.pinch(); Bitmap zoomed = render(f.view);
            f.pan(32,18);
            check(differences(zoomed,render(f.view)) > 100, "Finger pan did not move the enlarged page");
            sameNote(before, f.view.getNote(), "Finger pan left ink or changed existing coordinates");
            check(f.changes == 0 && f.view.canRedo(), "Finger pan notified autosave or lost redo");
            f.view.fitPage(); equal(fit,render(f.view),"Fit after pan failed to restore original framing");
            check(f.changes == 0, "Navigation called the handwriting edit listener");
        });

        run("navigation keeps stable pointer IDs and rebases a lifted primary without jumping", () -> {
            Fixture f = new Fixture(seed()); f.view.setPenOnly(true); f.pinch();
            InkNote before=f.view.getNote();float zoom=f.view.zoomFactor(),cx=WIDTH/2f,cy=HEIGHT/2f;
            f.send(MotionEvent.ACTION_DOWN,0,0,new Pointer(7,FINGER,cx-60,cy));
            f.send(MotionEvent.ACTION_POINTER_DOWN,1,0,
                    new Pointer(7,FINGER,cx-60,cy),new Pointer(13,FINGER,cx+60,cy));
            f.send(MotionEvent.ACTION_MOVE,0,0,
                    new Pointer(7,FINGER,cx-40,cy),new Pointer(13,FINGER,cx+80,cy));
            Bitmap moved=render(f.view);
            f.send(MotionEvent.ACTION_MOVE,0,0,
                    new Pointer(13,FINGER,cx+80,cy),new Pointer(7,FINGER,cx-40,cy));
            equal(moved,render(f.view),"Reordered pointer indices jumped the page");
            f.send(MotionEvent.ACTION_POINTER_UP,1,0,
                    new Pointer(13,FINGER,cx+80,cy),new Pointer(7,FINGER,cx-40,cy));
            equal(moved,render(f.view),"Lifting the original navigation pointer jumped the page");
            f.send(MotionEvent.ACTION_MOVE,0,0,new Pointer(13,FINGER,cx+100,cy));
            check(differences(moved,render(f.view))>100,"Remaining navigation pointer stopped panning");
            f.send(MotionEvent.ACTION_UP,0,0,new Pointer(13,FINGER,cx+100,cy));
            near(f.view.zoomFactor(),zoom,"Pure pointer handoff/pan changed scale");
            sameNote(before,f.view.getNote(),"Navigation pointer handoff altered stored ink");
            check(f.changes==0,"Pointer handoff caused autosave");
        });

        run("zoom/pan writing stores page coordinates and reopens identically after Fit", () -> {
            Fixture f = new Fixture(InkNote.empty()); f.view.setPenOnly(true); f.pinch();
            float cx = WIDTH/2f, cy = HEIGHT/2f;
            f.dotPixels(21,PEN,cx,cy);
            f.dotPixels(21,PEN,cx+80,cy);
            check(f.changes == 2, "Zoomed dot strokes did not commit individually");
            float[] center = f.view.getNote().strokes().get(0).points();
            near(center[0],.5f,"Pinch center changed stored horizontal anchor");
            near(center[1],.5f,"Pinch center changed stored vertical anchor");
            float offsetX = f.view.getNote().strokes().get(1).points()[0];
            float unzoomedX = f.layout().normalX(cx+80);
            check(offsetX > .5f && offsetX < unzoomedX-.01f,
                    "Zoomed writing used screen coordinates instead of inverse page scale");
            f.pan(32,18); f.dotPixels(21,PEN,cx+32,cy+18);
            float[] panned = f.view.getNote().strokes().get(2).points();
            near(panned[0],.5f,"Panned writing lost horizontal page anchor");
            near(panned[1],.5f,"Panned writing lost vertical page anchor");
            InkNote saved = f.view.getNote(); f.view.fitPage();
            check(f.changes == 3, "Pan or Fit emitted a spurious save notification");
            sameNote(saved,f.view.getNote(),"Fit changed stored ink coordinates");
            Fixture reopened = new Fixture(saved);
            equal(render(f.view),render(reopened.view),"Reopened note disagrees with the fitted zoom-written note");
            near(saved.strokes().get(0).width(),saved.strokes().get(1).width(),"Zoom changed stored physical pen width");
        });
    }

    private static final class Pointer {
        final int id, tool; final float x, y;
        Pointer(int id,int tool,float x,float y) { this.id=id;this.tool=tool;this.x=x;this.y=y; }
    }

    private static final class Fixture {
        final InkSheetView view = new InkSheetView(context);
        int changes;
        long clock = SystemClock.uptimeMillis(), downTime;
        Fixture(InkNote note) {
            FrameLayout parent = new FrameLayout(context); parent.addView(view);
            view.setNote(note); resize(view,WIDTH,HEIGHT);
            view.setOnChangeListener(() -> changes++);
        }
        InkSheetLayout layout() {
            return new InkSheetLayout(view.getWidth(),view.getHeight(),
                    6*context.getResources().getDisplayMetrics().density);
        }
        Pointer p(int id,int tool,float x,float y) {
            InkSheetLayout layout = layout(); return new Pointer(id,tool,layout.toX(x),layout.toY(y));
        }
        void stroke(int id,int tool,float x1,float y1,float x2,float y2) {
            send(MotionEvent.ACTION_DOWN,0,0,p(id,tool,x1,y1));
            send(MotionEvent.ACTION_MOVE,0,0,p(id,tool,x2,y2));
            send(MotionEvent.ACTION_UP,0,0,p(id,tool,x2,y2));
        }
        void dotPixels(int id,int tool,float x,float y) {
            send(MotionEvent.ACTION_DOWN,0,0,new Pointer(id,tool,x,y));
            send(MotionEvent.ACTION_UP,0,0,new Pointer(id,tool,x,y));
        }
        void pinch() {
            float cx=WIDTH/2f,cy=HEIGHT/2f;
            send(MotionEvent.ACTION_DOWN,0,0,new Pointer(7,FINGER,cx-60,cy));
            send(MotionEvent.ACTION_POINTER_DOWN,1,0,
                    new Pointer(7,FINGER,cx-60,cy),new Pointer(13,FINGER,cx+60,cy));
            for (int halfSpan : new int[]{90,120,160,200})
                send(MotionEvent.ACTION_MOVE,0,0,new Pointer(7,FINGER,cx-halfSpan,cy),
                        new Pointer(13,FINGER,cx+halfSpan,cy));
            send(MotionEvent.ACTION_POINTER_UP,0,0,
                    new Pointer(7,FINGER,cx-200,cy),new Pointer(13,FINGER,cx+200,cy));
            send(MotionEvent.ACTION_UP,0,0,new Pointer(13,FINGER,cx+200,cy));
        }
        void pan(float dx,float dy) {
            float cx=WIDTH/2f,cy=HEIGHT/2f;
            send(MotionEvent.ACTION_DOWN,0,0,new Pointer(7,FINGER,cx,cy));
            send(MotionEvent.ACTION_MOVE,0,0,new Pointer(7,FINGER,cx+dx,cy+dy));
            send(MotionEvent.ACTION_UP,0,0,new Pointer(7,FINGER,cx+dx,cy+dy));
        }
        void send(int action,int index,int flags,Pointer... pointers) {
            clock += 20;
            if (action == MotionEvent.ACTION_DOWN) downTime=clock;
            MotionEvent event = obtain(action | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT),flags,pointers);
            dispatch(event);
        }
        void batchedMove(Pointer previous,Pointer current) {
            clock += 20;
            MotionEvent event = obtain(MotionEvent.ACTION_MOVE,0,new Pointer[]{previous});
            clock += 20;
            event.addBatch(clock,coordinates(new Pointer[]{current}),0);
            dispatch(event);
        }
        void hover(int action,Pointer pointer) {
            clock+=20;MotionEvent event=obtain(action,0,new Pointer[]{pointer});
            try { check(view.onHoverEvent(event),"Synthetic pen hover escaped the View"); }
            finally { event.recycle(); }
        }
        MotionEvent obtain(int action,int flags,Pointer[] pointers) {
            MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointers.length];
            int source=InputDevice.SOURCE_TOUCHSCREEN;
            for (int i=0;i<pointers.length;i++) {
                properties[i]=new MotionEvent.PointerProperties();
                properties[i].id=pointers[i].id;properties[i].toolType=pointers[i].tool;
                if (pointers[i].tool == PEN || pointers[i].tool == ERASER) source=InputDevice.SOURCE_STYLUS;
            }
            return MotionEvent.obtain(downTime,clock,action,pointers.length,properties,coordinates(pointers),
                    0,0,1,1,0,0,source,flags);
        }
        void dispatch(MotionEvent event) {
            try { check(view.onTouchEvent(event),"Synthetic ink/navigation input escaped the View"); }
            finally { event.recycle(); }
        }
    }

    private static MotionEvent.PointerCoords[] coordinates(Pointer[] pointers) {
        MotionEvent.PointerCoords[] result = new MotionEvent.PointerCoords[pointers.length];
        for (int i=0;i<pointers.length;i++) {
            result[i]=new MotionEvent.PointerCoords();result[i].x=pointers[i].x;result[i].y=pointers[i].y;
            result[i].pressure=1;result[i].size=pointers[i].tool == FINGER ? .25f : .01f;
        }
        return result;
    }
    private static InkNote seed() {
        return new InkNote(Arrays.asList(
                new InkNote.Stroke(false,.02f,new float[]{.3f,.35f,.7f,.65f}),
                new InkNote.Stroke(false,.03f,new float[]{.7f,.4f})));
    }
    private static void resize(InkSheetView view,int width,int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        view.layout(0,0,width,height);
    }
    private static Bitmap render(InkSheetView view) {
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        BITMAPS.add(bitmap);view.draw(new Canvas(bitmap));return bitmap;
    }
    private static int[] pixels(Bitmap bitmap) {
        int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];
        bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());return pixels;
    }
    private static int differences(Bitmap before,Bitmap after) {
        check(before.getWidth()==after.getWidth() && before.getHeight()==after.getHeight(),"Different image sizes");
        int[] first=pixels(before),second=pixels(after);int changed=0;
        for(int i=0;i<first.length;i++) if(first[i]!=second[i]) changed++;
        return changed;
    }
    private static void equal(Bitmap before,Bitmap after,String message) { check(differences(before,after)==0,message); }
    private static void sameNote(InkNote before,InkNote after,String message) {
        check(before.strokes().size()==after.strokes().size(),message+" (stroke count)");
        for(int i=0;i<before.strokes().size();i++) {
            InkNote.Stroke a=before.strokes().get(i),b=after.strokes().get(i);
            check(a.eraser()==b.eraser() && a.width()==b.width() && Arrays.equals(a.points(),b.points()),message);
        }
    }
    private static boolean hasPoint(float[] points,float x,float y) {
        for(int i=0;i<points.length;i+=2) if(Math.abs(points[i]-x)<.002f && Math.abs(points[i+1]-y)<.002f) return true;
        return false;
    }
    private static void checkEndpoints(InkNote.Stroke stroke,float x1,float y1,float x2,float y2) {
        float[] p=stroke.points();near(p[0],x1,"Incorrect first x");near(p[1],y1,"Incorrect first y");
        near(p[p.length-2],x2,"Incorrect final x");near(p[p.length-1],y2,"Incorrect final y");
    }
    private static void near(float actual,float expected,String message) {
        check(Math.abs(actual-expected)<.002f,message+": expected "+expected+", got "+actual);
    }
    private static void check(boolean okay,String message) { if(!okay) throw new AssertionError(message); }
    private static void run(String name,Runnable test) {
        try { test.run();passed++;System.out.println("PASS "+passed+": "+name); }
        finally { for(Bitmap bitmap:BITMAPS) bitmap.recycle();BITMAPS.clear(); }
    }
}
