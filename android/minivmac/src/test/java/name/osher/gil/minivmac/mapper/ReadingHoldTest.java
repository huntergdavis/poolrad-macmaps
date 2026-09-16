package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReadingHoldTest {
    @Test public void anUnreadableFrameChangesNothingUntilTheHoldExpires() {
        ReadingHold hold = new ReadingHold(5000);
        assertTrue(hold.accept(true, 1000));
        for (long now = 1001; now < 6000; now += 250) assertFalse(hold.accept(false, now));
        assertTrue(hold.holding());
        assertTrue(hold.accept(false, 6000));   // 5000ms later: no longer a blink
        assertFalse(hold.holding());
    }

    @Test public void aGoodFrameRestartsTheHold() {
        ReadingHold hold = new ReadingHold(5000);
        // A battle refusing every other frame must never reach the deadline.
        for (long now = 0; now < 60000; now += 250)
            assertEquals(now % 500 == 0, hold.accept(now % 500 == 0, now));
    }

    @Test public void nothingIsHeldBeforeAnythingHasBeenRead() {
        ReadingHold hold = new ReadingHold(5000);
        assertTrue(hold.accept(false, 0));
        assertTrue(hold.accept(false, 9999));
        assertFalse(hold.holding());
    }

    @Test public void resetDropsTheReadingAtOnce() {
        ReadingHold hold = new ReadingHold(5000);
        assertTrue(hold.accept(true, 1000));
        hold.reset();
        assertFalse(hold.holding());
        assertTrue(hold.accept(false, 1001));
    }

    @Test public void theHoldExpiresOnceAndStaysExpired() {
        ReadingHold hold = new ReadingHold(5000);
        assertTrue(hold.accept(true, 0));
        assertTrue(hold.accept(false, 5000));
        assertTrue(hold.accept(false, 5001));   // not a fresh hold
        assertTrue(hold.accept(false, 99999));
    }

    @Test public void aClockThatGoesBackwardsCannotExtendAHold() {
        ReadingHold hold = new ReadingHold(5000);
        assertTrue(hold.accept(true, 10000));
        assertTrue(hold.accept(false, 9000));
        assertFalse(hold.holding());
    }

    @Test public void aZeroHoldIsTheOldImmediateBehaviour() {
        ReadingHold hold = new ReadingHold(0);
        assertTrue(hold.accept(true, 0));
        assertTrue(hold.accept(false, 0));
    }

    @Test public void theShippedHoldIsFiveSeconds() {
        assertEquals(5000, ReadingHold.HOLD_MS);
        ReadingHold shipped = new ReadingHold();
        assertTrue(shipped.accept(true, 0));
        assertFalse(shipped.accept(false, 4999));
        assertTrue(shipped.accept(false, 5000));
    }

    @Test public void aNegativeHoldIsRefused() {
        try { new ReadingHold(-1); fail("Accepted a negative hold"); }
        catch (IllegalArgumentException expected) { }
    }
}
