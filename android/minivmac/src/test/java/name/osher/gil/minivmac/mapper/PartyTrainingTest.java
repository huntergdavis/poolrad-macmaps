package name.osher.gil.minivmac.mapper;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Invented packets only; no private character record appears here. */
public class PartyTrainingTest {
    private byte[] packet(int experience, int[][] classes) {
        byte[] b = new byte[PartyState.TRAIN_PACKET_SIZE];
        b[0]='P';b[1]='R';b[2]='P';b[3]='6';b[4]=1;
        b[8]='A';b[24]=12;b[25]=12;b[26]=0;b[27]=2;
        int block = PartyState.EQUIP_PACKET_SIZE;
        if (classes == null) { b[block]=(byte)255; return b; }
        b[block] = (byte) classes.length;
        writeInt(b, block + 1, experience);
        for (int i = 0; i < classes.length; i++) {
            int at = block + 5 + i * 6;
            b[at] = (byte) classes[i][0]; b[at + 1] = (byte) classes[i][1];
            writeInt(b, at + 2, classes[i][2]);
        }
        return b;
    }
    private void writeInt(byte[] b, int at, int v) {
        b[at]=(byte)(v>>24); b[at+1]=(byte)(v>>16); b[at+2]=(byte)(v>>8); b[at+3]=(byte)v;
    }
    private PartyState.Member member(int xp, int[][] classes) {
        PartyState s = PartyState.parse(packet(xp, classes));
        assertNotNull(s); return s.members.get(0);
    }

    @Test public void experienceAndTheNextThresholdAreBothReported() {
        // Arax as decoded from a real capture: Fighter (slot 2) level 1, 2134 XP.
        PartyState.Member m = member(2134, new int[][]{{2, 1, 2001}});
        assertTrue(m.trainingAvailable());
        assertEquals(2134, m.experience);
        assertEquals("Experience: 2134", m.experienceLabel());
        List<PartyState.Member.Training> held = m.training();
        assertEquals(1, held.size());
        assertEquals("Fighter", held.get(0).className());
        assertEquals(1, held.get(0).level);
        assertEquals(2001, held.get(0).nextThreshold);
        assertTrue(m.readyToTrain());
        assertEquals("Fighter level 1 · 2001 reached, ready to train", m.trainingLabel(held.get(0)));
    }

    @Test public void shortOfTheThresholdReportsTheRemainderNotAPrediction() {
        PartyState.Member m = member(957, new int[][]{{2, 1, 2001}, {5, 1, 2501}});
        assertFalse(m.readyToTrain());
        List<PartyState.Member.Training> held = m.training();
        assertEquals("Fighter level 1 · 1044 more for 2001", m.trainingLabel(held.get(0)));
        assertEquals("Magic-User level 1 · 1544 more for 2501", m.trainingLabel(held.get(1)));
    }

    @Test public void oneEligibleClassIsEnoughToBeReady() {
        PartyState.Member m = member(1600, new int[][]{{2, 1, 2001}, {6, 1, 1251}});
        assertTrue("Thief threshold reached", m.readyToTrain());
        assertEquals("Thief", m.training().get(1).className());
    }

    @Test public void aClassWithNoFurtherLevelIsNeverCalledReady() {
        PartyState.Member m = member(9999999, new int[][]{{2, 9, 0}});
        assertTrue(m.trainingAvailable());
        assertTrue(m.training().get(0).atMaximum());
        assertFalse("A maxed class must not read as trainable", m.readyToTrain());
        assertEquals("Fighter level 9 · no further level in this game",
                m.trainingLabel(m.training().get(0)));
    }

    @Test public void unavailableTrainingIsNeverZeroExperience() {
        PartyState s = PartyState.parse(packet(0, null));
        assertNotNull(s);
        PartyState.Member m = s.members.get(0);
        assertFalse(m.trainingAvailable());
        assertTrue(m.training().isEmpty());
        assertFalse(m.readyToTrain());
        assertEquals("Experience unavailable", m.experienceLabel());
        assertEquals("Health still reads", 12, m.maxHp);
    }

