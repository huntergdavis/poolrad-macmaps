package name.osher.gil.minivmac.journal;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Invented packets only; the one real sentence here is the observed citation. */
public class JournalCitationTest {
    private byte[] packet(String text, boolean truncated) {
        byte[] b = new byte[GameMessage.PACKET_SIZE];
        b[0]='P'; b[1]='R'; b[2]='T'; b[3]='1'; b[4]=1; b[5]=(byte)(truncated?1:0);
        byte[] value = text.getBytes(StandardCharsets.US_ASCII);
        b[6]=(byte)(value.length>>8); b[7]=(byte)value.length;
        System.arraycopy(value,0,b,8,value.length);
        return b;
    }
    private byte[] unavailable() {
        byte[] b = new byte[GameMessage.PACKET_SIZE];
        b[0]='P'; b[1]='R'; b[2]='T'; b[3]='1'; b[4]=(byte)255;
        return b;
    }
    private Set<JournalBook.Key> cited(String text) {
        return JournalCitation.read(GameMessage.parse(packet(text,false)));
    }

    @Test public void theWordingTheGameActuallyPrintedIsRecognised() {
        // Read out of the Message window in the New Phlan tavern, 2026-09-14.
        Set<JournalBook.Key> found = cited("YOU OVERHEAR TAVERN TALE 15");
        assertEquals(1, found.size());
        JournalBook.Key key = found.iterator().next();
        assertEquals(2, key.kind);
        assertEquals(15, key.number);
        assertEquals("Tavern tale 15", key.label());
    }

    @Test public void everyTaleTheJournalDefinesIsAccepted() {
        for (int n = 1; n <= 23; n++)
            assertEquals("tale " + n, 1, cited("YOU OVERHEAR TAVERN TALE " + n).size());
    }

    @Test public void aNumberTheJournalDoesNotDefineIsNotInvented() {
        for (int n : new int[]{0, 24, 58, 99, 100, 999})
            assertTrue("tale " + n + " accepted", cited("YOU OVERHEAR TAVERN TALE " + n).isEmpty());
    }

    @Test public void aLongerNumberIsNotReadAsAShorterOne() {
        assertTrue(cited("YOU OVERHEAR TAVERN TALE 153").isEmpty());
        assertTrue(cited("YOU OVERHEAR TAVERN TALE 1500").isEmpty());
    }

    @Test public void theCitationIsFoundInsideAFullerSentence() {
        Set<JournalBook.Key> found = cited("AS YOU SIT DOWN, YOU OVERHEAR TAVERN TALE 7. THE ROOM QUIETENS.");
        assertEquals(1, found.size());
        assertEquals(7, found.iterator().next().number);
    }

    @Test public void twoCitationsKeepThePrintedOrder() {
        Set<JournalBook.Key> found =
                cited("YOU OVERHEAR TAVERN TALE 9 AND THEN YOU OVERHEAR TAVERN TALE 3");
        assertEquals(Arrays.asList(9, 3),
                Arrays.asList(found.toArray(new JournalBook.Key[0])[0].number,
                              found.toArray(new JournalBook.Key[0])[1].number));
    }

    /** Hunter's screenshot, 2026-09-15, verbatim. */
    private static final String POSTED =
            "PROCLAMATIONS ARE POSTED ON THE WALLS, IN YOUR JOURNAL YOU NOTE "
            + "PROCLAMATIONS LXIV, LXXVIII, CIX, AND LIX.";

    @Test public void theProclamationListTheGameActuallyPrintedIsRecognised() {
        Set<JournalBook.Key> found = cited(POSTED);
        assertEquals(4, found.size());
        int[] expected = {64, 78, 109, 59};
        int at = 0;
        for (JournalBook.Key key : found) {
            assertEquals("kind", 1, key.kind);
            assertEquals("order is the order the game printed", expected[at++], key.number);
        }
        assertTrue(found.contains(new JournalBook.Key(1, 64)));
        assertEquals("Proclamation 59", new JournalBook.Key(1, 59).label());
    }

    @Test public void everyProclamationTheJournalDefinesHasAMatchingNumeral() {
        for (int number : JournalBook.PROCLAMATIONS) {
            String numeral = JournalCitation.roman(number);
            Set<JournalBook.Key> found =
                    cited("IN YOUR JOURNAL YOU NOTE PROCLAMATION " + numeral + ".");
            assertEquals("proclamation " + number + " (" + numeral + ")", 1, found.size());
            assertEquals(number, found.iterator().next().number);
        }
        // The spellings are the ordinary canonical ones the reader panel lists.
        assertEquals("LIX", JournalCitation.roman(59));
        assertEquals("LXIV", JournalCitation.roman(64));
        assertEquals("LXXVIII", JournalCitation.roman(78));
        assertEquals("CXC", JournalCitation.roman(190));
        assertEquals("CCXIV", JournalCitation.roman(214));
    }

    @Test public void aNumeralTheJournalDoesNotDefineIsSkippedNotGuessed() {
        // III is 3, a perfectly good numeral, but no such proclamation exists.
        assertTrue(cited("IN YOUR JOURNAL YOU NOTE PROCLAMATIONS III AND IV.").isEmpty());
        // A list mixing known and unknown keeps only what the journal defines.
        Set<JournalBook.Key> mixed = cited("IN YOUR JOURNAL YOU NOTE PROCLAMATIONS LIX, III, AND CIX.");
        assertEquals(2, mixed.size());
        assertTrue(mixed.contains(new JournalBook.Key(1, 59)));
        assertTrue(mixed.contains(new JournalBook.Key(1, 109)));
        // A non-canonical spelling of a real number is not that number.
        assertTrue(cited("IN YOUR JOURNAL YOU NOTE PROCLAMATIONS LVIIII.").isEmpty());
    }

