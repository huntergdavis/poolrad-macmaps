package name.osher.gil.minivmac.mapper;

import name.osher.gil.minivmac.notebook.ExplorationTrail;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Doors the party has stood beside and never gone through. Invented maps only. */
public class UnwalkedExitsTest {
    /** A blank 16x16 record with no walls and no doors anywhere. */
    private static byte[] blank() { return new byte[1026]; }

    /** Give one edge a wall surface, and optionally the door bits over it. */
    private static void edge(byte[] data, int x, int y, int direction, boolean door) {
        int tile = y * 16 + x;
        int at = 2 + tile + (direction >= 2 ? 256 : 0);
        int existing = data[at] & 255;
        data[at] = (byte) (direction % 2 == 0 ? (existing & 0x0f) | 0x10 : (existing & 0xf0) | 0x01);
        if (door) data[770 + tile] = (byte) ((data[770 + tile] & 255) | (1 << (direction * 2)));
    }

    private static GeoMap map(byte[] data) { return new GeoMap(-1, data); }

    private static ExplorationTrail walked(int... tiles) {
        ExplorationTrail trail = ExplorationTrail.empty();
        int previous = -1;
        for (int tile : tiles) { trail = trail.record(tile, previous); previous = tile; }
        return trail;
    }

    private static List<String> found(GeoMap map, ExplorationTrail trail) {
        List<String> exits = new ArrayList<>();
        UnwalkedExits.forEach(map, trail, (x, y, direction) -> exits.add(x + "," + y + " " + direction));
        return exits;
    }

    @Test public void aDoorOffAWalkedSquareIntoAnUnwalkedOneIsAnExit() {
        byte[] data = blank();
        edge(data, 5, 5, 1, true);          // east door out of 5,5
        ExplorationTrail trail = walked(5 * 16 + 5);
        assertEquals(1, UnwalkedExits.count(map(data), trail));
        assertEquals(java.util.Collections.singletonList("5,5 1"), found(map(data), trail));
    }

    @Test public void aDoorYouHaveAlreadyBeenThroughIsNotAnExit() {
        byte[] data = blank();
        edge(data, 5, 5, 1, true);
        // Both sides walked: nothing left to come back for.
        assertEquals(0, UnwalkedExits.count(map(data), walked(5 * 16 + 5, 5 * 16 + 6)));
    }

    @Test public void aDoorOnASquareYouHaveNeverStoodOnIsNotReported() {
        byte[] data = blank();
        edge(data, 9, 9, 1, true);
        // The party has been nowhere near it, so the map has not shown it to them.
        assertEquals(0, UnwalkedExits.count(map(data), walked(0)));
        assertEquals(0, UnwalkedExits.count(map(data), ExplorationTrail.empty()));
    }

    @Test public void aPlainOpeningIsNotCountedAsADoor() {
        byte[] data = blank();
        // A wall surface with no door bits is a wall; no surface at all is open.
        edge(data, 5, 5, 1, false);
        assertEquals("a wall is not an exit", 0, UnwalkedExits.count(map(data), walked(5 * 16 + 5)));
        assertEquals("and neither is an open edge", 0, UnwalkedExits.count(map(blank()), walked(5 * 16 + 5)));
    }

    @Test public void aDoorInTheOuterWallIsLeftAlone() {
        /*
         * It leads out of the area, so there is no square on the far side to
         * have visited, and marking it would mark it forever however many times
         * the party used it.
         */
        byte[] data = blank();
        edge(data, 0, 7, 3, true);          // west door on the left edge
        edge(data, 15, 7, 1, true);         // east door on the right edge
        edge(data, 7, 0, 0, true);          // north door on the top edge
        edge(data, 7, 15, 2, true);         // south door on the bottom edge
        ExplorationTrail trail = walked(7 * 16, 7 * 16 + 15, 7, 15 * 16 + 7);
        assertEquals(0, UnwalkedExits.count(map(data), trail));
    }

    @Test public void eachDirectionIsFoundAndNamedCorrectly() {
        for (int direction = 0; direction < 4; direction++) {
            byte[] data = blank();
            edge(data, 8, 8, direction, true);
            List<String> exits = found(map(data), walked(8 * 16 + 8));
            assertEquals("direction " + direction, 1, exits.size());
            assertEquals("direction " + direction, "8,8 " + direction, exits.get(0));
        }
    }

    @Test public void oneSquareCanHaveSeveralWaysOutYouHaveNotTaken() {
        byte[] data = blank();
        edge(data, 4, 4, 0, true); edge(data, 4, 4, 1, true);
        edge(data, 4, 4, 2, true); edge(data, 4, 4, 3, true);
        assertEquals(4, UnwalkedExits.count(map(data), walked(4 * 16 + 4)));
    }

    @Test public void nothingIsReportedWithoutAMapOrATrail() {
        byte[] data = blank();
        edge(data, 5, 5, 1, true);
        assertEquals(0, UnwalkedExits.count(null, walked(5 * 16 + 5)));
        assertEquals(0, UnwalkedExits.count(map(data), null));
        try { UnwalkedExits.forEach(map(data), walked(0), null); fail("null visitor accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