    @Test public void everyClassSlotMapsToItsPrintedName() {
        String[] expected = {"Cleric","Druid","Fighter","Paladin","Ranger","Magic-User","Thief","Monk"};
        for (int slot = 0; slot < expected.length; slot++)
            assertEquals(expected[slot], member(1, new int[][]{{slot, 1, 10}}).training().get(0).className());
    }

    @Test public void malformedTrainingBlocksAreRejected() {
        byte[] good = packet(2134, new int[][]{{2, 1, 2001}});
        assertNotNull(PartyState.parse(good));
        int block = PartyState.EQUIP_PACKET_SIZE;

        for (int held : new int[]{0, 4, 5, 200, 254}) {
            byte[] b = good.clone(); b[block] = (byte) held;
            assertNull("class count " + held + " accepted", PartyState.parse(b));
        }
        byte[] mixed = good.clone(); mixed[block] = (byte) 255;
        assertNull("unavailable carrying data accepted", PartyState.parse(mixed));

        byte[] badSlot = good.clone(); badSlot[block + 5] = 8;
        assertNull("class slot 8 accepted", PartyState.parse(badSlot));
        byte[] zeroLevel = good.clone(); zeroLevel[block + 6] = 0;
        assertNull("level 0 accepted", PartyState.parse(zeroLevel));
        byte[] negativeXp = good.clone(); negativeXp[block + 1] = (byte) 0x80;
        assertNull("negative experience accepted", PartyState.parse(negativeXp));
        byte[] negativeNext = good.clone(); negativeNext[block + 7] = (byte) 0x80;
        assertNull("negative threshold accepted", PartyState.parse(negativeNext));

        byte[] duplicate = packet(100, new int[][]{{2, 1, 10}, {2, 2, 20}});
        assertNull("the same class twice accepted", PartyState.parse(duplicate));

        byte[] trailing = good.clone(); trailing[block + 11] = 1;
        assertNull("data past the declared classes accepted", PartyState.parse(trailing));

        for (int at = block + PartyState.TRAIN_STRIDE; at < good.length; at++) {
            byte[] tail = good.clone(); tail[at] = 1;
            assertNull("unused member block at " + at + " accepted", PartyState.parse(tail));
        }
        for (int length = 0; length < good.length; length++)
            assertNull(PartyState.parse(Arrays.copyOf(good, length)));
        assertNull(PartyState.parse(Arrays.copyOf(good, good.length + 1)));
    }

    @Test public void olderPacketsStayReadableWithTrainingUnavailable() {
        for (byte version : new byte[]{'1', '2', '3', '4', '5'}) {
            int size = version == '5' ? PartyState.EQUIP_PACKET_SIZE
                    : version == '4' ? PartyState.SPELL_PACKET_SIZE
                    : version == '3' ? PartyState.CONDITION_PACKET_SIZE : PartyState.PACKET_SIZE;
            byte[] b = Arrays.copyOf(packet(1, new int[][]{{2, 1, 10}}), size);
            b[3] = version;
            if (version == '1') { b[26] = 0; b[27] = 0; }
            PartyState state = PartyState.parse(b);
            assertNotNull("version " + (char) version + " rejected", state);
            assertFalse(state.members.get(0).trainingAvailable());
            assertEquals("Experience unavailable", state.members.get(0).experienceLabel());
        }
    }

    @Test public void experienceOnlyChangesStillInvalidateTheImmutableDisplay() {
        byte[] b = packet(1000, new int[][]{{2, 1, 2001}});
        PartyState old = PartyState.parse(b);
        writeInt(b, PartyState.EQUIP_PACKET_SIZE + 1, 2500);
        assertFalse("Earning experience must redraw", old.sameDisplay(PartyState.parse(b)));
        assertEquals(1000, old.members.get(0).experience);
    }

    @Test public void membersKeepIndependentTrainingBlocks() {
        byte[] b = packet(2134, new int[][]{{2, 1, 2001}});
        b[4] = 2;
        System.arraycopy(b, 8, b, 28, 20); b[28] = 'B';
        b[PartyState.EQUIP_PACKET_SIZE + PartyState.TRAIN_STRIDE] = (byte) 255;
        PartyState state = PartyState.parse(b);
        assertNotNull(state);
        assertTrue(state.members.get(0).readyToTrain());
        assertFalse(state.members.get(1).trainingAvailable());
    }
}
