package name.osher.gil.minivmac;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Looper;
import android.view.View;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.notebook.InkNote;
import name.osher.gil.minivmac.notebook.NoteIcon;

/** A readable, fitted page image, not a screenshot of the editor or the guest. */
public final class NotePageImage {
    public static final int IMAGE_WIDTH = 1600;
    public static final int IMAGE_HEIGHT = 768;
    private static final int MARGIN = 32;
    private static final int PAGE_TOP = 128;
    private static final int PAGE_WIDTH = IMAGE_WIDTH - 2 * MARGIN;
    private static final int PAGE_HEIGHT = IMAGE_HEIGHT - PAGE_TOP - MARGIN;

    private NotePageImage() { }

    /**
     * Call on the UI thread with committed ink and the sheet's pinned map, not a newer live area.
     * The caller owns/recycles the returned bitmap and decides where to save its PNG bytes.
     * No live View, input stream, notebook file, or undo history is modified by this method.
     * A null map remains explicitly unavailable rather than being substituted with another area.
     */
    public static Bitmap render(Context context, InkNote note, PoolRadState pinned,
            Map<Integer, NoteIcon> symbols, int x, int y, String title) {
        Bitmap bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888);
        try {
            drawPage(new Canvas(bitmap), context, note, pinned, symbols, x, y, title);
            return bitmap;
        } catch (RuntimeException | Error failure) {
            bitmap.recycle();
            throw failure;
        }
    }

    /**
     * Draw one note page onto any canvas at the standard {@link #IMAGE_WIDTH} x
     * {@link #IMAGE_HEIGHT} size, so the same page can go to a bitmap or straight
     * onto a PDF page (F53). Must run on the UI thread; it builds a detached View.
     */
    public static void drawPage(Canvas canvas, Context context, InkNote note, PoolRadState pinned,
            Map<Integer, NoteIcon> symbols, int x, int y, String title) {
        if (context == null || note == null) throw new IllegalArgumentException("A context and note are required");
        if (Looper.myLooper() != Looper.getMainLooper() || Looper.getMainLooper() == null)
            throw new IllegalStateException("Render a note page on the UI thread");
        if (x < 0 || x >= 16 || y < 0 || y >= 16)
            throw new IllegalArgumentException("The note tile must be within the map");
        Map<Integer, NoteIcon> markers = new HashMap<>(symbols == null ? Collections.emptyMap() : symbols);
        for (Map.Entry<Integer, NoteIcon> marker : markers.entrySet()) {
            if (marker.getKey() == null || marker.getKey() < 0 || marker.getKey() >= 256 || marker.getValue() == null)
                throw new IllegalArgumentException("Invalid note symbol");
        }
        NoteIcon selected = markers.get(y * 16 + x);
        if (selected == null) selected = NoteIcon.FLAG;

        // Reuse exactly the editor's map, transparent ink-only erasing, and foreground symbols.
        // This detached fresh View has no listeners, gestures, saved preference or window.
        InkSheetView page = new InkSheetView(context);
        page.setBackgroundColor(Color.WHITE);
        page.setMap(pinned, markers, x, y);
        page.setNote(note);
        page.measure(View.MeasureSpec.makeMeasureSpec(PAGE_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(PAGE_HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        page.fitPage();

        canvas.drawColor(Color.WHITE);
        drawHeader(canvas, pinned, x, y, selected, title);
        int saved = canvas.save();
        try {
            canvas.translate(MARGIN, PAGE_TOP);
            canvas.clipRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
            page.draw(canvas);
        } finally { canvas.restoreToCount(saved); }
    }

    private static void drawHeader(Canvas canvas, PoolRadState pinned, int x, int y,
            NoteIcon selected, String title) {
        Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setColor(Color.BLACK);
        int saved = canvas.save();
        try {
            canvas.clipRect(MARGIN, 0, IMAGE_WIDTH - MARGIN, PAGE_TOP - 8);
            ink.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            ink.setTextSize(32);
            String name = title == null ? "" : title.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
            drawLine(canvas, ink, name.isEmpty() ? "Notebook" : name, 42);
            ink.setTypeface(Typeface.DEFAULT);
            ink.setTextSize(25);
            String area = pinned == null ? "Map unavailable"
                    : pinned.area == null ? "Unidentified area" : pinned.area.label();
            drawLine(canvas, ink, area + "  ·  Tile " + x + "," + y + "  ·  " + selected.label(), 79);
            ink.setTextSize(19);
            drawLine(canvas, ink, pinned == null
                    ? "PoolRad flag note · No map snapshot available · Personal handwriting"
                    : "PoolRad flag note · Pinned map snapshot, north up · Personal ink and symbols", 109);
        } finally { canvas.restoreToCount(saved); }
    }

    private static void drawLine(Canvas canvas, Paint ink, String text, float baseline) {
        float width = PAGE_WIDTH;
        if (ink.measureText(text) > width) {
            String suffix = "…";
            int count = ink.breakText(text, true, width - ink.measureText(suffix), null);
            if (count > 0 && Character.isHighSurrogate(text.charAt(count - 1))) count--;
            text = text.substring(0, count) + suffix;
        }
        canvas.drawText(text, MARGIN, baseline, ink);
    }
}
