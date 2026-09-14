package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class MapViewportTest {
    @Test public void tileCentersSurviveResizeAndDensity() {
        for (int[] size : new int[][]{{1200, 520}, {1600, 300}, {600, 700}}) {
            for (float density : new float[]{1, 1.25f, 2}) {
                MapViewport grid = new MapViewport(size[0], size[1], density);
                for (int tile = 0; tile < 256; tile++)
                    assertEquals(tile, grid.tileAt(grid.left + (tile % 16 + .5f) * grid.cell,
                            grid.top + (tile / 16 + .5f) * grid.cell));
            }
        }
    }
    @Test public void rejectsMarginsAndExactOuterEdges() {
        MapViewport grid = new MapViewport(1200, 520, 1.25f);
        assertEquals(-1, grid.tileAt(grid.left - 1, grid.top));
        assertEquals(-1, grid.tileAt(grid.left, grid.top - 1));
        assertEquals(-1, grid.tileAt(grid.left + 16 * grid.cell, grid.top));
        assertEquals(-1, grid.tileAt(grid.left, grid.top + 16 * grid.cell));
        assertEquals(-1, grid.tileAt(Float.NaN, grid.top));
    }
    @Test public void tinyViewportCannotAcceptFlags() {
        assertEquals(-1, new MapViewport(20, 30, 2).tileAt(10, 15));
    }

    @Test public void nearbyCentersSurviveResizeAndDensity() {
        for (int[] size : new int[][]{{1200, 520}, {1600, 300}, {600, 700}}) {
            for (float density : new float[]{1, 1.25f, 2}) {
                MapViewport grid = new MapViewport(size[0], size[1], density);
                for (int tile : new int[]{0, 15, 17, 128, 240, 255}) {
                    float x = grid.left + (tile % 16 + .5f) * grid.cell;
                    float y = grid.top + (tile / 16 + .5f) * grid.cell;
                    assertArrayEquals(new int[]{tile}, grid.nearbyFlags(x, y,
                            Collections.singletonList(tile), 0));
                }
            }
        }
    }

    @Test public void nearestFirstThenTileOrderRegardlessOfCollectionOrder() {
        MapViewport grid = integerGrid();
        List<Integer> flags = Arrays.asList(33, 18, 17, 1, 16, 0);
        assertArrayEquals(new int[]{17, 1, 16, 18, 33},
                grid.nearbyFlags(centerX(grid, 17), centerY(grid, 17), flags, 32));
        Collections.reverse(flags);
        assertArrayEquals(new int[]{17, 1, 16, 18, 33},
                grid.nearbyFlags(centerX(grid, 17), centerY(grid, 17), flags, 32));
    }

    @Test public void inclusiveCircleRejectsSquareCornersAndJustOutsideRadius() {
        MapViewport grid = integerGrid();
        float x = centerX(grid, 17), y = centerY(grid, 17);
        assertArrayEquals(new int[]{1, 16, 18, 33}, grid.nearbyFlags(x, y,
                Arrays.asList(0, 1, 2, 16, 18, 32, 33, 34), 32));
        assertArrayEquals(new int[0], grid.nearbyFlags(x, y,
                Arrays.asList(1, 16, 18, 33), Math.nextDown(32f)));
        assertArrayEquals(new int[0], grid.nearbyFlags(x + 1, y,
                Collections.singletonList(17), 0));
    }

    @Test public void radiusNeverReachesIntoMapMarginsOrOtherPanels() {
        MapViewport grid = integerGrid();
        List<Integer> flags = Arrays.asList(0, 15, 240, 255);
        float right = grid.left + 16 * grid.cell;
        float bottom = grid.top + 16 * grid.cell;
        for (float[] point : new float[][]{
                {grid.left - 1, grid.top}, {grid.left, grid.top - 1},
                {right, grid.top}, {grid.left, bottom}, {right + 24, bottom / 2}}) {
            assertArrayEquals(new int[0], grid.nearbyFlags(point[0], point[1], flags, Float.MAX_VALUE));
        }
        // The inclusive top/left border is a genuine map position.
        assertArrayEquals(new int[]{0}, grid.nearbyFlags(grid.left, grid.top,
                Collections.singletonList(0), 24));
    }

    @Test public void emptyTileCanOfferMultipleCandidatesWithoutChoosingOne() {
        MapViewport grid = integerGrid();
        float x = centerX(grid, 17), y = centerY(grid, 17);
        assertEquals(17, grid.tileAt(x, y));
        assertArrayEquals(new int[]{16, 18}, grid.nearbyFlags(x, y, Arrays.asList(18, 16), 32));
        assertArrayEquals(new int[0], grid.nearbyFlags(x, y, Collections.singletonList(255), 24));
    }

    @Test public void invalidFlagsIgnoredDuplicatesRemovedAndInputUnchanged() {
        MapViewport grid = integerGrid();
        List<Integer> flags = Arrays.asList(null, -1, 256, Integer.MAX_VALUE, 17, 17);
        List<Integer> before = new ArrayList<>(flags);
        assertArrayEquals(new int[]{17}, grid.nearbyFlags(centerX(grid, 17), centerY(grid, 17), flags, 24));
        assertEquals(before, flags);
        assertArrayEquals(new int[0], grid.nearbyFlags(centerX(grid, 17), centerY(grid, 17), null, 24));
        assertArrayEquals(new int[0], grid.nearbyFlags(centerX(grid, 17), centerY(grid, 17),
                Collections.<Integer>emptyList(), 24));
    }

    @Test public void invalidRadiiCoordinatesAndViewportsFailClosed() {
        MapViewport grid = integerGrid();
        List<Integer> flags = Collections.singletonList(17);
        float x = centerX(grid, 17), y = centerY(grid, 17);
        for (float radius : new float[]{-1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            assertArrayEquals(new int[0], grid.nearbyFlags(x, y, flags, radius));
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertArrayEquals(new int[0], grid.nearbyFlags(bad, y, flags, 24));
            assertArrayEquals(new int[0], grid.nearbyFlags(x, bad, flags, 24));
        }
        MapViewport tiny = new MapViewport(20, 30, 2);
        assertArrayEquals(new int[0], tiny.nearbyFlags(10, 15, flags, 24));
        MapViewport invalid = new MapViewport(560, 592, Float.NaN);
        assertEquals(-1, invalid.tileAt(0, 0));
        assertArrayEquals(new int[0], invalid.nearbyFlags(0, 0, flags, 24));
    }

    @Test public void fullMapStaysBoundedAndLargeFiniteRadiusDoesNotOverflow() {
        MapViewport grid = integerGrid();
        List<Integer> flags = new ArrayList<>();
        for (int tile = 255; tile >= 0; tile--) flags.add(tile);
        int[] result = grid.nearbyFlags(centerX(grid, 0), centerY(grid, 0), flags, Float.MAX_VALUE);
        assertEquals(256, result.length);
        assertEquals(0, result[0]);
        assertEquals(255, result[255]);
        boolean[] seen = new boolean[256];
        for (int tile : result) {
            assertFalse(seen[tile]);
            seen[tile] = true;
        }
        flags.add(0);
        assertArrayEquals(new int[0], grid.nearbyFlags(centerX(grid, 0), centerY(grid, 0), flags, 24));
    }

    private static MapViewport integerGrid() {
        MapViewport grid = new MapViewport(560, 592, 1);
        assertEquals(32, grid.cell, 0);
        return grid;
    }
    private static float centerX(MapViewport grid, int tile) {
        return grid.left + (tile % 16 + .5f) * grid.cell;
    }
    private static float centerY(MapViewport grid, int tile) {
        return grid.top + (tile / 16 + .5f) * grid.cell;
    }
}
