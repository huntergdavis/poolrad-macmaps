package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

/** What the machine is doing, read from the heap rather than from the screen. */
public class GameSignalTest {
    /** A ten-byte PRPX refusal, as the probe sends one. */
    private static byte[] refusal(int code, int links, long detail) {
        return new byte[]{'P','R','P','X', (byte) code, (byte) links,
                (byte) (detail >>> 24), (byte) (detail >>> 16), (byte) (detail >>> 8), (byte) detail};
    }

    private static byte[] party(int count) {
        byte[] packet = new byte[PartyState.PACKET_SIZE];
        System.arraycopy(new byte[]{'P','R','P','1'}, 0, packet, 0, 4);
        packet[4] = (byte) count;
        for (int i = 0; i < count; i++) {
            int row = 8 + i * PartyState.ROW_SIZE;
            byte[] name = ("Hero " + (i + 1)).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(name, 0, packet, row, name.length);
            packet[row + 16] = 10; packet[row + 17] = 10;
        }
        return packet;
    }

    @Test public void theGameNotRunningIsItsOwnAnswer() {
        assertEquals(GameSignal.NO_GAME, GameSignal.of(refusal(1, 0, 0)));
        assertFalse(GameSignal.of(refusal(1, 0, 0)).gameRunning());
    }

    @Test public void aTitleScreenIsTheGameWithNobodyInIt() {
        assertEquals(GameSignal.NO_PARTY, GameSignal.of(refusal(4, 0, 0)));
        assertTrue(GameSignal.of(refusal(4, 0, 0)).gameRunning());
    }

    @Test public void areadablePartyIsAParty() {
        assertEquals(GameSignal.PARTY, GameSignal.of(party(6)));
        assertTrue(GameSignal.of(party(6)).gameRunning());
    }

    @Test public void everyOtherRefusalSaysNothingAboutTheMachine() {
        /*
         * These are complaints about the heap -- a rejected block, a looping
         * roster -- and a game is very much running while they happen.
         * Reading one as "no game" is how something gets typed into a live game.
         */
        for (int code : new int[]{2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14}) {
            GameSignal signal = GameSignal.of(refusal(code, 2, 0x82000140L));
            assertEquals("refusal " + code, GameSignal.UNKNOWN, signal);
            assertFalse("refusal " + code + " must not claim the game is up", signal.gameRunning());
        }
    }

    @Test public void nothingAtAllIsUnknownRatherThanAnAnswer() {
        assertEquals(GameSignal.UNKNOWN, GameSignal.of(null));
        assertEquals(GameSignal.UNKNOWN, GameSignal.of(new byte[0]));
        assertEquals(GameSignal.UNKNOWN, GameSignal.of(new byte[]{'P','R','P'}));
        assertEquals(GameSignal.UNKNOWN, GameSignal.of("not a packet at all".getBytes()));
        assertFalse(GameSignal.UNKNOWN.gameRunning());
    }

    @Test public void aMalformedPartyPacketIsNotAParty() {
        byte[] broken = party(6);
        broken[4] = 99;                       // more members than a party can hold
        assertEquals(GameSignal.UNKNOWN, GameSignal.of(broken));
    }
}
