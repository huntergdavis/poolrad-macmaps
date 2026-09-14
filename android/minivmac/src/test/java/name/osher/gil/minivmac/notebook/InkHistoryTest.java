package name.osher.gil.minivmac.notebook;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class InkHistoryTest {
    private static InkNote.Stroke dot(float x) {
        return new InkNote.Stroke(false, .01f, new float[]{x, .5f});
    }

    private static void draw(InkHistory history, boolean eraser, float x) {
        history.beginStroke(eraser, .01f, x, .5f);
        history.appendPoint(x, .75f);
        assertTrue(history.finishStroke());
    }

    @Test public void emptyHistoryAndIdleCommandsAreSafe() {
        InkHistory history = new InkHistory(InkNote.empty());
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
        assertFalse(history.undo());
        assertFalse(history.redo());
        assertFalse(history.finishStroke());
        history.cancelStroke();
        assertTrue(history.getNote().strokes().isEmpty());
    }

    @Test public void unfinishedInkIsNeverInAutosaveSnapshotAndCancelDropsOnlyActiveStroke() {
        InkNote loaded = new InkNote(Collections.singletonList(dot(.1f)));
        InkHistory history = new InkHistory(loaded);
        history.beginStroke(false, .01f, .2f, .3f);
        history.appendPoint(.4f, .5f);
        assertTrue(history.isDrawing());
        assertEquals(1, history.getNote().strokes().size());
        history.cancelStroke();
        assertFalse(history.isDrawing());
        assertEquals(loaded.strokes(), history.getNote().strokes());
        assertFalse(history.finishStroke());
    }

    @Test public void dotsAndSegmentsRoundTripWithoutChangingTheLoadedNote() {
        InkNote loaded = new InkNote(Collections.singletonList(dot(.1f)));
        InkHistory history = new InkHistory(loaded);
        history.beginStroke(false, .02f, .4f, .5f);
        assertFalse(history.appendPoint(.4f, .5f));
        history.finishStroke();
        InkNote snapshot = history.getNote();
        assertEquals(2, snapshot.strokes().size());
        assertArrayEquals(new float[]{.4f, .5f}, snapshot.strokes().get(1).points(), 0);
        draw(history, false, .75f);
        assertEquals(3, history.getNote().strokes().size());
        assertEquals(2, snapshot.strokes().size());
        assertEquals(1, loaded.strokes().size());
        assertArrayEquals(new float[]{.75f, .5f, .75f, .75f},
                history.getNote().strokes().get(2).points(), 0);
    }

    @Test public void undoRedoPreservesInkAndEraserOrder() {
        InkHistory history = new InkHistory(InkNote.empty());
        draw(history, false, .1f);
        draw(history, true, .2f);
        draw(history, false, .3f);
        assertTrue(history.undo());
        assertTrue(history.getNote().strokes().get(1).eraser());
        assertTrue(history.undo());
        assertFalse(history.getNote().strokes().get(0).eraser());
        assertTrue(history.redo());
        assertTrue(history.redo());
        assertFalse(history.canRedo());
        assertEquals(3, history.getNote().strokes().size());
        assertFalse(history.getNote().strokes().get(2).eraser());
    }

    @Test public void cancelledAndRejectedNewStrokesKeepRedoUntilARealStrokeCommits() {
        InkHistory history = new InkHistory(new InkNote(Arrays.asList(dot(.1f), dot(.2f))));
        history.undo();
        history.beginStroke(true, .03f, .4f, .4f);
        history.cancelStroke();
        assertTrue(history.canRedo());
        assertThrows(IllegalArgumentException.class,
                () -> history.beginStroke(false, .01f, Float.NaN, 0));
        assertTrue(history.canRedo());
        draw(history, true, .9f);
        assertFalse(history.canRedo());
        assertEquals(2, history.getNote().strokes().size());
        assertTrue(history.getNote().strokes().get(1).eraser());
        assertEquals(.9f, history.getNote().strokes().get(1).points()[0], 0);
    }

    @Test public void badPointAndSecondBeginDoNotDamageTheActiveStroke() {
        InkHistory history = new InkHistory(InkNote.empty());
        history.beginStroke(false, .01f, .1f, .2f);
        assertThrows(IllegalArgumentException.class, () -> history.appendPoint(Float.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> history.appendPoint(-.1f, 0));
        assertThrows(IllegalArgumentException.class, () -> history.appendPoint(0, 1.1f));
        assertThrows(IllegalStateException.class, () -> history.beginStroke(true, .01f, 0, 0));
        assertTrue(history.finishStroke());
        assertArrayEquals(new float[]{.1f, .2f}, history.getNote().strokes().get(0).points(), 0);
        assertThrows(IllegalStateException.class, () -> history.appendPoint(0, 0));
    }

    @Test public void undoRedoAndResetCancelTransientInk() {
        InkHistory history = new InkHistory(new InkNote(Collections.singletonList(dot(.1f))));
        history.beginStroke(false, .01f, .2f, .2f);
        assertTrue(history.undo());
        assertFalse(history.isDrawing());
        history.beginStroke(false, .01f, .3f, .3f);
        assertTrue(history.redo());
        assertFalse(history.isDrawing());
        history.beginStroke(false, .01f, .4f, .4f);
        history.reset(InkNote.empty());
        assertFalse(history.isDrawing());
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }

    @Test public void fullStrokeCanBeCommittedAfterRejectedExtraPoint() {
        InkHistory history = new InkHistory(InkNote.empty());
        history.beginStroke(false, .01f, 0, 0);
        for (int i = 1; i < InkNote.MAX_POINTS_PER_STROKE; i++) history.appendPoint(i % 2, 0);
        assertThrows(IllegalStateException.class, () -> history.appendPoint(0, 1));
        assertTrue(history.finishStroke());
        assertEquals(InkNote.MAX_POINTS_PER_STROKE, history.getNote().strokes().get(0).pointCount());
    }

    @Test public void fullNoteStrokeLimitCanBeRecoveredWithUndoAndReplacement() {
        List<InkNote.Stroke> full = new ArrayList<>();
        for (int i = 0; i < InkNote.MAX_STROKES; i++) full.add(dot(.1f));
        InkHistory history = new InkHistory(new InkNote(full));
        assertThrows(IllegalStateException.class, () -> history.beginStroke(false, .01f, 0, 0));
        assertFalse(history.isDrawing());
        assertTrue(history.undo());
        draw(history, true, .2f);
        assertEquals(InkNote.MAX_STROKES, history.getNote().strokes().size());
        assertFalse(history.canRedo());
    }

    @Test public void totalPointLimitAccountsForUndoAndRejectedPoint() {
        List<InkNote.Stroke> full = new ArrayList<>();
        int pointsLeft = InkNote.MAX_TOTAL_POINTS;
        while (pointsLeft > 0) {
            int count = Math.min(pointsLeft, InkNote.MAX_POINTS_PER_STROKE);
            full.add(new InkNote.Stroke(false, .01f, new float[count * 2]));
            pointsLeft -= count;
        }
        InkHistory history = new InkHistory(new InkNote(full));
        assertThrows(IllegalStateException.class, () -> history.beginStroke(false, .01f, 0, 0));
        history.undo();
        history.beginStroke(false, .01f, 0, 0);
        for (int i = 1; i < InkNote.MAX_POINTS_PER_STROKE; i++) history.appendPoint(i % 2, 0);
        assertThrows(IllegalStateException.class, () -> history.appendPoint(0, 1));
        history.cancelStroke();
        assertTrue(history.canRedo());
        assertTrue(history.redo());
        assertThrows(IllegalStateException.class, () -> history.beginStroke(false, .01f, 0, 0));
    }
}
