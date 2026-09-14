package name.osher.gil.minivmac.mapper;

/** One coordinate transform shared by map painting and flag hit testing. */
public final class MapViewport {
    public final float left, top, cell;

    public MapViewport(int width, int height, float density) {
        top = 42 * density;
        cell = Math.min((width - 48 * density) / 16f, (height - top - 38 * density) / 16f);
        left = (width - cell * 16) / 2;
    }

    public int tileAt(float x, float y) {
        if (cell < 3 || Float.isNaN(x) || Float.isInfinite(x) || Float.isNaN(y) || Float.isInfinite(y)
                || x < left || y < top || x >= left + cell * 16 || y >= top + cell * 16) return -1;
        return (int) ((y - top) / cell) * 16 + (int) ((x - left) / cell);
    }
}
