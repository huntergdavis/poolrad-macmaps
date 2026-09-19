package name.osher.gil.minivmac.notebook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Observed occupancy and recent movements only; never a pathfinder or game-state writer. */
public final class ExplorationTrail {
    public static final int MAX_STEPS = 256;
    private static final ExplorationTrail EMPTY = new ExplorationTrail(new byte[32], Collections.emptyList());
    private final byte[] visited;
    /**
     * Squares where a fight started, one bit each.
     *
     * Kept beside the walked squares rather than anywhere else because it is
     * the same kind of thing: something this party found out by being here, and
     * something a notebook backup should carry along with the rest of the map
     * they drew.
     */
    private final byte[] ambushed;
    /** Squares where the game said the party found treasure, one bit each. */
    private final byte[] found;
    public final List<Step> steps;

    private ExplorationTrail(byte[] visited, List<Step> steps) {
        this(visited, new byte[32], new byte[32], steps);
    }

    private ExplorationTrail(byte[] visited, byte[] ambushed, List<Step> steps) {
        this(visited, ambushed, new byte[32], steps);
    }

    private ExplorationTrail(byte[] visited, byte[] ambushed, byte[] found, List<Step> steps) {
        if (visited == null || visited.length != 32 || steps == null || steps.size() > MAX_STEPS)
            throw new IllegalArgumentException("Invalid exploration history size");
        if (ambushed == null || ambushed.length != 32)
            throw new IllegalArgumentException("Invalid ambush history size");
        if (found == null || found.length != 32)
            throw new IllegalArgumentException("Invalid discovery history size");
        this.ambushed = ambushed.clone();
        this.found = found.clone();
        this.visited = visited.clone();
        ArrayList<Step> copy = new ArrayList<>(steps.size());
        for (Step step : steps) {
            if (step == null || !visited(step.to) || (step.from != -1 && !visited(step.from)))
                throw new IllegalArgumentException("Exploration steps must refer to visited tiles");
            if (step.from != -1 && !copy.isEmpty() && step.from != copy.get(copy.size() - 1).to)
                throw new IllegalArgumentException("Exploration segment crosses an unobserved gap");
            copy.add(step);
        }
        this.steps = Collections.unmodifiableList(copy);
    }

    public static ExplorationTrail empty() { return EMPTY; }

    public boolean visited(int tile) {
        requireTile(tile);
        return (visited[tile >>> 3] & (1 << (tile & 7))) != 0;
    }

    public int visitedCount() {
        int count = 0;
        for (byte bits : visited) count += Integer.bitCount(bits & 255);
        return count;
    }

    /**
     * previousTile is this live session's last accepted sample, not a restored
     * history point. Use -1 after a pause, reload, area/notebook change or gap.
     * Re-observing the same live tile is a turn, not another movement.
     * Repeated stationary anchors carry no additional route information.
     */
    public ExplorationTrail record(int tile, int previousTile) {
        requireTile(tile);
        Step last = steps.isEmpty() ? null : steps.get(steps.size() - 1);
        if (previousTile == tile && last != null && last.to == tile) return this;
        if (previousTile == -1 && last != null && last.from == -1 && last.to == tile) return this;
        int from = last != null && previousTile == last.to && adjacent(previousTile, tile) ? previousTile : -1;
        byte[] seen = visited.clone();
        seen[tile >>> 3] |= (byte) (1 << (tile & 7));
        ArrayList<Step> recent = new ArrayList<>(steps.subList(steps.size() == MAX_STEPS ? 1 : 0, steps.size()));
        recent.add(new Step(from, tile));
        return new ExplorationTrail(seen, ambushed, found, recent);
    }

    /** Forget visible footprints while keeping all independently observed tiles. */
    /**
     * Forget the route but keep the coverage -- and keep where fights started,
     * which is a fact about the place rather than about the walk.
     */
    public ExplorationTrail clearTrail() {
        return steps.isEmpty() ? this
                : new ExplorationTrail(visited, ambushed, found, Collections.emptyList());
    }

    static ExplorationTrail restore(byte[] visited, List<Step> steps) { return new ExplorationTrail(visited, steps); }

    static ExplorationTrail restore(byte[] visited, byte[] ambushed, List<Step> steps) {
        return new ExplorationTrail(visited, ambushed, steps);
    }

    static ExplorationTrail restore(byte[] visited, byte[] ambushed, byte[] found, List<Step> steps) {
        return new ExplorationTrail(visited, ambushed, found, steps);
    }
    byte[] copyVisited() { return visited.clone(); }

    public byte[] copyAmbushed() { return ambushed.clone(); }

    /** True when a fight started on this square. */
    public boolean ambushed(int tile) {
        return tile >= 0 && tile < 256 && (ambushed[tile >>> 3] & (1 << (tile & 7))) != 0;
    }

    public int ambushCount() {
        int found = 0;
        for (int tile = 0; tile < 256; tile++) if (ambushed(tile)) found++;
        return found;
    }

    /**
     * Remember that a fight started here.
     *
     * Marking the same square twice changes nothing: the mark says a fight
     * happened here, not how many, and counting them would turn a note about
     * the map into a tally about the party.
     */
    public ExplorationTrail recordAmbush(int tile) {
        requireTile(tile);
        if (ambushed(tile)) return this;
        byte[] marks = ambushed.clone();
        marks[tile >>> 3] |= (byte) (1 << (tile & 7));
        return new ExplorationTrail(visited, marks, found, steps);
    }

    public byte[] copyFound() { return found.clone(); }

    /** True when the game announced treasure on this square. */
    public boolean found(int tile) {
        return tile >= 0 && tile < 256 && (found[tile >>> 3] & (1 << (tile & 7))) != 0;
    }

    public int foundCount() {
        int total = 0;
        for (int tile = 0; tile < 256; tile++) if (found(tile)) total++;
        return total;
    }

    /** Remember that the party found something here. Twice is the same as once. */
    public ExplorationTrail recordFound(int tile) {
        requireTile(tile);
        if (found(tile)) return this;
        byte[] marks = found.clone();
        marks[tile >>> 3] |= (byte) (1 << (tile & 7));
        return new ExplorationTrail(visited, ambushed, marks, steps);
    }

    private static boolean adjacent(int first, int second) {
        if (first < 0 || first > 255 || second < 0 || second > 255) return false;
        return Math.abs(first % 16 - second % 16) + Math.abs(first / 16 - second / 16) == 1;
    }

    private static void requireTile(int tile) {
        if (tile < 0 || tile > 255) throw new IllegalArgumentException("Exploration tile must be within the 16 by 16 area");
    }

    public static final class Step {
        public final int from, to;
        Step(int from, int to) {
            requireTile(to);
            if (from != -1 && !adjacent(from, to))
                throw new IllegalArgumentException("Exploration segment must connect adjacent tiles");
            this.from = from; this.to = to;
        }
        @Override public boolean equals(Object other) {
            return other instanceof Step && from == ((Step) other).from && to == ((Step) other).to;
        }
        @Override public int hashCode() { return 31 * from + to; }
    }

    @Override public boolean equals(Object other) {
        return other instanceof ExplorationTrail && Arrays.equals(visited, ((ExplorationTrail) other).visited)
                && steps.equals(((ExplorationTrail) other).steps);
    }
    @Override public int hashCode() { return 31 * Arrays.hashCode(visited) + steps.hashCode(); }
}
