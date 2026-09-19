package name.osher.gil.minivmac.notebook;

import org.junit.Test;
import static org.junit.Assert.*;

/** Remembering where a fight started, with the map rather than with the party. */
public class AmbushMarkTest {
    private static ExplorationTrail walked(int... tiles) {
        ExplorationTrail trail = ExplorationTrail.empty();
        int previous = -1;
        for (int tile : tiles) { trail = trail.record(tile, previous); previous = tile; }
        return trail;
    }

    @Test public void aFreshTrailRemembersNoFights() {
        ExplorationTrail trail = ExplorationTrail.empty();
        assertEquals(0, trail.ambushCount());
        for (int tile = 0; tile < 256; tile++) assertFalse(trail.ambushed(tile));
    }

    @Test public void aMarkedSquareStaysMarked() {
        ExplorationTrail trail = ExplorationTrail.empty().recordAmbush(42);
        assertTrue(trail.ambushed(42));
        assertFalse(trail.ambushed(43));
        assertEquals(1, trail.ambushCount());
    }

    @Test public void markingTheSameSquareTwiceChangesNothing() {
        /*
         * The mark says a fight happened here, not how many. Counting them
         * would turn a note about the map into a tally about the party.
         */
        ExplorationTrail once = ExplorationTrail.empty().recordAmbush(42);
        assertSame(once, once.recordAmbush(42));
        assertEquals(1, once.recordAmbush(42).ambushCount());
    }

    @Test public void walkingOnDoesNotForgetWhereTheFightWas() {
        ExplorationTrail trail = walked(10, 11).recordAmbush(11).record(12, 11).record(13, 12);
        assertTrue("the mark survived two more steps", trail.ambushed(11));
        assertTrue("and the walking still happened", trail.visited(13));
    }

    @Test public void clearingTheTrailKeepsWhereTheFightsWere() {
        // Forgetting the route is not the same as forgetting the place.
        ExplorationTrail trail = walked(10, 11, 12).recordAmbush(11).clearTrail();
        assertTrue(trail.steps.isEmpty());
        assertTrue(trail.ambushed(11));
        assertTrue("coverage survives too", trail.visited(12));
    }

    @Test public void clearingEverythingForgetsTheFightsAsWell() {
        assertEquals(0, ExplorationTrail.empty().ambushCount());
    }

    @Test public void aSquareOffTheMapIsRefused() {
        for (int bad : new int[]{-1, 256, 9999}) {
            try { ExplorationTrail.empty().recordAmbush(bad); fail("accepted tile " + bad); }
            catch (IllegalArgumentException expected) { }
        }
        assertFalse(ExplorationTrail.empty().ambushed(-1));
        assertFalse(ExplorationTrail.empty().ambushed(256));
    }

    @Test public void everySquareCanBeMarkedIndependently() {
        ExplorationTrail trail = ExplorationTrail.empty();
        for (int tile = 0; tile < 256; tile += 3) trail = trail.recordAmbush(tile);
        for (int tile = 0; tile < 256; tile++)
            assertEquals("tile " + tile, tile % 3 == 0, trail.ambushed(tile));
        assertEquals(86, trail.ambushCount());
    }

    @Test public void theMarksAreCopiedOutRatherThanShared() {
        ExplorationTrail trail = ExplorationTrail.empty().recordAmbush(7);
        byte[] marks = trail.copyAmbushed();
        java.util.Arrays.fill(marks, (byte) 0);
        assertTrue("mutating the copy changed the trail", trail.ambushed(7));
    }
}
