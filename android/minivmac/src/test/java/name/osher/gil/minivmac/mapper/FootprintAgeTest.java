package name.osher.gil.minivmac.mapper;

import name.osher.gil.minivmac.notebook.ExplorationTrail;
import org.junit.Test;
import static org.junit.Assert.*;

/** Older footprints drawn smaller, so a trail reads in order. */
public class FootprintAgeTest {
    private static ExplorationTrail walked(int... tiles) {
        ExplorationTrail trail = ExplorationTrail.empty();
        int previous = -1;
        for (int tile : tiles) { trail = trail.record(tile, previous); previous = tile; }
        return trail;
    }

    private static FootprintAge aged(ExplorationTrail trail) {
        FootprintAge age = new FootprintAge();
        age.prepare(trail);
        return age;
    }

    @Test public void theNewestPrintIsFullSizeAndTheOldestIsTheFloor() {
        // The first square of a walk is an anchor, not travel, so it carries no
        // footprint and no age; the oldest *step* is the second square.
        FootprintAge age = aged(walked(10, 11, 12, 13));
        assertEquals(FootprintAge.OLDEST, age.scale(11), 0.001);
        assertEquals(FootprintAge.NEWEST, age.scale(13), 0.001);
        assertEquals("the anchor itself is not aged", FootprintAge.NEWEST, age.scale(10), 0.001);
    }

    @Test public void thePrintsGrowSteadilyAlongTheWalk() {
        FootprintAge age = aged(walked(10, 11, 12, 13, 14));
        float last = -1;
        for (int tile : new int[]{11, 12, 13, 14}) {
            float now = age.scale(tile);
            assertTrue("tile " + tile + " should be larger than the one before", now > last);
            last = now;
        }
    }

    @Test public void walkingSomewhereAgainMakesItNewAgain() {
        // The whole point: a corridor walked twice should not look walked once.
        FootprintAge age = aged(walked(10, 11, 12, 11));
        assertEquals("the second visit is the latest step",
                FootprintAge.NEWEST, age.scale(11), 0.001);
        assertTrue(age.scale(11) > age.scale(12));
    }

    @Test public void aSquareWithNoRecordedStepIsFullSizeRatherThanSmallest() {
        /*
         * Being unsure how old something is is not the same as knowing it is
         * old. Fading it would shrink squares that might have been walked a
         * moment ago.
         */
        FootprintAge age = aged(walked(10, 11, 12));
        assertEquals(FootprintAge.NEWEST, age.scale(200), 0.001);
    }

    @Test public void oneStepAndAnEmptyTrailAreBothFullSize() {
        assertEquals(FootprintAge.NEWEST, aged(walked(10)).scale(10), 0.001);
        assertEquals(FootprintAge.NEWEST, aged(ExplorationTrail.empty()).scale(10), 0.001);
        assertEquals(FootprintAge.NEWEST, aged(null).scale(10), 0.001);
    }

    @Test public void nothingEverGoesOutsideTheRange() {
        FootprintAge age = aged(walked(0, 1, 2, 3, 4, 5, 6, 7, 8));
        for (int tile = -5; tile < 300; tile++) {
            float scale = age.scale(tile);
            assertTrue("tile " + tile + " scale " + scale, scale >= FootprintAge.OLDEST);
            assertTrue("tile " + tile + " scale " + scale, scale <= FootprintAge.NEWEST);
        }
    }

    @Test public void theRangeIsNarrowEnoughToStayLegible() {
        // A bigger spread would turn the earliest steps into specks, and those
        // are still squares the party walked.
        assertTrue(FootprintAge.OLDEST > 0.6f);
        assertTrue(FootprintAge.OLDEST < FootprintAge.NEWEST);
    }

    @Test public void standingStillChangesNothing() {
        /*
         * An anchor is an observation with no movement behind it. The drawing
         * already refuses to let one invent or erase a footprint; letting one
         * advance the ages would quietly resize every print on the map for
         * standing still.
         */
        ExplorationTrail walked = walked(10, 11, 12);
        FootprintAge before = aged(walked);
        FootprintAge after = aged(walked.record(12, -1).record(12, -1));
        for (int tile : new int[]{10, 11, 12})
            assertEquals("tile " + tile, before.scale(tile), after.scale(tile), 0.0001);
    }

    @Test public void preparingTheSameTrailTwiceIsFree() {
        ExplorationTrail trail = walked(10, 11, 12);
        FootprintAge age = new FootprintAge();
        age.prepare(trail);
        float before = age.scale(10);
        age.prepare(trail);
        assertEquals(before, age.scale(10), 0.0001);
    }
}
