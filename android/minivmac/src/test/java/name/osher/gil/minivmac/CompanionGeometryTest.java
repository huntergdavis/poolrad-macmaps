package name.osher.gil.minivmac;

import org.junit.Test;
import static org.junit.Assert.*;
import name.osher.gil.minivmac.CompanionGeometry.Bounds;

public class CompanionGeometryTest {
    private static Bounds box(int l, int t, int r, int b) { return new Bounds(l, t, r, b); }
    private static void equal(Bounds actual, int l, int t, int r, int b) {
        assertNotNull(actual);
        assertEquals(l, actual.left); assertEquals(t, actual.top);
        assertEquals(r, actual.right); assertEquals(b, actual.bottom);
    }

    @Test public void portraitUsesActualPaneBelowToolbarNotHalfActivity() {
        equal(CompanionGeometry.dialog(box(0, 24, 1080, 1896), true,
                box(0, 80, 1080, 790)), 0, 80, 1080, 790);
    }

    @Test public void keyboardAndLandscapeKeepDialogWithinSmallerCompanion() {
        equal(CompanionGeometry.dialog(box(0, 24, 1080, 1440), true,
                box(0, 80, 1080, 510)), 0, 80, 1080, 510);
        equal(CompanionGeometry.dialog(box(36, 0, 1884, 1032), true,
                box(36, 56, 1884, 300)), 36, 56, 1884, 300);
    }

    @Test public void screenCoordinatesPreserveRelocatedWindowAndInsets() {
        equal(CompanionGeometry.dialog(box(100, 700, 1000, 1780), true,
                box(120, 756, 980, 1020)), 120, 756, 980, 1020);
    }

    @Test public void clipsEveryCompanionEdgeToVisibleHost() {
        equal(CompanionGeometry.dialog(box(20, 40, 400, 600), true,
                box(0, 0, 500, 700)), 20, 40, 400, 600);
        equal(CompanionGeometry.dialog(box(20, 40, 400, 600), true,
                box(5, 80, 390, 650)), 20, 80, 390, 600);
    }

    @Test public void presentButHiddenOrEmptyNeverFallsBack() {
        Bounds host = box(0, 0, 1080, 1920);
        assertNull(CompanionGeometry.dialog(host, true, null));
        assertNull(CompanionGeometry.dialog(host, true, box(0, 80, 1080, 80)));
        assertNull(CompanionGeometry.dialog(host, true, box(0, 0, 0, 200)));
    }

    @Test public void fullyClippedOrJustTouchingPaneIsUnavailable() {
        Bounds host = box(0, 40, 1000, 1800);
        assertNull(CompanionGeometry.dialog(host, true, box(0, 0, 1000, 40)));
        assertNull(CompanionGeometry.dialog(host, true, box(1000, 40, 1200, 600)));
        assertNull(CompanionGeometry.dialog(host, true, box(0, 1900, 1000, 2000)));
    }

    @Test public void legacyHalfWindowOnlyWhenCompanionDoesNotExist() {
        equal(CompanionGeometry.dialog(box(100, 700, 1000, 1781), false, null),
                100, 700, 1000, 1240);
    }

    @Test public void rejectsInvalidHostAndOverflowWithoutInventingOnePixelWindow() {
        assertNull(CompanionGeometry.dialog(null, false, null));
        assertNull(CompanionGeometry.dialog(box(0, 0, 0, 100), false, null));
        assertNull(CompanionGeometry.dialog(box(0, 1, 100, 0), false, null));
        assertNull(CompanionGeometry.dialog(box(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 10), false, null));
        assertNull(CompanionGeometry.dialog(box(0, 0, 100, 1), false, null));
        equal(CompanionGeometry.dialog(box(0, 0, 1, 1), true, box(0, 0, 1, 1)), 0, 0, 1, 1);
    }

    @Test public void unchangedOriginalBudgetAcrossCommonGuestAndKeyboardSizes() {
        int[][] guests = {{640, 480}, {512, 342}, {1024, 768}};
        int[][] panes = {{1080, 1800}, {1080, 1300}, {1920, 900}, {600, 400}, {300, 1600}};
        for (int[] guest : guests) for (int[] pane : panes) {
            int width = pane[0], available = pane[1];
            int spare = available - (int) ((long) width * guest[1] / guest[0]);
            int oldHeight = Math.min(Math.min(width, Math.max(spare, available / 3)), available / 2);
            assertEquals(oldHeight, CompanionGeometry.allocation(width, available, guest[0], guest[1]));
        }
    }

    @Test public void keyboardBudgetStillReservesUnobscuredGuestSpace() {
        assertEquals(590, CompanionGeometry.allocation(1080, 1400, 640, 480));
        assertEquals(326, CompanionGeometry.allocation(1080, 1400 - 420, 640, 480));
        assertEquals(654, 1400 - 420 - CompanionGeometry.allocation(1080, 980, 640, 480));
    }

    @Test public void budgetIsBoundedAndHandlesInvalidAndVeryLargeSizes() {
        assertEquals(0, CompanionGeometry.allocation(0, 1000, 640, 480));
        assertEquals(0, CompanionGeometry.allocation(1000, -1, 640, 480));
        assertEquals(0, CompanionGeometry.allocation(1000, 1000, 0, 480));
        assertEquals(0, CompanionGeometry.allocation(1000, 1000, 640, 0));
        assertEquals(0, CompanionGeometry.allocation(100, 1, 640, 480));
        int result = CompanionGeometry.allocation(Integer.MAX_VALUE, 100, 1, Integer.MAX_VALUE);
        assertTrue(result >= 0 && result <= 50);
    }
}
