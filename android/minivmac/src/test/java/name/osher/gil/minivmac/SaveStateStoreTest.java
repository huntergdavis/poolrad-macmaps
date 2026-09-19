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
}
