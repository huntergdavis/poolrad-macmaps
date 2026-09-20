package name.osher.gil.minivmac.mapper;

import java.util.*;
import name.osher.gil.minivmac.notebook.AreaConnections;

/**
 * Discovered areas arranged as one world, in tile units.
 *
 * A crossing whose two squares sit on facing map borders (New Phlan 0,4 to the
 * Slums at 15,4) stitches the two areas edge to edge, so a city's districts
 * become one continuous map. Every other crossing (stairs, a boat, a teleport)
 * lands on interior squares, so its far area is placed as its own island and
 * the crossing is kept as a link between the two exact squares. Nothing here is
 * read from game data: the geometry comes only from travel the party made.
 */
public final class WorldLayout {
    public static final int SIDE = GeoMap.WIDTH;
    /** Tiles between islands, and between rows of islands. */
    public static final int GAP = 4;
    /** Tiles of headroom above each island so its names never sit on another island. */
    public static final int LABEL = 2;
    /** Islands are shelved left to right and wrap past this many tiles. */
    public static final int ROW_LIMIT = 3 * SIDE + 2 * GAP;
    public static final String ID_PREFIX = "por-mac-v11-geo-";

    /** One area's 16x16 square at a world tile origin. */
    public static final class Placed {
        public final int area, x, y;
        Placed(int area, int x, int y) { this.area = area; this.x = x; this.y = y; }
        public boolean contains(int tileX, int tileY) { return tileX >= x && tileX < x + SIDE && tileY >= y && tileY < y + SIDE; }
    }

    /** A stitched cluster or a lone island; what the chip row calls a place. */
    public static final class Place {
        public final String name;
        public final List<Placed> areas;
        /** World tile bounds, including the label headroom above. */
        public final int left, top, right, bottom;
        /** No crossing is recorded between this place and the place the party is in. */
        public final boolean separate;
        Place(String name, List<Placed> areas, boolean separate) {
            this.name = name; this.areas = Collections.unmodifiableList(areas); this.separate = separate;
            int l = Integer.MAX_VALUE, t = Integer.MAX_VALUE, r = Integer.MIN_VALUE, b = Integer.MIN_VALUE;
            for (Placed p : areas) { l = Math.min(l, p.x); t = Math.min(t, p.y); r = Math.max(r, p.x + SIDE); b = Math.max(b, p.y + SIDE); }
            left = l; top = t - LABEL; right = r; bottom = b;
        }
        public boolean has(int area) { for (Placed p : areas) if (p.area == area) return true; return false; }
    }

    public static final WorldLayout EMPTY = new WorldLayout(Collections.emptyList(), Collections.emptyList(), Collections.emptySet(), -1);

    public final List<Place> places;
    /** Crossings drawn as links because they could not be stitched. */
    public final List<AreaConnections.Edge> links;
    private final Set<AreaConnections.Edge> stitched;
    public final int current;
    /** World size in tiles; the origin is always 0,0. */
    public final int width, height;

    private WorldLayout(List<Place> places, List<AreaConnections.Edge> links, Set<AreaConnections.Edge> stitched, int current) {
        this.places = Collections.unmodifiableList(places); this.links = Collections.unmodifiableList(links);
        this.stitched = stitched; this.current = current;
        int w = 0, h = 0;
        for (Place p : places) { w = Math.max(w, p.right); h = Math.max(h, p.bottom); }
        width = w; height = h;
    }

    public boolean isEmpty() { return places.isEmpty(); }
    public boolean stitched(AreaConnections.Edge edge) { return stitched.contains(edge); }
    public Placed find(int area) { for (Place p : places) for (Placed a : p.areas) if (a.area == area) return a; return null; }
    public Place placeOf(int area) { for (Place p : places) if (p.has(area)) return p; return null; }
    public int areaCount() { int n = 0; for (Place p : places) n += p.areas.size(); return n; }

