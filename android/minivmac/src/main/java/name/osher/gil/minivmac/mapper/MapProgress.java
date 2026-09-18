package name.osher.gil.minivmac.mapper;

/**
 * How much of an area has been walked, said in the two places it belongs.
 *
 * "62 walked squares" is a number without a scale. The map is always sixteen by
 * sixteen, so the useful form is how much of it is done -- which turns a count
 * into the thing a mapper actually wants to know, whether there is much left.
 */
public final class MapProgress {
    /** Every local area in this game is the same size. */
    public static final int SQUARES = GeoMap.WIDTH * GeoMap.WIDTH;

    private MapProgress() {}

    /** The compact form for the map's own header: "62/256". */
    public static String badge(int walked) {
        return clamp(walked) + "/" + SQUARES;
    }

    /** The spoken form: "62 of 256 squares walked". */
    public static String spoken(int walked) {
        return clamp(walked) + " of " + SQUARES + " squares walked";
    }

    /**
     * Whole percent, rounded down, for anywhere a number is wanted rather than
     * a fraction. Rounded down on purpose: 255 squares is not "100%".
     */
    public static int percent(int walked) {
        return clamp(walked) * 100 / SQUARES;
    }

    /** True once there is nothing left to walk. */
    public static boolean complete(int walked) { return clamp(walked) >= SQUARES; }

    /**
     * A count outside the map is a reading, not an argument, so it is brought
     * into range rather than thrown back at the caller -- a progress badge is
     * never the right place to find out something else went wrong.
     */
    private static int clamp(int walked) {
        return walked < 0 ? 0 : Math.min(walked, SQUARES);
    }
}
