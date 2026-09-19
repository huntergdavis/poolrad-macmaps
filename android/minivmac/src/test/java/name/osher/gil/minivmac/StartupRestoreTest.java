package name.osher.gil.minivmac;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class StartupRestoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private static final DiskSnapshotGuard.Fingerprint NONE = DiskSnapshotGuard.Fingerprint.empty();

    @Test public void cancelledReadCannotApplyOrStartTheSameLaunchAgain() {
        StartupRestoreGate gate = new StartupRestoreGate();
        assertTrue(gate.held()); assertTrue(gate.start());
        assertFalse(gate.start()); assertTrue(gate.waiting());
        assertTrue(gate.cancel()); assertFalse(gate.held());
        assertFalse(gate.beginApply()); assertFalse(gate.start());
        gate.release(); assertFalse(gate.held());
    }

    @Test public void committedApplyOwnsItsResultAndReleasesTheGuestExactlyOnce() {
        StartupRestoreGate gate = new StartupRestoreGate();
        gate.start(); assertTrue(gate.beginApply());
        assertFalse(gate.cancel()); assertTrue(gate.held());
        assertFalse(gate.beginApply());
        gate.release(); assertFalse(gate.held());
        assertFalse(gate.start()); assertFalse(gate.cancel());
    }

    @Test public void cancellationBeforeFirstTickAndAnotherCoreAreIndependent() {
        StartupRestoreGate old = new StartupRestoreGate(), next = new StartupRestoreGate();
        assertTrue(old.cancel()); assertFalse(old.start());
        assertTrue(next.start()); assertTrue(next.waiting());
        old.release(); assertTrue(next.held());
    }

    @Test public void lastCompletedNamedAndAutomaticSavesWinAcrossClockRollback() throws Exception {
        File dir = tmp.newFolder(); SaveStateStore store = new SaveStateStore(dir);
        File quick = store.writeQuick(new byte[10000], 900000, NONE);
        store.rememberLatest(quick);
        File named = store.write("later..named", new byte[10000], NONE);
        assertTrue(named.setLastModified(100));
        store.rememberLatest(named);
        assertEquals(named.getCanonicalFile(), new SaveStateStore(dir).latestSave());
        File automatic = store.writeAuto("Auto latest", new byte[10000], 20, NONE);
        assertTrue(automatic.setLastModified(1));
        store.rememberLatest(automatic);
        assertEquals(automatic.getCanonicalFile(), new SaveStateStore(dir).latestSave());
        assertEquals(quick, store.quickSaves().get(0));
    }

    @Test public void noSaveAndFirstUpgradeUseTheExistingBrowserOrder() throws Exception {
        SaveStateStore store = new SaveStateStore(tmp.newFolder());
        assertNull(store.latestSave());
        File older = store.write("first", new byte[10000], NONE);
        assertTrue(older.setLastModified(100));
        File newest = store.writeQuick(new byte[10000], 200, NONE);
        assertEquals(newest, store.latestSave());
        store.rememberLatest(newest);
        assertTrue(store.delete(newest));
        assertEquals(older, store.latestSave());
    }

    @Test public void corruptNewestIsReturnedForRefusalNeverSilentlyReplacedWithAnOlderGame() throws Exception {
        File dir = tmp.newFolder(); SaveStateStore store = new SaveStateStore(dir);
        File older = store.write("older", new byte[10000], NONE);
        File newest = store.write("newest", new byte[10000], NONE);
        store.rememberLatest(newest);
        Files.write(newest.toPath(), new byte[]{1,2,3});
        assertEquals(newest.getCanonicalFile(), store.latestSave());
        try { store.readSnapshot(store.latestSave()); fail("Loaded corrupt state"); }
        catch (IOException expected) { }
        assertEquals(10000, store.read(older).length);
    }

    @Test public void latestIndexCannotEscapeToAnExternalSnapshot() throws Exception {
        File dir = tmp.newFolder(); SaveStateStore store = new SaveStateStore(dir);
        File outside = tmp.newFile("outside.prqs");
        try { store.rememberLatest(outside); fail("Accepted outside save"); }
        catch (IOException expected) { }
        Files.write(new File(dir, "latest-snapshot").toPath(), "../outside.prqs".getBytes(StandardCharsets.UTF_8));
        try { store.latestSave(); fail("Followed path traversal"); }
        catch (IOException expected) { }
    }

    @Test public void failedLatestPublicationPreservesThePreviousCompletedChoice() throws Exception {
        File dir = tmp.newFolder(); SaveStateStore store = new SaveStateStore(dir);
        File first = store.write("first", new byte[10000], NONE);
        store.rememberLatest(first);
        File next = store.write("next", new byte[10000], NONE);
        assertTrue(new File(dir, "latest-snapshot.part").mkdir());
        try { store.rememberLatest(next); fail("Published through blocked partial path"); }
        catch (IOException expected) { }
        assertEquals(first.getCanonicalFile(), store.latestSave());
    }
}
