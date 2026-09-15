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

    @Test public void wordingsThatHaveNeverBeenObservedAreNotGuessed() {
        // Plausible, and precisely why they must not be matched.
        for (String invented : new String[]{
                "SEE JOURNAL ENTRY 15", "READ JOURNAL ENTRY 15", "(JOURNAL ENTRY 15)",
                "YOU READ PROCLAMATION 59", "SEE PROCLAMATION 59", "TAVERN TALE 15",
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
