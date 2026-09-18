package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/**
 * The "W" mark: who in the party is being slowed by what they are carrying.
 * Invented packets only; no private character records appear here.
 */
public class PartyLoadTest {
    /** A PRP7 packet whose members move at the given rates, one per member. */
    private static byte[] packet(int... movement) {
        byte[] packet = new byte[PartyState.QUICK_PACKET_SIZE];
        System.arraycopy(new byte[] {'P', 'R', 'P', '7'}, 0, packet, 0, 4);
        packet[4] = (byte) movement.length;
        for (int i = 0; i < movement.length; i++) {
            int row = 8 + i * PartyState.ROW_SIZE;
            byte[] name = ("Hero " + (i + 1)).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(name, 0, packet, row, name.length);
            packet[row + 16] = (byte) (10 + i);
            packet[row + 17] = (byte) (10 + i);
            packet[row + 18] = (byte) 0x80;   // armour class unavailable
            packet[row + 19] = (byte) 0xff;   // class unavailable
            packet[PartyState.PACKET_SIZE + i * 2] = (byte) 0xff;
            packet[PartyState.PACKET_SIZE + i * 2 + 1] = (byte) 0xff;
            packet[PartyState.CONDITION_PACKET_SIZE + i * PartyState.SPELL_STRIDE] = (byte) 0xff;
            int equip = PartyState.SPELL_PACKET_SIZE + i * PartyState.EQUIP_STRIDE;
            packet[equip] = (byte) 0xff;      // item names unavailable; load still reads
            packet[equip + 1 + 2 * PartyState.NAME_BYTES] = (byte) movement[i];
            packet[PartyState.EQUIP_PACKET_SIZE + i * PartyState.TRAIN_STRIDE] = (byte) 0xff;
        }
        return packet;
    }

    private static PartyState party(int... movement) {
        PartyState state = PartyState.parse(packet(movement));
        assertNotNull(state);
        return state;
    }

    private static boolean[] slowed(int... movement) {
        PartyState state = party(movement);
        boolean[] marks = new boolean[movement.length];
        for (int i = 0; i < movement.length; i++)
            marks[i] = state.slowedByLoad(state.members.get(i));
        return marks;
    }

    @Test public void thePartysBestRateIsTheOneToBeat() {
        assertEquals(9, party(9, 9, 9).quickestMovement());
        assertEquals(12, party(6, 12, 9).quickestMovement());
        assertEquals(4, party(4).quickestMovement());
    }

    @Test public void nobodyIsMarkedWhenEveryoneMovesAlike() {
        for (boolean mark : slowed(9, 9, 9, 9, 9, 9)) assertFalse(mark);
    }

    @Test public void onlyThePersonHaulingTheLootIsMarked() {
        // The case this exists for: five at the normal rate, one behind.
        boolean[] marks = slowed(9, 9, 6, 9, 9, 9);
        for (int i = 0; i < marks.length; i++)
            assertEquals("member " + i, i == 2, marks[i]);
    }

    @Test public void everyoneBehindTheBestIsMarkedNotJustTheWorst() {
        boolean[] marks = slowed(12, 9, 6);
        assertFalse(marks[0]);
        assertTrue(marks[1]);
        assertTrue(marks[2]);
    }

    @Test public void aUniformlyOverloadedPartyIsStillMarked() {
        /*
         * The gap the relative test alone would leave: when everyone is equally
         * slow nobody is behind anybody. At or below the game's own printed
         * floor that is an absolute statement, so the mark stands.
         */
        for (boolean mark : slowed(3, 3, 3)) assertTrue(mark);
        for (boolean mark : slowed(0, 0)) assertTrue(mark);
    }

    @Test public void theFloorIsWhereTheGamePutsItAndNotOneSquareHigher() {
        assertEquals(3, PartyState.SLOWEST_MOVEMENT);
        for (boolean mark : slowed(4, 4)) assertFalse("4 is above the floor", mark);
        for (boolean mark : slowed(3, 3)) assertTrue("3 is the floor", mark);
    }

    @Test public void aLoneMemberIsJudgedAgainstTheFloorAlone() {
        assertFalse("nobody to be behind, and moving freely", slowed(9)[0]);
        assertTrue("nobody to be behind, but at the floor", slowed(2)[0]);
    }

    @Test public void anUnreadableRateIsNeverMarked() {
        // The oldest packet carries no equipment section, so movement reads -1.
        byte[] legacy = new byte[PartyState.PACKET_SIZE];
        System.arraycopy(new byte[] {'P', 'R', 'P', '1'}, 0, legacy, 0, 4);
        legacy[4] = 2;
        for (int i = 0; i < 2; i++) {
            int row = 8 + i * PartyState.ROW_SIZE;
            byte[] name = ("Hero " + (i + 1)).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(name, 0, legacy, row, name.length);
            legacy[row + 16] = 10; legacy[row + 17] = 10;
        }
        PartyState state = PartyState.parse(legacy);
        assertNotNull(state);
        for (PartyState.Member member : state.members) {
            assertFalse(member.loadAvailable());
            assertFalse("an unknown rate must not draw a mark", state.slowedByLoad(member));
        }
        assertEquals(-1, state.quickestMovement());
    }
}
