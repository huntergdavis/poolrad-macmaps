package name.osher.gil.minivmac.notebook;

import org.junit.Test;
import static org.junit.Assert.*;

public class InkSheetLayoutTest {
    @Test public void paperIsTwoFourByThreeHalvesAndMapFitsNinetyPercent() {
        InkSheetLayout layout = new InkSheetLayout(1200, 700, 12);
        assertEquals(8f / 3f, layout.width / layout.height, .00001f);
        assertEquals(4f / 3f, layout.width / 2 / layout.height, .00001f);
        assertEquals(.9f, layout.mapSize / layout.height, .00001f);
        assertEquals(layout.left + layout.width / 4, layout.mapLeft + layout.mapSize / 2, .0001f);
        assertEquals(layout.top + layout.height / 2, layout.mapTop + layout.mapSize / 2, .0001f);
        assertTrue(layout.mapLeft + layout.mapSize < layout.left + layout.width / 2);
    }

    @Test public void paperLetterboxesWithinPortraitLandscapeAndSmallPanels() {
        float[][] sizes = {{600, 900}, {900, 600}, {320, 100}, {1200, 300}, {60, 40}};
        for (float[] size : sizes) {
            InkSheetLayout layout = new InkSheetLayout(size[0], size[1], 6);
            assertTrue(layout.left >= 6 && layout.top >= 6);
            assertTrue(layout.left + layout.width <= size[0] - 6 + .0001f);
            assertTrue(layout.top + layout.height <= size[1] - 6 + .0001f);
            assertEquals(size[0] / 2, layout.left + layout.width / 2, .0001f);
            assertEquals(size[1] / 2, layout.top + layout.height / 2, .0001f);
        }
    }

    @Test public void tileAnchorsRemainTheSameStoredFractionsAcrossResize() {
        InkSheetLayout portrait = new InkSheetLayout(600, 800, 6);
        InkSheetLayout landscape = new InkSheetLayout(1100, 330, 6);
        for (int y = 0; y <= 16; y++) for (int x = 0; x <= 16; x++) {
            assertEquals(portrait.normalX(portrait.mapTileX(x)),
                    landscape.normalX(landscape.mapTileX(x)), .00001f);
            assertEquals(portrait.normalY(portrait.mapTileY(y)),
                    landscape.normalY(landscape.mapTileY(y)), .00001f);
        }
    }

    @Test public void normalizedCoordinatesCrossBothHalvesAndClampOutsidePaper() {
        InkSheetLayout layout = new InkSheetLayout(800, 500, 6);
        for (float fraction : new float[]{0, .1f, .49f, .5f, .75f, 1}) {
            assertEquals(fraction, layout.normalX(layout.toX(fraction)), .00001f);
            assertEquals(fraction, layout.normalY(layout.toY(fraction)), .00001f);
        }
        assertEquals(0, layout.normalX(-100), 0);
        assertEquals(1, layout.normalX(10000), 0);
        assertEquals(0, layout.normalY(-100), 0);
        assertEquals(1, layout.normalY(10000), 0);
        assertTrue(layout.contains(layout.toX(.5f), layout.toY(.5f)));
        assertFalse(layout.contains(layout.left + layout.width, layout.top));
        assertFalse(layout.contains(Float.NaN, layout.top));
    }

    @Test public void migratedLegacyPaperKeepsItsPhysicalAspectInRightHalf() {
        InkSheetLayout layout = new InkSheetLayout(800, 500, 6);
        float oldNormalizedX = .2f, oldNormalizedY = .7f;
        float migratedX = .5f + oldNormalizedX * .5f;
        assertEquals(layout.left + layout.width / 2 + oldNormalizedX * layout.width / 2,
                layout.toX(migratedX), .0001f);
        assertEquals(layout.top + oldNormalizedY * layout.height, layout.toY(oldNormalizedY), .0001f);
        assertEquals(4f / 3f, (layout.width / 2) / layout.height, .00001f);
    }

    @Test public void emptyOrTinyPanelCannotAcceptInk() {
        for (InkSheetLayout layout : new InkSheetLayout[]{new InkSheetLayout(0, 0, 6),
                new InkSheetLayout(100, 0, 6), new InkSheetLayout(8, 8, 6)}) {
            assertEquals(0, layout.width, 0); assertEquals(0, layout.height, 0);
            assertEquals(0, layout.mapSize, 0); assertFalse(layout.contains(layout.left, layout.top));
            assertEquals(0, layout.normalX(42), 0); assertEquals(0, layout.normalY(42), 0);
        }
    }

    @Test public void invalidDimensionsAreRejectedWithoutNonfiniteGeometry() {
        assertThrows(IllegalArgumentException.class, () -> new InkSheetLayout(-1, 30, 6));
        assertThrows(IllegalArgumentException.class, () -> new InkSheetLayout(30, Float.NaN, 6));
        assertThrows(IllegalArgumentException.class, () -> new InkSheetLayout(30, 30, Float.POSITIVE_INFINITY));
        InkSheetLayout large = new InkSheetLayout(Float.MAX_VALUE, Float.MAX_VALUE, 0);
        assertFalse(Float.isInfinite(large.mapSize));
        assertFalse(Float.isNaN(large.mapLeft));
    }
}
