package name.osher.gil.minivmac.journal;

import java.util.Locale;

/**
 * The one thing the game says when the party finds something worth marking.
 *
 * `The party has found Treasure!` is a single exact string in the game's own
 * `STRS` resource, which is what makes this safe to act on. There is no
 * guessing at phrasing and no pattern that might catch something else: the
 * game either said that sentence or it did not.
 *
 * Deliberately narrow. "Look" and "Search" produce plenty of prose, and a map
 * covered in marks for every barrel examined would be worth less than one with
 * a handful of marks that mean something.
 */
public final class DiscoveryMessage {
    /** Verbatim from the application's STRS resource. */
    public static final String TREASURE = "The party has found Treasure!";

    private DiscoveryMessage() {}

    /**
     * True when this message is the game announcing treasure.
     *
     * Case and surrounding whitespace are ignored because the Message window
     * wraps and pads; the words themselves are matched exactly.
     */
    public static boolean foundTreasure(String message) {
        if (message == null) return false;
        return squash(message).contains(squash(TREASURE));
    }

    /** Collapse runs of whitespace, so a wrapped line still matches. */
    private static String squash(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            if (Character.isWhitespace(letter)) { space = out.length() > 0; continue; }
            if (space) { out.append(' '); space = false; }
            out.append(Character.toLowerCase(letter));
        }
        return out.toString();
    }
}
