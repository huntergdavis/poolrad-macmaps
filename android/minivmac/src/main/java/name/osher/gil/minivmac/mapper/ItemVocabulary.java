package name.osher.gil.minivmac.mapper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Every word this version of the game builds an item name out of.
 *
 * Read from the application's own  resource, in the order it stores them.
 * An item's printed name is composed from these rather than stored whole, which
 * is why a saved game shows "Banded Mail  Mail" and "Long Sword Sword" -- a base
 * and a modifier, run together.
 *
 * The converter needs this for one question: does this version have the item
 * somebody's save is carrying? A name built from words that are not here cannot
 * be represented, and F81 must say so rather than substitute the nearest thing.
 *
 * Not a replacement for the equipment reference, which carries the printed
 * damage, weight and class permissions for the weapons and armour a player
 * actually buys. This is the vocabulary, including the magic items that
 * reference does not cover.
 */
public final class ItemVocabulary {
    /** The 47 weapon names, in the game's own order. */
    public static final String[] WEAPONS = {
            "Battle Axe",
            "Hand Axe",
            "Bardiche",
            "Bec De Corbin",
            "Bill-Guisarme",
            "Bo Stick",
            "Club",
            "Dagger",
            "Dart",
            "Fauchard",
            "Fauchard-Fork",
            "Flail",
            "Military Fork",
            "Glaive",
            "Glaive-Guisarme",
            "Guisarme",
            "Guisarme-Voulge",
            "Halberd",
            "Lucern Hammer",
            "Hammer",
            "Javelin",
            "Jo Stick",
            "Mace",
            "Morning Star",
            "Partisan",
            "Military Pick",
            "Awl Pike",
            "Quarrel",
            "Ranseur",
            "Scimitar",
            "Spear",
            "Spetum",
            "Quarter Staff",
            "Bastard Sword",
            "Broad Sword",
            "Long Sword",
            "Short Sword",
            "Two-Handed Sword",
            "Trident",
            "Voulge",
            "Composite Long Bow",
            "Composite Short Bow",
            "Long Bow",
            "Short Bow",
            "Heavy Crossbow",
            "Light Crossbow",
            "Sling",
    };

    /** Armour materials and the words that qualify them. */
    public static final String[] MATERIALS = {
            "Mail",
            "Armor",
            "Leather",
            "Padded",
            "Studded",
            "Ring",
            "Scale",
            "Chain",
            "Splint",
            "Banded",
            "Plate",
            "Shield",
            "Woods",
            "Arrow",
    };

    /** Everything else an item can be, magic items included. */
    public static final String[] OTHER = {
            "Potion",
            "Scroll",
            "Ring",
            "Rod",
            "Stave",
            "Wand",
            "Jug",
            "Amulet",
            "Apparatus",
            "Bag",
            "Beaker",
            "Boat",
            "Book",
            "Boots",
            "Bowl",
            "Bracers",
            "Brazier",
            "Brooch",
            "Broom",
            "Purse",
            "Candle",
            "Carpet",
            "Censer",
            "Chime",
            "Cloak",
            "Crystal",
            "Cube",
            "Cubic",
            "Fortress",
            "Decanter",
            "Deck",
            "Drums",
            "Dust",
            "Eyes",
            "Figurine",
            "Flask",
            "Gauntlets",
            "Gem",
            "Girdle",
            "Helm",
            "Horn",
            "Horseshoes",
            "Incense",
            "Stone",
            "Instrument",
            "Javelin",
            "Jewel",
            "Ointment",
            "Libram",
            "Lyre",
            "Manual",
            "Mattock",
            "Maul",
            "Medallion",
            "Mirror",
            "Necklace",
            "Net",
            "Pigment",
            "Pearl",
            "Periapt",
            "Phylactery",
            "Pipes",
            "Hole",
            "Token",
            "Robe",
            "Rope",
            "Rug",
            "Saw",
            "Scarab",
            "Spade",
            "Sphere",
            "Talisman",
            "Tome",
            "Trident",
            "Grimoire",
            "Well",
            "Wings",
            "Vial",
            "Lantern",
            "Oil",
            "10 ft. Pole",
            "50 ft. Rope",
            "Iron",
            "Thf Prickly Tools",
            "Iron Rations",
            "Standard Rations",
    };

    private static final Set<String> ALL = new HashSet<>();
    static {
        for (String[] group : new String[][]{WEAPONS, MATERIALS, OTHER})
            for (String word : group) ALL.add(word.toLowerCase(Locale.US));
    }

    private ItemVocabulary() {}

    /** How many words the game has in all. */
    public static int size() { return ALL.size(); }

    /** True when the game knows this exact word. */
    public static boolean knows(String word) {
        return word != null && ALL.contains(word.trim().toLowerCase(Locale.US));
    }

    /**
     * True when every word of a printed item name is one the game has.
     *
     * Deliberately strict. An item name from another version that uses a word
     * this one has never heard of is exactly the case the converter has to
     * refuse, and a loose match would turn that refusal into a silent
     * substitution.
     */
    public static boolean canName(String printed) {
        if (printed == null || printed.trim().isEmpty()) return false;
        for (String word : printed.trim().split("[ ]+")) {
            if (word.isEmpty()) continue;
            // Counts such as "30 Arrows" carry a number the vocabulary has no
            // word for; a bare number is not evidence of an unknown item.
            if (word.chars().allMatch(Character::isDigit)) continue;
            if (!knows(word) && !knowsAsPartOfAPhrase(word) && !knowsSingular(word)) return false;
        }
        return true;
    }

    /**
     * The game counts things: its vocabulary has "Arrow" and it prints "30
     * Arrows". A trailing s is therefore not evidence of an unknown word.
     */
    private static boolean knowsSingular(String word) {
        String lower = word.toLowerCase(Locale.US);
        return lower.endsWith("s") && lower.length() > 1
                && (knows(lower.substring(0, lower.length() - 1))
                    || knowsAsPartOfAPhrase(lower.substring(0, lower.length() - 1)));
    }

    /** Several entries are two or three words; match their pieces too. */
    private static boolean knowsAsPartOfAPhrase(String word) {
        String needle = word.toLowerCase(Locale.US);
        for (String known : ALL)
            for (String piece : known.split("[ ]+"))
                if (piece.equals(needle)) return true;
        return false;
    }

    /** Every group, for a reference list. */
    public static String[][] groups() {
        return new String[][]{Arrays.copyOf(WEAPONS, WEAPONS.length),
                Arrays.copyOf(MATERIALS, MATERIALS.length),
                Arrays.copyOf(OTHER, OTHER.length)};
    }
}
