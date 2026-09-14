package name.osher.gil.minivmac;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.notebook.InkHistory;
import name.osher.gil.minivmac.notebook.InkNote;
import name.osher.gil.minivmac.notebook.InkSheetLayout;
import name.osher.gil.minivmac.notebook.NoteIcon;

/** One flag's fixed 8:3 paper: map left, writing right, and ink across both halves. */
public final class InkSheetView extends View {
    private static final float PEN_WIDTH = .006f;
    private static final float ERASER_WIDTH = .055f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PorterDuffXfermode clear = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
    private final RectF sheet = new RectF();
    private final MapArtwork artwork = new MapArtwork();
    private InkSheetLayout layout = new InkSheetLayout(0, 0, 0);
    private PoolRadState mapSnapshot;
    private Map<Integer, NoteIcon> flags = Collections.emptyMap();
    private final InkHistory history = new InkHistory(InkNote.empty());
    private final List<RenderedStroke> rendered = new ArrayList<>();
    private final Path activePath = new Path();
    private final float density;
    private int activePointer = MotionEvent.INVALID_POINTER_ID;
    private boolean eraser;
    private boolean activeEraser;
    private boolean activeHasSegment;
    private float activeStartX;
    private float activeStartY;
    private Runnable onChange;

    public InkSheetView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setBackgroundColor(0xffeeeeee);
        setClickable(true);
        setFocusable(false); // No keyboard or hardware key focus is stolen from the guest.
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("Flag note. Map on the left, writing space on the right. Draw across both halves.");
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    /** A read-only pinned map snapshot; each flag still owns its own independent sheet of ink. */
    public void setMap(PoolRadState snapshot, Map<Integer, NoteIcon> areaFlags, int selectedX, int selectedY) {
        if (selectedX < 0 || selectedX >= 16 || selectedY < 0 || selectedY >= 16)
            throw new IllegalArgumentException("Selected note tile must be within the map");
        cancelActiveStroke();
        mapSnapshot = snapshot;
        Map<Integer, NoteIcon> copy = new HashMap<>(areaFlags == null ? Collections.emptyMap() : areaFlags);
        int selected = selectedY * 16 + selectedX;
        if (!copy.containsKey(selected)) copy.put(selected, NoteIcon.FLAG);
        flags = copy;
        invalidate();
    }

    public void setNote(InkNote note) {
        cancelActiveStroke();
        history.reset(note);
        rebuildStrokes();
        invalidate();
    }

    public InkNote getNote() { return history.getNote(); }
    public void setOnChangeListener(Runnable listener) { onChange = listener; }
    public boolean canUndo() { return history.canUndo(); }
    public boolean canRedo() { return history.canRedo(); }

    public void setEraser(boolean enabled) {
        cancelActiveStroke();
        eraser = enabled;
        setContentDescription(enabled
                ? "Flag note. Eraser selected. Only ink is erased; map and flags are protected."
                : "Flag note. Pen selected. Draw over the left map and right writing space.");
    }

    public void undo() {
        cancelActiveStroke();
        if (history.undo()) changed();
    }

    public void redo() {
        cancelActiveStroke();
        if (history.redo()) changed();
    }

    public void cancelActiveStroke() {
        history.cancelStroke();
        activePointer = MotionEvent.INVALID_POINTER_ID;
        activePath.reset();
        activeHasSegment = false;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        cancelActiveStroke(); // A rotated coordinate system must not splice into a live stroke.
        layout = new InkSheetLayout(width, height, 6 * density);
        sheet.set(layout.left, layout.top, layout.left + layout.width, layout.top + layout.height);
        rebuildStrokes();
    }

