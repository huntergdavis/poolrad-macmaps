package name.osher.gil.minivmac.mapper;

/**
 * What the Macintosh version of Pool of Radiance actually supports.
 *
 * A converter cannot bring a party inside limits nobody has written down, and
 * these are exactly the limits a port is likely to have changed. Every value
 * here was read out of the game itself: the names from the application's own
 * `STRS` resource, the experience thresholds from the table the game consults
 * when it decides whether a character may train.
 *
 * The important finding is the absences. Four of the eight single classes have
 * no experience thresholds at all -- the game knows their names and will not
 * let anybody be one. A character converted from a version that has them has
 * nowhere to go here, and the converter must say so rather than pick a
 * substitute.
 */
public final class MacintoshLimits {
    /** Every class the game names, in its own order. Index is the record's class byte. */
    public static final String[] CLASSES = {
            "Cleric", "Druid", "Fighter", "Paladin", "Ranger", "Magic-User", "Thief", "Monk",
            "Cleric/Fighter", "Cleric/Fighter/Magic-User", "Cleric/Ranger", "Cleric/Magic-User",
            "Cleric/Thief", "Fighter/Magic-User", "Fighter/Thief", "Fighter/Magic-User/Thief",
            "Magic-User/Thief", "Monster"
    };

    /**
     * Every race the game names. **One-based**: the record's race byte counts
     * from 1, which is forced rather than assumed -- a real saved character
     * reads 7, and a zero-based list of seven names has no 7.
     */
    public static final String[] RACES = {
            "Dwarf", "Elf", "Gnome", "Half-Elf", "Halfling", "Half-Orc", "Human"
    };

    /**
     * The highest level each single class can reach, or 0 for one this version
     * does not let anybody be. Indexed like {@link #CLASSES}; a multi-class
     * character is bounded by each of its component classes.
     */
    public static final int[] SINGLE_CLASS_CAPS = {
            6,   // Cleric
            0,   // Druid      -- no thresholds; not in this version
            8,   // Fighter
            0,   // Paladin    -- no thresholds; not in this version
            0,   // Ranger     -- no thresholds; not in this version
            6,   // Magic-User
            9,   // Thief
            0    // Monk       -- no thresholds; not in this version
    };

    /** The highest spell level this version has. */
    public static final int MAX_SPELL_LEVEL = 3;
    /** How many spells a character may have memorised at once. */
    public static final int MEMORISED_SPELL_SLOTS = 21;
    /** The most characters a party can hold, NPCs included. */
    public static final int MAX_PARTY = 8;

    private MacintoshLimits() {}

    public static String className(int value) {
        return value >= 0 && value < CLASSES.length ? CLASSES[value] : null;
    }

    /** The race byte counts from one; zero and anything past the list are unknown. */
    public static String raceName(int value) {
        return value >= 1 && value <= RACES.length ? RACES[value - 1] : null;
    }

    /** True when the game will let a character be this class at all. */
    public static boolean playable(int classValue) {
        if (classValue < 0 || classValue >= CLASSES.length) return false;
        if (classValue == CLASSES.length - 1) return false;            // Monster
        if (classValue < SINGLE_CLASS_CAPS.length) return SINGLE_CLASS_CAPS[classValue] > 0;
        // A multi-class character needs every component to exist. The four
        // missing classes appear in the combinations, so those go too.
        String name = CLASSES[classValue];
        for (int i = 0; i < SINGLE_CLASS_CAPS.length; i++)
            if (SINGLE_CLASS_CAPS[i] == 0 && containsComponent(name, CLASSES[i])) return false;
        return true;
    }

    /**
     * The cap for a class, or 0 if this version does not have it. For a
     * multi-class character it is the lowest cap among its components, because
     * that is the first wall the character meets.
     */
    public static int cap(int classValue) {
        if (!playable(classValue)) return 0;
        if (classValue < SINGLE_CLASS_CAPS.length) return SINGLE_CLASS_CAPS[classValue];
        int lowest = Integer.MAX_VALUE;
        String name = CLASSES[classValue];
        for (int i = 0; i < SINGLE_CLASS_CAPS.length; i++)
            if (SINGLE_CLASS_CAPS[i] > 0 && containsComponent(name, CLASSES[i]))
                lowest = Math.min(lowest, SINGLE_CLASS_CAPS[i]);
        return lowest == Integer.MAX_VALUE ? 0 : lowest;
    }

    /**
     * Whole-word component match, so "Cleric/Magic-User" contains "Cleric" and
     * "Magic-User" but not "Magic-User/Thief" matching on "Thief" by accident
     * -- and so "Ranger" does not match inside "Cleric/Ranger" incorrectly (it
     * should, and does, which is the point).
     */
    private static boolean containsComponent(String combined, String component) {
        for (String part : combined.split("/")) if (part.equals(component)) return true;
        return false;
    }
}
