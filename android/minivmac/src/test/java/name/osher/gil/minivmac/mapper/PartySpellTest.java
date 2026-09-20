package name.osher.gil.minivmac.mapper;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Invented packets only; nothing here comes from a private character record. */
public class PartySpellTest {
    private byte[] packet(int[] ready, int[] awaiting, boolean available) {
        byte[] b = new byte[PartyState.SPELL_PACKET_SIZE];
        b[0]='P';b[1]='R';b[2]='P';b[3]='4';b[4]=1;
        b[8]='A';b[24]=12;b[25]=12;b[26]=0;b[27]=2;
        int block = PartyState.CONDITION_PACKET_SIZE;
        if (!available) { b[block]=(byte)255; return b; }
        for (int i = 0; i < 3; i++) { b[block+1+i]=(byte)ready[i]; b[block+4+i]=(byte)awaiting[i]; }
        return b;
    }
    private PartyState.Member member(int[] ready, int[] awaiting) {
        PartyState state = PartyState.parse(packet(ready, awaiting, true));
        assertNotNull(state); return state.members.get(0);
    }

    @Test public void readyAndAwaitingCountsAreReportedPerLevel() {
        PartyState.Member m = member(new int[]{2,1,0}, new int[]{0,0,3});
        assertTrue(m.spellsAvailable());
        assertEquals(2, m.spellsReady(1));
        assertEquals(1, m.spellsReady(2));
        assertEquals(0, m.spellsReady(3));
        assertEquals(3, m.spellsAwaitingRest(3));
        assertEquals(3, m.spellsReadyTotal());
        assertEquals(3, m.spellsAwaitingRestTotal());
        assertTrue(m.restWouldMemorize());
        assertTrue("the reminder must name the path that actually memorizes",
                PartyState.Member.REST_REMINDER.contains("Magic \u2192 Rest"));
        assertTrue(PartyState.Member.REST_REMINDER.contains("leaving camp forgets"));
        assertEquals("Ready to cast: level 1 × 2, level 2 × 1", m.spellsReadyLabel());
        assertEquals("Awaiting rest: level 3 × 3", m.spellsAwaitingRestLabel());
    }

    @Test public void aCharacterWithNoMemorizedSpellsSaysSoRatherThanUnavailable() {
        PartyState.Member m = member(new int[]{0,0,0}, new int[]{0,0,0});
        assertTrue(m.spellsAvailable());
        assertEquals(0, m.spellsReadyTotal());
        assertFalse("Nothing to rest for means no reminder", m.restWouldMemorize());
        assertEquals("No spells ready to cast", m.spellsReadyLabel());
        assertEquals("Nothing waiting on rest", m.spellsAwaitingRestLabel());
    }

    @Test public void unavailableSpellDataIsNeverPresentedAsZeroSpells() {
        PartyState state = PartyState.parse(packet(null, null, false));
        assertNotNull(state);
        PartyState.Member m = state.members.get(0);
        assertFalse(m.spellsAvailable());
        assertEquals(0, m.spellsReady(1));
        assertFalse(m.restWouldMemorize());
        assertEquals("Spell readiness unavailable", m.spellsReadyLabel());
        assertEquals("Spell readiness unavailable", m.spellsAwaitingRestLabel());
        assertEquals("Health still reads normally", 12, m.maxHp);
        assertEquals("Okay", m.conditionLabel());
    }

    @Test public void levelsOutsideOneToThreeAreNeverInvented() {
        PartyState.Member m = member(new int[]{1,1,1}, new int[]{1,1,1});
        for (int level : new int[]{-1, 0, 4, 99}) {
            assertEquals(0, m.spellsReady(level));
            assertEquals(0, m.spellsAwaitingRest(level));
        }
        assertEquals(3, m.spellsReadyTotal());
    }

