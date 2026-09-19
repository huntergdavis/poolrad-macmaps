package name.osher.gil.minivmac;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import static org.junit.Assert.*;

/** The Info → Saves page text for a new player, a long history and a tight disk. */
public class SaveStatusTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static byte[] machine(int length, int seed) {
        byte[] b = new byte[length];
        for (int i = 0; i < length / 2; i++) b[i] = (byte) (i * 31 + seed);
        return b;
    }

    private static final long GB = 1024L * 1024 * 1024;

    @Test public void aBrandNewPlayerSeesNoSavesAndPlainRoom() {
        String text = new SaveStatus().describe(new SaveStateStore.Usage(0, 0, 0, 0, 0), 5 * GB);
        assertTrue(text, text.contains("Active save\nNone yet."));
        assertTrue(text, text.contains("Saves stored\nNo saves yet."));
        assertTrue(text, text.contains("5.0 GB free"));
        assertTrue(text, text.contains("more saves at the current average of 1.1 MB each"));
        assertFalse(text, text.contains("Low on space"));
    }

    @Test public void aNormalBootWithHistoryIsNotCalledASave() {
        String text = new SaveStatus().describe(new SaveStateStore.Usage(9, 20, 3, 32L * 1024 * 1024, 40L * 1024 * 1024), 2 * GB);
        assertTrue(text, text.contains("None this session."));
        assertTrue(text, text.contains("32 saves · 40.0 MB"));
        assertTrue(text, text.contains("9 quick (newest 10 kept), 20 automatic (newest 20 kept), 3 named (kept until deleted)"));
        assertTrue(text, text.contains("2.0 GB free"));
        assertTrue(text, text.contains("Roughly 1,9"));   // (2 GB - 64 MB) / 1 MB ≈ 1,984
        assertTrue(text, text.contains("at the current average of 1.0 MB each"));
    }

    @Test public void theActiveSaveNamesTheLoadedFileThenTheLaterManualSave() throws IOException {
        SaveStateStore store = new SaveStateStore(tmp.getRoot());
        File loaded = store.write("Temple", machine(50_000, 1), DiskSnapshotGuard.Fingerprint.empty());
        SaveStatus status = new SaveStatus();
        status.loaded(loaded, 0);
        String text = status.describe(store.usage(), GB);
        assertTrue(text, text.contains("Active save\nTemple "));
        assertTrue(text, text.contains("\nLoaded "));
        assertEquals(loaded, status.active());
        assertEquals(SaveStatus.How.LOADED, status.how());

        File quick = store.writeQuick(machine(50_000, 2), 1_700_000_000_000L, DiskSnapshotGuard.Fingerprint.empty());
        status.saved(quick, 1_700_000_000_000L);
        text = status.describe(store.usage(), GB);
        assertTrue(text, text.contains("Active save\n" + SaveStateStore.QUICK_NAME + " "));
        assertTrue(text, text.contains("\nSaved "));
        assertTrue(text, text.contains("2 saves"));
        assertTrue(text, text.contains("1 quick (newest 10 kept), 0 automatic (newest 20 kept), 1 named"));
    }

    @Test public void aDeletedActiveSaveIsStillNamedButMarked() throws IOException {
        SaveStateStore store = new SaveStateStore(tmp.getRoot());
        File file = store.write("Gone", machine(20_000, 3), DiskSnapshotGuard.Fingerprint.empty());
        SaveStatus status = new SaveStatus();
        status.loaded(file, 0);
        assertTrue(store.delete(file));
        String text = status.describe(store.usage(), GB);
        assertTrue(text, text.contains("Active save\nGone "));
        assertTrue(text, text.contains("since been deleted or rotated out"));
    }

    @Test public void lowSpaceSaysToDeleteOldSavesAndUnknownSpaceIsAdmitted() {
        SaveStateStore.Usage usage = new SaveStateStore.Usage(2, 0, 0, 2_000_000, 3_200_000);
        String low = new SaveStatus().describe(usage, 10L * 1024 * 1024);
        assertTrue(low, low.contains("10.0 MB free"));
        assertTrue(low, low.contains("Low on space: delete old saves in Load…"));
        assertFalse(low, low.contains("Roughly"));
        String unknown = new SaveStatus().describe(usage, -1);
        assertTrue(unknown, unknown.contains("Free space unknown."));
    }

    @Test public void nullRecordsAreIgnoredAndCountsAreCapped() {
        SaveStatus status = new SaveStatus();
        status.loaded(null, 5);
        assertNull(status.active());
        assertEquals("100,000+", SaveStatus.count(5_000_000));
        assertEquals("1,234", SaveStatus.count(1234));
        assertEquals("512 B", SaveStatus.bytes(512));
        assertEquals("700 KB", SaveStatus.bytes(700 * 1024));
        assertEquals("1.0 MB", SaveStatus.bytes(1004 * 1024));
        assertEquals("1.5 GB", SaveStatus.bytes(GB + GB / 2));
    }
}
