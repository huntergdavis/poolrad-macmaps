package name.osher.gil.minivmac.mapper;

import java.io.IOException;
import org.junit.Test;
import name.osher.gil.minivmac.notebook.AreaConnections;
import static org.junit.Assert.*;

public class WorldLayoutTest {
    private static int tile(int x, int y) { return y * 16 + x; }
    private static AreaConnections.Edge edge(int from, int fx, int fy, int to, int tx, int ty) {
        return new AreaConnections.Edge(from, tile(fx, fy), to, tile(tx, ty));
    }
    private static AreaConnections history(AreaConnections.Edge... edges) throws IOException {
        AreaConnections h = new AreaConnections();
        for (AreaConnections.Edge e : edges) h = h.add(e);
        return h;
    }
    private static void assertNoOverlap(WorldLayout layout) {
        java.util.List<WorldLayout.Placed> all = new java.util.ArrayList<>();
        for (WorldLayout.Place p : layout.places) all.addAll(p.areas);
        for (int i = 0; i < all.size(); i++) for (int j = i + 1; j < all.size(); j++) {
            WorldLayout.Placed a = all.get(i), b = all.get(j);
            assertFalse("areas " + a.area + " and " + b.area + " overlap",
                    Math.abs(a.x - b.x) < WorldLayout.SIDE && Math.abs(a.y - b.y) < WorldLayout.SIDE);
        }
    }

    @Test public void nothingDiscoveredIsAnEmptyWorld() throws IOException {
        assertTrue(WorldLayout.build(new AreaConnections(), -1).isEmpty());
        WorldLayout alone = WorldLayout.build(new AreaConnections(), 0);
        assertEquals(1, alone.places.size());
        assertEquals("New Phlan", alone.places.get(0).name);
        assertFalse(alone.places.get(0).separate);
        assertEquals(WorldLayout.SIDE, alone.width);
        assertEquals(WorldLayout.SIDE + WorldLayout.LABEL, alone.height);
    }

    @Test public void theGateStitchesTheSlumsWestOfNewPhlanOnTheSameRow() throws IOException {
        // The real crossing: New Phlan 0,4 west through the gate into the Slums at 15,4.
        WorldLayout layout = WorldLayout.build(history(edge(0, 0, 4, 20, 15, 4)), 0);
        assertEquals(1, layout.places.size());
        WorldLayout.Place city = layout.places.get(0);
        assertEquals("New Phlan & Slums of Phlan", city.name);
        WorldLayout.Placed phlan = layout.find(0), slums = layout.find(20);
        assertEquals(slums.x + WorldLayout.SIDE, phlan.x);
        assertEquals(slums.y, phlan.y);
        assertEquals(0, slums.x);
        assertEquals(WorldLayout.LABEL, slums.y);
        assertTrue(layout.links.isEmpty());
        assertTrue(layout.stitched(edge(0, 0, 4, 20, 15, 4)));
        assertEquals(2 * WorldLayout.SIDE, layout.width);
    }

    @Test public void aRowOffsetAtTheBorderShiftsTheNeighbourVertically() throws IOException {
        // Leaving at row 4 and arriving at row 9 means the far map sits five rows higher.
        WorldLayout layout = WorldLayout.build(history(edge(0, 15, 4, 18, 0, 9)), 0);
        WorldLayout.Placed phlan = layout.find(0), plaza = layout.find(18);
        assertEquals(phlan.x + WorldLayout.SIDE, plaza.x);
        assertEquals(phlan.y - 5, plaza.y);
        assertEquals(0, plaza.y - WorldLayout.LABEL);
        assertNoOverlap(layout);
    }

    @Test public void northAndSouthBordersStitchToo() throws IOException {
        WorldLayout layout = WorldLayout.build(history(edge(0, 7, 0, 31, 7, 15), edge(0, 3, 15, 24, 5, 0)), 0);
        WorldLayout.Placed phlan = layout.find(0);
        assertEquals(phlan.y - WorldLayout.SIDE, layout.find(31).y);
        assertEquals(phlan.x, layout.find(31).x);
        assertEquals(phlan.y + WorldLayout.SIDE, layout.find(24).y);
        assertEquals(phlan.x - 2, layout.find(24).x);
        assertEquals(1, layout.places.size());
        assertEquals("New Phlan +2", layout.places.get(0).name);
        assertNoOverlap(layout);
    }

    @Test public void theReturnTripIsTheSameSeamNotASecondLink() throws IOException {
        WorldLayout layout = WorldLayout.build(history(edge(0, 0, 4, 20, 15, 4), edge(20, 15, 4, 0, 0, 4)), 20);
        assertEquals(1, layout.places.size());
        assertTrue(layout.links.isEmpty());
        assertTrue(layout.stitched(edge(0, 0, 4, 20, 15, 4)));
        assertTrue(layout.stitched(edge(20, 15, 4, 0, 0, 4)));
    }

