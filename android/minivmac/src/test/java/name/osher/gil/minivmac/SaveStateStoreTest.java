package name.osher.gil.minivmac;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Packing and unpacking save-state files. No emulator here; the bytes stand in. */
public class SaveStateStoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static byte[] machine(int length, int seed) {
        byte[] b = new byte[length];
        // Half structured, half zero -- like a real image, so gzip actually shrinks it.
        for (int i = 0; i < length / 2; i++) b[i] = (byte) (i * 31 + seed);
        return b;
    }

    private SaveStateStore store() { return new SaveStateStore(tmp.getRoot()); }

    @Test public void aStateSurvivesTheRoundTrip() throws IOException {
        SaveStateStore s = store();
        byte[] raw = machine(200000, 7);
        File f = s.write("Test", raw, DiskSnapshotGuard.Fingerprint.empty());
        assertArrayEquals(raw, s.read(f));
    }

    @Test public void usageCountsEachKindAndEveryByteInTheFolder() throws IOException {
        SaveStateStore s = store();
        SaveStateStore.Usage empty = s.usage();
        assertEquals(0, empty.total()); assertEquals(0, empty.totalBytes);
        File named = s.write("Camp", machine(40_000, 1), DiskSnapshotGuard.Fingerprint.empty());
        File auto = s.writeAuto(SaveStateStore.AUTO_PREFIX + "Sep 1", machine(40_000, 2), 20, DiskSnapshotGuard.Fingerprint.empty());
        File quick = s.writeQuick(machine(40_000, 3), 1_700_000_000_000L, DiskSnapshotGuard.Fingerprint.empty());
        assertTrue(s.writeBinding(named, "notebook-1"));
        byte[] png = {(byte) 137, 'P', 'N', 'G', 13, 10, 26, 10};
        s.writePreview(quick, png);
        SaveStateStore.Usage usage = s.usage();
        assertEquals(1, usage.named); assertEquals(1, usage.auto); assertEquals(1, usage.quick);
        assertEquals(3, usage.total());
        assertEquals(named.length() + auto.length() + quick.length(), usage.saveBytes);
        long expected = usage.saveBytes + "notebook-1".length() + png.length
                + new File(tmp.getRoot(), "reference.prqref").length()
                + new File(tmp.getRoot(), "latest-snapshot").length();
        assertEquals(expected, usage.totalBytes);
        assertTrue(usage.totalBytes > usage.saveBytes);
    }

    @Test public void sidecarsPublishAtomicallyAndLeaveWithTheirSave() throws IOException {
        SaveStateStore s = store();
        File save = s.write("Camp", machine(20_000, 1), DiskSnapshotGuard.Fingerprint.empty());
        assertNull(s.readSidecar(save, SaveStateStore.TALLY_SUFFIX, 4096));
        assertTrue(s.writeSidecar(save, SaveStateStore.TALLY_SUFFIX, "PRRT1\n".getBytes()));
        assertArrayEquals("PRRT1\n".getBytes(), s.readSidecar(save, SaveStateStore.TALLY_SUFFIX, 4096));
        assertNull("over the limit reads as absent", s.readSidecar(save, SaveStateStore.TALLY_SUFFIX, 3));
        assertFalse(new File(save.getPath() + SaveStateStore.TALLY_SUFFIX + ".part").exists());
        assertTrue(s.writeSidecar(save, SaveStateStore.TALLY_SUFFIX, null));
        assertNull(s.readSidecar(save, SaveStateStore.TALLY_SUFFIX, 4096));
        assertTrue(s.writeSidecar(save, SaveStateStore.TALLY_SUFFIX, "PRRT1\n".getBytes()));
        assertTrue(s.delete(save));
        assertFalse(new File(save.getPath() + SaveStateStore.TALLY_SUFFIX).exists());
    }

    @Test public void compressionActuallyShrinksATypicalImage() throws IOException {
        SaveStateStore s = store();
        byte[] raw = machine(1_000_000, 3);
        File f = s.write("Big", raw, DiskSnapshotGuard.Fingerprint.empty());
        assertTrue("a mostly-repetitive image should compress", f.length() < raw.length);
    }

    @Test public void theQuickSlotIsAFixedFile() throws IOException {
        SaveStateStore s = store();
        s.write(s.quickFile(), machine(1000, 1), DiskSnapshotGuard.Fingerprint.empty());
        byte[] again = machine(1000, 2);
        s.write(s.quickFile(), again, DiskSnapshotGuard.Fingerprint.empty());   // overwrite the quick slot
        assertArrayEquals(again, s.read(s.quickFile()));
        // The quick file is not double-counted as several named saves.
        assertEquals(1, s.saves().size());
    }

    @Test public void namedSavesNeverClobberEachOther() throws IOException {
        SaveStateStore s = store();
        File a = s.write("Slums", machine(500, 1), DiskSnapshotGuard.Fingerprint.empty());
        File b = s.write("Slums", machine(500, 2), DiskSnapshotGuard.Fingerprint.empty());
        assertNotEquals(a.getName(), b.getName());
        assertEquals(2, s.saves().size());
    }

    @Test public void savesAreListedNewestFirst() throws IOException {
        SaveStateStore s = store();
        File first = s.write("First", machine(400, 1), DiskSnapshotGuard.Fingerprint.empty());
        first.setLastModified(1000);
        File second = s.write("Second", machine(400, 2), DiskSnapshotGuard.Fingerprint.empty());
        second.setLastModified(2000);
        assertEquals("Second", SaveStateStore.label(s.saves().get(0)));
    }

    @Test public void somethingThatIsNotASaveStateIsRefused() throws IOException {
        File bogus = tmp.newFile("bogus" + SaveStateStore.EXTENSION);
        java.nio.file.Files.write(bogus.toPath(), "not a save state at all".getBytes());
        try { store().read(bogus); fail("read a non-save-state"); }
        catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("PoolRad")
                    || expected.getMessage().contains("usable"));
        }
    }

    @Test public void anEmptyStateIsRefusedRatherThanWritten() {
        try { store().write("Empty", new byte[0], DiskSnapshotGuard.Fingerprint.empty()); fail("wrote an empty state"); }
        catch (IOException expected) { }
    }

    @Test public void aTruncatedFileIsRefused() throws IOException {
        SaveStateStore s = store();
        File f = s.write("Whole", machine(100000, 5), DiskSnapshotGuard.Fingerprint.empty());
        byte[] whole = java.nio.file.Files.readAllBytes(f.toPath());
        java.nio.file.Files.write(f.toPath(), Arrays.copyOf(whole, whole.length / 2));
        try { s.read(f); fail("read a truncated save"); }
        catch (IOException expected) { }
    }

    @Test public void labelStripsTheExtension() {
        assertEquals("Kuto's Well", SaveStateStore.label(new File("/x/Kuto's Well" + SaveStateStore.EXTENSION)));
    }

    /** A save that barely differs from the reference is a tiny fraction of a whole image. */
    @Test public void aLaterSaveIsFarSmallerThanTheImage() throws IOException {
        SaveStateStore s = store();
        byte[] first = machine(2_000_000, 1);      // becomes the reference template
        s.write("First", first, DiskSnapshotGuard.Fingerprint.empty());
        byte[] second = first.clone();
        for (int i = 0; i < 500; i++) second[i * 37 % second.length] ^= 0x5a;  // a few changes
        File f = s.write("Second", second, DiskSnapshotGuard.Fingerprint.empty());
        assertTrue("a near-identical save should be a small diff, was " + f.length(),
                f.length() < second.length / 20);
        assertArrayEquals(second, s.read(f));
    }

    /** Different images both reconstruct exactly through the one shared reference. */
    @Test public void severalImagesRoundTripThroughOneReference() throws IOException {
        SaveStateStore s = store();
        byte[] a = machine(300000, 1);
        byte[] b = machine(300000, 99);
        File fa = s.write("A", a, DiskSnapshotGuard.Fingerprint.empty());
        File fb = s.write("B", b, DiskSnapshotGuard.Fingerprint.empty());
        assertArrayEquals(a, s.read(fa));
        assertArrayEquals(b, s.read(fb));
    }

    /** A fresh store reading files written by an earlier one still finds the reference on disk. */
    @Test public void aDiffReadsBackAfterAColdStart() throws IOException {
        byte[] raw = machine(250000, 4);
        File f = store().write("Cold", raw, DiskSnapshotGuard.Fingerprint.empty());       // one store writes it
        assertArrayEquals(raw, store().read(f));    // a brand-new store reads it
    }

    /** Losing the reference makes a diff unreadable rather than silently wrong. */
    @Test public void aDiffWithoutItsReferenceIsRefused() throws IOException {
        SaveStateStore writer = store();
        File f = writer.write("Orphan", machine(120000, 8), DiskSnapshotGuard.Fingerprint.empty());
        // Delete the reference template that the diff depends on.
        for (File ref : tmp.getRoot().listFiles()) if (ref.getName().endsWith(".prqref")) assertTrue(ref.delete());
        try { store().read(f); fail("read a diff with no reference"); }
        catch (IOException expected) { }
    }

    /** A save remembers which notebook it belongs with, by reference. */
    @Test public void aSaveRemembersItsNotebook() throws IOException {
        SaveStateStore s = store();
        File f = s.write("Paired", machine(50000, 2), DiskSnapshotGuard.Fingerprint.empty());
        assertNull("no pairing until one is written", s.readBinding(f));
        assertTrue(s.writeBinding(f, "notebook-abc"));
        assertEquals("notebook-abc", s.readBinding(f));
    }

    @Test public void aFailedRebindingPreservesThePreviousPairing() throws IOException {
        SaveStateStore s = store();
        File f = s.write("Pair failure", machine(50000, 2), DiskSnapshotGuard.Fingerprint.empty());
        assertTrue(s.writeBinding(f, "original-notebook"));
        File blocked = new File(f.getPath() + ".notebook.part");
        assertTrue(blocked.mkdir());
        assertTrue(new File(blocked, "keep").createNewFile());
        assertFalse(s.writeBinding(f, "replacement-notebook"));
        assertEquals("original-notebook", s.readBinding(f));
        assertTrue(new File(blocked, "keep").exists());
    }

    /** A blank notebook id clears the pairing rather than writing an empty one. */
    @Test public void aBlankNotebookClearsThePairing() throws IOException {
        SaveStateStore s = store();
        File f = s.write("Clear", machine(40000, 2), DiskSnapshotGuard.Fingerprint.empty());
        s.writeBinding(f, "notebook-xyz");
        s.writeBinding(f, "   ");
        assertNull(s.readBinding(f));
    }

    /** Deleting a save also removes the notebook pairing beside it. */
    @Test public void deletingASaveRemovesItsPairing() throws IOException {
        SaveStateStore s = store();
        File f = s.write("Gone", machine(40000, 2), DiskSnapshotGuard.Fingerprint.empty());
        s.writeBinding(f, "notebook-1");
        assertTrue(s.delete(f));
        assertFalse(f.exists());
        assertNull(s.readBinding(f));
        assertEquals(0, s.saves().size());
    }

    /** An auto-save round-trips and is listed among auto-saves. */
    @Test public void anAutoSaveRoundTrips() throws IOException {
        SaveStateStore s = store();
        byte[] raw = machine(60000, 3);
        File f = s.writeAuto(SaveStateStore.AUTO_PREFIX + "Sep 19 3-45 PM", raw, 20, DiskSnapshotGuard.Fingerprint.empty());
        assertTrue(SaveStateStore.label(f).startsWith(SaveStateStore.AUTO_PREFIX));
        assertArrayEquals(raw, s.read(f));
        assertEquals(1, s.autoSaves().size());
    }

    /** Auto-saves rotate: only the newest `keep` survive, oldest first out. */
    @Test public void autoSavesRotateToTheLimit() throws IOException {
        SaveStateStore s = store();
        // Five older auto-saves with distinct, increasing timestamps; keep high so none prune yet.
        File[] old = new File[5];
        for (int i = 0; i < 5; i++) {
            old[i] = s.writeAuto(SaveStateStore.AUTO_PREFIX + "old " + i, machine(20000, i), 100, DiskSnapshotGuard.Fingerprint.empty());
            old[i].setLastModified(1000L + i);
        }
        // One more with keep=3: its real (now) timestamp is newest, so it and the two newest olds stay.
        File newest = s.writeAuto(SaveStateStore.AUTO_PREFIX + "newest", machine(20000, 9), 3, DiskSnapshotGuard.Fingerprint.empty());
        assertEquals("only the limit is kept", 3, s.autoSaves().size());
        assertTrue(newest.exists());
        assertTrue("second-newest kept", old[4].exists());
        assertTrue("third-newest kept", old[3].exists());
        assertFalse("oldest rotated out", old[0].exists());
        assertFalse(old[1].exists());
    }

    /** Rotating an auto-save away also removes its notebook sidecar. */
    @Test public void rotatingAnAutoSaveClearsItsSidecar() throws IOException {
        SaveStateStore s = store();
        File oldest = s.writeAuto(SaveStateStore.AUTO_PREFIX + "old", machine(20000, 1), 10, DiskSnapshotGuard.Fingerprint.empty());
        s.writeBinding(oldest, "notebook-old");
        oldest.setLastModified(1000L);
        for (int i = 0; i < 3; i++) {
            File f = s.writeAuto(SaveStateStore.AUTO_PREFIX + "new " + i, machine(20000, i + 2), 1, DiskSnapshotGuard.Fingerprint.empty());
            f.setLastModified(2000L + i);
        }
        assertFalse("oldest auto-save rotated out", oldest.exists());
        assertNull("its sidecar went with it", s.readBinding(oldest));
    }

    /** Auto-saves do not disturb the quick slot or the player's named saves. */
    @Test public void autoSavesLeaveNamedSavesAlone() throws IOException {
        SaveStateStore s = store();
        File named = s.write("My camp", machine(20000, 1), DiskSnapshotGuard.Fingerprint.empty());
        s.write(s.quickFile(), machine(20000, 2), DiskSnapshotGuard.Fingerprint.empty());
        for (int i = 0; i < 4; i++) s.writeAuto(SaveStateStore.AUTO_PREFIX + "a " + i, machine(20000, i), 2, DiskSnapshotGuard.Fingerprint.empty());
        assertTrue("named save survives", named.exists());
        assertTrue("quick slot survives", s.quickFile().exists());
        assertEquals("two auto-saves kept", 2, s.autoSaves().size());
    }

    @Test public void olderFormatsAreExplicitlyRefused() throws IOException {
        for (char version : new char[]{'1', '2', '3'}) {
            File legacy = new File(tmp.getRoot(), "legacy-" + version + SaveStateStore.EXTENSION);
            java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
            body.write(new byte[]{'P', 'R', 'Q', 'S', (byte)version, '\n'});
            if (version == '3') body.write(0); // empty disk fingerprint
            if (version != '1') body.write(0);
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(body)) {
                gz.write(machine(80000, 6));
            }
            java.nio.file.Files.write(legacy.toPath(), body.toByteArray());
            try { store().read(legacy); fail("Loaded unverified old snapshot"); }
            catch (IOException expected) {
                assertTrue(expected.getMessage().contains("Unsupported older snapshot"));
            }
        }
    }
}
