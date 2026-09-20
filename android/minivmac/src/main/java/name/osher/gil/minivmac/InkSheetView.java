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
import name.osher.gil.minivmac.notebook.InkViewport;
import name.osher.gil.minivmac.notebook.NoteIcon;
import name.osher.gil.minivmac.notebook.NoteTemplate;

/** One flag's fixed 8:3 paper: map left, writing right, and ink across both halves. */
public final class InkSheetView extends View {
    private static final float PEN_WIDTH = .006f;
    private static final float ERASER_WIDTH = .055f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PorterDuffXfermode clear = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
    private final RectF sheet = new RectF();
    private final MapArtwork artwork = new MapArtwork();
    private InkSheetLayout layout = new InkSheetLayout(0, 0, 0);
    private InkViewport viewport = new InkViewport(0, 0, 0);
    private PoolRadState mapSnapshot;
    private Map<Integer, NoteIcon> flags = Collections.emptyMap();
    private final InkHistory history = new InkHistory(InkNote.empty());
    private final List<RenderedStroke> rendered = new ArrayList<>();
    private final Path activePath = new Path();
    private final float density;
    private int activePointer = MotionEvent.INVALID_POINTER_ID;
    private int activeTool;
    private boolean eraser;
    private boolean activeEraser;
    private boolean penOnly, activeStylus, blockFingers, navigating, penHovering;
    private int navFirst = -1, navSecond = -1;
    private float navX, navY, navSpan;
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
        describeInput();
        rebuildStrokes();
        invalidate();
    }

    public void setTemplate(NoteTemplate template) {
        cancelActiveStroke();
        if (history.setTemplate(template)) { describeInput(); changed(); }
    }

    public InkNote getNote() { return history.getNote(); }
    public void setOnChangeListener(Runnable listener) { onChange = listener; }
    public boolean canUndo() { return history.canUndo(); }
    public boolean canRedo() { return history.canRedo(); }
    public boolean isPenOnly() { return penOnly; }
    public float zoomFactor() { return viewport.scale(); }

    public void setPenOnly(boolean enabled) {
        cancelActiveStroke();
        penOnly = enabled;
        describeInput();
    }

    public void fitPage() {
        cancelActiveStroke();
        viewport.fit();
        invalidate();
    }

    public void setEraser(boolean enabled) {
        cancelActiveStroke();
        eraser = enabled;
        describeInput();
    }

    private void describeInput() {
        setContentDescription("Flag note. Map left, writing space right. " + history.template().label() + ". "
                + (eraser ? "Eraser selected; only ink is erased. " : "Black pen selected. ")
                + (penOnly ? "Pen only: a finger moves the page without drawing. " : "Pen or one finger draws. ")
                + "Two fingers zoom and move the page. Fit page restores the whole sheet.");
    }

    public void undo() {
        cancelActiveStroke();
        if (history.undo()) changed();
    }

    public void redo() {
        cancelActiveStroke();
        if (history.redo()) changed();
    }

    public boolean isDrawing() { return history.isDrawing(); }

    public void cancelActiveStroke() {
        history.cancelStroke();
        activePointer = MotionEvent.INVALID_POINTER_ID;
        activeStylus = navigating = false;
        blockFingers = true; // Never turn the tail of a canceled gesture into a new stroke.
        navFirst = navSecond = -1;
        activePath.reset();
        activeHasSegment = false;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        cancelActiveStroke(); // A rotated coordinate system must not splice into a live stroke.
        viewport = new InkViewport(width, height, 6 * density);
        layout = viewport.paper;
        sheet.set(layout.left, layout.top, layout.left + layout.width, layout.top + layout.height);
        rebuildStrokes();
    }

    @Override protected void onDetachedFromWindow() {
        cancelActiveStroke();
        super.onDetachedFromWindow();
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) { penHovering = false; cancelActiveStroke(); }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int saved = canvas.save();
        try {
            canvas.translate(viewport.offsetX(), viewport.offsetY());
            canvas.scale(viewport.scale(), viewport.scale());
            drawPaper(canvas);
        } finally { canvas.restoreToCount(saved); }
    }

    private void drawPaper(Canvas canvas) {
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
            drawTemplate(canvas);
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

    /** Background guides use sheet coordinates, so zoom, export and ink stay aligned. */
    private void drawTemplate(Canvas canvas) {
        NoteTemplate template = history.template();
        if (template == NoteTemplate.PLAIN) return;
        int saved = canvas.save();
        try {
            canvas.clipRect(layout.toX(.5f), sheet.top, sheet.right, sheet.bottom);
            paint.setXfermode(null);
            paint.setColor(0xffa0a0a0);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(.5f, layout.height / 700));
            if (template == NoteTemplate.MAP_FRAME) {
                float left = layout.mapLeft + layout.width / 2;
                canvas.drawRect(left, layout.mapTop, left + layout.mapSize,
                        layout.mapTop + layout.mapSize, paint);
                return;
            }
            float left = layout.toX(.53f), right = layout.toX(.97f);
            float top = layout.toY(.05f), bottom = layout.toY(.95f);
            float step = (bottom - top) / (template == NoteTemplate.GRID ? 16 : 12);
            if (template == NoteTemplate.GRID) {
                int columns = (int) ((right - left) / step);
                right = left + columns * step;
                for (int i = 0; i <= columns; i++)
                    canvas.drawLine(left + i * step, top, left + i * step, bottom, paint);
            }
            int rows = template == NoteTemplate.GRID ? 16 : 12;
            for (int i = template == NoteTemplate.GRID ? 0 : 1; i <= rows; i++)
                canvas.drawLine(left, top + i * step, right, top + i * step, paint);
        } finally { canvas.restoreToCount(saved); }
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
                    blockFingers = false;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    if (stylus(event, 0)) begin(event, 0);
                    else if (penHovering) blockFingers = true;
                    else if (penOnly) { navigating = true; navigate(event, -1, false); }
                    else begin(event, 0);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (navigating) { navigate(event, -1, true); return true; }
                    if (!history.isDrawing()) return true;
                    int index = event.findPointerIndex(activePointer);
                    if (index < 0 || activeTool != event.getToolType(index)) { cancelActiveStroke(); return true; }
                    appendEvent(event, index);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    int added = event.getActionIndex();
                    if (stylus(event, added)) {
                        if (!activeStylus) { cancelActiveStroke(); begin(event, added); }
                    } else if (!activeStylus && !blockFingers && !penHovering) {
                        cancelActiveStroke(); blockFingers = false;
                        navigating = true; navigate(event, -1, false);
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    int lifted = event.getActionIndex();
                    if (event.getPointerId(lifted) == activePointer) end(event, lifted);
                    else if (navigating) navigate(event, lifted, false);
                    // A different (possibly rejected palm) pointer does not cancel the real pen.
                    return true;
                case MotionEvent.ACTION_UP:
                    if (event.getPointerId(event.getActionIndex()) == activePointer)
                        end(event, event.getActionIndex());
                    else cancelActiveStroke();
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

    @Override public boolean onHoverEvent(MotionEvent event) {
        if (stylus(event, 0)) {
            penHovering = event.getActionMasked() != MotionEvent.ACTION_HOVER_EXIT;
            if (penHovering && !activeStylus) cancelActiveStroke();
            return true;
        }
        return super.onHoverEvent(event);
    }

    private static boolean stylus(MotionEvent event, int index) {
        int tool = event.getToolType(index);
        return tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER;
    }

    private void begin(MotionEvent event, int index) {
        if (sheet.isEmpty() || !sheet.contains(viewport.paperX(event.getX(index)),
                viewport.paperY(event.getY(index)))) return;
        activeStylus = stylus(event, index);
        activeTool = event.getToolType(index);
        activeEraser = eraser || event.getToolType(index) == MotionEvent.TOOL_TYPE_ERASER;
        float x = normalX(event.getX(index)), y = normalY(event.getY(index));
        history.beginStroke(activeEraser, activeEraser ? ERASER_WIDTH : PEN_WIDTH, x, y);
        activePointer = event.getPointerId(index);
        activeStartX = toX(x); activeStartY = toY(y);
        activePath.moveTo(activeStartX, activeStartY);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        invalidate();
    }

    private void appendEvent(MotionEvent event, int index) {
        for (int i = 0; i < event.getHistorySize(); i++)
            append(event.getHistoricalX(index, i), event.getHistoricalY(index, i));
        append(event.getX(index), event.getY(index));
    }

    private void end(MotionEvent event, int index) {
        // Android 13+ marks a rejected pointer's UP. The constant is inlined on older APIs.
        // https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/advanced-stylus-features
        if ((event.getFlags() & MotionEvent.FLAG_CANCELED) != 0
                || event.getToolType(index) != activeTool) cancelActiveStroke();
        else finish(event, index);
    }

    /** Only finger streams navigate. Pointer changes rebase instead of jumping the page. */
    private void navigate(MotionEvent event, int excluded, boolean move) {
        int first = -1, second = -1;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == excluded || stylus(event, i)) continue;
            if (first < 0) first = i;
            else if (second < 0) second = i;
        }
        if (first < 0) { navFirst = navSecond = -1; return; }
        // Stable IDs are independent of the array order Android chooses for this event.
        if (second >= 0 && event.getPointerId(first) > event.getPointerId(second)) {
            int swap = first; first = second; second = swap;
        }
        int firstId = event.getPointerId(first), secondId = second < 0 ? -1 : event.getPointerId(second);
        float x = event.getX(first), y = event.getY(first), span = 0;
        if (second >= 0) {
            span = (float) Math.hypot(x - event.getX(second), y - event.getY(second));
            x = (x + event.getX(second)) / 2; y = (y + event.getY(second)) / 2;
        }
        if (move && firstId == navFirst && secondId == navSecond) {
            if (second >= 0 && navSpan > 0 && span > 0) viewport.zoom(span / navSpan, navX, navY);
            viewport.pan(x - navX, y - navY);
            invalidate();
        }
        navFirst = firstId; navSecond = secondId; navX = x; navY = y; navSpan = span;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    }

    private void append(float px, float py) {
        float x = normalX(px), y = normalY(py);
        if (history.appendPoint(x, y)) {
            activePath.lineTo(toX(x), toY(y));
            activeHasSegment = true;
        }
    }

    private void finish(MotionEvent event, int index) {
        appendEvent(event, index);
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

    private float normalX(float x) { return layout.normalX(viewport.paperX(x)); }
    private float normalY(float y) { return layout.normalY(viewport.paperY(y)); }
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
