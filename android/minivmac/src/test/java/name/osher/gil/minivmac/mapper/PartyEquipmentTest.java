package name.osher.gil.minivmac.mapper;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Invented packets only; no private character or item records appear here. */
public class PartyEquipmentTest {
    private byte[] packet(String weapon, String armor, boolean available) {
        byte[] b = new byte[PartyState.EQUIP_PACKET_SIZE];
        b[0]='P';b[1]='R';b[2]='P';b[3]='5';b[4]=1;
        b[8]='A';b[24]=12;b[25]=12;b[26]=0;b[27]=2;
        int block = PartyState.SPELL_PACKET_SIZE;
        if (!available) { b[block]=(byte)255; load(b, block, 9, 988); return b; }
        write(b, block + 1, weapon);
        write(b, block + 1 + PartyState.NAME_BYTES, armor);
        load(b, block, 9, 988);
        return b;
    }
    private void load(byte[] b, int block, int movement, int weight) {
        int at = block + 1 + 2 * PartyState.NAME_BYTES;
        b[at] = (byte) movement; b[at + 1] = (byte) (weight >> 8); b[at + 2] = (byte) weight;
    }
    private void write(byte[] b, int at, String value) {
        for (int i = 0; i < value.length(); i++) b[at + i] = (byte) value.charAt(i);
    }
    private PartyState.Member member(String weapon, String armor) {
        PartyState state = PartyState.parse(packet(weapon, armor, true));
        assertNotNull(state); return state.members.get(0);
    }

    @Test public void readiedNamesAreReportedAsTheGameNamesThem() {
        PartyState.Member m = member("Long Sword", "Banded Mail");
        assertTrue(m.equipmentAvailable());
        assertEquals("Long Sword", m.readiedWeapon());
        assertEquals("Banded Mail", m.readiedArmor());
        assertEquals("Weapon: Long Sword", m.readiedWeaponLabel());
        assertEquals("Armor: Banded Mail", m.readiedArmorLabel());
    }

    @Test public void anEmptySlotIsEmptyAndNotUnavailable() {
        PartyState.Member m = member("Long Bow", "");
        assertTrue(m.equipmentAvailable());
        assertEquals("", m.readiedArmor());
        assertEquals("No armor readied", m.readiedArmorLabel());
        assertEquals("Weapon: Long Bow", m.readiedWeaponLabel());

        PartyState.Member bare = member("", "");
        assertTrue(bare.equipmentAvailable());
        assertEquals("No weapon readied", bare.readiedWeaponLabel());
        assertEquals("No armor readied", bare.readiedArmorLabel());
    }

    @Test public void unavailableEquipmentNeverReadsAsAnEmptyHand() {
        PartyState state = PartyState.parse(packet(null, null, false));
        assertNotNull(state);
        PartyState.Member m = state.members.get(0);
        assertFalse(m.equipmentAvailable());
        assertNull(m.readiedWeapon());
        assertNull(m.readiedArmor());
        assertEquals("Readied equipment unavailable", m.readiedWeaponLabel());
        assertEquals("Readied equipment unavailable", m.readiedArmorLabel());
        assertEquals("Everything else still reads", 12, m.maxHp);
        assertEquals("Okay", m.conditionLabel());
    }

    @Test public void theLongestNameThatFitsIsAcceptedAndOneMoreIsRejected() {
        String fits = "ABCDEFGHIJKLMNOPQRSTUVWXYZ01234"; // 31 characters
        assertEquals(PartyState.NAME_BYTES - 1, fits.length());
        assertEquals(fits, member(fits, "").readiedWeapon());

        byte[] full = packet("", "", true);
        write(full, PartyState.SPELL_PACKET_SIZE + 1, fits + "5"); // 32, no terminator
        assertNull("An unterminated name must be rejected", PartyState.parse(full));
    }

