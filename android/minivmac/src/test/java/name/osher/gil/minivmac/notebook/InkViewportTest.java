package name.osher.gil.minivmac.notebook;

import org.junit.Test;

import static org.junit.Assert.*;

public class InkViewportTest {
    private static final float EPSILON = .001f;

    private static InkViewport page() {
        // An exact 800 x 300 paper with a 20-pixel margin on all sides.
        return new InkViewport(840, 340, 20);
    }

    private static float screenX(InkViewport view, float paperX) {
        return paperX * view.scale() + view.offsetX();
    }

    private static float screenY(InkViewport view, float paperY) {
        return paperY * view.scale() + view.offsetY();
    }

    private static void state(InkViewport view, float scale, float x, float y) {
        assertEquals(scale, view.scale(), EPSILON);
        assertEquals(x, view.offsetX(), EPSILON);
        assertEquals(y, view.offsetY(), EPSILON);
    }

    @Test public void startsFittedAndCannotPanTheUnzoomedPage() {
        InkViewport view = page();
        state(view, 1, 0, 0);
        assertEquals(800, view.paper.width, EPSILON);
        assertEquals(300, view.paper.height, EPSILON);
        assertEquals(20, view.paper.left, EPSILON);
        assertEquals(20, view.paper.top, EPSILON);
        view.pan(100, -50);
        state(view, 1, 0, 0);
        assertEquals(350, view.paperX(350), EPSILON);
        assertEquals(140, view.paperY(140), EPSILON);
    }

    @Test public void pinchKeepsThePaperPointUnderItsFocusWhenBoundsAllow() {
        InkViewport view = page();
        float paperX = view.paperX(350), paperY = view.paperY(140);
        view.zoom(2, 350, 140);
        state(view, 2, -350, -140);
        assertEquals(paperX, view.paperX(350), EPSILON);
        assertEquals(paperY, view.paperY(140), EPSILON);
        // The next pinch is anchored against the current transform, not Fit.
        view.pan(-40, -10);
        paperX = view.paperX(400); paperY = view.paperY(180);
        view.zoom(1.5f, 400, 180);
        assertEquals(3, view.scale(), EPSILON);
        assertEquals(paperX, view.paperX(400), EPSILON);
        assertEquals(paperY, view.paperY(180), EPSILON);
    }

    @Test public void scaleClampsAtOneAndFourWithoutOvershoot() {
        InkViewport view = page();
        view.zoom(100, 420, 170);
        state(view, 4, -1260, -510);
        view.zoom(Float.MAX_VALUE, 420, 170);
        state(view, 4, -1260, -510);
        view.zoom(Float.MIN_VALUE, 420, 170);
        state(view, 1, 0, 0);
        view.zoom(.5f, 420, 170);
        state(view, 1, 0, 0);
    }

    @Test public void panStopsAtEachPaperEdgeInsteadOfExposingEmptySpace() {
        InkViewport view = page();
        view.zoom(2, 420, 170);
        view.pan(10000, 10000);
        state(view, 2, -20, -20);
        assertEquals(20, screenX(view, view.paper.left), EPSILON);
        assertEquals(20, screenY(view, view.paper.top), EPSILON);
        view.pan(-20000, -20000);
        state(view, 2, -820, -320);
        assertEquals(820, screenX(view, view.paper.left + view.paper.width), EPSILON);
        assertEquals(320, screenY(view, view.paper.top + view.paper.height), EPSILON);
    }

    @Test public void letterboxedAxisStaysCenteredUntilItsPaperExceedsTheViewport() {
        InkViewport view = new InkViewport(840, 1000, 20);
        assertEquals(350, view.paper.top, EPSILON);
        view.zoom(2, 400, 100);
        assertEquals(200, screenY(view, view.paper.top), EPSILON);
        assertEquals(800, screenY(view, view.paper.top + view.paper.height), EPSILON);
        view.pan(-30, 5000);
        assertEquals(200, screenY(view, view.paper.top), EPSILON);
        view.zoom(2, 420, 500);
        view.pan(0, 5000);
        assertEquals(20, screenY(view, view.paper.top), EPSILON);
        view.pan(0, -10000);
        assertEquals(980, screenY(view, view.paper.top + view.paper.height), EPSILON);
    }

    @Test public void inverseMappingPreservesOriginalNormalizedInkAfterZoomAndPan() {
        InkViewport view = page();
        view.zoom(3, 420, 170);
        view.pan(110, -70);
        float[] original = {.1f, .2f, .5f, .5f, .85f, .9f};
        for (int i = 0; i < original.length; i += 2) {
            float x = screenX(view, view.paper.toX(original[i]));
            float y = screenY(view, view.paper.toY(original[i + 1]));
            assertEquals(original[i], view.paper.normalX(view.paperX(x)), EPSILON);
            assertEquals(original[i + 1], view.paper.normalY(view.paperY(y)), EPSILON);
        }
        // A screen-space stroke width also maps back through the same scale.
        assertEquals(.02f, (view.paperX(300 + .02f * view.paper.height * view.scale())
                - view.paperX(300)) / view.paper.height, EPSILON);
    }

    @Test public void fitRestoresTheOriginalLayoutAndInverseTransform() {
        InkViewport view = page();
        InkSheetLayout original = view.paper;
        view.zoom(4, 350, 140);
        view.pan(-300, 80);
        view.fit();
        state(view, 1, 0, 0);
        assertSame(original, view.paper);
        assertEquals(123, view.paperX(123), EPSILON);
        assertEquals(234, view.paperY(234), EPSILON);
        view.fit();
        state(view, 1, 0, 0);
    }

    @Test public void invalidGesturesDoNotChangeAnExistingTransform() {
        InkViewport view = page();
        view.zoom(2, 350, 140);
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            view.zoom(invalid, 400, 150);
            view.zoom(2, invalid, 150);
            view.zoom(2, 400, invalid);
            view.pan(invalid, 10);
            view.pan(10, invalid);
            state(view, 2, -350, -140);
        }
        view.zoom(0, 400, 150);
        view.zoom(-1, 400, 150);
        state(view, 2, -350, -140);
    }

    @Test public void emptyPaperDoesNotZoomOrAcquireOffsets() {
        for (InkViewport view : new InkViewport[]{new InkViewport(0, 0, 0),
                new InkViewport(100, 100, 60)}) {
            assertEquals(0, view.paper.width, EPSILON);
            view.zoom(2, 50, 50);
            view.pan(100, -100);
            state(view, 1, 0, 0);
        }
    }

    @Test public void invalidViewportDimensionsAreRejectedByThePaperLayout() {
        assertThrows(IllegalArgumentException.class, () -> new InkViewport(-1, 300, 20));
        assertThrows(IllegalArgumentException.class, () -> new InkViewport(840, Float.NaN, 20));
        assertThrows(IllegalArgumentException.class, () -> new InkViewport(840, 300, Float.POSITIVE_INFINITY));
    }
}
