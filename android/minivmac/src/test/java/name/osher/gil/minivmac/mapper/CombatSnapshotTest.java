package name.osher.gil.minivmac.mapper;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Invented packets only; no private capture appears here. */
public class CombatSnapshotTest {
    private byte[] packet(int[][] rows) {
        byte[] b = new byte[CombatSnapshot.PACKET_SIZE];
        b[0]='P'; b[1]='R'; b[2]='C'; b[3]='1'; b[4]=1; b[5]=(byte) rows.length;
        for (int i = 0; i < rows.length; i++) {
            b[8+i*4] = (byte) rows[i][0];
            b[8+i*4+1] = (byte) rows[i][1];
            b[8+i*4+2] = (byte) rows[i][2];
        }
        return b;
    }
    private byte[] unavailable() {
        byte[] b = new byte[CombatSnapshot.PACKET_SIZE];
        b[0]='P'; b[1]='R'; b[2]='C'; b[3]='1'; b[4]=(byte)255;
        return b;
    }
    private static final int[][] BATTLE = {
        {1, 22, 12}, {1, 27, 14}, {1, 30, 13}, {1, 29, 12}, {1, 31, 14}, {1, 20, 12},
        {2, 21, 13}, {2, 19, 11}, {2, 22, 14}, {2, 19, 12},
    };

    @Test public void everySquareTheGameDrewIsReadBack() {
        CombatSnapshot snapshot = CombatSnapshot.parse(packet(BATTLE));
        assertNotNull(snapshot);
        assertEquals(10, snapshot.size());
        assertEquals(6, snapshot.partyCount());
        assertTrue(snapshot.spots().get(0).party);
        assertFalse(snapshot.spots().get(6).party);
        assertEquals(22, snapshot.spots().get(0).x);
        assertEquals(12, snapshot.spots().get(0).y);
    }

    @Test public void theOccupiedAreaIsTheBoundsOfWhatWasRead() {
        CombatSnapshot snapshot = CombatSnapshot.parse(packet(BATTLE));
        assertEquals(19, snapshot.left);
        assertEquals(31, snapshot.right);
        assertEquals(11, snapshot.top);
        assertEquals(14, snapshot.bottom);
        assertEquals(13, snapshot.width());
        assertEquals(4, snapshot.height());
    }

    @Test public void aSingleCombatantIsAOneSquareArea() {
        CombatSnapshot snapshot = CombatSnapshot.parse(packet(new int[][]{{1, 5, 9}}));
        assertNotNull(snapshot);
        assertEquals(1, snapshot.width());
        assertEquals(1, snapshot.height());
        assertEquals(5, snapshot.left);
        assertEquals(9, snapshot.top);
    }

    @Test public void noBattleIsNotAnEmptyBattlefield() {
        assertNull(CombatSnapshot.parse(unavailable()));
        assertNull(CombatSnapshot.parse(null));
    }

    @Test public void theSummaryCountsBothSidesWithoutAdvice() {
        assertEquals("6 of yours · 4 others", CombatSnapshot.parse(packet(BATTLE)).summary());
        assertEquals("1 of yours · 0 others",
                CombatSnapshot.parse(packet(new int[][]{{1, 1, 1}})).summary());
        assertEquals("0 of yours · 1 other",
                CombatSnapshot.parse(packet(new int[][]{{2, 1, 1}})).summary());
    }

    @Test public void theCeilingIsAcceptedAndOneMoreIsNot() {
        assertNotNull(CombatSnapshot.parse(packet(new int[][]{{1, 63, 63}})));
        for (int axis = 1; axis <= 2; axis++) {
            byte[] b = packet(new int[][]{{1, 5, 5}});
            b[8 + axis] = 64;
            assertNull("coordinate 64 accepted", CombatSnapshot.parse(b));
        }
        int[][] full = new int[CombatSnapshot.MAX_COMBATANTS][];
        for (int i = 0; i < full.length; i++) full[i] = new int[]{i < 6 ? 1 : 2, i % 64, (i * 3) % 64};
        assertEquals(CombatSnapshot.MAX_COMBATANTS, CombatSnapshot.parse(packet(full)).size());
    }

    @Test public void malformedPacketsAreRejected() {
        byte[] good = packet(BATTLE);
        assertNotNull(CombatSnapshot.parse(good));
        for (int at = 0; at < 4; at++) {
            byte[] b = good.clone(); b[at] = 'X';
            assertNull(CombatSnapshot.parse(b));
        }
        for (int status : new int[]{0, 2, 3, 254}) {
            byte[] b = good.clone(); b[4] = (byte) status;
            assertNull("status " + status + " accepted", CombatSnapshot.parse(b));
        }
        byte[] zeroCount = good.clone(); zeroCount[5] = 0;
        assertNull("a present packet with no combatants accepted", CombatSnapshot.parse(zeroCount));
        byte[] tooMany = good.clone(); tooMany[5] = (byte) (CombatSnapshot.MAX_COMBATANTS + 1);
        assertNull(CombatSnapshot.parse(tooMany));

        for (int at : new int[]{6, 7}) {
            byte[] b = good.clone(); b[at] = 1;
            assertNull("reserved byte " + at + " accepted", CombatSnapshot.parse(b));
        }
        for (int kind : new int[]{0, 3, 255}) {
            byte[] b = good.clone(); b[8 + 4 * 4] = (byte) kind;
            assertNull("kind " + kind + " accepted", CombatSnapshot.parse(b));
        }
        byte[] padded = good.clone(); padded[8 + 2 * 4 + 3] = 1;
        assertNull("a used reserved byte inside a row accepted", CombatSnapshot.parse(padded));
        byte[] trailing = good.clone(); trailing[8 + BATTLE.length * 4] = 1;
        assertNull("a row past the count accepted", CombatSnapshot.parse(trailing));

        byte[] mixed = good.clone(); mixed[4] = (byte) 255;
        assertNull("unavailable carrying squares accepted", CombatSnapshot.parse(mixed));

        for (int length : new int[]{0, 7, CombatSnapshot.PACKET_SIZE - 1, CombatSnapshot.PACKET_SIZE + 1})
            assertNull(CombatSnapshot.parse(Arrays.copyOf(good, length)));
    }

    @Test public void theSnapshotOwnsItsSquaresAfterTheCallerReusesThePacket() {
        byte[] b = packet(BATTLE);
        CombatSnapshot snapshot = CombatSnapshot.parse(b);
        b[8 + 1] = 0; b[5] = 0;
        assertEquals(22, snapshot.spots().get(0).x);
        assertEquals(10, snapshot.size());
        try { snapshot.spots().add(null); fail("the square list is mutable"); }
        catch (UnsupportedOperationException expected) { }
    }
}
