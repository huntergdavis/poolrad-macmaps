package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** One line for what the party is waiting on. Invented packets only. */
public class PartyChoresTest {
    /** A PRP7 party; each member's hit points and condition set as asked. */
    private static PartyState party(int[][] members) {
        byte[] packet = new byte[PartyState.QUICK_PACKET_SIZE];
        System.arraycopy(new byte[]{'P','R','P','7'}, 0, packet, 0, 4);
        packet[4] = (byte) members.length;
        for (int i = 0; i < members.length; i++) {
            int current = members[i][0], max = members[i][1], condition = members[i][2];
            int row = 8 + i * PartyState.ROW_SIZE;
            byte[] name = ("Hero " + (i + 1)).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(name, 0, packet, row, name.length);
            packet[row + 16] = (byte) current;
            packet[row + 17] = (byte) max;
            packet[row + 18] = (byte) 0x80;
            packet[row + 19] = (byte) 0xff;
            packet[PartyState.PACKET_SIZE + i * 2] = (byte) condition;
            packet[PartyState.PACKET_SIZE + i * 2 + 1] = 0;
            packet[PartyState.CONDITION_PACKET_SIZE + i * PartyState.SPELL_STRIDE] = (byte) 0xff;
            packet[PartyState.SPELL_PACKET_SIZE + i * PartyState.EQUIP_STRIDE] = (byte) 0xff;
            packet[PartyState.EQUIP_PACKET_SIZE + i * PartyState.TRAIN_STRIDE] = (byte) 0xff;
        }
        PartyState state = PartyState.parse(packet);
        assertNotNull(state);
        return state;
    }

    @Test public void aPartyWithNothingOutstandingKeepsThePlainHeading() {
        assertEquals(PartyChores.NOTHING, PartyChores.summary(party(new int[][]{{10, 10, 0}, {8, 8, 0}})));
    }

    @Test public void noPartyAtAllKeepsThePlainHeading() {
        assertEquals(PartyChores.NOTHING, PartyChores.summary(null));
    }

    @Test public void somebodyHurtIsCounted() {
        assertEquals("1 hurt", PartyChores.summary(party(new int[][]{{4, 10, 0}, {8, 8, 0}})));
        assertEquals("2 hurt", PartyChores.summary(party(new int[][]{{4, 10, 0}, {1, 8, 0}})));
    }

    @Test public void somebodyDownIsCountedSeparatelyFromMerelyHurt() {
        // Condition 5 is dying, 6 is dead; both are down rather than hurt.
        assertEquals("1 down", PartyChores.summary(party(new int[][]{{0, 10, 5}, {8, 8, 0}})));
        assertEquals("2 down", PartyChores.summary(party(new int[][]{{0, 10, 5}, {0, 8, 6}})));
    }

    @Test public void theWorstThingComesFirst() {
        /*
         * Somebody dying is not a footnote to somebody's spell slots, so the
         * order is by how much it matters rather than by how the record is
         * laid out.
         */
        String line = PartyChores.summary(party(new int[][]{{0, 10, 5}, {3, 8, 0}, {9, 9, 0}}));
        assertTrue(line, line.startsWith("1 down"));
        assertTrue(line, line.contains("1 hurt"));
        assertTrue(line.indexOf("down") < line.indexOf("hurt"));
    }

    @Test public void oneOfSomethingReadsAsOne() {
        assertFalse(PartyChores.summary(party(new int[][]{{4, 10, 0}})).contains("1 hurts"));
        assertEquals("1 hurt", PartyChores.summary(party(new int[][]{{4, 10, 0}})));
    }

    @Test public void somebodyDownIsNotAlsoCountedAsHurt() {
        // They are on nought hit points, which is hurt by any measure, but
        // saying both would double-count one person.
        assertEquals("1 down", PartyChores.summary(party(new int[][]{{0, 10, 6}})));
    }

    @Test public void theChoresAreJoinedWithTheSameSeparatorTheMapUses() {
        String line = PartyChores.summary(party(new int[][]{{0, 10, 5}, {3, 8, 0}}));
        assertTrue(line, line.contains(" · "));
    }
}
