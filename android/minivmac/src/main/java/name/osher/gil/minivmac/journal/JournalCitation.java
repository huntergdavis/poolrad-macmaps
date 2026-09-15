package name.osher.gil.minivmac.journal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises the game citing one of its own numbered references in the text it
 * is displaying.
 *
 * <p><b>Only wordings observed in the running game are matched.</b> Two are:
 *
 * <ul>
 *   <li>the tavern form, read out of the Message window on 2026-09-14:
 *       {@code YOU OVERHEAR TAVERN TALE 15};</li>
 *   <li>the proclamation form, from Hunter's own screenshot on 2026-09-15:
 *       {@code PROCLAMATIONS ARE POSTED ON THE WALLS, IN YOUR JOURNAL YOU NOTE
 *       PROCLAMATIONS LXIV, LXXVIII, CIX, AND LIX.} — a list, in Roman
 *       numerals.</li>
 * </ul>
 *
 * <p>The <b>journal-entry</b> form is matched from a screenshot Hunter found on
 * 2026-09-15 of <i>another port</i> of the same game, not of the supported
 * Macintosh v1.1 build:
 * {@code ...THERE IS A PASSAGE OF INTEREST WHICH YOU COPY AS ENTRY 19 IN YOUR
 * JOURNAL.} That provenance is weaker than the other two and is recorded as
 * such, but the risk it carries is not: the anchor puts the number between the
 * words {@code ENTRY} and {@code IN YOUR JOURNAL}, so if the Macintosh build
 * words it differently the pattern simply never fires. The failure is silence,
 * never a wrong number. Confirm it on the Mac build when a citation is seen
 * there, and tighten or widen it then.
 *
 * <p>Add any further pattern here only with a screenshot of a game printing it.
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

    /**
     * The proclamation list, anchored on the sentence the game actually prints.
     * Only the plural was observed; the singular is accepted because it is the
     * same sentence with one item, and the anchor is far too specific to match
     * anything else. The list itself is read from the capture group.
     */
    private static final Pattern PROCLAMATIONS = Pattern.compile(
            "IN YOUR JOURNAL YOU NOTE PROCLAMATIONS? ([IVXLC]+(?:,? (?:AND )?[IVXLC]+)*)");
    private static final Pattern ROMAN_ITEM = Pattern.compile("[IVXLC]+");

    /**
     * Canonical Roman spellings of exactly the eighteen proclamations the
     * supported journal defines, and nothing else. Matching whole spellings
     * rather than parsing arbitrary numerals means a token this table does not
     * know records nothing at all, instead of becoming some other number.
     */
    private static final Map<String, Integer> PROCLAMATION_NUMERALS = numerals();

    private static Map<String, Integer> numerals() {
        Map<String, Integer> byNumeral = new LinkedHashMap<>();
        for (int number : JournalBook.PROCLAMATIONS) byNumeral.put(roman(number), number);
        return Collections.unmodifiableMap(byNumeral);
    }

    /** Ordinary canonical Roman notation; the journal's numbers are all under 1000. */
    static String roman(int value) {
        int[] amounts = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] letters = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < amounts.length; i++)
            while (value >= amounts[i]) { out.append(letters[i]); value -= amounts[i]; }
        return out.toString();
    }

    /**
     * A journal entry, anchored on both sides of its number. The verb varies in
     * the surrounding sentence — the observed one is "which you copy as" — so
     * only the part that cannot vary is matched.
     */
    private static final Pattern JOURNAL_ENTRY =
            Pattern.compile("(?<![A-Z])ENTRY ([0-9]{1,3})(?![0-9]) IN YOUR JOURNAL");

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
        Matcher entry = JOURNAL_ENTRY.matcher(message.text);
        while (entry.find()) {
            try {
                found.add(new JournalBook.Key(0, Integer.parseInt(entry.group(1))));
            } catch (IllegalArgumentException outsideTheBook) {
                // A number this journal does not define is not a reference.
            }
        }
        Matcher posted = PROCLAMATIONS.matcher(message.text);
        while (posted.find()) {
            Matcher item = ROMAN_ITEM.matcher(posted.group(1));
            while (item.find()) {
                Integer number = PROCLAMATION_NUMERALS.get(item.group());
                // An unrecognised numeral is skipped, never guessed at; the
                // rest of the list is still perfectly readable.
                if (number != null) found.add(new JournalBook.Key(1, number));
            }
        }
        return Collections.unmodifiableSet(found);
    }
}
