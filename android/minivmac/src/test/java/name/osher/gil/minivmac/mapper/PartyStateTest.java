package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

public class PartyStateTest {
    private static byte[] packet(int count) {
        byte[] packet = new byte[PartyState.PACKET_SIZE];
        System.arraycopy(new byte[] {'P', 'R', 'P', '1'}, 0, packet, 0, 4);
        packet[4] = (byte) count;
        for (int i = 0; i < count; i++) {
            int row = 8 + i * PartyState.ROW_SIZE;
            byte[] name = ("Hero " + (i + 1)).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(name, 0, packet, row, name.length);
            packet[row + 16] = (byte) (10 + i);
            packet[row + 17] = (byte) (10 + i);
        }
        return packet;
    }

    @Test public void decodesOrderedPartyWithoutAssumingNamesOrSixMembers() {
        for (int count = 1; count <= 8; count++) {
            PartyState state = PartyState.parse(packet(count));
            assertNotNull(state);
            assertEquals(count, state.members.size());
            assertSame(state.members, state.members());
            for (int i = 0; i < count; i++) {
                PartyState.Member member = state.members.get(i);
                assertEquals("Hero " + (i + 1), member.name);
                assertEquals(10 + i, member.currentHp);
                assertEquals(10 + i, member.maxHp);
                assertEquals(1f, member.healthFraction(), 0f);
            }
        }
    }

    @Test public void damageHealingAndZeroChangeOnlyCurrentHealth() {
        byte[] packet = packet(1);
        for (int hp : new int[] {3, 10, 0}) {
            packet[24] = (byte) hp;
            PartyState.Member member = PartyState.parse(packet).members.get(0);
            assertEquals(hp, member.currentHp);
            assertEquals(10, member.maxHp);
            assertEquals(hp / 10f, member.healthFraction(), 0f);
        }
    }

    @Test public void hpBytesAreUnsignedAsInMacintoshFormatter() {
        byte[] packet = packet(1);
        packet[24] = (byte) 200;
        packet[25] = (byte) 255;
        PartyState.Member member = PartyState.parse(packet).members.get(0);
        assertEquals(200, member.currentHp);
        assertEquals(255, member.maxHp);
        packet[24] = (byte) 255;
        assertEquals(1f, PartyState.parse(packet).members.get(0).healthFraction(), 0f);
    }

    @Test public void decodesMacRomanNamesAndMaximumNameLength() {
        byte[] packet = packet(1);
        Arrays.fill(packet, 8, 24, (byte) 0);
        packet[8] = (byte) 0x8e; packet[9] = 'l'; packet[10] = 'f';
        assertEquals("\u00e9lf", PartyState.parse(packet).members.get(0).name);
        Arrays.fill(packet, 8, 23, (byte) 'A');
        assertEquals("AAAAAAAAAAAAAAA", PartyState.parse(packet).members.get(0).name);
    }

    @Test public void capturesImmutableSnapshot() {
        byte[] packet = packet(1);
        PartyState state = PartyState.parse(packet);
        PartyState same = PartyState.parse(packet.clone());
        packet[8] = 'X'; packet[24] = 3;
        assertEquals("Hero 1", state.members.get(0).name);
        assertEquals(10, state.members.get(0).currentHp);
        assertTrue(state.sameDisplay(same));
        assertFalse(state.sameDisplay(PartyState.parse(packet)));
        try { state.members.clear(); fail("Party list must be immutable"); }
        catch (UnsupportedOperationException expected) { /* immutable */ }
    }

    @Test public void displayComparisonDetectsOrderJoinLeaveAndMaxChanges() {
        byte[] packet = packet(2);
        PartyState initial = PartyState.parse(packet);
        assertTrue(initial.sameDisplay(PartyState.parse(packet.clone())));
        assertFalse(initial.sameDisplay(null));
        assertFalse(initial.sameDisplay(PartyState.parse(packet(1))));
        assertFalse(initial.sameDisplay(PartyState.parse(packet(3))));
        byte[] first = Arrays.copyOfRange(packet, 8, 28);
        System.arraycopy(packet, 28, packet, 8, 20);
        System.arraycopy(first, 0, packet, 28, 20);
        PartyState reordered = PartyState.parse(packet);
        assertFalse(initial.sameDisplay(reordered));
        assertEquals("Hero 2", reordered.members.get(0).name);
        packet = packet(2); packet[25] = 11;
        assertFalse(initial.sameDisplay(PartyState.parse(packet)));
    }

    @Test public void rejectsWrongLengthMagicAndFutureVersion() {
        assertNull(PartyState.parse(null));
        assertNull(PartyState.parse(new byte[0]));
        byte[] valid = packet(1);
        assertNull(PartyState.parse(Arrays.copyOf(valid, valid.length - 1)));
        assertNull(PartyState.parse(Arrays.copyOf(valid, valid.length + 1)));
        for (int offset = 0; offset < 4; offset++) {
            byte[] broken = valid.clone(); broken[offset]++;
            assertNull(PartyState.parse(broken));
        }
    }

    @Test public void rejectsEmptyOrExcessivePartyAndReservedHeader() {
        for (int count : new int[] {0, 9, 255}) {
            byte[] broken = packet(1); broken[4] = (byte) count;
            assertNull(PartyState.parse(broken));
        }
        for (int offset = 5; offset < 8; offset++) {
            byte[] broken = packet(1); broken[offset] = 1;
            assertNull(PartyState.parse(broken));
        }
    }

    @Test public void rejectsReservedAndUnusedRowBytes() {
        for (int offset = 26; offset < PartyState.PACKET_SIZE; offset++) {
            byte[] broken = packet(1); broken[offset] = 1;
            assertNull("Unexpected nonzero byte " + offset, PartyState.parse(broken));
        }
    }

    @Test public void rejectsInvalidHealthInsteadOfClampingOrInventingStatus() {
        byte[] broken = packet(1); broken[25] = 0;
        assertNull(PartyState.parse(broken));
        broken[24] = 0;
        assertNull(PartyState.parse(broken));
        broken = packet(1); broken[24] = 11;
        assertNull(PartyState.parse(broken));
        broken[24] = (byte) 255;
        assertNull(PartyState.parse(broken));
    }

    @Test public void rejectsBlankUnterminatedControlAndNonzeroPaddedNames() {
        byte[] broken = packet(1);
        Arrays.fill(broken, 8, 24, (byte) 0);
        assertNull(PartyState.parse(broken));
        Arrays.fill(broken, 8, 23, (byte) ' ');
        assertNull(PartyState.parse(broken));
        Arrays.fill(broken, 8, 24, (byte) 'A');
        assertNull(PartyState.parse(broken));
        for (int letter : new int[] {1, 10, 27, 31, 127}) {
            broken = packet(1); broken[8] = (byte) letter;
            assertNull(PartyState.parse(broken));
        }
        broken = packet(1); broken[23] = 'X';
        assertNull(PartyState.parse(broken));
    }
}
