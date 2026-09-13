package name.osher.gil.minivmac.mapper;

/** Independent Java lookup from the wheel's published table, not a game-memory patch.
 * Reference: https://dkennedy.io/por-code-wheel/wwm.html (Dave Kennedy / Andrew Schultz).
 * Rune numbers are 1..36 in that reference's reading order.
 */
public final class CodeWheel {
    private CodeWheel() {}
    private static final String[] WORDS = {
        "AXEIAX", "BEWARE", "COPPER", "DRAGON", "EFREET", "FRIEND", "GOOGLE",
        "HARASH", "IXYVSI", "JUNGLE", "KNIGHT", "LQLGMT", "MLSSXS", "NOTNOW", "OTTAWA",
        "POOLRD", "QUOHOG", "RHUDIA", "SAVIOR", "TEMPLE", "UICDRH", "VULCAN", "WYVERN",
        "XRSEHK", "YUFSTA", "ZOMBIE", "1GKKRY", "2IOLCD", "3MASAI", "4NINER", "5GUNGA",
        "6BROWN", "7GNATS", "80ASIS", "9TROUT", "0SOMAS"
    };

    /** Path: 0 = dots, 1 = dash/two dots, 2 = dashes. Retains the reference's digit prefix. */
    public static String lookup(int espruar, int dethek, int path) {
        if (espruar < 1 || espruar > 36 || dethek < 1 || dethek > 36 || path < 0 || path > 2)
            throw new IllegalArgumentException("Select both runes (1–36) and a path (0–2)");
        return WORDS[(espruar + dethek - 1 + path * 12) % WORDS.length];
    }

    /** The game asks for the word, without a leading wheel alignment digit. */
    public static String entry(int espruar, int dethek, int path) {
        String word = lookup(espruar, dethek, path);
        return Character.isDigit(word.charAt(0)) ? word.substring(1) : word;
    }
}
