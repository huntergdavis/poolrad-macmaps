package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

/** What this version of the game supports, and what it quietly does not. */
public class MacintoshLimitsTest {
    @Test public void theClassListIsTheGamesOwnAndInItsOwnOrder() {
        assertEquals(18, MacintoshLimits.CLASSES.length);
        assertEquals("Cleric", MacintoshLimits.className(0));
        assertEquals("Fighter", MacintoshLimits.className(2));
        assertEquals("Magic-User", MacintoshLimits.className(5));
        assertEquals("Monster", MacintoshLimits.className(17));
        assertNull(MacintoshLimits.className(18));
        assertNull(MacintoshLimits.className(-1));
    }

    @Test public void theRaceByteCountsFromOne() {
        /*
         * Forced, not assumed. A real saved character reads 7, and a list of
         * seven names counted from zero has no 7.
         */
        assertEquals(7, MacintoshLimits.RACES.length);
        assertEquals("Dwarf", MacintoshLimits.raceName(1));
        assertEquals("Human", MacintoshLimits.raceName(7));
        assertNull("zero is not a race", MacintoshLimits.raceName(0));
        assertNull(MacintoshLimits.raceName(8));
    }

    @Test public void theFourClassesThisVersionLacksAreNotPlayable() {
        // Named by the game, with no experience thresholds behind them.
        for (String absent : new String[]{"Druid", "Paladin", "Ranger", "Monk"}) {
            int at = indexOf(absent);
            assertFalse(absent + " should not be playable", MacintoshLimits.playable(at));
            assertEquals(absent + " should have no cap", 0, MacintoshLimits.cap(at));
        }
    }

    @Test public void theFourThatExistCarryTheGamesOwnCaps() {
        assertEquals(6, MacintoshLimits.cap(indexOf("Cleric")));
        assertEquals(8, MacintoshLimits.cap(indexOf("Fighter")));
        assertEquals(6, MacintoshLimits.cap(indexOf("Magic-User")));
        assertEquals(9, MacintoshLimits.cap(indexOf("Thief")));
        for (String there : new String[]{"Cleric", "Fighter", "Magic-User", "Thief"})
            assertTrue(there, MacintoshLimits.playable(indexOf(there)));
    }

    @Test public void aMulticlassCharacterMeetsItsLowestWallFirst() {
        assertEquals("a fighter-thief stops where the fighter does",
                8, MacintoshLimits.cap(indexOf("Fighter/Thief")));
        assertEquals("a cleric-magic-user stops at six either way",
                6, MacintoshLimits.cap(indexOf("Cleric/Magic-User")));
        assertEquals(6, MacintoshLimits.cap(indexOf("Fighter/Magic-User")));
        assertEquals(6, MacintoshLimits.cap(indexOf("Cleric/Fighter/Magic-User")));
    }

    @Test public void aCombinationContainingAMissingClassIsMissingToo() {
        // Cleric/Ranger names a class nobody can be, so nobody can be it either.
        assertFalse(MacintoshLimits.playable(indexOf("Cleric/Ranger")));
        assertEquals(0, MacintoshLimits.cap(indexOf("Cleric/Ranger")));
    }

    @Test public void nobodyIsAMonster() {
        assertFalse(MacintoshLimits.playable(indexOf("Monster")));
        assertEquals(0, MacintoshLimits.cap(indexOf("Monster")));
    }

    @Test public void theSpellAndPartyLimitsAreTheOnesTheReaderAlreadyUses() {
        assertEquals(3, MacintoshLimits.MAX_SPELL_LEVEL);
        assertEquals(21, MacintoshLimits.MEMORISED_SPELL_SLOTS);
        assertEquals(8, MacintoshLimits.MAX_PARTY);
        assertEquals("the party reader agrees about the spell slots",
                MacintoshLimits.MEMORISED_SPELL_SLOTS, SavedPartySlots.SLOTS);
    }

    @Test public void everyRealSavedCharacterIsPlayableAndWithinItsCap() {
        /*
         * The six characters on the owner's own disk, as their class bytes
         * actually read. If any of them came out unplayable the lists would be
         * wrong in a way no synthetic test would catch.
         */
        for (int classValue : new int[]{0, 2, 11, 13, 14}) {
            assertTrue("class " + classValue + " (" + MacintoshLimits.className(classValue) + ")",
                    MacintoshLimits.playable(classValue));
            assertTrue(MacintoshLimits.cap(classValue) > 0);
        }
        // And their races, which read 1, 2, 4 and 7.
        for (int race : new int[]{1, 2, 4, 7}) assertNotNull(MacintoshLimits.raceName(race));
    }

    private static int indexOf(String name) {
        for (int i = 0; i < MacintoshLimits.CLASSES.length; i++)
            if (MacintoshLimits.CLASSES[i].equals(name)) return i;
        throw new AssertionError("No such class: " + name);
    }

    /** Ties this to the reader that already knows the slot count. */
    private static final class SavedPartySlots {
        static final int SLOTS = name.osher.gil.minivmac.hfs.SavedParty.SPELL_SLOTS;
    }
}