    public static String label(int area) { return AreaIdentity.labelForId(ID_PREFIX + area); }
    /** The catalog number inside an area ID, or -1. */
    public static int number(String areaId) {
        if (areaId == null || !areaId.startsWith(ID_PREFIX)) return -1;
        try { return Integer.parseInt(areaId.substring(ID_PREFIX.length())); } catch (RuntimeException invalid) { return -1; }
    }

    /** Where the far area sits relative to the near one if this crossing joins facing borders, else null. */
    static int[] offset(AreaConnections.Edge e) {
        int fx = e.fromTile % SIDE, fy = e.fromTile / SIDE, tx = e.toTile % SIDE, ty = e.toTile / SIDE;
        if (fx == 0 && tx == SIDE - 1) return new int[]{-SIDE, fy - ty};
        if (fx == SIDE - 1 && tx == 0) return new int[]{SIDE, fy - ty};
        if (fy == 0 && ty == SIDE - 1) return new int[]{fx - tx, -SIDE};
        if (fy == SIDE - 1 && ty == 0) return new int[]{fx - tx, SIDE};
        return null;
    }

    private static final class Stitch {
        final int other, dx, dy; final AreaConnections.Edge edge;
        Stitch(int other, int dx, int dy, AreaConnections.Edge edge) { this.other = other; this.dx = dx; this.dy = dy; this.edge = edge; }
    }

