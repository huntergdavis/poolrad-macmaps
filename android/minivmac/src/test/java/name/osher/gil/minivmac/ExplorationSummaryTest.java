package name.osher.gil.minivmac;

import org.junit.Test;
import name.osher.gil.minivmac.notebook.ExplorationTrail;
import static org.junit.Assert.*;

public class ExplorationSummaryTest {
    @Test public void reverseDirectionsFollowActualMovesAndNewestAppearsFirst() {
        ExplorationTrail trail = ExplorationTrail.empty().record(17,-1).record(18,17)
                .record(34,18).record(33,34).record(17,33);
        String summary = ExplorationSummary.describe(trail);
        assertTrue(summary.startsWith("1,1 — return south to 1,2\n"));
        assertTrue(summary.contains("1,2 — return east to 2,2"));
        assertTrue(summary.contains("2,2 — return north to 2,1"));
        assertTrue(summary.contains("2,1 — return west to 1,1"));
        assertTrue(summary.endsWith("1,1 — segment starts here; earlier route unknown\n"));
    }
    @Test public void gapsAndEmptyHistoryNeverInventAReturnDirection() {
        assertEquals("No recent steps recorded.", ExplorationSummary.describe(ExplorationTrail.empty()));
        String summary = ExplorationSummary.describe(ExplorationTrail.empty().record(1,-1).record(200,1));
        assertFalse(summary.contains("return"));
        assertTrue(summary.startsWith("8,12 — segment starts here"));
    }
}
