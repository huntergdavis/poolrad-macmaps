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

    /**
     * A PRP7 packet: everything PRP6 carries, plus one quick byte per member.
     * The later sections have to be filled in properly -- 0xff meaning
     * "unavailable" -- or the parser rejects the packet before ever reaching
     * the quick bytes, which is how the first version of this fixture failed.
     */
    private static byte[] quickPacket(int count, int... flags) {
        byte[] packet = new byte[PartyState.QUICK_PACKET_SIZE];
        System.arraycopy(new byte[] {'P', 'R', 'P', '7'}, 0, packet, 0, 4);
        packet[4] = (byte) count;
        for (int i = 0; i < count; i++) {
            int row = 8 + i * PartyState.ROW_SIZE;
            byte[] name = ("Hero " + (i + 1)).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(name, 0, packet, row, name.length);
            packet[row + 16] = (byte) (10 + i);
            packet[row + 17] = (byte) (10 + i);
            packet[row + 18] = (byte) 0x80;   // armour class unavailable
            packet[row + 19] = (byte) 0xff;   // class unavailable
            packet[PartyState.PACKET_SIZE + i * 2] = (byte) 0xff;       // condition
            packet[PartyState.PACKET_SIZE + i * 2 + 1] = (byte) 0xff;   // effects
            packet[PartyState.CONDITION_PACKET_SIZE + i * PartyState.SPELL_STRIDE] = (byte) 0xff;
            packet[PartyState.SPELL_PACKET_SIZE + i * PartyState.EQUIP_STRIDE] = (byte) 0xff;
            packet[PartyState.EQUIP_PACKET_SIZE + i * PartyState.TRAIN_STRIDE] = (byte) 0xff;
            packet[PartyState.TRAIN_PACKET_SIZE + i] = (byte) (i < flags.length ? flags[i] : 0);
        }
        return packet;
    }

    @Test public void npcMaskFollowsEachEmittedRowAndIsBounded() {
        for (int count=1; count<=8; count++) for (int mask=0; mask<256; mask++) {
            byte[] sample=quickPacket(count); sample[3]='9'; sample[6]=(byte)mask; sample[5]=(byte)count;
            PartyState party=PartyState.parse(sample);
            if (mask >= (1 << count)) { assertNull(party); continue; }
            assertNotNull(party); assertEquals(count-1,party.selectedIndex);
            for (int i=0; i<count; i++) {
                PartyState.Member member=party.members.get(i);
                assertEquals(Boolean.valueOf((mask & (1 << i)) != 0), member.npc);
                assertEquals("Hero "+(i+1), member.name);
                assertEquals((member.npc ? "NPC · " : "")+member.name, member.displayName());
            }
        }
    }

    @Test public void legacyNpcStatusStaysUnknownAndReservedBytesAreRejected() {
        assertNull(PartyState.parse(packet(2)).members.get(0).npc);
        byte[] sample=quickPacket(2);
        assertNull(PartyState.parse(sample).members.get(0).npc);
        sample[3]='8'; sample[5]=1;
        assertNull(PartyState.parse(sample).members.get(0).npc);
        sample[6]=1; assertNull(PartyState.parse(sample));
        sample[3]='9'; assertNotNull(PartyState.parse(sample));
        sample[7]=1; assertNull(PartyState.parse(sample));
    }

    @Test public void npcOnlyChangesRedrawWithoutChangingTheCharacterIdentity() {
        byte[] sample=quickPacket(2); sample[3]='9';
        PartyState player=PartyState.parse(sample);
        sample[6]=1; PartyState npc=PartyState.parse(sample);
        assertFalse(player.sameDisplay(npc));
        assertEquals(player.members.get(0).name,npc.members.get(0).name);
        assertEquals("Player character",player.members.get(0).characterKindLabel());
        assertEquals("NPC companion",npc.members.get(0).characterKindLabel());
        sample[6]=0; assertEquals(Boolean.TRUE,npc.members.get(0).npc);
        assertEquals("NPC status unavailable",PartyState.parse(packet(1)).members.get(0).characterKindLabel());
    }

    @Test public void theQuickFlagIsReadPerMember() {
        PartyState party = PartyState.parse(quickPacket(3, 1, 0, 1));
        assertNotNull(party);
        assertEquals(Boolean.TRUE, party.members.get(0).quick);
        assertEquals(Boolean.FALSE, party.members.get(1).quick);
        assertEquals(Boolean.TRUE, party.members.get(2).quick);
    }

    @Test public void selectionIsBoundedAndLegacyPacketsHaveNone() {
        assertEquals(-1, PartyState.parse(quickPacket(3)).selectedIndex);
        assertEquals(-1, PartyState.parse(packet(3)).selectedIndex);
        byte[] sample = quickPacket(3); sample[3] = '8';
        for (int value = 0; value <= 255; value++) {
            sample[5] = (byte) value;
            PartyState parsed = PartyState.parse(sample);
            if (value <= 3) {
                assertNotNull(parsed); assertEquals(value - 1, parsed.selectedIndex);
            } else assertNull(parsed);
        }
        sample[5] = 1;
        sample[3] = '7'; assertNull(PartyState.parse(sample));
    }

    @Test public void selectionChangesTriggerARedrawAndAreImmutable() {
        byte[] sample = quickPacket(3); sample[3] = '8'; sample[5] = 1;
        PartyState first = PartyState.parse(sample);
        assertTrue(first.sameDisplay(PartyState.parse(sample.clone())));
        sample[5] = 3;
        assertEquals(0, first.selectedIndex);
        assertFalse(first.sameDisplay(PartyState.parse(sample)));
        sample[5] = 0;
        assertEquals(-1, PartyState.parse(sample).selectedIndex);
        assertFalse(first.sameDisplay(PartyState.parse(sample)));
    }

    @Test public void anUnreadableQuickFlagIsUnknownRatherThanOff() {
        // The probe sends 0xff when the byte holds a value the field is not
        // allowed to have. That must never be drawn as a confident "off".
        for (int odd : new int[]{0xff, 2, 0x80, 0x7f}) {
            PartyState party = PartyState.parse(quickPacket(1, odd));
            assertNotNull("value " + odd, party);
            assertNull("value " + odd, party.members.get(0).quick);
        }
    }

    @Test public void olderPacketsCarryNoQuickFlagAtAll() {
        assertNull(PartyState.parse(packet(1)).members.get(0).quick);
        assertNull(PartyState.parse(detailedPacket(1)).members.get(0).quick);
    }

    @Test public void theQuickFlagChangesTheDisplay() {
        // The pane redraws off packet equality, so a flag that flips has to
        // count as a different display or the Q would never repaint.
        PartyState off = PartyState.parse(quickPacket(1, 0));
        PartyState on = PartyState.parse(quickPacket(1, 1));
        assertFalse(off.sameDisplay(on));
        assertTrue(off.sameDisplay(PartyState.parse(quickPacket(1, 0))));
    }

    @Test public void aQuickPacketOfTheWrongLengthIsRefused() {
        byte[] good = quickPacket(2, 1, 0);
        assertNotNull(PartyState.parse(good));
        assertNull(PartyState.parse(Arrays.copyOf(good, good.length - 1)));
        assertNull(PartyState.parse(Arrays.copyOf(good, good.length + 1)));
        // Unused members' quick bytes must be zero, like every other tail field.
        byte[] dirty = quickPacket(2, 1, 0);
        dirty[PartyState.TRAIN_PACKET_SIZE + 5] = 1;
        assertNull(PartyState.parse(dirty));
    }

    private static byte[] detailedPacket(int count) {
        byte[] packet = packet(count);
        packet[3] = '2';
        for (int i = 0; i < count; i++) {
            int row = 8 + i * PartyState.ROW_SIZE;
            packet[row + 18] = (byte) 0x80;
            packet[row + 19] = (byte) 0xff;
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
        for (int offset = 0; offset < 3; offset++) {
            byte[] broken = valid.clone(); broken[offset]++;
            assertNull(PartyState.parse(broken));
        }
        for (int version : new int[] {0, '0', '3', 255}) {
            byte[] broken = valid.clone(); broken[3] = (byte) version;
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

    @Test public void legacyReservedZerosDoNotInventArmorClassOrClericIdentity() {
        for (PartyState.Member member : PartyState.parse(packet(8)).members) {
            assertNull(member.armorClass);
            assertEquals(-1, member.characterClass);
            assertEquals("Class unavailable", member.classLabel());
        }
    }

    @Test public void detailedPacketDecodesSignedArmorClassesAndAllEightRows() {
        byte[] packet = detailedPacket(8);
        int[] ac = {0, -1, 60, -127, -10, 10, -128, 3};
        int[] classes = {0, 5, 17, 15, 1, 7, 255, 13};
        for (int i = 0; i < ac.length; i++) {
            packet[8 + i * PartyState.ROW_SIZE + 18] = (byte) ac[i];
            packet[8 + i * PartyState.ROW_SIZE + 19] = (byte) classes[i];
        }
        PartyState state = PartyState.parse(packet);
        assertNotNull(state);
        assertEquals(8, state.members.size());
        for (int i = 0; i < ac.length; i++) {
            PartyState.Member member = state.members.get(i);
            assertEquals(ac[i] == -128 ? null : Integer.valueOf(ac[i]), member.armorClass);
            assertEquals(classes[i] == 255 ? -1 : classes[i], member.characterClass);
            assertEquals("Hero " + (i + 1), member.name);
            assertEquals(10 + i, member.currentHp);
        }
    }

    @Test public void classLabelsMatchTheOriginalMacTableIncludingMulticlasses() {
        String[] expected = {"Cleric", "Druid", "Fighter", "Paladin", "Ranger", "Magic-User", "Thief", "Monk",
                "Cleric/Fighter", "Cleric/Fighter/Magic-User", "Cleric/Ranger", "Cleric/Magic-User",
                "Cleric/Thief", "Fighter/Magic-User", "Fighter/Thief", "Fighter/Magic-User/Thief",
                "Magic-User/Thief", "Monster"};
        for (int id = 0; id < expected.length; id++) {
            byte[] packet = detailedPacket(1); packet[27] = (byte) id;
            PartyState.Member member = PartyState.parse(packet).members.get(0);
            assertEquals(id, member.characterClass);
            assertEquals(expected[id], member.classLabel());
        }
    }

    @Test public void unknownDetailsAreIndependentAndDoNotHideValidHealth() {
        byte[] packet = detailedPacket(1);
        PartyState.Member unknown = PartyState.parse(packet).members.get(0);
        assertNull(unknown.armorClass);
        assertEquals(-1, unknown.characterClass);
        assertEquals("Class unavailable", unknown.classLabel());
        assertEquals(10, unknown.currentHp);
        packet[26] = 0;
        PartyState.Member onlyAc = PartyState.parse(packet).members.get(0);
        assertEquals(Integer.valueOf(0), onlyAc.armorClass);
        assertEquals(-1, onlyAc.characterClass);
        packet[26] = (byte) 0x80; packet[27] = 0;
        PartyState.Member onlyClass = PartyState.parse(packet).members.get(0);
        assertNull(onlyClass.armorClass);
        assertEquals(0, onlyClass.characterClass);
        assertEquals("Cleric", onlyClass.classLabel());
    }

    @Test public void detailedDisplayDetectsAcClassAndAvailabilityChanges() {
        byte[] packet = detailedPacket(1); packet[26] = -1; packet[27] = 2;
        PartyState first = PartyState.parse(packet);
        assertTrue(first.sameDisplay(PartyState.parse(packet.clone())));
        byte[] ac = packet.clone(); ac[26] = 0;
        byte[] characterClass = packet.clone(); characterClass[27] = 5;
        assertFalse(first.sameDisplay(PartyState.parse(ac)));
        assertFalse(first.sameDisplay(PartyState.parse(characterClass)));
        assertFalse(first.sameDisplay(PartyState.parse(detailedPacket(1))));
        packet[26] = 3; packet[27] = 6;
        assertEquals(Integer.valueOf(-1), first.members.get(0).armorClass);
        assertEquals("Fighter", first.members.get(0).classLabel());
    }

    @Test public void reorderedDetailedRowsKeepAcAndClassWithTheirMember() {
        byte[] packet = detailedPacket(2);
        packet[26] = -1; packet[27] = 2;
        packet[46] = 3; packet[47] = 5;
        PartyState original = PartyState.parse(packet);
        byte[] first = Arrays.copyOfRange(packet, 8, 28);
        System.arraycopy(packet, 28, packet, 8, PartyState.ROW_SIZE);
        System.arraycopy(first, 0, packet, 28, PartyState.ROW_SIZE);
        PartyState reordered = PartyState.parse(packet);
        assertFalse(original.sameDisplay(reordered));
        assertEquals("Hero 2", reordered.members.get(0).name);
        assertEquals(Integer.valueOf(3), reordered.members.get(0).armorClass);
        assertEquals("Magic-User", reordered.members.get(0).classLabel());
        assertEquals("Hero 1", reordered.members.get(1).name);
        assertEquals(Integer.valueOf(-1), reordered.members.get(1).armorClass);
        assertEquals("Fighter", reordered.members.get(1).classLabel());
    }

    @Test public void rejectsDetailedWireValuesOutsideVerifiedRanges() {
        for (int ac : new int[]{61, 100, 127}) {
            byte[] broken = detailedPacket(1); broken[26] = (byte) ac;
            assertNull("AC " + ac + " is not in the PRP2 contract", PartyState.parse(broken));
        }
        for (int id : new int[]{18, 19, 127, 128, 254}) {
            byte[] broken = detailedPacket(1); broken[27] = (byte) id;
            assertNull("Class " + id + " must be normalized to unavailable by the native reader", PartyState.parse(broken));
        }
    }

    @Test public void detailedPacketsKeepStrictHeaderUnusedRowsAndNameValidation() {
        byte[] valid = detailedPacket(1);
        for (int offset : new int[]{5, 6, 7, 23, 28, 46, 47, PartyState.PACKET_SIZE - 1}) {
            byte[] broken = valid.clone(); broken[offset] = 1;
            assertNull("Invalid detailed padding/header byte " + offset, PartyState.parse(broken));
        }
        byte[] broken = valid.clone(); Arrays.fill(broken, 8, 24, (byte) 'A');
        assertNull(PartyState.parse(broken));
        broken = valid.clone(); broken[8] = 31;
        assertNull(PartyState.parse(broken));
        assertNull(PartyState.parse(Arrays.copyOf(valid, valid.length - 1)));
        assertNull(PartyState.parse(Arrays.copyOf(valid, valid.length + 1)));
    }

    @Test public void detailedHpKeepsUnsignedZeroAndInvalidHealthSemantics() {
        byte[] packet = detailedPacket(1); packet[25] = (byte) 255; packet[24] = (byte) 200;
        PartyState.Member member = PartyState.parse(packet).members.get(0);
        assertEquals(200, member.currentHp); assertEquals(255, member.maxHp);
        packet[24] = 0; assertEquals(0f, PartyState.parse(packet).members.get(0).healthFraction(), 0f);
        packet[25] = 0; assertNull(PartyState.parse(packet));
        packet[25] = 3; packet[24] = 4; assertNull(PartyState.parse(packet));
    }
}
