package name.osher.gil.minivmac.mapper;

import name.osher.gil.minivmac.notebook.ExplorationTrail;

/**
 * How recently the party walked each square, as a size for its footprints.
 *
 * A trail of identical prints says where you have been and nothing about the
 * order, so a corridor walked twice looks like one walked once and a dead end
 * looks like a through route. Drawing the older prints smaller puts the order
 * back without adding a second kind of mark.
 *
 * Shrinking rather than shading is deliberate. Shading does not survive e-ink,
 * where a light grey and a dither pattern are the same thing at this size, and
 * the owner said so. A smaller print is smaller at any contrast.
 *
 * The range is narrow on purpose: the oldest print is about three-quarters of
 * the newest, which reads as a gradient when you look for it and as an ordinary
 * trail when you do not. A bigger range would turn the earliest steps into
 * specks, and those are still squares the party walked.
 */
public final class FootprintAge {
    /** The newest print is full size. */
    public static final float NEWEST = 1f;
    /** The oldest is this much of it. */
    public static final float OLDEST = 0.72f;

    private final int[] rank = new int[GeoMap.WIDTH * GeoMap.WIDTH];
    private int newest;
    private ExplorationTrail prepared;

    /**
     * Look at a trail, once. Repeated calls with the same trail do nothing, so
     * this can sit in a draw loop.
     */
    public void prepare(ExplorationTrail trail) {
        if (prepared == trail) return;
        java.util.Arrays.fill(rank, -1);
        newest = 0;
        prepared = trail;
        if (trail == null) return;
        /*
         * Only recorded travel counts. An anchor -- an observation with no
         * movement behind it -- must not change a footprint in any way, which
         * is the rule the drawing already follows: it neither invents a
         * direction nor erases one. Letting an anchor advance the ages would
         * quietly resize every print on the map for standing still.
         */
        int at = 0;
        for (ExplorationTrail.Step step : trail.steps) {
            if (step.from < 0 || step.to < 0 || step.to >= rank.length) continue;
            at++;
            rank[step.to] = at;
            newest = at;
        }
    }

    /**
     * The size for this square's print, between {@link #OLDEST} and
     * {@link #NEWEST}.
     *
     * A square with no recorded step -- visited before the trail began, or
     * reached across a gap the recorder did not see -- is drawn full size
     * rather than smallest. Being unsure how old something is is not the same
     * as knowing it is old, and guessing the second would fade squares that
     * might have been walked a moment ago.
     */
    public float scale(int tile) {
        if (tile < 0 || tile >= rank.length || newest <= 0) return NEWEST;
        int step = rank[tile];
        if (step < 0) return NEWEST;
        if (newest == 1) return NEWEST;
        float through = (step - 1) / (float) (newest - 1);
        return OLDEST + (NEWEST - OLDEST) * through;
    }
}
