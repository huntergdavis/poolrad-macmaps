package name.osher.gil.minivmac.mapper;

/** Offline original Pool of Radiance reference facts; never reads or changes a character. */
public final class LevelReference {
    public static final String MANUAL_URL = "https://www.bestoldgames.net/download/games/pool-of-radiance/pool-of-radiance-mac-manual.pdf";
    public static final String CLUEBOOK_URL = "https://www.lemonamiga.com/doc/pool-of-radiance/1250";
    public static final String PROGRESSION_URL = "https://gbc.zorbus.net/xp/xp_01_por.txt";
    public static final String FORMAT_URL = "https://gbc.zorbus.net/mm/01_por.html";
    public static final String CHARACTER_FORMAT_URL = "https://gbc.zorbus.net/formats.zip";

    public enum CharacterClass {
        FIGHTER("Fighter", 10), CLERIC("Cleric", 8), MAGIC_USER("Magic-user", 4), THIEF("Thief", 6);
        public final String label;
        public final int hitDieSides;
        CharacterClass(String label, int hitDieSides) { this.label = label; this.hitDieSides = hitDieSides; }
    }

    public enum Race {
        HUMAN("Human"), DWARF("Dwarf"), ELF("Elf"), GNOME("Gnome"), HALF_ELF("Half-elf"), HALFLING("Halfling");
        public final String label;
        Race(String label) { this.label = label; }
    }

    /** This ordering matches the GBC author's labeled Pool of Radiance monster-data tables. */
    public enum Save {
        DEATH("Paralysis / poison / death"), PETRIFICATION("Petrification / polymorph"),
        WAND("Rod / staff / wand"), BREATH("Breath weapon"), SPELL("Spell");
        public final String label;
        Save(String label) { this.label = label; }
    }

    /** Minimum cleric level to attempt influence, from the supplied game's tables appendix. */
    public enum Undead {
        SKELETON("Skeleton", 1), ZOMBIE("Zombie", 1), GHOUL("Ghoul", 1), WIGHT("Wight", 1),
        WRAITH("Wraith", 3), MUMMY("Mummy", 4), SPECTRE("Spectre", 5), VAMPIRE("Vampire", 6);
        public final String label;
        public final int minimumClericLevel;
        Undead(String label, int minimumClericLevel) { this.label = label; this.minimumClericLevel = minimumClericLevel; }
    }

    public static final int UNAVAILABLE = 0;
    public static final int UNLIMITED = Integer.MAX_VALUE;

    // SSI Adventurer's Journal, Table of Experience per Level, printed pp. 35–36.
    // All 29 rows also matched the supplied Macintosh archive's tables file.
    // Values are minimum class XP, including the original +1 at each threshold.
    private static final int[][] XP = {
        {0, 2001, 4001, 8001, 18001, 35001, 70001, 125001},
        {0, 1501, 3001, 6001, 13001, 27501},
        {0, 2501, 5001, 10001, 22501, 40001},
        {0, 1251, 2501, 5001, 10001, 20001, 42501, 70001, 110001}
    };

    // GBC author's Pool of Radiance table, original four classes only.
    // In particular, this game's thief THAC0 is 19 at levels 5–8.
    private static final int[][] THAC0 = {
        {20, 19, 18, 17, 16, 15, 14, 13},
        {20, 20, 20, 18, 18, 18},
        {20, 20, 20, 20, 20, 19},
        {20, 20, 20, 20, 19, 19, 19, 19, 16}
    };
    private static final int[][][] SAVES = {
        {{14,15,16,17,17}, {14,15,16,17,17}, {13,14,15,16,16}, {13,14,15,16,16},
         {11,12,13,13,14}, {11,12,13,13,14}, {10,11,12,12,13}, {10,11,12,12,13}},
        {{10,13,14,16,15}, {10,13,14,16,15}, {10,13,14,16,15},
         {9,12,13,15,14}, {9,12,13,15,14}, {9,12,13,15,14}},
        {{14,13,11,15,12}, {14,13,11,15,12}, {14,13,11,15,12},
         {14,13,11,15,12}, {14,13,11,15,12}, {13,11,9,13,10}},
        {{13,12,14,16,15}, {13,12,14,16,15}, {13,12,14,16,15}, {13,12,14,16,15},
         {12,11,12,15,13}, {12,11,12,15,13}, {12,11,12,15,13}, {12,11,12,15,13}, {11,10,10,14,11}}
    };

