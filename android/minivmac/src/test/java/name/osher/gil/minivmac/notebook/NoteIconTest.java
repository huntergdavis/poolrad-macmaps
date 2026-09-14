package name.osher.gil.minivmac.notebook;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class NoteIconTest {
    @Test public void stableIdsAndLabelsCoverEveryManuallyChosenSymbol() {
        assertArrayEquals(new String[]{"flag", "smithy", "temple", "inn", "shop", "monster",
                "hidden-wall", "district", "treasure"},
                java.util.Arrays.stream(NoteIcon.values()).map(NoteIcon::id).toArray(String[]::new));
        Set<String> ids = new HashSet<>();
        for (NoteIcon icon : NoteIcon.values()) {
            assertTrue(ids.add(icon.id()));
            assertSame(icon, NoteIcon.fromId(icon.id()));
            assertFalse(icon.label().trim().isEmpty());
        }
        assertEquals("Hidden wall", NoteIcon.HIDDEN_WALL.label());
    }

    @Test public void invalidIdsNeverSilentlyBecomeFlags() {
        for (String id : new String[]{null, "", "FLAG", " flag", "flag ", "hidden_wall", "0", "new-icon"}) {
            assertThrows(IllegalArgumentException.class, () -> NoteIcon.fromId(id));
        }
    }
}
