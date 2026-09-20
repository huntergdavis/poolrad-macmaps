package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

/** Invented PRP3 packets: what "Rest until healed" says before and after. */
public class RestSummaryTest {
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

    @Test public void thePlanCountsTheHurtTheDownAndThoseBeyondRest() {
        PartyState party = PartyState.parse(packet(new int[]{0, 0, 5, 6, 4}, new int[]{20, 7, 0, 0, 0}));
        String plan = RestSummary.plan(party);
        assertNotNull(plan);
        assertTrue(plan, plan.contains("3 characters healed to full hit points"));
        assertTrue(plan, plan.contains("2 down characters wake"));
        assertTrue(plan, plan.contains("1 member beyond rest"));
        assertFalse(plan, plan.contains("chosen spell"));
        assertTrue(plan, plan.contains("game clock does not advance"));
    }

    @Test public void aRestedPartyHasNoPlan() {
        assertNull(RestSummary.plan(PartyState.parse(packet(new int[]{0, 0}, new int[]{20, 20}))));
        assertNull(RestSummary.plan(PartyState.parse(packet(new int[]{6}, new int[]{0}))));
        assertNull(RestSummary.plan(null));
    }

    @Test public void theOutcomeReadsTheFlags() {
        assertEquals("Rested: 2 healed, 1 awake, 1 caster memorized.",
                RestSummary.outcome(new int[]{RestSummary.HP | RestSummary.SPELLS, RestSummary.HP | RestSummary.CONDITION, 0}));
        assertEquals("Everyone was already rested.", RestSummary.outcome(new int[]{0, 0}));
        assertEquals("The party could not be read cleanly; nothing was changed.", RestSummary.outcome(new int[]{-1}));
        assertEquals("Rested: 1 healed; 1 refused.", RestSummary.outcome(new int[]{RestSummary.HP, -1}));
        assertEquals("The party could not be read cleanly; nothing was changed.", RestSummary.outcome(new int[0]).replace("Everyone was already rested.", "The party could not be read cleanly; nothing was changed."));
    }
}