    // Open locks, find/remove traps, climb walls: abilities identified by the original cluebook.
    // Other recorded tabletop-style skill fields are deliberately not offered as game actions.
    private static final int[][] THIEF_SKILLS = {
        {25,20,85}, {29,25,86}, {33,30,87}, {37,35,88}, {42,40,90},
        {47,45,92}, {52,50,94}, {57,55,96}, {62,60,98}
    };

    // Printed racial ceilings in rulebook p. 3, with half-elf cleric 5 from the cluebook.
    // Column order: fighter, cleric, magic-user, thief. Phlan's separate limits apply below.
    private static final int[][] RACE_CEILINGS = {
        {UNLIMITED, UNLIMITED, UNLIMITED, UNLIMITED},
        {9, UNAVAILABLE, UNAVAILABLE, UNLIMITED},
        {7, UNAVAILABLE, 11, UNLIMITED},
        {6, UNAVAILABLE, UNAVAILABLE, UNLIMITED},
        {8, 5, 8, UNLIMITED},
        {6, UNAVAILABLE, UNAVAILABLE, UNLIMITED}
    };

    public static final class Level {
        public final CharacterClass characterClass;
        public final int number, minimumXp, thac0, attacksPerTwoRounds;
        private final int[] saves;
        private Level(CharacterClass characterClass, int number) {
            int c = characterClass.ordinal(), i = number - 1;
            this.characterClass = characterClass;
            this.number = number;
            minimumXp = XP[c][i];
            thac0 = THAC0[c][i];
            attacksPerTwoRounds = characterClass == CharacterClass.FIGHTER && number >= 7 ? 3 : 2;
            saves = SAVES[c][i].clone();
        }
        public String hitDice() { return number + "d" + characterClass.hitDieSides; }
        public String attacksPerRound() { return attacksPerTwoRounds == 3 ? "3/2" : "1"; }
        public int savingThrow(Save category) {
            if (category == null) throw new IllegalArgumentException("A saving-throw category is required");
            return saves[category.ordinal()];
        }
    }

    public static final class ThiefSkills {
        public final int level, openLocks, findRemoveTraps, climbWalls, backstabMultiplier;
        private ThiefSkills(int level) {
            int[] row = THIEF_SKILLS[level - 1];
            this.level = level; openLocks = row[0]; findRemoveTraps = row[1]; climbWalls = row[2];
            backstabMultiplier = level <= 4 ? 2 : level <= 8 ? 3 : 4;
        }
    }

    public static int trainingCeiling(CharacterClass characterClass) {
        requireClass(characterClass);
        return XP[characterClass.ordinal()].length;
    }

    public static Level level(CharacterClass characterClass, int number) {
        if (number < 1 || number > trainingCeiling(characterClass))
            throw new IllegalArgumentException("Level is outside Pool of Radiance's training range");
        return new Level(characterClass, number);
    }

    public static ThiefSkills thiefSkills(int level) {
        if (level < 1 || level > trainingCeiling(CharacterClass.THIEF))
            throw new IllegalArgumentException("Thief level must be 1 through 9");
        return new ThiefSkills(level);
    }

    public static int racialCeiling(Race race, CharacterClass characterClass) {
        requireClass(characterClass);
        if (race == null) throw new IllegalArgumentException("A race is required");
        return RACE_CEILINGS[race.ordinal()][characterClass.ordinal()];
    }

    public static int gameCeiling(Race race, CharacterClass characterClass) {
        return Math.min(racialCeiling(race, characterClass), trainingCeiling(characterClass));
    }

    private static void requireClass(CharacterClass characterClass) {
        if (characterClass == null) throw new IllegalArgumentException("A class is required");
    }

    private LevelReference() { }
}
