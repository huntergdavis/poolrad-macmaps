package name.osher.gil.minivmac.journal;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Invented packets only; no private capture appears here. */
public class GameMessageTest {
    private byte[] packet(String text) {
        byte[] b = new byte[GameMessage.PACKET_SIZE];
        b[0]='P'; b[1]='R'; b[2]='T'; b[3]='1'; b[4]=1;
        byte[] value = text.getBytes(StandardCharsets.US_ASCII);
        b[6]=(byte)(value.length>>8); b[7]=(byte)value.length;
        System.arraycopy(value,0,b,8,value.length);
        return b;
    }

    @Test public void theGamesOwnTextIsReadBackUnchanged() {
        GameMessage message = GameMessage.parse(packet("A DRUNKEN BRAWL BREAKS OUT."));
        assertNotNull(message);
        assertEquals("A DRUNKEN BRAWL BREAKS OUT.", message.text);
        assertFalse(message.truncated);
    }

    @Test public void anEmptyMessageWindowIsEmptyNotMissing() {
        GameMessage message = GameMessage.parse(packet(""));
        assertNotNull(message);
        assertEquals("", message.text);
    }

    @Test public void lineBreaksAndTabsSurvive() {
        assertEquals("ONE\rTWO\tTHREE", GameMessage.parse(packet("ONE\rTWO\tTHREE")).text);
    }

    @Test public void anUnavailableReaderIsNotAnEmptyMessage() {
        byte[] b = new byte[GameMessage.PACKET_SIZE];
        b[0]='P'; b[1]='R'; b[2]='T'; b[3]='1'; b[4]=(byte)255;
        assertNull(GameMessage.parse(b));
    }

    @Test public void malformedPacketsAreRejected() {
        byte[] good = packet("HELLO");
        assertNotNull(GameMessage.parse(good));
        assertNull(GameMessage.parse(null));
        for (int at = 0; at < 4; at++) {
            byte[] b = good.clone(); b[at] = 'X';
            assertNull(GameMessage.parse(b));
        }
        for (int status : new int[]{0, 2, 3, 254}) {
            byte[] b = good.clone(); b[4] = (byte) status;
            assertNull("status " + status + " accepted", GameMessage.parse(b));
        }
        byte[] flag = good.clone(); flag[5] = 2;
        assertNull("truncation flag 2 accepted", GameMessage.parse(flag));

        // Bytes past the declared length must be zero.
        byte[] trailing = good.clone(); trailing[8 + 5] = 'X';
        assertNull(GameMessage.parse(trailing));

        // Non-text bytes never reach the reader.
        for (int value : new int[]{0x00, 0x01, 0x0a, 0x1f, 0x7f, 0x80, 0xff}) {
            byte[] b = good.clone(); b[8] = (byte) value;
            assertNull("byte " + value + " accepted", GameMessage.parse(b));
        }
        // An unavailable packet carrying text is contradictory.
        byte[] mixed = good.clone(); mixed[4] = (byte) 255;
        assertNull(GameMessage.parse(mixed));

        byte[] over = good.clone(); over[6] = 2; over[7] = 1;
        assertNull("length past the ceiling accepted", GameMessage.parse(over));

        for (int length : new int[]{0, 7, 519, 521})
            assertNull(GameMessage.parse(Arrays.copyOf(good, length)));
    }

    @Test public void theCeilingItselfIsReadable() {
        StringBuilder full = new StringBuilder();
        while (full.length() < GameMessage.MAX_TEXT) full.append('A');
        GameMessage message = GameMessage.parse(packet(full.toString()));
        assertNotNull(message);
        assertEquals(GameMessage.MAX_TEXT, message.text.length());
    }
}