    @Test public void aProclamationListOfAnyLengthReads() {
        assertEquals(1, cited("IN YOUR JOURNAL YOU NOTE PROCLAMATIONS LIX.").size());
        assertEquals(2, cited("IN YOUR JOURNAL YOU NOTE PROCLAMATIONS LIX AND CIX.").size());
        assertEquals(3, cited("IN YOUR JOURNAL YOU NOTE PROCLAMATIONS LIX, CIX, AND CX.").size());
        // A repeat inside one list is still one reference.
        assertEquals(1, cited("IN YOUR JOURNAL YOU NOTE PROCLAMATIONS LIX AND LIX.").size());
    }

    @Test public void bothKindsInOneMessageAreBothRead() {
        Set<JournalBook.Key> found = cited(
                "YOU OVERHEAR TAVERN TALE 6. " + POSTED);
        assertEquals(5, found.size());
        assertTrue(found.contains(new JournalBook.Key(2, 6)));
        assertTrue(found.contains(new JournalBook.Key(1, 78)));
    }

    /** From the screenshot of another port, 2026-09-15; see JournalCitation. */
    private static final String COPIED =
            "YOU FIND URGUND'S DESCRIPTION OF DARKNESS. THIS IS AN ACCOUNT OF HIS "
            + "IMPRISONMENT IN THE LOWER REALMS. THERE IS A PASSAGE OF INTEREST WHICH "
            + "YOU COPY AS ENTRY 19 IN YOUR JOURNAL.";

    @Test public void theJournalEntrySentenceIsRecognised() {
        Set<JournalBook.Key> found = cited(COPIED);
        assertEquals(1, found.size());
        JournalBook.Key key = found.iterator().next();
        assertEquals(0, key.kind);
        assertEquals(19, key.number);
        assertEquals("Journal 19", key.label());
    }

    @Test public void everyEntryTheJournalDefinesIsAccepted() {
        for (int n = 1; n <= 58; n++)
            assertEquals("entry " + n, 1, cited("ENTRY " + n + " IN YOUR JOURNAL.").size());
        for (int n : new int[]{0, 59, 99, 214})
            assertTrue("entry " + n + " accepted", cited("ENTRY " + n + " IN YOUR JOURNAL.").isEmpty());
    }

    @Test public void theJournalEntryAnchorNeedsBothSides() {
        // Either half alone is not a citation.
        assertTrue(cited("YOU COPY AS ENTRY 19.").isEmpty());
        assertTrue(cited("THERE IS A PASSAGE OF INTEREST IN YOUR JOURNAL.").isEmpty());
        assertTrue(cited("ENTRY 19 IN YOUR POCKET").isEmpty());
        assertTrue(cited("SENTRY 19 IN YOUR JOURNAL").isEmpty());
        assertTrue(cited("ENTRY 190 IN YOUR JOURNAL").isEmpty());
        // The verb in front may vary; only the anchored part is required.
        assertEquals(1, cited("YOU RECORD THIS AS ENTRY 4 IN YOUR JOURNAL.").size());
    }

    @Test public void allThreeKindsInOneMessageAreAllRead() {
        Set<JournalBook.Key> found = cited(POSTED + " " + COPIED + " YOU OVERHEAR TAVERN TALE 6");
        assertEquals(6, found.size());
        assertTrue(found.contains(new JournalBook.Key(1, 109)));
        assertTrue(found.contains(new JournalBook.Key(0, 19)));
        assertTrue(found.contains(new JournalBook.Key(2, 6)));
    }

    @Test public void wordingsThatHaveNeverBeenObservedAreNotGuessed() {
        // Plausible, and precisely why they must not be matched.
        for (String invented : new String[]{
                // The journal-entry wording is still unobserved. These are all
                // plausible, and precisely why none of them may be matched.
                "SEE JOURNAL ENTRY 15", "READ JOURNAL ENTRY 15", "(JOURNAL ENTRY 15)",
                "IN YOUR JOURNAL YOU NOTE ENTRY LIX",
                "YOU READ PROCLAMATION 59", "SEE PROCLAMATION 59", "TAVERN TALE 15",
                "PROCLAMATIONS ARE POSTED ON THE WALLS",
                "YOU NOTE PROCLAMATIONS LIX", "IN YOUR JOURNAL YOU SEE PROCLAMATIONS LIX",
                "YOU OVERHEAR TAVERN TALES 15", "YOU OVERHEARD TAVERN TALE 15",
                "you overhear tavern tale 15"})
            assertTrue(invented + " was matched", cited(invented).isEmpty());
    }

    @Test public void aTruncatedMessageMayHaveLostADigitAndIsRefused() {
        byte[] cut = packet("YOU OVERHEAR TAVERN TALE 1", true);
        GameMessage message = GameMessage.parse(cut);
        assertNotNull(message);
        assertTrue(message.truncated);
        assertTrue(JournalCitation.read(message).isEmpty());
    }

    @Test public void anUnreadableOrMissingMessageCitesNothing() {
        assertNull(GameMessage.parse(unavailable()));
        assertTrue(JournalCitation.read(null).isEmpty());
        assertTrue(cited("").isEmpty());
        assertTrue(cited("THE PARTY MAKES CAMP").isEmpty());
    }
}
