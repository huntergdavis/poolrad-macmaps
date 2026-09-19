package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Quitting, restarting and loading, driven entirely from what the probe says
 * the machine is doing. The case that matters most is the one that already
 * went wrong once: never type into a running game.
 */
public class LoadSequenceTest {
    private static LoadSequence sequence() {
        return new LoadSequence("Pool of Radiance v1.1", "PoolRadSave", "F7Healed");
    }

    /** Run the sequence with a fixed signal until it sends something or settles. */
    private static LoadSequence.Instruction drive(LoadSequence load, GameSignal signal,
                                                 long from, long to, long step) {
        LoadSequence.Instruction last = null;
        for (long now = from; now <= to; now += step) {
            last = load.next(signal, now);
            if (last.kind != LoadSequence.Kind.WAIT) return last;
        }
        return last;
    }

    @Test public void theMachineIsRestartedFirst() {
        /*
         * Rather than quitting the game, which asks "Do you really want to
         * quit?" in a dialog with no default button -- Return does nothing
         * there, and confirming it would mean clicking a measured coordinate.
         */
        LoadSequence load = sequence();
        LoadSequence.Instruction first = load.next(GameSignal.PARTY, 0);
        assertEquals(LoadSequence.Kind.RESTART_GUEST, first.kind);
        assertEquals("Restarting the machine", load.describe());
    }

    @Test public void aGameThatDoesNotStartItselfStopsAndSaysWhy() {
        /*
         * Driving the Finder by typing was tried and is not safe -- the
         * application has to be in the frontmost window, and when it is not the
         * typing selects something else and Cmd-O opens that. One run renamed a
         * journal; another opened Apple Extras.
         */
        LoadSequence load = sequence();
        load.next(GameSignal.PARTY, 0);
        LoadSequence.Instruction end = null;
        for (long t = 1_000; t < LoadSequence.BOOT_PATIENCE + 10_000; t += 5_000)
            end = load.next(GameSignal.NO_GAME, t);
        assertEquals(LoadSequence.Kind.FAILED, end.kind);
        assertTrue(load.failure(), load.failure().contains("did not start"));
        assertTrue(load.failure(), load.failure().contains("Startup Items"));
    }

    @Test public void nothingIsEverTypedIntoTheFinder() {
        // Only the game's own dialogs are typed into, and each ends with Return.
        LoadSequence load = sequence();
        long t = 0;
        load.next(GameSignal.PARTY, t);
        for (long step = 1_000; step < LoadSequence.BOOT_PATIENCE; step += 10_000) {
            LoadSequence.Instruction what = load.next(GameSignal.NO_GAME, step);
            assertNotEquals("nothing is typed while the machine is not running the game",
                    LoadSequence.Kind.TYPE_ONLY, what.kind);
            assertNotEquals(LoadSequence.Kind.TYPE_LINE, what.kind);
        }
    }

    @Test public void theMachineIsRestartedEvenFromTheFinder() {
        // It costs a boot, and it is the one way to reach a state the sequence
        // understands from any state at all.
        LoadSequence load = sequence();
        assertEquals(LoadSequence.Kind.RESTART_GUEST, load.next(GameSignal.NO_GAME, 0).kind);
    }

    @Test public void aDiskThatLaunchesTheGameItselfSkipsTheFinder() {
        LoadSequence load = sequence();
        load.next(GameSignal.PARTY, 0);                       // restart
        assertEquals(LoadSequence.Kind.WAIT, load.next(GameSignal.NO_GAME, 40_000).kind);
        assertEquals("the game came up by itself", LoadSequence.Kind.WAIT,
                load.next(GameSignal.NO_PARTY, 90_000).kind);
        assertEquals('L', load.next(GameSignal.NO_PARTY,
                90_000 + LoadSequence.READY_SETTLE + 100).key);
    }