    @Test public void malformedSpellBlocksAreRejected() {
        byte[] good = packet(new int[]{1,0,0}, new int[]{0,1,0}, true);
        assertNotNull(PartyState.parse(good));
        int block = PartyState.CONDITION_PACKET_SIZE;

        for (int status = 1; status < 255; status++) {
            byte[] b = good.clone(); b[block] = (byte) status;
            assertNull("status " + status + " accepted", PartyState.parse(b));
        }
        byte[] reserved = good.clone(); reserved[block + 7] = 1;
        assertNull("reserved byte accepted", PartyState.parse(reserved));

        // Unavailable must carry no counts at all.
        byte[] mixed = new byte[good.length];
        System.arraycopy(good, 0, mixed, 0, good.length);
        mixed[block] = (byte) 255;
        assertNull("unavailable with counts accepted", PartyState.parse(mixed));

        // The game's array holds 21 slots; more than that is malformed.
        byte[] tooMany = good.clone();
        tooMany[block + 1] = 11; tooMany[block + 2] = 11;
        tooMany[block + 4] = 0; tooMany[block + 5] = 0;
        assertNull("22 memorized spells accepted", PartyState.parse(tooMany));
        byte[] exactly = good.clone();
        exactly[block + 1] = 11; exactly[block + 2] = 10;
        exactly[block + 4] = 0; exactly[block + 5] = 0;
        assertNotNull("21 memorized spells rejected", PartyState.parse(exactly));

        for (int at = PartyState.CONDITION_PACKET_SIZE + PartyState.SPELL_STRIDE; at < good.length; at++) {
            byte[] tail = good.clone(); tail[at] = 1;
            assertNull("unused member block at " + at + " accepted", PartyState.parse(tail));
        }
        for (int length = 0; length < good.length; length++)
            assertNull(PartyState.parse(Arrays.copyOf(good, length)));
        assertNull(PartyState.parse(Arrays.copyOf(good, good.length + 1)));
    }

    @Test public void olderPacketsStayReadableWithSpellsUnavailable() {
        for (byte version : new byte[]{'1', '2', '3'}) {
            int size = version == '3' ? PartyState.CONDITION_PACKET_SIZE : PartyState.PACKET_SIZE;
            byte[] b = Arrays.copyOf(packet(new int[]{0,0,0}, new int[]{0,0,0}, true), size);
            b[3] = version;
            if (version == '1') { b[26] = 0; b[27] = 0; }
            PartyState state = PartyState.parse(b);
            assertNotNull("version " + (char) version + " rejected", state);
            PartyState.Member m = state.members.get(0);
            assertFalse(m.spellsAvailable());
            assertEquals("Spell readiness unavailable", m.spellsReadyLabel());
            assertFalse(m.restWouldMemorize());
        }
    }

    @Test public void spellOnlyChangesStillInvalidateTheImmutableDisplay() {
        byte[] b = packet(new int[]{1,0,0}, new int[]{0,0,0}, true);
        PartyState old = PartyState.parse(b);
        int block = PartyState.CONDITION_PACKET_SIZE;
        b[block + 1] = 0; b[block + 4] = 1;
        assertFalse("Casting or resting must redraw", old.sameDisplay(PartyState.parse(b)));
        assertEquals("The old snapshot keeps its own values", 1, old.members.get(0).spellsReady(1));
    }

    @Test public void membersKeepIndependentSpellBlocks() {
        byte[] b = packet(new int[]{2,0,0}, new int[]{0,0,0}, true);
        b[4] = 2;
        System.arraycopy(b, 8, b, 28, 20); b[28] = 'B';
        int second = PartyState.CONDITION_PACKET_SIZE + PartyState.SPELL_STRIDE;
        b[second] = (byte) 255;
        PartyState state = PartyState.parse(b);
        assertNotNull(state);
        assertTrue(state.members.get(0).spellsAvailable());
        assertEquals(2, state.members.get(0).spellsReady(1));
        assertFalse("One unreadable member must not blank the others",
                state.members.get(1).spellsAvailable());
    }
}
