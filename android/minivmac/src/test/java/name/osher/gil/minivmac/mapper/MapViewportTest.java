package name.osher.gil.minivmac.mapper;

import org.junit.Test;
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
}