    @Override protected void onDetachedFromWindow() {
        cancelActiveStroke();
        super.onDetachedFromWindow();
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) cancelActiveStroke();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setXfermode(null);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(sheet, paint);
        if (sheet.isEmpty()) return;
        int saved = canvas.save();
        try {
            canvas.clipRect(sheet);
            if (mapSnapshot != null)
                artwork.drawGeometry(canvas, mapSnapshot.map, layout.mapLeft, layout.mapTop,
                        layout.mapSize / 16, density);
            // The paper/map is outside this temporary layer. CLEAR can erase only note ink.
            if (!rendered.isEmpty() || history.isDrawing()) {
                int layer = canvas.saveLayer(sheet.left, sheet.top, sheet.right, sheet.bottom, null);
                try {
                    for (RenderedStroke stroke : rendered)
                        drawStroke(canvas, stroke.path, stroke.x, stroke.y, stroke.hasSegment,
                                stroke.eraser, stroke.width);
                    if (history.isDrawing())
                        drawStroke(canvas, activePath, activeStartX, activeStartY, activeHasSegment,
                                activeEraser, activeEraser ? ERASER_WIDTH : PEN_WIDTH);
                } finally {
                    paint.setXfermode(null);
                    canvas.restoreToCount(layer);
                }
            }
            if (mapSnapshot != null)
                artwork.drawMarkers(canvas, flags, mapSnapshot, true, layout.mapLeft, layout.mapTop,
                        layout.mapSize / 16, density);
        } finally {
            paint.setXfermode(null);
            canvas.restoreToCount(saved);
        }
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        canvas.drawRect(sheet, paint);
    }

    private void drawStroke(Canvas canvas, Path path, float x, float y, boolean hasSegment,
            boolean erase, float width) {
        paint.setColor(Color.BLACK);
        paint.setXfermode(erase ? clear : null);
        float strokeWidth = width * Math.min(sheet.width(), sheet.height());
        paint.setStrokeWidth(strokeWidth);
        if (hasSegment) {
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, paint);
        } else {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x, y, strokeWidth / 2, paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        // Always consume this view's stream, including margins, cancelled gestures and extra fingers.
        if (!isEnabled()) { cancelActiveStroke(); return true; }
        int action = event.getActionMasked();
        try {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    cancelActiveStroke();
                    if (sheet.isEmpty() || !sheet.contains(event.getX(), event.getY())) return true;
                    activeEraser = eraser;
                    float x = normalX(event.getX());
                    float y = normalY(event.getY());
                    history.beginStroke(activeEraser, activeEraser ? ERASER_WIDTH : PEN_WIDTH, x, y);
                    activePointer = event.getPointerId(0);
                    activeStartX = toX(x);
                    activeStartY = toY(y);
                    activePath.moveTo(activeStartX, activeStartY);
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!history.isDrawing()) return true;
                    int index = event.findPointerIndex(activePointer);
                    if (index < 0) { cancelActiveStroke(); return true; }
                    for (int i = 0; i < event.getHistorySize(); i++)
                        append(event.getHistoricalX(index, i), event.getHistoricalY(index, i));
                    append(event.getX(index), event.getY(index));
                    invalidate();
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_POINTER_UP:
                    cancelActiveStroke();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (history.isDrawing()) {
                        int pointer = event.findPointerIndex(activePointer);
                        if (pointer < 0) cancelActiveStroke();
                        else finish(event, pointer);
                    }
                    performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelActiveStroke();
                    return true;
                default:
                    return true;
            }
        } catch (IllegalArgumentException | IllegalStateException problem) {
            cancelActiveStroke();
            Toast.makeText(getContext(), problem.getMessage() + " The unfinished stroke was not saved.",
                    Toast.LENGTH_LONG).show();
            return true;
        }
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private void append(float px, float py) {
        float x = normalX(px), y = normalY(py);
        if (history.appendPoint(x, y)) {
            activePath.lineTo(toX(x), toY(y));
            activeHasSegment = true;
        }
    }

    private void finish(MotionEvent event, int index) {
        append(event.getX(index), event.getY(index));
        boolean committed = history.finishStroke();
        cancelActiveStroke();
        if (committed) changed();
    }

    private void changed() {
        rebuildStrokes();
        invalidate();
        if (onChange != null) onChange.run();
    }

    private void rebuildStrokes() {
        rendered.clear();
        for (InkNote.Stroke stroke : history.getNote().strokes()) {
            float[] points = stroke.points();
            Path path = new Path();
            path.moveTo(toX(points[0]), toY(points[1]));
            boolean hasSegment = false;
            for (int p = 2; p < points.length; p += 2) {
                path.lineTo(toX(points[p]), toY(points[p + 1]));
                hasSegment |= points[p] != points[0] || points[p + 1] != points[1];
            }
            rendered.add(new RenderedStroke(path, toX(points[0]), toY(points[1]), hasSegment,
                    stroke.eraser(), stroke.width()));
        }
    }

    private float normalX(float x) { return layout.normalX(x); }
    private float normalY(float y) { return layout.normalY(y); }
    private float toX(float x) { return layout.toX(x); }
    private float toY(float y) { return layout.toY(y); }

    private static final class RenderedStroke {
        final Path path;
        final float x, y, width;
        final boolean hasSegment, eraser;
        RenderedStroke(Path path, float x, float y, boolean hasSegment, boolean eraser, float width) {
            this.path = path; this.x = x; this.y = y; this.hasSegment = hasSegment;
            this.eraser = eraser; this.width = width;
        }
    }
}
