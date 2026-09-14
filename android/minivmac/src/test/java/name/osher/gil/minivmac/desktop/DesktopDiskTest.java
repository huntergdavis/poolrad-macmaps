package name.osher.gil.minivmac.desktop;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** Entirely synthetic HFS records; no System, firmware, game, or save bytes. */
public class DesktopDiskTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    static final int GAME_OFFSET = 2048 + 20 * 512;
    private static final int RESOURCE_LENGTH = 2560;

    static byte[] fixture() {
        byte[] disk = new byte[2048 + 128 * 512 + 1024];
        w16(disk, 1024, 0x4244);
        w32(disk, 1026, 0x11223344);
        w16(disk, 1034, 0x100);
        w16(disk, 1038, 3);
        w16(disk, 1042, 128);
        w32(disk, 1044, 512);
        w16(disk, 1052, 4);
        disk[1060] = 9;
        text(disk, 1061, "Test Boot");
        w32(disk, 1116, 173);
        w32(disk, 1154, 1024);
        w16(disk, 1158, 0); w16(disk, 1160, 2);
        w32(disk, 1170, 1024);
        w16(disk, 1174, 2); w16(disk, 1176, 2);
        Arrays.fill(disk, 1536, 1552, (byte) 255);
        System.arraycopy(disk, 1024, disk, disk.length - 1024, 162);

        byte[] extra = new byte[20];
        extra[0] = 7; extra[1] = (byte) 255;
        w32(extra, 2, 190); w16(extra, 6, 3);
        w16(extra, 8, 10); w16(extra, 10, 2);
        System.arraycopy(tree(Arrays.asList(extra)), 0, disk, 2048, 1024);

        byte[] directory = new byte[70];
        directory[0] = 1; w32(directory, 6, 173);
        byte[] system = fileRecord("zsys", "MACS", 190);
        w32(system, 36, RESOURCE_LENGTH); w32(system, 40, RESOURCE_LENGTH);
        w16(system, 86, 4); w16(system, 88, 1);
        w16(system, 90, 6); w16(system, 92, 1);
        w16(system, 94, 8); w16(system, 96, 1);
        byte[] save = fileRecord("TEXT", "TEST", 200);
        w32(save, 26, 512); w32(save, 30, 512);
        w16(save, 74, 20); w16(save, 76, 1);
        byte[] catalog = tree(Arrays.asList(catalogRecord(2, "System Folder", directory),
                catalogRecord(2, "Saved Party", save), catalogRecord(173, "System", system)));
        System.arraycopy(catalog, 0, disk, 3072, catalog.length);
        Arrays.fill(disk, GAME_OFFSET, GAME_OFFSET + 512, (byte) 0x5a);

        byte[] resources = new byte[RESOURCE_LENGTH];
        w32(resources, 0, 256); w32(resources, 4, 2048);
        w32(resources, 8, 1792); w32(resources, 12, 512);
        w32(resources, 256, 8);
        for (int i = 0; i < 8; i++) resources[260 + i] = (byte) (i % 2 == 0 ? 0xaa : 0x55);
        w32(resources, 1496, 182);
        System.arraycopy(pixelPattern(), 0, resources, 1500, 182);
        w32(resources, 1800, 12);
        resources[1804] = 7; resources[1805] = 0x55; resources[1806] = (byte) 0x80;
        resources[1810] = 5; text(resources, 1811, "7.5.5");
        System.arraycopy(resources, 0, resources, 2048, 16);
        w16(resources, 2048 + 24, 28); w16(resources, 2048 + 26, 90);
        w16(resources, 2048 + 28, 2);
        String[] kinds = {"PAT ", "ppat", "vers"};
        int[] ids = {16, 16, 1}, offsets = {0, 1240, 1544};
        for (int i = 0; i < 3; i++) {
            int at = 2048 + 30 + i * 8;
            text(resources, at, kinds[i]); w16(resources, at + 4, 0);
            w16(resources, at + 6, 26 + i * 12);
            at = 2048 + 54 + i * 12;
            w16(resources, at, ids[i]); w16(resources, at + 2, 65535);
            w32(resources, at + 4, offsets[i]);
        }
        for (int i = 0; i < resources.length; i++) disk[resourcePosition(i)] = resources[i];
        return disk;
    }

    private static byte[] pixelPattern() {
        byte[] p = new byte[182];
        w16(p, 0, 1); w32(p, 2, 28); w32(p, 6, 78);
        for (int y = 0; y < 8; y++) {
            p[20 + y] = (byte) (y % 2 == 0 ? 0xaa : 0x55);
            Arrays.fill(p, 78 + y * 4, 82 + y * 4, (byte) (y % 2 == 0 ? 0x10 : 0x01));
        }
        w16(p, 32, 0x8004); w16(p, 38, 8); w16(p, 40, 8);
        w32(p, 50, 0x00480000); w32(p, 54, 0x00480000);
        w16(p, 60, 4); w16(p, 62, 1); w16(p, 64, 4);
        w32(p, 70, 110); w16(p, 116, 7);
        for (int i = 0; i < 8; i++) {
            w16(p, 118 + i * 8, i);
            for (int channel = 0; channel < 3; channel++) w16(p, 120 + i * 8 + channel * 2, 0x2000 + i * 0x1000);
        }
        return p;
    }

    private static int resourcePosition(int logical) {
        if (logical < 512) return 2048 + 4 * 512 + logical;
        if (logical < 1024) return 2048 + 6 * 512 + logical - 512;
        if (logical < 1536) return 2048 + 8 * 512 + logical - 1024;
        return 2048 + 10 * 512 + logical - 1536;
    }
    private static byte[] fileRecord(String type, String creator, int cnid) {
        byte[] value = new byte[102]; value[0] = 2;
        text(value, 4, type); text(value, 8, creator); w32(value, 20, cnid); return value;
    }
    private static byte[] catalogRecord(int parent, String name, byte[] value) {
        int keyLength = 6 + name.length(), valueAt = (keyLength + 2) & ~1;
        byte[] result = new byte[valueAt + value.length];
        result[0] = (byte) keyLength; w32(result, 2, parent); result[6] = (byte) name.length();
        text(result, 7, name); System.arraycopy(value, 0, result, valueAt, value.length); return result;
    }
    private static byte[] tree(List<byte[]> records) {
        byte[] header = new byte[106];
        w16(header, 0, 1); w32(header, 2, 1); w32(header, 6, records.size());
        w32(header, 10, 1); w32(header, 14, 1); w16(header, 18, 512); w32(header, 22, 2);
        byte[] result = new byte[1024];
        node(result, 0, 1, 0, Arrays.asList(header, new byte[128], new byte[256]));
        node(result, 512, 255, 1, records); return result;
    }
    private static void node(byte[] target, int base, int kind, int height, List<byte[]> records) {
        target[base + 8] = (byte) kind; target[base + 9] = (byte) height;
        w16(target, base + 10, records.size());
        int at = 14, index = 0;
        for (byte[] record : records) {
            w16(target, base + 510 - index++ * 2, at);
            System.arraycopy(record, 0, target, base + at, record.length); at += record.length;
        }
        w16(target, base + 510 - index * 2, at);
    }
    private static void w16(byte[] b, int at, int value) { b[at] = (byte) (value >>> 8); b[at + 1] = (byte) value; }
    private static void w32(byte[] b, int at, long value) { w16(b, at, (int) (value >>> 16)); w16(b, at + 2, (int) value); }
    private static void text(byte[] b, int at, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII); System.arraycopy(bytes, 0, b, at, bytes.length);
    }
    private File disk(byte[] bytes) throws IOException {
        File file = temporary.newFile(); Files.write(file.toPath(), bytes); return file;
    }

    @Test public void readsFragmentedSystemResourceAndClonesResults() throws Exception {
        DesktopDisk.Inspection state = DesktopDisk.inspect(disk(fixture()));
        assertEquals("Test Boot", state.volumeName());
        assertEquals(fixture().length, state.length());
        assertTrue(state.identity().contains("11223344-173-190"));
        byte[] p = state.pat(); p[0] = 0;
        assertEquals((byte) 0xaa, state.pat()[0]);
        assertEquals(182, state.ppat().length);
        assertArrayEquals(pixelPattern(), state.ppat());
    }

    @Test public void changesOnlyDesktopPayloadBytesAndRestoresWithoutRewindingSave() throws Exception {
        byte[] original = fixture();
        File source = disk(original), staged = disk(original);
        DesktopDisk.Inspection before = DesktopDisk.inspect(source);
        byte[] bits = DesktopDisk.patternBits(DesktopDisk.Style.MIST);
        byte[] color = DesktopDisk.colorPattern(before.ppat(), bits);
        before.patch(staged, bits, color);
        byte[] expected = original.clone();
        for (int i = 0; i < 8; i++) expected[resourcePosition(260 + i)] = bits[i];
        for (int i = 0; i < 182; i++) expected[resourcePosition(1500 + i)] = color[i];
        assertArrayEquals(expected, Files.readAllBytes(staged.toPath()));
        assertArrayEquals(original, Files.readAllBytes(source.toPath()));
        expected[GAME_OFFSET] = 99;
        Files.write(staged.toPath(), expected);
        File restore = disk(expected);
        DesktopDisk.inspect(staged).patch(restore, before.pat(), before.ppat());
        original[GAME_OFFSET] = 99;
        assertArrayEquals(original, Files.readAllBytes(restore.toPath()));
    }

    @Test public void whiteMistAndStoneHaveConsistentMonoAndColorBits() throws Exception {
        for (DesktopDisk.Style style : DesktopDisk.Style.values()) {
            byte[] bits = DesktopDisk.patternBits(style), p = DesktopDisk.colorPattern(pixelPattern(), bits);
            assertArrayEquals(bits, Arrays.copyOfRange(p, 20, 28));
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                int pixel = (p[78 + y * 4 + x / 2] >>> (x % 2 == 0 ? 4 : 0)) & 15;
                assertEquals((bits[y] >>> (7 - x)) & 1, pixel);
            }
            assertArrayEquals(new byte[]{-1, -1, -1, -1, -1, -1}, Arrays.copyOfRange(p, 120, 126));
            assertArrayEquals(new byte[6], Arrays.copyOfRange(p, 128, 134));
        }
    }

    @Test public void refusesOriginalPathAndStaleCopiesBeforeAnyWrite() throws Exception {
        File original = disk(fixture());
        DesktopDisk.Inspection inspection = DesktopDisk.inspect(original);
        byte[] bits = DesktopDisk.patternBits(DesktopDisk.Style.WHITE);
        byte[] color = DesktopDisk.colorPattern(inspection.ppat(), bits);
        assertThrows(IOException.class, () -> inspection.patch(original, bits, color));
        int[] changedOffsets = {1026, 1061, resourcePosition(260), resourcePosition(1500 + 20)};
        for (int offset : changedOffsets) {
            byte[] changed = fixture(); changed[offset] ^= 1;
            File copy = disk(changed);
            assertThrows(IOException.class, () -> inspection.patch(copy, bits, color));
            assertArrayEquals(changed, Files.readAllBytes(copy.toPath()));
        }
    }

    @Test public void forbidsChangesToUnusedPaletteEntriesAndStructure() throws Exception {
        File original = disk(fixture());
        DesktopDisk.Inspection inspection = DesktopDisk.inspect(original);
        for (int offset : new int[]{14, 110, 136}) {
            byte[] changed = inspection.ppat(); changed[offset] ^= 1;
            File stage = disk(fixture());
            assertThrows(IOException.class, () -> inspection.patch(stage, inspection.pat(), changed));
            assertArrayEquals(fixture(), Files.readAllBytes(stage.toPath()));
        }
    }

    @Test public void rejectsDirtyLockedPartitionedTruncatedAndWrongAllocationImages() throws Exception {
        int[] offsets = {1024, 1034, 1035, 1044, 1052};
        int[] values = {0, 0, 0x80, 1, 0x7f};
        for (int i = 0; i < offsets.length; i++) {
            byte[] bytes = fixture(); bytes[offsets[i]] = (byte) values[i];
            File bad = disk(bytes);
            assertThrows(IOException.class, () -> DesktopDisk.inspect(bad));
        }
        File truncated = disk(Arrays.copyOf(fixture(), fixture().length - 1));
        assertThrows(IOException.class, () -> DesktopDisk.inspect(truncated));
    }

    @Test public void rejectsWrongBlessingVersionOrPixelFormat() throws Exception {
        byte[] wrongBlessing = fixture(); w32(wrongBlessing, 1116, 999);
        File bad = disk(wrongBlessing);
        assertThrows(IOException.class, () -> DesktopDisk.inspect(bad));
        for (int logical : new int[]{1805, 1500, 1502, 1500 + 32, 1500 + 60, 1500 + 78}) {
            byte[] bytes = fixture(); bytes[resourcePosition(logical)] = 3;
            File file = disk(bytes);
            assertThrows(IOException.class, () -> DesktopDisk.inspect(file));
        }
    }

    @Test public void rejectsMissingAndDuplicatedOverflowExtents() throws Exception {
        byte[] missing = fixture(); w32(missing, 2048 + 512 + 14 + 2, 999);
        File absent = disk(missing);
        assertThrows(IOException.class, () -> DesktopDisk.inspect(absent));
        byte[] duplicate = fixture();
        byte[] entry = Arrays.copyOfRange(duplicate, 2048 + 512 + 14, 2048 + 512 + 34);
        System.arraycopy(tree(Arrays.asList(entry, entry)), 0, duplicate, 2048, 1024);
        File repeated = disk(duplicate);
        assertThrows(IOException.class, () -> DesktopDisk.inspect(repeated));
    }

    @Test public void rejectsOverlappingSystemSaveAndMetadataAllocation() throws Exception {
        int firstDirectoryLength = catalogRecord(2, "System Folder", new byte[70]).length;
        int saveValue = 3072 + 512 + 14 + firstDirectoryLength + ((8 + "Saved Party".length()) & ~1);
        byte[] collision = fixture(); w16(collision, saveValue + 74, 4);
        File otherFile = disk(collision);
        assertThrows(IOException.class, () -> DesktopDisk.inspect(otherFile));
        byte[] metadata = fixture(); w16(metadata, saveValue + 74, 0);
        File overlap = disk(metadata);
        assertThrows(IOException.class, () -> DesktopDisk.inspect(overlap));
        byte[] free = fixture(); free[1537] &= 0x7f;
        File unallocated = disk(free);
        assertThrows(IOException.class, () -> DesktopDisk.inspect(unallocated));
    }

    @Test public void rejectsBadLeafLinksNodeSizesAndRecordBounds() throws Exception {
        int[][] corruptions = {{3072 + 512, 1}, {3072 + 512 + 4, 1}, {3072 + 14 + 10, 99},
                {3072 + 14 + 18, 256}, {3072 + 512 + 510, 2}};
        for (int[] change : corruptions) {
            byte[] bytes = fixture();
            if (change[0] == 3072 + 14 + 18 || change[0] == 3072 + 512 + 510)
                w16(bytes, change[0], change[1]);
            else w32(bytes, change[0], change[1]);
            File bad = disk(bytes);
            assertThrows(IOException.class, () -> DesktopDisk.inspect(bad));
        }
    }

    @Test public void rejectsDuplicateMissingOverlappingAndOutOfRangeResources() throws Exception {
        byte[] duplicate = fixture();
        byte[] name = "PAT ".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < name.length; i++) duplicate[resourcePosition(2048 + 38 + i)] = name[i];
        File duplicateFile = disk(duplicate);
        assertThrows(IOException.class, () -> DesktopDisk.inspect(duplicateFile));
        byte[] missing = fixture(); missing[resourcePosition(2048 + 38)] = 'X';
        File missingFile = disk(missing);
        assertThrows(IOException.class, () -> DesktopDisk.inspect(missingFile));
        byte[] overlap = fixture();
        // ppat record points at the existing PAT resource's length/data.
        for (int i = 0; i < 4; i++) overlap[resourcePosition(2048 + 66 + 4 + i)] = 0;
        File overlapFile = disk(overlap);
        assertThrows(IOException.class, () -> DesktopDisk.inspect(overlapFile));
        byte[] out = fixture(); out[resourcePosition(2048 + 66 + 5)] = (byte) 255;
        File outFile = disk(out);
        assertThrows(IOException.class, () -> DesktopDisk.inspect(outFile));
    }

    @Test public void acceptsBothLegalCatalogKeyPaddingConventions() throws Exception {
        byte[] padded = fixture();
        int systemRecord = 3072 + 512 + 14
                + catalogRecord(2, "System Folder", new byte[70]).length
                + catalogRecord(2, "Saved Party", new byte[102]).length;
        assertEquals(12, padded[systemRecord]);
        padded[systemRecord] = 13; // hfsutils includes its trailing zero in key length.
        assertEquals(DesktopDisk.inspect(disk(fixture())).identity(), DesktopDisk.inspect(disk(padded)).identity());
    }

    @Test public void rejectsSymbolicLinkDiskWithoutTouchingTarget() throws Exception {
        File original = disk(fixture());
        File link = new File(temporary.getRoot(), "alias.dsk");
        try { Files.createSymbolicLink(link.toPath(), original.toPath()); }
        catch (UnsupportedOperationException | IOException failure) {
            org.junit.Assume.assumeNoException(failure);
        }
        assertThrows(IOException.class, () -> DesktopDisk.inspect(link));
        assertArrayEquals(fixture(), Files.readAllBytes(original.toPath()));
    }
}
