package name.osher.gil.minivmac.journal;

import org.junit.Test;
import static org.junit.Assert.*;

/** The one sentence that means the party found something. */
public class DiscoveryMessageTest {
    @Test public void theGamesOwnSentenceIsRecognised() {
        assertTrue(DiscoveryMessage.foundTreasure("The party has found Treasure!"));
    }

    @Test public void aWrappedOrPaddedLineStillMatches() {
        // The Message window wraps and pads; the words are what matter.
        assertTrue(DiscoveryMessage.foundTreasure("  The party has found\n  Treasure!  \r\r"));
        assertTrue(DiscoveryMessage.foundTreasure("THE PARTY HAS FOUND TREASURE!"));
        assertTrue(DiscoveryMessage.foundTreasure("You open it.\rThe party has found Treasure!\r\r"));
    }

    @Test public void everythingElseTheGameSaysIsNotADiscovery() {
        /*
         * A map covered in marks for every barrel examined would be worth less
         * than one with a handful that mean something.
         */
        for (String other : new String[]{
                "Nothing Happens...",
                "YOU SPY A GROUP OF SEEDY-LOOKING KOBOLDS.",
                "The party has found nothing.",
                "found Treasure",
                "The party has lost Treasure!",
                "A file not found error occurred",
                "", "   "})
            assertFalse(other, DiscoveryMessage.foundTreasure(other));
    }

    @Test public void nothingAtAllIsNotADiscovery() {
        assertFalse(DiscoveryMessage.foundTreasure(null));
    }
}
