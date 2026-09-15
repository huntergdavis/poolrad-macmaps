package name.osher.gil.minivmac.journal;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises the game citing one of its own numbered references in the text it
 * is displaying.
 *
 * <p><b>Only wordings observed in the running game are matched.</b> The tavern
 * form was read out of the Message window on 2026-09-14, verbatim:
 * {@code YOU OVERHEAR TAVERN TALE 15}. The journal-entry and proclamation
 * wordings have not been seen yet, so this class deliberately recognises
 * neither: a guessed pattern would file a wrong number into the player's
 * notebook, which is worse than recording nothing. Add a pattern here only
 * with a screenshot of the game actually printing it.
 */
public final class JournalCitation {
    /**
     * Anchored on the exact observed sentence. The number is bounded to three
     * digits so a runaway match cannot produce an absurd reference, and the
     * surrounding boundaries stop "TALE 15" inside a longer word or a longer
     * number ("TALE 153") from reading as tale 15.
     */
    private static final Pattern TAVERN_TALE =
            Pattern.compile("(?<![A-Z0-9])YOU OVERHEAR TAVERN TALE ([0-9]{1,3})(?![0-9])");

    /** How much text is worth scanning; the reader's own ceiling. */
    static final int MAX_TEXT = GameMessage.MAX_TEXT;

    private JournalCitation() { }

    /**
     * Every reference the message cites, in the order the game printed them.
     * Empty for an absent, truncated or uncited message. A number the supplied
     * journal does not define is dropped rather than invented.
     */
    public static Set<JournalBook.Key> read(GameMessage message) {
        Set<JournalBook.Key> found = new LinkedHashSet<>();
        if (message == null || message.text == null || message.truncated) return found;
        if (message.text.length() > MAX_TEXT) return found;
        Matcher matcher = TAVERN_TALE.matcher(message.text);
        while (matcher.find()) {
            try {
                found.add(new JournalBook.Key(2, Integer.parseInt(matcher.group(1))));
            } catch (IllegalArgumentException outsideTheBook) {
                // A number this journal does not define is not a reference.
            }
        }
        return Collections.unmodifiableSet(found);
    }
}