    @Test public void malformedEquipmentBlocksAreRejected() {
        byte[] good = packet("Flail", "Banded Mail", true);
        assertNotNull(PartyState.parse(good));
        int block = PartyState.SPELL_PACKET_SIZE;

        for (int status = 1; status < 255; status++) {
            byte[] b = good.clone(); b[block] = (byte) status;
            assertNull("status " + status + " accepted", PartyState.parse(b));
        }
        byte[] mixed = good.clone(); mixed[block] = (byte) 255;
        assertNull("unavailable carrying names accepted", PartyState.parse(mixed));

        // Anything after the terminator must be zero.
        byte[] trailing = good.clone();
        trailing[block + 1 + "Flail".length() + 1] = 'X';
        assertNull("bytes past the terminator accepted", PartyState.parse(trailing));

        // Only printable ASCII.
        for (int letter : new int[]{0x01, 0x1f, 0x7f, 0x80, 0xff}) {
            byte[] b = good.clone(); b[block + 1] = (byte) letter;
            assertNull("byte " + letter + " accepted in a name", PartyState.parse(b));
        }

        for (int at = block + PartyState.EQUIP_STRIDE; at < good.length; at++) {
            byte[] tail = good.clone(); tail[at] = 1;
            assertNull("unused member block at " + at + " accepted", PartyState.parse(tail));
        }
        for (int length = 0; length < good.length; length++)
            assertNull(PartyState.parse(Arrays.copyOf(good, length)));
        assertNull(PartyState.parse(Arrays.copyOf(good, good.length + 1)));
    }

    @Test public void movementAndWeightSurviveEvenWhenTheItemsDoNot() {
        PartyState.Member carried = member("Long Sword", "Banded Mail");
        assertTrue(carried.loadAvailable());
        assertEquals(9, carried.movementSquares);
        assertEquals(988, carried.carriedWeight);
        assertEquals("Movement: 9 squares \u00b7 carrying 988", carried.loadLabel());
        assertFalse(carried.slowedToMinimum());

        // The whole point: purged item blocks must not hide the load fields.
        PartyState state = PartyState.parse(packet(null, null, false));
        assertNotNull(state);
        PartyState.Member m = state.members.get(0);
        assertFalse(m.equipmentAvailable());
        assertTrue("Load is a plain record field, not an item block", m.loadAvailable());
        assertEquals(9, m.movementSquares);
        assertEquals(988, m.carriedWeight);
    }

    @Test public void theSlowestPrintedMovementIsFlaggedAndNothingElseIs() {
        for (int squares = 0; squares <= 12; squares++) {
            byte[] b = packet("Flail", "", true);
            load(b, PartyState.SPELL_PACKET_SIZE, squares, 100);
            PartyState.Member m = PartyState.parse(b).members.get(0);
            assertEquals(squares, m.movementSquares);
            assertEquals("squares " + squares, squares <= PartyState.SLOWEST_MOVEMENT, m.slowedToMinimum());
        }
        byte[] heavy = packet("Flail", "", true);
        load(heavy, PartyState.SPELL_PACKET_SIZE, 3, 65535);
        assertEquals(65535, PartyState.parse(heavy).members.get(0).carriedWeight);
    }

    @Test public void olderPacketsStayReadableWithEquipmentUnavailable() {
        for (byte version : new byte[]{'1', '2', '3', '4'}) {
            int size = version == '4' ? PartyState.SPELL_PACKET_SIZE
                    : version == '3' ? PartyState.CONDITION_PACKET_SIZE : PartyState.PACKET_SIZE;
            byte[] b = Arrays.copyOf(packet("", "", true), size);
            b[3] = version;
            if (version == '1') { b[26] = 0; b[27] = 0; }
            PartyState state = PartyState.parse(b);
            assertNotNull("version " + (char) version + " rejected", state);
            PartyState.Member m = state.members.get(0);
            assertFalse(m.equipmentAvailable());
            assertFalse("Older packets carry no load fields", m.loadAvailable());
            assertEquals("Movement and carried weight unavailable", m.loadLabel());
            assertEquals("Readied equipment unavailable", m.readiedWeaponLabel());
        }
    }

    @Test public void equipmentOnlyChangesStillInvalidateTheImmutableDisplay() {
        byte[] b = packet("Long Sword", "Banded Mail", true);
        PartyState old = PartyState.parse(b);
        b[PartyState.SPELL_PACKET_SIZE + 1] = 'F';
        assertFalse("Readying another weapon must redraw", old.sameDisplay(PartyState.parse(b)));
        assertEquals("Long Sword", old.members.get(0).readiedWeapon());
    }

    @Test public void membersKeepIndependentEquipmentBlocks() {
        byte[] b = packet("Long Sword", "Banded Mail", true);
        b[4] = 2;
        System.arraycopy(b, 8, b, 28, 20); b[28] = 'B';
        b[PartyState.SPELL_PACKET_SIZE + PartyState.EQUIP_STRIDE] = (byte) 255;
        PartyState state = PartyState.parse(b);
        assertNotNull(state);
        assertEquals("Long Sword", state.members.get(0).readiedWeapon());
        assertFalse("One unreadable member must not blank another",
                state.members.get(1).equipmentAvailable());
    }
}
