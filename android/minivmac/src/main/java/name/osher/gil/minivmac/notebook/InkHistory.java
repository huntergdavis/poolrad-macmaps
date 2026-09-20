package name.osher.gil.minivmac.notebook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A note editor's bounded, transactional stroke history; it never changes the loaded note. */
public final class InkHistory {
    private final List<InkNote.Stroke> strokes = new ArrayList<>();
    private int cursor;
    private NoteTemplate template;
    private int committedPoints;
    private float[] active;
    private int activePoints;
    private boolean activeEraser;
    private float activeWidth;

    public InkHistory(InkNote note) { reset(note); }

    public void reset(InkNote note) {
        if (note == null) throw new IllegalArgumentException("A note is required");
        cancelStroke();
        template = note.template();
        strokes.clear();
        strokes.addAll(note.strokes());
        cursor = strokes.size();
        committedPoints = 0;
        for (InkNote.Stroke stroke : strokes) committedPoints += stroke.pointCount();
    }

    public InkNote getNote() { return new InkNote(strokes.subList(0, cursor), template); }
    /** Paper is independent of stroke undo/redo; switching it never discards handwriting. */
    public boolean setTemplate(NoteTemplate value) {
        if (value == null) throw new IllegalArgumentException("Missing note template");
        cancelStroke();
        if (template == value) return false;
        template = value;
        return true;
    }
    public NoteTemplate template() { return template; }
    public boolean canUndo() { return cursor > 0; }
    public boolean canRedo() { return cursor < strokes.size(); }
    public boolean isDrawing() { return active != null; }

    public void beginStroke(boolean eraser, float width, float x, float y) {
        if (isDrawing()) throw new IllegalStateException("Finish or cancel the active stroke first");
        // Model validation and capacity checks precede all mutation, including the redo branch.
        new InkNote.Stroke(eraser, width, new float[]{x, y});
        if (cursor >= InkNote.MAX_STROKES || committedPoints >= InkNote.MAX_TOTAL_POINTS)
            throw new IllegalStateException("This note is full. Undo a stroke to make room.");
        active = new float[64];
        active[0] = x;
        active[1] = y;
        activePoints = 1;
        activeEraser = eraser;
        activeWidth = width;
    }

    /** Returns false for an identical point; rejected points leave the active stroke intact. */
    public boolean appendPoint(float x, float y) {
        if (!isDrawing()) throw new IllegalStateException("No active stroke");
        checkCoordinate(x);
        checkCoordinate(y);
        int next = activePoints * 2;
        if (active[next - 2] == x && active[next - 1] == y) return false;
        if (activePoints >= InkNote.MAX_POINTS_PER_STROKE
                || committedPoints + activePoints >= InkNote.MAX_TOTAL_POINTS)
            throw new IllegalStateException("This stroke is too long. Lift the pen and try a shorter stroke.");
        if (next + 2 > active.length)
            active = Arrays.copyOf(active, Math.min(InkNote.MAX_POINTS_PER_STROKE * 2, active.length * 2));
        active[next] = x;
        active[next + 1] = y;
        activePoints++;
        return true;
    }

    public boolean finishStroke() {
        if (!isDrawing()) return false;
        InkNote.Stroke stroke = new InkNote.Stroke(activeEraser, activeWidth,
                Arrays.copyOf(active, activePoints * 2));
        while (strokes.size() > cursor) strokes.remove(strokes.size() - 1);
        strokes.add(stroke);
        cursor++;
        committedPoints += activePoints;
        cancelStroke();
        return true;
    }

    public void cancelStroke() { active = null; activePoints = 0; }

    public boolean undo() {
        cancelStroke();
        if (!canUndo()) return false;
        committedPoints -= strokes.get(--cursor).pointCount();
        return true;
    }

    public boolean redo() {
        cancelStroke();
        if (!canRedo()) return false;
        committedPoints += strokes.get(cursor++).pointCount();
        return true;
    }

    private static void checkCoordinate(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Ink coordinates must be between zero and one");
    }
}
