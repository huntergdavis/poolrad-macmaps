package name.osher.gil.minivmac.hfs;

import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Reading a guest disk image well enough to find, copy and replace a saved game. */
public class HfsVolumeTest {
    private static byte[] pattern(int length, int seed) {
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) out[i] = (byte) (i * 31 + seed);
        return out;
    }

    private static SyntheticVolume volume() {
        return new SyntheticVolume("Test Volume", "Saves",
                new SyntheticVolume.Entry("Alpha", "prdt", "prad",
                        pattern(700, 1), pattern(200, 2), 4, 2, 6, 1),
                new SyntheticVolume.Entry("Beta", "prdt", "prad",
                        pattern(512, 3), new byte[0], 8, 1, 0, 0));
    }

    private static HfsFile named(List<HfsFile> files, String name) {
        for (HfsFile file : files) if (file.name.equals(name)) return file;
        throw new AssertionError("No such file: " + name + " in " + files);
    }

    @Test public void theVolumeNamesItselfAndItsBlockSize() throws IOException {
        HfsVolume volume = HfsVolume.open(volume().blocks());
        assertEquals("Test Volume", volume.volumeName());
        assertEquals(SyntheticVolume.BLOCK, volume.allocationBlockSize());
    }

    @Test public void aFolderIsFoundByNameAndByPath() throws IOException {
        HfsVolume volume = HfsVolume.open(volume().blocks());
        assertEquals(SyntheticVolume.FOLDER_ID, volume.folder(HfsVolume.ROOT, "Saves"));
        assertEquals(SyntheticVolume.FOLDER_ID, volume.folderAt("Saves"));
        assertEquals("a leading colon is the root, not a folder",
                SyntheticVolume.FOLDER_ID, volume.folderAt(":Saves"));
        assertEquals("names are matched the way the Finder matches them",
                SyntheticVolume.FOLDER_ID, volume.folder(HfsVolume.ROOT, "saves"));
        assertEquals(-1, volume.folder(HfsVolume.ROOT, "Nothing"));
        assertEquals(-1, volume.folderAt("Saves:Deeper"));
    }

    @Test public void filesCarryTheirNameTypeCreatorAndForkLengths() throws IOException {
        HfsVolume volume = HfsVolume.open(volume().blocks());
        List<HfsFile> files = volume.files(volume.folderAt("Saves"));
        assertEquals(2, files.size());
        HfsFile alpha = named(files, "Alpha");
        assertEquals("prdt", alpha.type);
        assertEquals("prad", alpha.creator);
        assertEquals(700, alpha.dataLength);
        assertEquals(200, alpha.resourceLength);
        assertEquals(1024, alpha.dataPhysical);
    }

    @Test public void bothForksReadBackExactly() throws IOException {
        HfsVolume volume = HfsVolume.open(volume().blocks());
        List<HfsFile> files = volume.files(volume.folderAt("Saves"));
        HfsFile alpha = named(files, "Alpha");
        assertArrayEquals(pattern(700, 1), volume.readDataFork(alpha));
        assertArrayEquals(pattern(200, 2), volume.readResourceFork(alpha));
        // A fork spanning two allocation blocks is joined, not truncated at one.
        assertEquals(700, volume.readDataFork(alpha).length);
    }

    @Test public void anAbsentForkIsEmptyRatherThanMissing() throws IOException {
        HfsVolume volume = HfsVolume.open(volume().blocks());
        HfsFile beta = named(volume.files(volume.folderAt("Saves")), "Beta");
        assertEquals(0, volume.readResourceFork(beta).length);
        assertArrayEquals(pattern(512, 3), volume.readDataFork(beta));
    }

    @Test public void aForkTooLongForItsOwnExtentsIsRefusedRatherThanReturnedShort() {
        /*
         * Such a fork continues in the extents overflow file, which this does
         * not read. A truncated saved game that looks whole is worse than one
         * that will not open at all.
         */
        SyntheticVolume synthetic = volume();
        // Claim Beta's data fork is far longer than the single block it owns.
        Blocks.Array blocks = (Blocks.Array) synthetic.blocks();
        overwriteLength(blocks.data(), "Beta", 9_000);
        try {
            HfsVolume volume = HfsVolume.open(blocks);
            volume.readDataFork(named(volume.files(volume.folderAt("Saves")), "Beta"));
            fail("A fork needing the extents overflow file was read anyway");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("extents overflow"));
        }
    }

    @Test public void replacingAForkInPlaceChangesTheBytesAndTheRecordedLength() throws IOException {
        SyntheticVolume synthetic = volume();
        Blocks blocks = synthetic.blocks();
        HfsVolume volume = HfsVolume.open(blocks);
        HfsFile alpha = named(volume.files(volume.folderAt("Saves")), "Alpha");

        byte[] replacement = pattern(640, 9);
        volume.writeDataForkInPlace(alpha, replacement);

        HfsVolume reopened = HfsVolume.open(blocks);
        HfsFile again = named(reopened.files(reopened.folderAt("Saves")), "Alpha");
        assertEquals(640, again.dataLength);
        assertArrayEquals(replacement, reopened.readDataFork(again));
        assertArrayEquals("the other fork is untouched", pattern(200, 2), reopened.readResourceFork(again));
        assertArrayEquals("and so is the other file",
                pattern(512, 3), reopened.readDataFork(named(reopened.files(reopened.folderAt("Saves")), "Beta")));
    }

    @Test public void aForkThatWouldNeedAnotherBlockIsRefused() throws IOException {
        SyntheticVolume synthetic = volume();
        HfsVolume volume = HfsVolume.open(synthetic.blocks());
        HfsFile beta = named(volume.files(volume.folderAt("Saves")), "Beta");
        byte[] before = volume.readDataFork(beta);
        try {
            volume.writeDataForkInPlace(beta, pattern(1024, 4));   // owns one 512-byte block
            fail("A write past the blocks the file owns was allowed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("does not allocate blocks"));
        }
        assertArrayEquals("a refused write changes nothing", before, volume.readDataFork(beta));
    }

    @Test public void aReadOnlyImageRefusesEveryWrite() throws IOException {
        SyntheticVolume synthetic = volume();
        HfsVolume volume = HfsVolume.open(new Blocks.ReadOnly(synthetic.blocks()));
        HfsFile alpha = named(volume.files(volume.folderAt("Saves")), "Alpha");
        try { volume.writeDataForkInPlace(alpha, pattern(512, 5)); fail("wrote to a read-only image"); }
        catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("read-only"));
        }
    }

    @Test public void somethingThatIsNotAnHfsVolumeIsRefusedAtOnce() {
        try { HfsVolume.open(new Blocks.Array(new byte[8192])); fail("opened a blank image as HFS"); }
        catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Not an HFS volume"));
        }
    }

    /** Reach into the synthetic image and change one file's recorded data length. */
    private static void overwriteLength(byte[] image, String name, int length) {
        int node = SyntheticVolume.blockOffset(SyntheticVolume.CATALOG_BLOCK) + SyntheticVolume.SECTOR;
        for (int at = 14; at < SyntheticVolume.SECTOR - 40; at++) {
            int keyLength = image[node + at] & 255;
            if (keyLength != 6 + name.length()) continue;
            int nameLength = image[node + at + 6] & 255;
            if (nameLength != name.length()) continue;
            boolean same = true;
            for (int i = 0; i < nameLength && same; i++) same = image[node + at + 7 + i] == (byte) name.charAt(i);
            if (!same) continue;
            int d = at + keyLength + 1; if ((d & 1) != 0) d++;
            int base = node + d;
            image[base + 26] = (byte) (length >>> 24); image[base + 27] = (byte) (length >>> 16);
            image[base + 28] = (byte) (length >>> 8);  image[base + 29] = (byte) length;
            return;
        }
        throw new AssertionError("No record for " + name + " in the synthetic catalog");
    }
}
