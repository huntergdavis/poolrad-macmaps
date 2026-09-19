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
        File f = s.write("Test", raw);
        assertArrayEquals(raw, s.read(f));
    }

    @Test public void compressionActuallyShrinksATypicalImage() throws IOException {
        SaveStateStore s = store();
        byte[] raw = machine(1_000_000, 3);
        File f = s.write("Big", raw);
        assertTrue("a mostly-repetitive image should compress", f.length() < raw.length);
    }

    @Test public void theQuickSlotIsAFixedFile() throws IOException {
        SaveStateStore s = store();
        s.write(s.quickFile(), machine(1000, 1));
        byte[] again = machine(1000, 2);
        s.write(s.quickFile(), again);   // overwrite the quick slot
        assertArrayEquals(again, s.read(s.quickFile()));
        // The quick file is not double-counted as several named saves.
        assertEquals(1, s.saves().size());
    }

    @Test public void namedSavesNeverClobberEachOther() throws IOException {
        SaveStateStore s = store();
        File a = s.write("Slums", machine(500, 1));
        File b = s.write("Slums", machine(500, 2));
        assertNotEquals(a.getName(), b.getName());
        assertEquals(2, s.saves().size());
    }

    @Test public void savesAreListedNewestFirst() throws IOException {
        SaveStateStore s = store();
        File first = s.write("First", machine(400, 1));
        first.setLastModified(1000);
        File second = s.write("Second", machine(400, 2));
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
        try { store().write("Empty", new byte[0]); fail("wrote an empty state"); }
        catch (IOException expected) { }
    }

    @Test public void aTruncatedFileIsRefused() throws IOException {
        SaveStateStore s = store();
        File f = s.write("Whole", machine(100000, 5));
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
        s.write("First", first);
        byte[] second = first.clone();
        for (int i = 0; i < 500; i++) second[i * 37 % second.length] ^= 0x5a;  // a few changes
        File f = s.write("Second", second);
        assertTrue("a near-identical save should be a small diff, was " + f.length(),
                f.length() < second.length / 20);
        assertArrayEquals(second, s.read(f));
    }

    /** Different images both reconstruct exactly through the one shared reference. */
    @Test public void severalImagesRoundTripThroughOneReference() throws IOException {
        SaveStateStore s = store();
        byte[] a = machine(300000, 1);
        byte[] b = machine(300000, 99);
        File fa = s.write("A", a);
        File fb = s.write("B", b);
        assertArrayEquals(a, s.read(fa));
        assertArrayEquals(b, s.read(fb));
    }

    /** A fresh store reading files written by an earlier one still finds the reference on disk. */
    @Test public void aDiffReadsBackAfterAColdStart() throws IOException {
        byte[] raw = machine(250000, 4);
        File f = store().write("Cold", raw);       // one store writes it
        assertArrayEquals(raw, store().read(f));    // a brand-new store reads it
    }

    /** Losing the reference makes a diff unreadable rather than silently wrong. */
    @Test public void aDiffWithoutItsReferenceIsRefused() throws IOException {
        SaveStateStore writer = store();
        File f = writer.write("Orphan", machine(120000, 8));
        // Delete the reference template that the diff depends on.
        for (File ref : tmp.getRoot().listFiles()) if (ref.getName().endsWith(".prqref")) assertTrue(ref.delete());
        try { store().read(f); fail("read a diff with no reference"); }
        catch (IOException expected) { }
    }

    /** An old whole-image file (the v1 format) still loads. */
    @Test public void aLegacyWholeImageStillLoads() throws IOException {
        byte[] raw = machine(80000, 6);
        File legacy = new File(tmp.getRoot(), "legacy" + SaveStateStore.EXTENSION);
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        body.write(new byte[]{'P', 'R', 'Q', 'S', '1', '\n'});
        try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(body)) { gz.write(raw); }
        java.nio.file.Files.write(legacy.toPath(), body.toByteArray());
        assertArrayEquals(raw, store().read(legacy));
    }
}