    @Test public void stairsMakeAnIslandJoinedByALink() throws IOException {
        // Kuto's Well 5,5 down to its catacombs at 5,5: interior squares, separate maps.
        WorldLayout layout = WorldLayout.build(history(edge(29, 5, 5, 32, 5, 5)), 29);
        assertEquals(2, layout.places.size());
        assertEquals("Kuto's Well", layout.places.get(0).name);
        assertEquals("Kuto's Well Catacombs", layout.places.get(1).name);
        assertEquals(1, layout.links.size());
        assertFalse(layout.places.get(1).separate);
        WorldLayout.Placed well = layout.find(29), below = layout.find(32);
        assertEquals(well.x + WorldLayout.SIDE + WorldLayout.GAP, below.x);
        assertEquals(well.y, below.y);
        assertNoOverlap(layout);
    }

    @Test public void aBorderCrossingThatWouldOverlapBecomesALink() throws IOException {
        // Two different areas both claim the square west of New Phlan; the second stays an island.
        WorldLayout layout = WorldLayout.build(history(edge(0, 0, 4, 20, 15, 4), edge(0, 0, 9, 18, 15, 9)), 0);
        assertEquals(2, layout.places.size());
        assertEquals(1, layout.links.size());
        assertEquals(18, layout.links.get(0).toArea);
        assertTrue(layout.stitched(edge(0, 0, 4, 20, 15, 4)));
        assertFalse(layout.stitched(edge(0, 0, 9, 18, 15, 9)));
        assertNoOverlap(layout);
    }

    @Test public void inconsistentSeamsKeepTheFirstAndLinkTheSecond() throws IOException {
        // The Slums are stitched west of New Phlan; a later crossing claims they are also north of it.
        WorldLayout layout = WorldLayout.build(history(edge(0, 0, 4, 20, 15, 4), edge(0, 7, 0, 20, 7, 15)), 0);
        assertEquals(1, layout.places.size());
        assertEquals(1, layout.links.size());
        assertNoOverlap(layout);
    }

    @Test public void islandsShelveAndWrapWithoutOverlapping() throws IOException {
        AreaConnections.Edge[] edges = new AreaConnections.Edge[6];
        int[] far = {1, 2, 9, 10, 13, 14};
        for (int i = 0; i < far.length; i++) edges[i] = edge(0, 5, 5, far[i], 8, 8);
        WorldLayout layout = WorldLayout.build(history(edges), 0);
        assertEquals(7, layout.places.size());
        assertEquals(6, layout.links.size());
        assertNoOverlap(layout);
        assertTrue(layout.width <= WorldLayout.ROW_LIMIT);
        assertTrue(layout.height > WorldLayout.SIDE + WorldLayout.LABEL);
        for (WorldLayout.Place p : layout.places) assertFalse(p.separate);
    }

    @Test public void placesWithNoCrossingToThePartyAreSeparateAndShelvedBelow() throws IOException {
        // Caves reached across the wilderness: no recorded crossing joins them to Phlan.
        WorldLayout layout = WorldLayout.build(history(edge(0, 0, 4, 20, 15, 4), edge(13, 5, 5, 27, 6, 6)), 20);
        assertEquals(3, layout.places.size());
        assertFalse(layout.places.get(0).separate);
        assertTrue(layout.places.get(1).separate);
        assertTrue(layout.places.get(2).separate);
        assertTrue(layout.places.get(1).top > layout.places.get(0).bottom);
        assertEquals(layout.places.get(1).top, layout.places.get(2).top);
        assertNoOverlap(layout);
        assertEquals(20, layout.current);
        assertSame(layout.places.get(0), layout.placeOf(0));
    }

    @Test public void thePartyAreaLeadsEvenWhenItIsNotTheHub() throws IOException {
        WorldLayout layout = WorldLayout.build(history(edge(0, 5, 5, 21, 8, 8), edge(0, 0, 4, 20, 15, 4)), 21);
        assertEquals("Sokal Keep", layout.places.get(0).name);
        assertEquals("New Phlan & Slums of Phlan", layout.places.get(1).name);
        assertFalse(layout.places.get(1).separate);
        assertEquals(WorldLayout.SIDE + WorldLayout.GAP, layout.places.get(1).left);
    }

    @Test public void areaNumbersParseOnlyFromRealIds() {
        assertEquals(20, WorldLayout.number("por-mac-v11-geo-20"));
        assertEquals(-1, WorldLayout.number(null));
        assertEquals(-1, WorldLayout.number("por-mac-v11-geo-x"));
        assertEquals(-1, WorldLayout.number("other-20"));
        assertEquals("Slums of Phlan", WorldLayout.label(20));
    }
}
