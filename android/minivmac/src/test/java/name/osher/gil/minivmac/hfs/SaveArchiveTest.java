package name.osher.gil.minivmac.hfs;

import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.*;

/** A backup that cannot be restored is not a backup. Invented forks only. */
public class SaveArchiveTest {
    private static byte[] fork(int length, int seed) {
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) out[i] = (byte) (i * 13 + seed);
        return out;
    }

    private static SaveArchive sample() {
        return new SaveArchive("SampleParty", "prdt", "prad", fork(12906, 1), fork(4610, 2));
    }

    @Test public void everythingNeededToPutTheFileBackSurvivesTheRoundTrip() throws IOException {
        SaveArchive original = sample();
        SaveArchive restored = SaveArchive.unpack(original.pack());
        assertEquals("SampleParty", restored.name);
        assertEquals("prdt", restored.type);
        assertEquals("prad", restored.creator);
        assertArrayEquals(fork(12906, 1), restored.data);
        assertArrayEquals(fork(4610, 2), restored.resource);
        assertEquals(12906 + 4610, restored.totalBytes());
    }

    @Test public void aForkOfNothingIsStillAFork() throws IOException {
        SaveArchive empty = new SaveArchive("PoRCharacters", "prch", "prad", new byte[0], fork(324, 3));
        SaveArchive restored = SaveArchive.unpack(empty.pack());
        assertEquals(0, restored.data.length);
        assertArrayEquals(fork(324, 3), restored.resource);
    }

    @Test public void aDamagedBackupIsRefusedRatherThanRestored() {
        // Writing half a saved game over a good one is worse than not restoring.
        byte[] packed = sample().pack();
        for (int at : new int[]{10, packed.length / 2, packed.length - 8}) {
            byte[] damaged = packed.clone();
            damaged[at] ^= 0x40;
            try { SaveArchive.unpack(damaged); fail("restored a backup damaged at " + at); }
            catch (IOException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("damaged")
                        || expected.getMessage().contains("improbable")
                        || expected.getMessage().contains("truncated")
                        || expected.getMessage().contains("does not contain"));
            }
        }
    }

    @Test public void aTruncatedBackupIsRefused() {
        byte[] packed = sample().pack();
        for (int length = 0; length < packed.length; length += 997) {
            try { SaveArchive.unpack(Arrays.copyOf(packed, length)); fail("restored " + length + " bytes"); }
            catch (IOException expected) { }
        }
    }

    @Test public void somethingThatIsNotABackupAtAllIsRefused() {
        try { SaveArchive.unpack(null); fail("null accepted"); } catch (IOException expected) { }
        try { SaveArchive.unpack(new byte[64]); fail("zeroes accepted"); } catch (IOException expected) { }
        try { SaveArchive.unpack("not a backup, just some text here".getBytes()); fail("text accepted"); }
        catch (IOException expected) { }
    }

    @Test public void aFutureVersionSaysSoRatherThanGuessing() {
        byte[] packed = sample().pack();
        packed[5] = 9;
        try { SaveArchive.unpack(packed); fail("a version from the future was read"); }
        catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("not understood"));
        }
    }

    @Test public void impossibleMacintoshFilesAreRefusedWhenMade() {
        byte[] some = fork(16, 4);
        try { new SaveArchive("", "prdt", "prad", some, some); fail("empty name"); }
        catch (IllegalArgumentException expected) { }
        try { new SaveArchive(new String(new char[32]).replace('\0', 'x'), "prdt", "prad", some, some);
            fail("32-character name"); } catch (IllegalArgumentException expected) { }
        try { new SaveArchive("Fine", "prd", "prad", some, some); fail("three-letter type"); }
        catch (IllegalArgumentException expected) { }
        try { new SaveArchive("Fine", "prdt", "prad", null, some); fail("no data fork"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void theArchiveDoesNotShareTheCallersArrays() throws IOException {
        byte[] data = fork(64, 5);
        SaveArchive archive = new SaveArchive("Alpha", "prdt", "prad", data, new byte[0]);
        Arrays.fill(data, (byte) 0);
        assertArrayEquals("mutating the caller's array changed the archive",
                fork(64, 5), SaveArchive.unpack(archive.pack()).data);
    }
}
