package name.osher.gil.minivmac.notebook;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class InkNoteTest {
    @Test public void copiesCoordinatesAndTheStrokeList() {
        float[] points = {0.1f, 0.2f, 0.9f, 1f};
        InkNote.Stroke stroke = new InkNote.Stroke(false, 0.01f, points);
        points[0] = 0.8f;
        List<InkNote.Stroke> original = new ArrayList<>(Collections.singletonList(stroke));
        InkNote note = new InkNote(original);
        original.clear();
        assertEquals(1, note.strokes().size());
        assertEquals(2, stroke.pointCount());
        assertEquals(0.1f, stroke.points()[0], 0f);
        stroke.points()[0] = 0.7f;
        assertEquals(0.1f, stroke.points()[0], 0f);
        assertThrows(UnsupportedOperationException.class, () -> note.strokes().clear());
    }

    @Test public void keepsEraserAndPenOrderIncludingDots() {
        InkNote.Stroke pen = new InkNote.Stroke(false, 0.01f, new float[]{0f, 1f});
        InkNote.Stroke eraser = new InkNote.Stroke(true, 0.04f, new float[]{1f, 0f});
        InkNote note = new InkNote(Arrays.asList(pen, eraser, pen));
        assertSame(pen, note.strokes().get(0));
        assertTrue(note.strokes().get(1).eraser());
        assertEquals(0.04f, note.strokes().get(1).width(), 0f);
        assertFalse(note.strokes().get(2).eraser());
        assertTrue(InkNote.empty().strokes().isEmpty());
    }

    @Test public void rejectsInvalidCoordinatesWidthsAndShapes() {
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -0.01f, 1.01f}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new InkNote.Stroke(false, 0.01f, new float[]{invalid, 0f}));
        }
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f, 1.01f}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new InkNote.Stroke(false, invalid, new float[]{0f, 0f}));
        }
        assertThrows(IllegalArgumentException.class, () -> new InkNote.Stroke(false, 0.01f, null));
        assertThrows(IllegalArgumentException.class, () -> new InkNote.Stroke(false, 0.01f, new float[0]));
        assertThrows(IllegalArgumentException.class, () -> new InkNote.Stroke(false, 0.01f, new float[3]));
        assertThrows(IllegalArgumentException.class, () -> new InkNote(null));
        assertThrows(IllegalArgumentException.class, () -> new InkNote(Collections.singletonList(null)));
    }

    @Test public void boundsPerStrokeAndTotalMemory() {
        InkNote.Stroke large = new InkNote.Stroke(false, 0.01f,
                new float[InkNote.MAX_POINTS_PER_STROKE * 2]);
        assertThrows(IllegalArgumentException.class, () -> new InkNote.Stroke(false, 0.01f,
                new float[(InkNote.MAX_POINTS_PER_STROKE + 1) * 2]));
        int fits = InkNote.MAX_TOTAL_POINTS / InkNote.MAX_POINTS_PER_STROKE;
        assertEquals(fits, new InkNote(Collections.nCopies(fits, large)).strokes().size());
        assertThrows(IllegalArgumentException.class, () -> new InkNote(Collections.nCopies(fits + 1, large)));
        InkNote.Stroke dot = new InkNote.Stroke(false, 0.01f, new float[]{0f, 0f});
        assertEquals(InkNote.MAX_STROKES,
                new InkNote(Collections.nCopies(InkNote.MAX_STROKES, dot)).strokes().size());
        assertThrows(IllegalArgumentException.class,
                () -> new InkNote(Collections.nCopies(InkNote.MAX_STROKES + 1, dot)));
    }
}
