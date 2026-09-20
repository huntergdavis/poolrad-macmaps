package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Invented PRP3 packets: who gets bandaged when a fight ends. */
public class BandagePlanTest {
    private static byte[] packet(int[] conditions, int[] hp) {
        byte[] b = new byte[PartyState.CONDITION_PACKET_SIZE];
        b[0]='P'; b[1]='R'; b[2]='P'; b[3]='3'; b[4]=(byte) conditions.length;
        for (int i = 0; i < conditions.length; i++) {
            int row = 8 + PartyState.ROW_SIZE * i;
            b[row] = (byte) ('A' + i); b[row + 16] = (byte) hp[i]; b[row + 17] = 20; b[row + 18] = 0; b[row + 19] = 2;
            b[PartyState.PACKET_SIZE + 2 * i] = (byte) conditions[i];
        }
        return b;
    }

    @Test public void onlyDyingMembersArePlannedAndTheirRowsAreTheReadersRows() {
        PartyState party = PartyState.parse(packet(new int[]{0, 5, 4, 5, 6}, new int[]{9, 0, 0, 0, 0}));
        assertNotNull(party);
        assertEquals(Arrays.asList(1, 3), BandagePlan.dying(party));
        assertTrue(BandagePlan.anyoneStanding(party));
    }

    @Test public void aFallenPartyIsLeftAlone() {
        PartyState party = PartyState.parse(packet(new int[]{5, 4, 6}, new int[]{0, 0, 0}));
        assertEquals(Arrays.asList(0), BandagePlan.dying(party));
        assertFalse("nobody Okay means nobody to do the bandaging", BandagePlan.anyoneStanding(party));
        assertFalse(BandagePlan.anyoneStanding(null));
        assertTrue(BandagePlan.dying(null).isEmpty());
    }

    @Test public void unavailableConditionsAreNeverDying() {
        byte[] legacy = new byte[PartyState.PACKET_SIZE];
        legacy[0]='P'; legacy[1]='R'; legacy[2]='P'; legacy[3]='2'; legacy[4]=1; legacy[8]='A'; legacy[24]=5; legacy[25]=9; legacy[27]=2;
        PartyState party = PartyState.parse(legacy);
        assertNotNull(party);
        assertTrue(BandagePlan.dying(party).isEmpty());
        assertFalse("an unreadable condition is not Okay either", BandagePlan.anyoneStanding(party));
    }
}
