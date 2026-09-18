package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

/** The words this version builds item names out of, read from the game itself. */
public class ItemVocabularyTest {
    @Test public void theGroupsAreTheSizesTheGameStores() {
        assertEquals(47, ItemVocabulary.WEAPONS.length);
        assertEquals(14, ItemVocabulary.MATERIALS.length);
        assertEquals(86, ItemVocabulary.OTHER.length);
        assertEquals(3, ItemVocabulary.groups().length);
    }

    @Test public void theWeaponsIncludeTheOnesNobodyRemembers() {
        // The polearms are the reason this came from the game and not a list
        // somebody typed out.
        for (String odd : new String[]{"Bec De Corbin", "Bill-Guisarme", "Fauchard-Fork",
                "Guisarme-Voulge", "Lucern Hammer", "Awl Pike", "Spetum", "Bo Stick"})
            assertTrue(odd, ItemVocabulary.knows(odd));
    }

    @Test public void theEverydayWeaponsAreThere() {
        for (String plain : new String[]{"Long Sword", "Short Sword", "Dagger", "Mace",
                "Long Bow", "Sling", "Two-Handed Sword"})
            assertTrue(plain, ItemVocabulary.knows(plain));
    }

    @Test public void armourIsAssembledFromMaterialsRatherThanNamedWhole() {
        // Which is why a save shows "Banded Mail  Mail": a base and a modifier.
        for (String part : new String[]{"Banded", "Plate", "Chain", "Splint", "Mail",
                "Armor", "Shield", "Leather"})
            assertTrue(part, ItemVocabulary.knows(part));
        assertTrue(ItemVocabulary.canName("Banded Mail"));
        assertTrue(ItemVocabulary.canName("Plate Mail"));
    }

    @Test public void magicItemsAreInTheVocabularyToo() {
        for (String magic : new String[]{"Potion", "Scroll", "Wand", "Bracers", "Girdle",
                "Figurine", "Phylactery", "Decanter", "Horseshoes"})
            assertTrue(magic, ItemVocabulary.knows(magic));
    }

    @Test public void matchingIgnoresCaseAndSurroundingSpace() {
        assertTrue(ItemVocabulary.knows("long sword"));
        assertTrue(ItemVocabulary.knows("  LONG SWORD  "));
        assertFalse(ItemVocabulary.knows(null));
        assertFalse(ItemVocabulary.knows("   "));
    }

    @Test public void aCountInFrontOfAnItemIsNotAnUnknownWord() {
        // The game writes "30 Arrows"; the number is not in any vocabulary.
        assertTrue(ItemVocabulary.canName("30 Arrows"));
        assertTrue(ItemVocabulary.canName("20 Quarrel"));
    }

    @Test public void somethingThisVersionHasNeverHeardOfIsRefused() {
        /*
         * The case the converter exists to catch. A save from a later title
         * carrying one of its own items must be refused, not quietly turned
         * into the nearest thing here.
         */
        assertFalse(ItemVocabulary.canName("Bastard Sword of Wounding +3"));
        assertFalse(ItemVocabulary.canName("Frostbrand"));
        assertFalse(ItemVocabulary.canName("Hackmaster"));
        assertFalse(ItemVocabulary.canName(""));
        assertFalse(ItemVocabulary.canName(null));
    }

    @Test public void everyWordInEveryGroupIsFindable() {
        for (String[] group : ItemVocabulary.groups())
            for (String word : group) {
                assertTrue(word, ItemVocabulary.knows(word));
                assertTrue(word, ItemVocabulary.canName(word));
            }
        assertTrue(ItemVocabulary.size() >= 140);
    }

    @Test public void theRealItemsOutOfARealSaveAllNameThemselves() {
        // Read out of the owner's own saved game by SavedParty, verbatim.
        for (String carried : new String[]{"Banded Mail  Mail", "Long Bow", "30 Arrows",
                "Two-Handed Sword", "Shield", "Long Sword Sword"})
            assertTrue(carried, ItemVocabulary.canName(carried));
    }
}
