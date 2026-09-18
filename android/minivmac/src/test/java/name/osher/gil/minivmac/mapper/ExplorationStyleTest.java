package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Fog and footprints are remembered per area, falling back to one global answer. */
public class ExplorationStyleTest {
    private static final class Fake implements ExplorationStyle.Stored {
        final Map<String, Boolean> values = new HashMap<>();
        @Override public boolean has(String key) { return values.containsKey(key); }
        @Override public boolean read(String key, boolean fallback) {
            Boolean stored = values.get(key); return stored == null ? fallback : stored;
        }
    }

    /** New Phlan and Kuto's Well Catacombs: two real, shipped, distinct areas. */
    private static AreaIdentity slums() {
        return AreaIdentity.resolveFingerprint(
                "4d2541a2db2e3c3af90a9f1de437e2483146be72a91a766f7b970aec9d2db326");
    }
    private static AreaIdentity elsewhere() {
        return AreaIdentity.resolveFingerprint(
                "d50ae52621114d264567cd8ee26b119d23cd95ad852d2fee54dcd5fb81654d8f");
    }

    @Test public void anAreaWithNoAnswerOfItsOwnUsesTheGlobalOne() {
        Fake prefs = new Fake();
        assertFalse(ExplorationStyle.resolve(prefs, ExplorationStyle.FOG, slums(), false));
        prefs.values.put(ExplorationStyle.FOG, true);
        assertTrue(ExplorationStyle.resolve(prefs, ExplorationStyle.FOG, slums(), false));
    }

    @Test public void anAreasOwnAnswerBeatsTheGlobalOne() {
        Fake prefs = new Fake();
        prefs.values.put(ExplorationStyle.FOG, true);
        prefs.values.put(ExplorationStyle.key(ExplorationStyle.FOG, slums()), false);
        assertFalse("the slums said no", ExplorationStyle.resolve(prefs, ExplorationStyle.FOG, slums(), false));
        assertTrue("and said nothing about anywhere else",
                ExplorationStyle.resolve(prefs, ExplorationStyle.FOG, elsewhere(), false));
    }

    @Test public void clearingFogInOnePlaceLeavesItUpInAnother() {
        // The whole reason this exists.
        Fake prefs = new Fake();
        prefs.values.put(ExplorationStyle.key(ExplorationStyle.FOG, slums()), false);
        prefs.values.put(ExplorationStyle.key(ExplorationStyle.FOG, elsewhere()), true);
        assertFalse(ExplorationStyle.resolve(prefs, ExplorationStyle.FOG, slums(), true));
        assertTrue(ExplorationStyle.resolve(prefs, ExplorationStyle.FOG, elsewhere(), false));
    }

    @Test public void anUnidentifiedAreaHasNowhereToStoreAnAnswer() {
        Fake prefs = new Fake();
        assertNull(ExplorationStyle.key(ExplorationStyle.FOG, null));
        prefs.values.put(ExplorationStyle.FOOTPRINTS, false);
        assertFalse(ExplorationStyle.resolve(prefs, ExplorationStyle.FOOTPRINTS, null, true));
    }

    @Test public void theShippedAnswerStandsUntilAnybodyExpressesOne() {
        Fake prefs = new Fake();
        assertTrue(ExplorationStyle.resolve(prefs, ExplorationStyle.FOOTPRINTS, slums(),
                ExplorationStyle.FOOTPRINTS_DEFAULT));
        assertFalse(ExplorationStyle.resolve(prefs, ExplorationStyle.FOG, slums(),
                ExplorationStyle.FOG_DEFAULT));
    }

    @Test public void keysAreDistinctPerAreaAndPerSwitch() {
        String fogHere = ExplorationStyle.key(ExplorationStyle.FOG, slums());
        String feetHere = ExplorationStyle.key(ExplorationStyle.FOOTPRINTS, slums());
        String fogThere = ExplorationStyle.key(ExplorationStyle.FOG, elsewhere());
        assertNotEquals(fogHere, feetHere);
        assertNotEquals(fogHere, fogThere);
        assertTrue(fogHere.startsWith(ExplorationStyle.FOG + "."));
    }

    @Test public void aMissingBaseIsRefusedRatherThanStoredUnderNothing() {
        try { ExplorationStyle.key("", slums()); fail("empty base accepted"); }
        catch (IllegalArgumentException expected) { }
        try { ExplorationStyle.key(null, slums()); fail("null base accepted"); }
        catch (IllegalArgumentException expected) { }
        try { ExplorationStyle.resolve(null, ExplorationStyle.FOG, slums(), false); fail("null prefs accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
