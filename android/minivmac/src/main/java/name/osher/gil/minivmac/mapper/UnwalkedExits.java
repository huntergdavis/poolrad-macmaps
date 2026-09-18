package name.osher.gil.minivmac.mapper;

import name.osher.gil.minivmac.notebook.ExplorationTrail;

/**
 * Doors you have stood beside and never gone through.
 *
 * The wall and door decoding already knows where every opening is, and the
 * trail already knows where the party has been. Put together they answer a
 * question the game never will: on the map you have drawn yourself, which ways
 * out have you not taken? It is the oldest habit in dungeon mapping -- circling
 * the doors you mean to come back to -- and it needs no new reading.
 *
 * This spoils nothing. Every door it reports is on a square the party has
 * already walked, and the only other thing it knows is that they have not
 * walked the square on the far side. Both facts are the player's own.
 *
 * Two deliberate exclusions:
 *
 * A plain opening is not counted, only a doorway. The frontier of an explored
 * region is mostly open edges, and marking all of them would put a mark on
 * every unfinished corridor rather than on the handful of doors worth
 * remembering.
 *
 * A door in the map's outer wall is not counted either. It leads out of the
 * area, so there is no square on the far side to have visited or not, and it
 * would be marked forever however many times the party used it.
 */
public final class UnwalkedExits {
    /** North, east, south, west: the order GeoMap uses. */
    private static final int[] STEP_X = {0, 1, 0, -1};
    private static final int[] STEP_Y = {-1, 0, 1, 0};

    private UnwalkedExits() {}

    /** Told about each door found, in tile then direction order. */
    public interface Visitor {
        void exit(int x, int y, int direction);
    }

    public static void forEach(GeoMap map, ExplorationTrail trail, Visitor visitor) {
        if (visitor == null) throw new IllegalArgumentException("No visitor");
        if (map == null || trail == null) return;
        for (int tile = 0; tile < GeoMap.WIDTH * GeoMap.WIDTH; tile++) {
            if (!trail.visited(tile)) continue;
            int x = tile % GeoMap.WIDTH, y = tile / GeoMap.WIDTH;
            for (int direction = 0; direction < 4; direction++) {
                if (map.edgeKind(x, y, direction) != GeoMap.EdgeKind.DOORWAY) continue;
                int nx = x + STEP_X[direction], ny = y + STEP_Y[direction];
                if (nx < 0 || nx >= GeoMap.WIDTH || ny < 0 || ny >= GeoMap.WIDTH) continue;
                if (trail.visited(ny * GeoMap.WIDTH + nx)) continue;
                visitor.exit(x, y, direction);
            }
        }
    }

    /**
     * How many such doors there are. A door between two squares you have both
     * walked is counted from neither side; one you have seen from one side only
     * is counted once, from that side.
     */
    public static int count(GeoMap map, ExplorationTrail trail) {
        int[] found = {0};
        forEach(map, trail, (x, y, direction) -> found[0]++);
        return found[0];
    }
}
