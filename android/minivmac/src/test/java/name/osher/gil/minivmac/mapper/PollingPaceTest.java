package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

/** How often the companion reads the machine, given how still its screen is. */
public class PollingPaceTest {
    @Test public void aPaneThatHasSeenNothingYetReadsAtFullRate() {
        PollingPace pace = new PollingPace();
        assertEquals(PollingPace.ACTIVE_MS, pace.interval(0));
        assertEquals("even much later, having still seen nothing",
                PollingPace.ACTIVE_MS, pace.interval(600_000));
        assertFalse(pace.slowed(600_000));
    }

    @Test public void aMovingScreenKeepsTheFullRate() {
        PollingPace pace = new PollingPace();
        pace.sawActivity(1_000);
        assertEquals(PollingPace.ACTIVE_MS, pace.interval(1_000));
        assertEquals(PollingPace.ACTIVE_MS, pace.interval(1_000 + PollingPace.IDLE_AFTER_MS - 1));
        assertFalse(pace.slowed(1_000));
    }

    @Test public void aStillScreenSlowsDownAndThenSlowsAgain() {
        PollingPace pace = new PollingPace();
        pace.sawActivity(1_000);
        assertEquals(PollingPace.IDLE_MS, pace.interval(1_000 + PollingPace.IDLE_AFTER_MS));
        assertEquals(PollingPace.IDLE_MS, pace.interval(1_000 + PollingPace.RESTING_AFTER_MS - 1));
        assertEquals(PollingPace.RESTING_MS, pace.interval(1_000 + PollingPace.RESTING_AFTER_MS));
        assertEquals(PollingPace.RESTING_MS, pace.interval(1_000 + 3_600_000));
        assertTrue(pace.slowed(1_000 + PollingPace.IDLE_AFTER_MS));
    }

    @Test public void anyMovementPutsItStraightBack() {
        PollingPace pace = new PollingPace();
        pace.sawActivity(1_000);
        long late = 1_000 + PollingPace.RESTING_AFTER_MS + 500_000;
        assertEquals(PollingPace.RESTING_MS, pace.interval(late));
        pace.sawActivity(late);
        assertEquals(PollingPace.ACTIVE_MS, pace.interval(late));
        assertFalse(pace.slowed(late));
    }

    @Test public void aClockThatGoesBackwardsCannotMakeTheGuestLookBusier() {
        PollingPace pace = new PollingPace();
        pace.sawActivity(10_000);
        assertEquals("earlier than the last activity is not a long silence",
                PollingPace.ACTIVE_MS, pace.interval(5_000));
    }

    @Test public void resettingForgetsEverythingAndReadsAtFullRateAgain() {
        PollingPace pace = new PollingPace();
        pace.sawActivity(1_000);
        assertEquals(PollingPace.RESTING_MS, pace.interval(1_000 + PollingPace.RESTING_AFTER_MS));
        pace.reset();
        assertEquals(PollingPace.ACTIVE_MS, pace.interval(1_000 + PollingPace.RESTING_AFTER_MS));
    }

    @Test public void theRatesRiseAndTheThresholdsWithThem() {
        assertTrue(PollingPace.ACTIVE_MS < PollingPace.IDLE_MS);
        assertTrue(PollingPace.IDLE_MS < PollingPace.RESTING_MS);
        assertTrue(PollingPace.IDLE_AFTER_MS < PollingPace.RESTING_AFTER_MS);
    }

    @Test public void nonsensicalPacingIsRefusedRatherThanApplied() {
        // A companion that reads more slowly when busy would be worse than none.
        try { new PollingPace(0, 10, 20, 10, 20); fail("zero active accepted"); }
        catch (IllegalArgumentException expected) { }
        try { new PollingPace(100, 50, 200, 10, 20); fail("idle faster than active accepted"); }
        catch (IllegalArgumentException expected) { }
        try { new PollingPace(100, 200, 150, 10, 20); fail("resting faster than idle accepted"); }
        catch (IllegalArgumentException expected) { }
        try { new PollingPace(100, 200, 300, 0, 20); fail("zero threshold accepted"); }
        catch (IllegalArgumentException expected) { }
        try { new PollingPace(100, 200, 300, 50, 20); fail("falling thresholds accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