    @Test public void theWholeSequenceInOrder() {
        LoadSequence load = sequence();
        long t = 0;
        assertEquals(LoadSequence.Kind.RESTART_GUEST, load.next(GameSignal.PARTY, t).kind);
        t += 60_000;                                       // the machine boots
        assertEquals(LoadSequence.Kind.WAIT, load.next(GameSignal.NO_GAME, t).kind);
        t += 30_000;                                       // and the game starts itself
        assertEquals(LoadSequence.Kind.WAIT, load.next(GameSignal.NO_PARTY, t).kind);
        // The game is seen before it is ready for menus; Cmd-L waits for that.
        assertEquals(LoadSequence.Kind.WAIT, load.next(GameSignal.NO_PARTY, t + 100).kind);
        t += LoadSequence.READY_SETTLE + 100;
        assertEquals('L', load.next(GameSignal.NO_PARTY, t).key);           // load dialog
        t += LoadSequence.DIALOG_SETTLE + 100;
        assertEquals(LoadSequence.Kind.WAIT, load.next(GameSignal.NO_PARTY, t).kind);
        t += 100;
        LoadSequence.Instruction folder = load.next(GameSignal.NO_PARTY, t);
        assertEquals("PoolRadSave", folder.text);
        t += LoadSequence.DIALOG_SETTLE + 100;
        assertEquals(LoadSequence.Kind.WAIT, load.next(GameSignal.NO_PARTY, t).kind);
        t += 100;
        assertEquals("F7Healed", load.next(GameSignal.NO_PARTY, t).text);
        t += 5_000;
        LoadSequence.Instruction done = load.next(GameSignal.PARTY, t);
        assertEquals(LoadSequence.Kind.FINISHED, done.kind);
        assertTrue(load.finished());
        assertFalse(load.failed());
        assertEquals("Loaded F7Healed", load.describe());
    }

    @Test public void aPartyAppearingAtTheDialogStopsBeforeAnythingIsTyped() {
        // The exact failure that once had a save name typed into a live game.
        LoadSequence load = sequence();
        load.next(GameSignal.PARTY, 0);             // restart
        load.next(GameSignal.NO_PARTY, 90_000);     // game up; on to the dialog
        LoadSequence.Instruction stopped = load.next(GameSignal.PARTY, 90_100);
        // Even inside the settle, a party appearing stops it.
        assertEquals(LoadSequence.Kind.FAILED, stopped.kind);
        assertTrue(load.failure(), load.failure().contains("still running"));
        assertTrue(load.failure(), load.failure().contains("nothing was typed"));
    }

    @Test public void aSaveThatNeverOpensFailsPlainly() {
        LoadSequence load = sequence();
        long t = 0;
        load.next(GameSignal.PARTY, t);
        load.next(GameSignal.NO_PARTY, t += 90_000);
        load.next(GameSignal.NO_PARTY, t += LoadSequence.READY_SETTLE + 100);   // Cmd-L
        load.next(GameSignal.NO_PARTY, t += LoadSequence.DIALOG_SETTLE + 100);
        load.next(GameSignal.NO_PARTY, t += 100);                       // folder
        load.next(GameSignal.NO_PARTY, t += LoadSequence.DIALOG_SETTLE + 100);
        load.next(GameSignal.NO_PARTY, t += 100);                       // save
        LoadSequence.Instruction end = null;
        for (long extra = 0; extra < LoadSequence.LOAD_PATIENCE + 5_000; extra += 1_000)
            end = load.next(GameSignal.NO_PARTY, t + extra);
        assertEquals(LoadSequence.Kind.FAILED, end.kind);
        assertTrue(load.failure(), load.failure().contains("did not open"));
    }

    @Test public void aFinishedSequenceKeepsSayingSoAndSendsNothingMore() {
        LoadSequence load = sequence();
        long t = 0;
        load.next(GameSignal.NO_GAME, t);
        load.next(GameSignal.NO_PARTY, t += 100);
        load.next(GameSignal.NO_PARTY, t += LoadSequence.READY_SETTLE + 100);
        load.next(GameSignal.NO_PARTY, t += LoadSequence.DIALOG_SETTLE + 100);
        load.next(GameSignal.NO_PARTY, t += 100);
        load.next(GameSignal.NO_PARTY, t += LoadSequence.DIALOG_SETTLE + 100);
        load.next(GameSignal.NO_PARTY, t += 100);
        assertEquals(LoadSequence.Kind.FINISHED, load.next(GameSignal.PARTY, t += 100).kind);
        for (int i = 0; i < 5; i++)
            assertEquals(LoadSequence.Kind.FINISHED, load.next(GameSignal.PARTY, t += 1_000).kind);
    }

    @Test public void anUnnamedSaveIsRefusedBeforeAnythingHappens() {
        for (String[] bad : new String[][]{{"", "f", "s"}, {"a", "", "s"}, {"a", "f", ""}}) {
            try { new LoadSequence(bad[0], bad[1], bad[2]); fail("accepted " + java.util.Arrays.toString(bad)); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
