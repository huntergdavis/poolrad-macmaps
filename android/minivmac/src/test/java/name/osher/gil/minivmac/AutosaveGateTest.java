package name.osher.gil.minivmac;

import org.junit.Test;
import static org.junit.Assert.*;

/** When a timer autosave is worth taking, and when nothing new needs protecting. */
public class AutosaveGateTest {
    @Test public void theFirstAutosaveAlwaysHappens() {
        AutosaveGate gate = new AutosaveGate();
        assertTrue(gate.shouldSave());
        gate.requested(); gate.saved(true);
        assertFalse("nothing moved since the save", gate.shouldSave());
        assertEquals(1, gate.savedCount());
    }

    @Test public void anyActivityAfterTheProtectedPointMeansSave() {
        AutosaveGate gate = new AutosaveGate();
        gate.requested(); gate.saved(true);
        gate.noteActivity("party changed");
        assertTrue(gate.shouldSave());
        assertEquals("party changed", gate.lastReason());
        gate.requested(); gate.saved(true);
        assertFalse(gate.shouldSave());
    }

    @Test public void inputAndDiskCountersOnlyCountWhenTheyMove() {
        AutosaveGate gate = new AutosaveGate();
        gate.observeInput(10); gate.observeDisks(3);     // first readings establish a baseline, not activity
        gate.requested(); gate.saved(true);
        gate.observeInput(10); gate.observeDisks(3);
        assertFalse(gate.shouldSave());
        gate.observeInput(11);
        assertTrue(gate.shouldSave());
        gate.requested(); gate.saved(false);             // a quick save protects the same state
        assertFalse(gate.shouldSave());
        gate.observeDisks(4);                            // the game wrote its own save file
        assertTrue(gate.shouldSave());
        assertEquals("disk change", gate.lastReason());
    }

    @Test public void activityDuringAPendingSaveBelongsToTheNextPeriod() {
        AutosaveGate gate = new AutosaveGate();
        gate.requested();
        gate.noteActivity("map changed");                // the player walked while the worker was compressing
        gate.saved(true);
        assertTrue("the published state predates that step", gate.shouldSave());
    }

    @Test public void failuresNeverSkipAndRestoresProtect() {
        AutosaveGate gate = new AutosaveGate();
        gate.requested(); gate.failed();
        assertTrue(gate.shouldSave());
        gate.requested(); gate.saved(true);
        gate.noteActivity("guest input");
        gate.restored();                                 // the loaded snapshot holds exactly this state
        assertFalse(gate.shouldSave());
        gate.skipped(); gate.skipped();
        assertEquals(2, gate.skippedCount());
    }
}
