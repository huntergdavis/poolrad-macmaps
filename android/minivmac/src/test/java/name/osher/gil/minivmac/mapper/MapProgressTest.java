package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

/** How much of an area has been walked. */
public class MapProgressTest {
    @Test public void everyAreaIsTheSameSize() {
        assertEquals(256, MapProgress.SQUARES);
    }

    @Test public void theBadgeIsTheCountAgainstTheWhole() {
        assertEquals("0/256", MapProgress.badge(0));
        assertEquals("62/256", MapProgress.badge(62));
        assertEquals("256/256", MapProgress.badge(256));
    }

    @Test public void theSpokenFormReadsAsASentence() {
        assertEquals("62 of 256 squares walked", MapProgress.spoken(62));
        assertEquals("0 of 256 squares walked", MapProgress.spoken(0));
    }

    @Test public void thePercentRoundsDownSoNearlyDoneIsNotDone() {
        assertEquals(0, MapProgress.percent(0));
        assertEquals(24, MapProgress.percent(62));
        assertEquals(50, MapProgress.percent(128));
        assertEquals("255 squares is not a hundred percent", 99, MapProgress.percent(255));
        assertEquals(100, MapProgress.percent(256));
    }

    @Test public void completeMeansNothingLeft() {
        assertFalse(MapProgress.complete(255));
        assertTrue(MapProgress.complete(256));
    }

    @Test public void aCountOutsideTheMapIsBroughtIntoRangeRatherThanThrown() {
        // A progress badge is never the right place to learn something else broke.
        assertEquals("0/256", MapProgress.badge(-1));
        assertEquals("256/256", MapProgress.badge(9999));
        assertEquals(0, MapProgress.percent(-50));
        assertEquals(100, MapProgress.percent(1000));
        assertEquals("0 of 256 squares walked", MapProgress.spoken(-3));
    }
}
