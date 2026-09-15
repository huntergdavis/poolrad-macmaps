package name.osher.gil.minivmac.mapper;

import java.util.Arrays;
import java.util.Collection;

/** One coordinate transform shared by map painting and flag hit testing. */
public final class MapViewport {
    public final float left, top, cell;

    public MapViewport(int width, int height, float density) {
        top = 42 * density;
        // One caption line, not two: the reclaimed height goes to the map.
        cell = Math.min((width - 48 * density) / 16f, (height - top - 22 * density) / 16f);
        left = (width - cell * 16) / 2;
    }

    public int tileAt(float x, float y) {
        if (!finite(left) || !finite(top) || !finite(cell) || cell < 3 || !finite(x) || !finite(y)
                || x < left || y < top || x >= left + cell * 16 || y >= top + cell * 16) return -1;
        return (int) ((y - top) / cell) * 16 + (int) ((x - left) / cell);
    }

    /**
     * Candidate flags within an inclusive circular pixel radius of a map tap.
     * Anchors are tile centers in this viewport's coordinate space. Results are
     * unique, nearest first, then ascending tile ID for equal distances.
     *
     * This only supplies candidates: the caller retains exact-tile behavior and
     * chooses how to present ambiguity or creation on an empty tile. A radius
     * never extends the map's hit area into its margins or adjacent panels.
     *
     * Invalid taps/radii, null collections, and collections larger than the
     * map's 256 tiles return an empty array. Null/out-of-range entries are ignored.
     * No input collection is modified; all work and temporary storage are bounded.
     */
    public int[] nearbyFlags(float x, float y, Collection<Integer> flagTiles, float radius) {
        if (tileAt(x, y) < 0 || !finite(radius) || radius < 0
                || flagTiles == null || flagTiles.size() > 256) return new int[0];

        boolean[] present = new boolean[256];
        for (Integer tile : flagTiles) {
            if (tile != null && tile >= 0 && tile < 256) present[tile] = true;
        }
        int[] candidates = new int[256];
        double[] distances = new double[256];
        double limit = (double) radius * radius;
        int count = 0;
        for (int tile = 0; tile < 256; tile++) {
            if (!present[tile]) continue;
            // Match the float tile-center transform used by the map artwork.
            double dx = (double) (left + (tile % 16 + .5f) * cell) - x;
            double dy = (double) (top + (tile / 16 + .5f) * cell) - y;
            double distance = dx * dx + dy * dy;
            if (distance > limit) continue;
            int index = count;
            // Tiles arrive in ascending order, so equal-distance ties keep that
            // order. At most 256 candidates: no unbounded list/sort allocation.
            while (index > 0 && distances[index - 1] > distance) {
                candidates[index] = candidates[index - 1];
                distances[index] = distances[index - 1];
                index--;
            }
            candidates[index] = tile;
            distances[index] = distance;
            count++;
        }
        return Arrays.copyOf(candidates, count);
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
