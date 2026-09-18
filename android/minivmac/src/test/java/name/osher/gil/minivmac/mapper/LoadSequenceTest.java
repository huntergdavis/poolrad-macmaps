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

    @Test public void aRunningGameIsQuitFirst() {
        LoadSequence load = sequence();
        LoadSequence.Instruction first = load.next(GameSignal.PARTY, 0);
        assertEquals(LoadSequence.Kind.COMMAND_KEY, first.kind);
        assertEquals('Q', first.key);
        assertEquals("Closing the game", load.describe());
    }

    @Test public void nothingIsQuitWhenNothingIsRunning() {
        LoadSequence load = sequence();
        // Straight from the Finder: no Cmd-Q, on to starting the game.
        assertEquals(LoadSequence.Kind.WAIT, load.next(GameSignal.NO_GAME, 0).kind);
        assertEquals("Starting the game again", load.describe());
    }

    @Test public void theWholeSequenceInOrder() {
        LoadSequence load = sequence();
        long t = 0;
        assertEquals('Q', load.next(GameSignal.PARTY, t).key);              // quit
        t += 3_000;
        assertEquals(LoadSequence.Kind.WAIT, load.next(GameSignal.NO_GAME, t).kind);
        t += 100;
        LoadSequence.Instruction name = load.next(GameSignal.NO_GAME, t);   // type the app name
        assertEquals("the Finder selects by typing; Return there renames",
                LoadSequence.Kind.TYPE_ONLY, name.kind);
        assertEquals("Pool of Radiance v1.1", name.text);
        t += LoadSequence.TYPING_SETTLE + 100;
        assertEquals('O', load.next(GameSignal.NO_GAME, t).key);            // Finder's Open
        t += 10_000;
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

    @Test public void aGameThatWillNotQuitStopsEverything() {
        /*
         * The game may be asking something the heap cannot show. Waiting is
         * right; pressing on would type a save name into a live game.
         */
        LoadSequence load = sequence();
        LoadSequence.Instruction end = drive(load, GameSignal.PARTY, 0,
                LoadSequence.QUIT_PATIENCE + 5_000, 500);
        // The first thing sent is the quit itself; keep going past its patience.
        while (!load.finished()) end = load.next(GameSignal.PARTY, LoadSequence.QUIT_PATIENCE + 10_000);
        assertEquals(LoadSequence.Kind.FAILED, end.kind);
        assertTrue(load.failed());
        assertTrue(load.failure(), load.failure().contains("did not close"));
        assertTrue(load.failure(), load.failure().contains("nothing else was sent"));
    }

    @Test public void aPartyAppearingAtTheDialogStopsBeforeAnythingIsTyped() {
        // The exact failure that once had a save name typed into a live game.
        LoadSequence load = sequence();
        load.next(GameSignal.NO_GAME, 0);
        load.next(GameSignal.NO_GAME, 100);
        load.next(GameSignal.NO_PARTY, 20_000);     // game up; on to the dialog
        LoadSequence.Instruction stopped = load.next(GameSignal.PARTY, 20_100);
        // Even inside the settle, a party appearing stops it.
        assertEquals(LoadSequence.Kind.FAILED, stopped.kind);
        assertTrue(load.failure(), load.failure().contains("still running"));
        assertTrue(load.failure(), load.failure().contains("nothing was typed"));
    }

    @Test public void aGameThatNeverComesBackFailsWithoutLoading() {
        LoadSequence load = sequence();
        load.next(GameSignal.NO_GAME, 0);
        LoadSequence.Instruction end = null;
        for (long t = 100; t < LoadSequence.RELAUNCH_PATIENCE + 5_000; t += 1_000)
            end = load.next(GameSignal.NO_GAME, t);
        assertEquals(LoadSequence.Kind.FAILED, end.kind);
        assertTrue(load.failure(), load.failure().contains("did not start again"));
        assertTrue(load.failure(), load.failure().contains("Nothing was loaded"));
    }

    @Test public void aSaveThatNeverOpensFailsPlainly() {
        LoadSequence load = sequence();
        long t = 0;
        load.next(GameSignal.NO_GAME, t);
        load.next(GameSignal.NO_PARTY, t += 100);
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

    @Test public void theFinderIsNeverSentAReturn() {
        /*
         * It renames the selected item. This once renamed a journal and then
         * opened it instead of the game, which is a mess to explain and a
         * worse one to undo.
         */
        LoadSequence load = sequence();
        load.next(GameSignal.NO_GAME, 0);
        LoadSequence.Instruction name = load.next(GameSignal.NO_GAME, 100);
        assertEquals(LoadSequence.Kind.TYPE_ONLY, name.kind);
        // Everything typed into the game's own dialogs still ends with Return.
        long t = 200;
        load.next(GameSignal.NO_PARTY, t);
        load.next(GameSignal.NO_PARTY, t += LoadSequence.READY_SETTLE + 100);
        load.next(GameSignal.NO_PARTY, t += LoadSequence.DIALOG_SETTLE + 100);
        assertEquals(LoadSequence.Kind.TYPE_LINE, load.next(GameSignal.NO_PARTY, t + 100).kind);
    }

    @Test public void anUnnamedSaveIsRefusedBeforeAnythingHappens() {
        for (String[] bad : new String[][]{{"", "f", "s"}, {"a", "", "s"}, {"a", "f", ""}}) {
            try { new LoadSequence(bad[0], bad[1], bad[2]); fail("accepted " + java.util.Arrays.toString(bad)); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
