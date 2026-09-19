package name.osher.gil.minivmac.notebook;

import org.junit.Test;
import static org.junit.Assert.*;

public class AreaNoteFollowTest {
    @Test public void firstEntryUsesArrivalSquareEvenIfPartyKeepsWalking() {
        AreaNoteFollow follow = new AreaNoteFollow();
        follow.observe("a", 34); follow.observe("a", 35);
        assertTrue(follow.pending("a")); assertEquals(34, follow.tile(-1));
    }
    @Test public void transientUnavailableAndSameAreaDoNotReopenClosedPage() {
        AreaNoteFollow follow = new AreaNoteFollow();
        follow.observe("a", 34); follow.opened("a");
        follow.observe(null, 0); follow.observe("a", 35);
        assertFalse(follow.pending("a"));
    }
    @Test public void newestAreaWinsWhileAnEditorIsProtected() {
        AreaNoteFollow follow = new AreaNoteFollow();
        follow.observe("a", 1); follow.opened("a");
        follow.observe("b", 2); follow.observe("c", 3);
        follow.opened("b");
        assertFalse(follow.pending("b")); assertTrue(follow.pending("c"));
        assertEquals(3, follow.tile(-1));
    }
    @Test public void returningAreaUsesRememberedTileWithValidatedFallback() {
        AreaNoteFollow follow = new AreaNoteFollow();
        follow.observe("a", 1); follow.opened("a");
        follow.observe("b", 2); follow.observe("a", 9);
        assertEquals(42, follow.tile(42));
        assertEquals(9, follow.tile(256)); assertEquals(9, follow.tile(-1));
    }
    @Test public void campaignSwitchResetsEntryAndHasIndependentMemory() {
        AreaNoteFollow follow = new AreaNoteFollow();
        follow.observe("a", 1); follow.opened("a"); follow.reset(); follow.observe("a", 2);
        assertTrue(follow.pending("a")); assertEquals(2, follow.tile(-1));
        assertNotEquals(AreaNoteFollow.preferenceKey("one", "a"), AreaNoteFollow.preferenceKey("two", "a"));
        assertNotEquals(AreaNoteFollow.preferenceKey("one", "a"), AreaNoteFollow.preferenceKey("one", "b"));
    }
}
