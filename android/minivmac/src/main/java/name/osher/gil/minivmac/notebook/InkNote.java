package name.osher.gil.minivmac.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable vector ink. Coordinates are fractions of a fixed-aspect note sheet. */
public final class InkNote {
    public static final int MAX_STROKES = 2048;
    /** Number of x/y coordinate pairs, not the length of the float array. */
    public static final int MAX_POINTS_PER_STROKE = 16384;
    public static final int MAX_TOTAL_POINTS = 131072;

    private final List<Stroke> strokes;
    private final NoteTemplate template;

    public InkNote(List<Stroke> strokes) { this(strokes, NoteTemplate.PLAIN); }

    public InkNote(List<Stroke> strokes, NoteTemplate template) {
        if (template == null) throw new IllegalArgumentException("Missing note template");
        this.template = template;
        if (strokes == null || strokes.size() > MAX_STROKES) {
            throw new IllegalArgumentException("This note has too many strokes");
        }
        int total = 0;
        ArrayList<Stroke> copy = new ArrayList<>(strokes.size());
        for (Stroke stroke : strokes) {
            if (stroke == null) throw new IllegalArgumentException("Missing stroke");
            total += stroke.pointCount();
            if (total > MAX_TOTAL_POINTS) {
                throw new IllegalArgumentException("This note has too many points");
            }
            copy.add(stroke);
        }
        this.strokes = Collections.unmodifiableList(copy);
    }

    public static InkNote empty() { return new InkNote(Collections.emptyList()); }
    public List<Stroke> strokes() { return strokes; }
    public NoteTemplate template() { return template; }
    public InkNote withTemplate(NoteTemplate value) { return new InkNote(strokes, value); }

    /** Erasers are replayed in stroke order, never applied to the underlying map. */
    public static final class Stroke {
        private final boolean eraser;
        private final float width;
        private final float[] points;

        public Stroke(boolean eraser, float width, float[] points) {
            if (!(width > 0f && width <= 1f)) {
                throw new IllegalArgumentException("Stroke width must be between zero and one");
            }
            if (points == null || points.length < 2 || points.length % 2 != 0
                    || points.length / 2 > MAX_POINTS_PER_STROKE) {
                throw new IllegalArgumentException("Invalid stroke point count");
            }
            this.points = points.clone();
            for (float point : this.points) {
                if (!(point >= 0f && point <= 1f)) {
                    throw new IllegalArgumentException("Stroke coordinates must be finite sheet fractions");
                }
            }
            this.eraser = eraser;
            this.width = width;
        }

        public boolean eraser() { return eraser; }
        /** Width is a fraction of the sheet's shorter dimension. */
        public float width() { return width; }
        public float[] points() { return points.clone(); }
        public int pointCount() { return points.length / 2; }
    }
}