    public static WorldLayout build(AreaConnections history, int currentArea) {
        TreeSet<Integer> areas = new TreeSet<>();
        if (currentArea >= 0) areas.add(currentArea);
        for (AreaConnections.Edge e : history.edges) { areas.add(e.fromArea); areas.add(e.toArea); }
        if (areas.isEmpty()) return EMPTY;

        Map<Integer, List<Stitch>> stitches = new HashMap<>();
        for (int a : areas) stitches.put(a, new ArrayList<>());
        for (AreaConnections.Edge e : history.edges) {
            int[] off = offset(e);
            if (off == null) continue;
            stitches.get(e.fromArea).add(new Stitch(e.toArea, off[0], off[1], e));
            stitches.get(e.toArea).add(new Stitch(e.fromArea, -off[0], -off[1], e));
        }

        // Seeds: the party's area first, then the best-joined areas, so a city grows from its hub.
        List<Integer> seeds = new ArrayList<>(areas);
        seeds.sort((a, b) -> {
            if (a == currentArea) return -1; if (b == currentArea) return 1;
            int byJoins = stitches.get(b).size() - stitches.get(a).size();
            return byJoins != 0 ? byJoins : a - b;
        });

        Map<Integer, int[]> local = new HashMap<>();
        Map<Integer, Integer> clusterOf = new HashMap<>();
        List<List<Integer>> clusters = new ArrayList<>();
        Set<AreaConnections.Edge> stitched = new HashSet<>();
        for (int seed : seeds) {
            if (local.containsKey(seed)) continue;
            int cluster = clusters.size(); List<Integer> members = new ArrayList<>(); clusters.add(members);
            local.put(seed, new int[]{0, 0}); clusterOf.put(seed, cluster); members.add(seed);
            ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(seed);
            while (!queue.isEmpty()) {
                int at = queue.poll(); int[] here = local.get(at);
                for (Stitch s : stitches.get(at)) {
                    int ex = here[0] + s.dx, ey = here[1] + s.dy;
                    int[] there = local.get(s.other);
                    if (there != null) {
                        if (clusterOf.get(s.other) == cluster && there[0] == ex && there[1] == ey) stitched.add(s.edge);
                        continue;   // placed elsewhere, or inconsistently: the crossing stays a link
                    }
                    boolean overlaps = false;
                    for (int member : members) {
                        int[] m = local.get(member);
                        if (Math.abs(m[0] - ex) < SIDE && Math.abs(m[1] - ey) < SIDE) { overlaps = true; break; }
                    }
                    if (overlaps) continue;
                    local.put(s.other, new int[]{ex, ey}); clusterOf.put(s.other, cluster); members.add(s.other);
                    stitched.add(s.edge); queue.add(s.other);
                }
            }
        }

        List<AreaConnections.Edge> links = new ArrayList<>();
        for (AreaConnections.Edge e : history.edges) if (!stitched.contains(e)) links.add(e);

        // Components over links decide which islands belong with the party's part of the world.
        int[] component = new int[clusters.size()];
        for (int i = 0; i < component.length; i++) component[i] = i;
        for (AreaConnections.Edge e : links) union(component, clusterOf.get(e.fromArea), clusterOf.get(e.toArea));
        int primary = root(component, currentArea >= 0 ? clusterOf.get(currentArea) : 0);

        // Shelve clusters: the party's component first, walking links so joined islands sit near each other.
        Map<Integer, List<Integer>> neighbors = new HashMap<>();
        for (AreaConnections.Edge e : links) {
            int a = clusterOf.get(e.fromArea), b = clusterOf.get(e.toArea);
            if (a == b) continue;
            neighbors.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
            neighbors.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
        }
        List<Integer> order = new ArrayList<>(); boolean[] queued = new boolean[clusters.size()];
        List<Integer> starts = new ArrayList<>();
        starts.add(currentArea >= 0 ? clusterOf.get(currentArea) : 0);
        for (int i = 0; i < clusters.size(); i++) starts.add(i);
        for (int start : starts) {
            if (queued[start]) continue;
            ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(start); queued[start] = true;
            while (!queue.isEmpty()) {
                int c = queue.poll(); order.add(c);
                List<Integer> next = neighbors.getOrDefault(c, Collections.emptyList());
                Collections.sort(next);
                for (int n : next) if (!queued[n]) { queued[n] = true; queue.add(n); }
            }
        }

        List<Place> places = new ArrayList<>();
        int cursorX = 0, rowY = 0, rowHeight = 0; int lastComponent = primary;
        for (int c : order) {
            List<Integer> members = clusters.get(c);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (int m : members) { int[] p = local.get(m); minX = Math.min(minX, p[0]); minY = Math.min(minY, p[1]); maxX = Math.max(maxX, p[0]); maxY = Math.max(maxY, p[1]); }
            int w = maxX - minX + SIDE, h = maxY - minY + SIDE + LABEL;
            boolean separate = root(component, c) != primary;
            boolean newBand = root(component, c) != lastComponent;
            if (cursorX > 0 && (newBand || cursorX + w > ROW_LIMIT)) { rowY += rowHeight + GAP + (newBand ? GAP : 0); cursorX = 0; rowHeight = 0; }
            else if (cursorX == 0 && newBand && !places.isEmpty()) rowY += GAP;
            lastComponent = root(component, c);
            int shiftX = cursorX - minX, shiftY = rowY + LABEL - minY;
            List<Placed> placed = new ArrayList<>();
            List<Integer> sorted = new ArrayList<>(members); Collections.sort(sorted);
            for (int m : sorted) { int[] p = local.get(m); placed.add(new Placed(m, p[0] + shiftX, p[1] + shiftY)); }
            places.add(new Place(name(sorted, stitched), placed, separate));
            cursorX += w + GAP; rowHeight = Math.max(rowHeight, h);
        }
        return new WorldLayout(places, links, stitched, currentArea);
    }

    /** One area keeps its name; a city is named for its best-joined district. */
    private static String name(List<Integer> members, Set<AreaConnections.Edge> stitched) {
        if (members.size() == 1) return label(members.get(0));
        int anchor = members.get(0), best = -1;
        for (int m : members) {
            int joins = 0;
            for (AreaConnections.Edge e : stitched) if (e.fromArea == m || e.toArea == m) joins++;
            if (joins > best) { best = joins; anchor = m; }
        }
        if (members.size() == 2) {
            int other = members.get(0) == anchor ? members.get(1) : members.get(0);
            return label(anchor) + " & " + label(other);
        }
        return label(anchor) + " +" + (members.size() - 1);
    }

    private static int root(int[] parent, int i) { while (parent[i] != i) i = parent[i] = parent[parent[i]]; return i; }
    private static void union(int[] parent, int a, int b) { a = root(parent, a); b = root(parent, b); if (a != b) parent[Math.max(a, b)] = Math.min(a, b); }
}
