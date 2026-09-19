package name.osher.gil.minivmac.mapper;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Invented packets only; no private capture appears here. */
public class CombatSnapshotTest {
    private byte[] packet(int[][] rows) {
        byte[] b = new byte[CombatSnapshot.PACKET_SIZE];
        b[0]='P'; b[1]='R'; b[2]='C'; b[3]='3'; b[4]=1; b[5]=(byte) rows.length;
        for (int i = 0; i < rows.length; i++) {
            b[8+i*4] = (byte) rows[i][0];
            b[8+i*4+1] = (byte) rows[i][1];
            b[8+i*4+2] = (byte) rows[i][2];
        }
        return b;
    }
    private byte[] unavailable() {
        byte[] b = new byte[CombatSnapshot.PACKET_SIZE];
        b[0]='P'; b[1]='R'; b[2]='C'; b[3]='3'; b[4]=(byte)255;
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

    @Test public void theActingCharacterIsReadBackByName() {
        // Whose turn it is comes from the game's own Combat Message window, so
        // it is the same name the player is looking at.
        byte[] p = packetOf(new int[][]{{1, 10, 10, 0}, {2, 20, 10, 0}});
        assertNull("no name means nobody", CombatSnapshot.parse(p).acting);
        byte[] named = withActor(p, "Shara the Grey");
        CombatSnapshot battle = CombatSnapshot.parse(named);
        assertNotNull(battle);
        assertEquals("Shara the Grey", battle.acting);
        assertTrue(battle.isActing("Shara the Grey"));
        assertFalse(battle.isActing("Zarram"));
        assertFalse(battle.isActing(null));
    }

    @Test public void aNameThatIsNotOneIsRefused() {
        byte[] p = packetOf(new int[][]{{1, 10, 10, 0}});
        // Control bytes are not a name.
        byte[] bad = p.clone(); bad[CombatSnapshot.ENTRIES_SIZE] = 7;
        assertNull(CombatSnapshot.parse(bad));
        // Nor is anything written after the terminator.
        byte[] trailing = withActor(p, "Arax");
        trailing[CombatSnapshot.ENTRIES_SIZE + 9] = 'x';
        assertNull(CombatSnapshot.parse(trailing));
        // A name filling the field with no room to terminate is still a name.
        assertEquals(16, CombatSnapshot.ACTOR_BYTES);
        byte[] full = withActor(p, "Sixteen chars!!!");
        assertEquals("Sixteen chars!!!", CombatSnapshot.parse(full).acting);
    }

    private static byte[] withActor(byte[] packet, String name) {
        byte[] out = packet.clone();
        byte[] bytes = name.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, out, CombatSnapshot.ENTRIES_SIZE,
                Math.min(bytes.length, CombatSnapshot.ACTOR_BYTES));
        return out;
    }

    @Test public void aFallenCharacterSaysWhichKindOfDownTheyAre() {
        // Dying is savable and dead is not; the map draws them differently and
        // the pane says the word, so the word has to arrive.
        int[][] rows = {{1, 10, 10, 0}, {4, 11, 10, 5}, {4, 12, 10, 6},
                        {4, 13, 10, 4}, {4, 14, 10, 7}, {2, 20, 10, 0}};
        CombatSnapshot battle = CombatSnapshot.parse(packetOf(rows));
        assertNotNull(battle);
        assertEquals(5, battle.partyCount());
        assertEquals(4, battle.fallenCount());
        assertEquals("Dying", battle.spots().get(1).stateLabel());
        assertEquals("Dead", battle.spots().get(2).stateLabel());
        assertEquals("Unconscious", battle.spots().get(3).stateLabel());
        assertEquals("Petrified", battle.spots().get(4).stateLabel());
        assertTrue(battle.spots().get(1).savable());
        assertFalse(battle.spots().get(2).savable());
        assertTrue(battle.spots().get(3).savable());
        assertFalse(battle.spots().get(4).savable());
        assertNull(battle.spots().get(0).stateLabel());
        assertTrue(battle.summary().contains("2 down"));
        assertTrue(battle.summary().contains("2 lost"));
    }

    /** Put a grouped foe list in the packet: name, then how many are standing. */
    private static byte[] withFoes(byte[] packet, Object... nameThenCount) {
        byte[] out = packet.clone();
        int kinds = nameThenCount.length / 2;
        out[CombatSnapshot.FOES_OUT] = (byte) kinds;
        for (int i = 0; i < kinds; i++) {
            String name = (String) nameThenCount[i * 2];
            int standing = (Integer) nameThenCount[i * 2 + 1];
            int at = CombatSnapshot.FOES_OUT + 1 + i * (CombatSnapshot.FOE_NAME + 1);
            byte[] bytes = name.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(bytes, 0, out, at, bytes.length);
            out[at + CombatSnapshot.FOE_NAME] = (byte) standing;
        }
        return out;
    }

    @Test public void theHeaderNamesWhatThePartyIsFighting() {
        // "12 others" is the least informative thing the pane could say.
        byte[] p = withFoes(packetOf(new int[][]{{1, 10, 10, 0}, {2, 20, 10, 0}, {2, 21, 10, 0}}),
                "GOBLIN", 2);
        CombatSnapshot battle = CombatSnapshot.parse(p);
        assertNotNull(battle);
        assertEquals("2 GOBLIN", battle.opposition());
        assertEquals("1 of yours · 2 GOBLIN", battle.summary());
    }

    @Test public void severalKindsAreListedSeparately() {
        byte[] p = withFoes(packetOf(new int[][]{{1, 10, 10, 0}, {2, 20, 10, 0}, {2, 21, 10, 0}}),
                "GOBLIN", 8, "ORC", 4);
        CombatSnapshot battle = CombatSnapshot.parse(p);
        assertNotNull(battle);
        assertEquals("8 GOBLIN · 4 ORC", battle.opposition());
        assertEquals(2, battle.foes().size());
        assertEquals("GOBLIN", battle.foes().get(0).name);
        assertEquals(8, battle.foes().get(0).standing);
    }

    @Test public void withoutNamesItFallsBackToACount() {
        /*
         * The probe reports no kinds when it will not vouch for the grouping --
         * an unreadable name, or more kinds than the packet holds. Saying
         * "2 others" then is honest; naming some of them would not be.
         */
        CombatSnapshot battle = CombatSnapshot.parse(
                packetOf(new int[][]{{1, 10, 10, 0}, {2, 20, 10, 0}, {2, 21, 10, 0}}));
        assertNotNull(battle);
        assertEquals("2 others", battle.opposition());
        assertTrue(battle.foes().isEmpty());
    }

    @Test public void oneOfSomethingIsNotPluralised() {
        CombatSnapshot battle = CombatSnapshot.parse(
                packetOf(new int[][]{{1, 10, 10, 0}, {2, 20, 10, 0}}));
        assertEquals("1 other", battle.opposition());
    }

    @Test public void aFoeListThatDoesNotCheckOutRejectsTheWholePacket() {
        byte[] good = packetOf(new int[][]{{1, 10, 10, 0}, {2, 20, 10, 0}});
        // A control byte in a name.
        byte[] bad = withFoes(good, "ORC", 1);
        bad[CombatSnapshot.FOES_OUT + 1] = 7;
        assertNull(CombatSnapshot.parse(bad));
        // A tally of nobody.
        assertNull(CombatSnapshot.parse(withFoes(good, "ORC", 0)));
        // More kinds than the packet holds.
        byte[] tooMany = good.clone();
        tooMany[CombatSnapshot.FOES_OUT] = (byte) (CombatSnapshot.FOES_MAX + 1);
        assertNull(CombatSnapshot.parse(tooMany));
        // Anything written past the kinds reported.
        byte[] trailing = withFoes(good, "ORC", 3);
        trailing[CombatSnapshot.FOES_OUT + 1 + 2 * (CombatSnapshot.FOE_NAME + 1)] = 'x';
        assertNull(CombatSnapshot.parse(trailing));
    }

    private static byte[] packetOf(int[][] rows) {
        byte[] p = new byte[CombatSnapshot.PACKET_SIZE];
        p[0] = 'P'; p[1] = 'R'; p[2] = 'C'; p[3] = '3'; p[4] = 1; p[5] = (byte) rows.length;
        for (int i = 0; i < rows.length; i++)
            for (int j = 0; j < 4; j++) p[8 + i * 4 + j] = (byte) rows[i][j];
        return p;
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
        // The row's fourth byte used to be reserved and had to be zero. It now
        // carries the game's own condition, so the check is on its value.
        for (int condition = 9; condition < 255; condition++) {
            byte[] b = good.clone(); b[8 + 2 * 4 + 3] = (byte) condition;
            assertNull("condition " + condition + " accepted", CombatSnapshot.parse(b));
        }
        for (int condition = 0; condition <= 8; condition++) {
            byte[] b = good.clone(); b[8 + 2 * 4 + 3] = (byte) condition;
            assertNotNull("condition " + condition + " rejected", CombatSnapshot.parse(b));
        }
        // A fallen marker must be one of the four ways of being down.
        for (int condition : new int[]{0, 1, 2, 3, 8}) {
            byte[] b = good.clone();
            b[8 + 2 * 4] = 4; b[8 + 2 * 4 + 3] = (byte) condition;
            assertNull("fallen with condition " + condition + " accepted", CombatSnapshot.parse(b));
        }
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
