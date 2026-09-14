package name.osher.gil.minivmac.desktop;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded System 7.5.5 appearance editor, not an HFS writer or repair tool.
 * HFS/catalog and resource layouts follow Apple's Inside Macintosh and the
 * existing machfs/macresources readers (Elliot Nunn, MIT); no code is copied.
 * https://dev.os9.ca/techpubs/mac/Files/Files-99.html
 * https://dev.os9.ca/techpubs/mac/QuickDraw/QuickDraw-266.html
 * Only PAT 16 and ppat 16 payload bytes can change, on a separate staged copy.
 */
public final class DesktopDisk {
    private DesktopDisk() {}
    private static final long MAX_DISK = 128L * 1024 * 1024;
    private static final int MAX_TREE = 4 * 1024 * 1024;
    public enum Style { WHITE, MIST, STONE }

    public static byte[] patternBits(Style style) {
        if (style == null) throw new IllegalArgumentException("Desktop style is required");
        switch (style) {
            case MIST: return new byte[]{(byte) 0x88, 0, 0x22, 0, (byte) 0x88, 0, 0x22, 0};
            case STONE: return new byte[]{(byte) 0xff, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                    (byte) 0xff, 8, 8, 8};
            default: return new byte[8];
        }
    }

    public static byte[] colorPattern(byte[] current, byte[] bits) throws IOException {
        validatePattern(current);
        require(bits != null && bits.length == 8, "A desktop bit pattern contains eight bytes");
        byte[] result = current.clone();
        System.arraycopy(bits, 0, result, 20, 8);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x += 2) {
            int row = bits[y] & 255;
            result[78 + y * 4 + x / 2] = (byte) (((row >>> (7 - x)) & 1) * 16
                    + ((row >>> (6 - x)) & 1));
        }
        Arrays.fill(result, 120, 126, (byte) 255); // Palette 0: white.
        Arrays.fill(result, 128, 134, (byte) 0);   // Palette 1: black.
        return result;
    }

    public static final class Inspection {
        private final File source;
        private final String identity, volumeName;
        private final long length;
        private final byte[] pat, ppat;
        private final long[] patOffsets, ppatOffsets;

        private Inspection(File source, String identity, String volumeName, long length,
                           byte[] pat, byte[] ppat, long[] patOffsets, long[] ppatOffsets) {
            this.source = source;
            this.identity = identity;
            this.volumeName = volumeName;
            this.length = length;
            this.pat = pat.clone();
            this.ppat = ppat.clone();
            this.patOffsets = patOffsets;
            this.ppatOffsets = ppatOffsets;
        }
        public String identity() { return identity; }
        public String volumeName() { return volumeName; }
        public long length() { return length; }
        public byte[] pat() { return pat.clone(); }
        public byte[] ppat() { return ppat.clone(); }

        /** Caller owns copying, native shutdown, transaction publication, and backups.
         * An interrupted write affects only the caller's disposable staged copy. */
        public void patch(File stagedCopy, byte[] newPat, byte[] newPpat) throws IOException {
            require(stagedCopy != null, "A separate staged copy is required");
            require(newPat != null && newPat.length == 8, "Unsupported desktop bit pattern");
            newPat = newPat.clone();
            require(newPpat != null, "A desktop pixel pattern is required");
            newPpat = newPpat.clone();
            validatePattern(newPpat);
            require(!source.equals(stagedCopy.getCanonicalFile()), "Desktop patch requires a separate staged copy");
            Inspection fresh = inspect(stagedCopy);
            require(identity.equals(fresh.identity) && length == fresh.length
                    && Arrays.equals(pat, fresh.pat) && Arrays.equals(ppat, fresh.ppat)
                    && Arrays.equals(patOffsets, fresh.patOffsets)
                    && Arrays.equals(ppatOffsets, fresh.ppatOffsets), "Staged desktop settings changed; nothing was written");
            // Never allow this API to alter a palette entry or header it does not own.
            for (int i = 0; i < ppat.length; i++)
                require(appearanceByte(i) || ppat[i] == newPpat[i], "Unsupported desktop resource change");
            try (RandomAccessFile file = new RandomAccessFile(stagedCopy, "rw")) {
                require(file.length() == length, "Staged disk length changed");
                compareAt(file, patOffsets, pat);
                compareAt(file, ppatOffsets, ppat);
                writeAt(file, patOffsets, newPat);
                writeAt(file, ppatOffsets, newPpat);
                file.getFD().sync();
            }
        }
    }

    private static boolean appearanceByte(int i) {
        return i >= 20 && i < 28 || i >= 78 && i < 110
                || i >= 120 && i < 126 || i >= 128 && i < 134;
    }

    private static void validatePattern(byte[] p) throws IOException {
        require(p != null && p.length == 182, "Unsupported System desktop pixel-pattern size");
        require(u16(p, 0) == 1 && u32(p, 2) == 28 && u32(p, 6) == 78
                && u32(p, 10) == 0 && (u16(p, 14) == 0 || u16(p, 14) == 65535) && u32(p, 16) == 0
                && u32(p, 28) == 0 && u16(p, 32) == 0x8004 && u32(p, 34) == 0
                && u16(p, 38) == 8 && u16(p, 40) == 8 && u32(p, 42) == 0
                && u32(p, 46) == 0 && u32(p, 50) == 0x00480000 && u32(p, 54) == 0x00480000
                && u16(p, 58) == 0 && u16(p, 60) == 4 && u16(p, 62) == 1 && u16(p, 64) == 4
                && u32(p, 66) == 0 && u32(p, 70) == 110 && u32(p, 74) == 0
                && u16(p, 114) == 0 && u16(p, 116) == 7, "Unsupported System desktop pixel-pattern layout");
        for (int i = 0; i < 8; i++) require(u16(p, 118 + i * 8) == i, "Unsupported desktop palette indices");
        for (int i = 78; i < 110; i++)
            require(((p[i] & 255) & ~0x11) == 0, "Unsupported desktop pattern colors");
    }

    public static Inspection inspect(File source) throws IOException {
        require(source != null && source.isFile(), "A local raw HFS disk is required");
        File canonical = source.getCanonicalFile();
        File leaf = new File(source.getAbsoluteFile().getParentFile().getCanonicalFile(), source.getName());
        require(canonical.equals(leaf), "Symbolic-link disk files are unsupported");
        long modified = source.lastModified();
        try (RandomAccessFile file = new RandomAccessFile(source, "r")) {
            Reader reader = new Reader(file);
            Inspection result = reader.inspect(canonical);
            require(file.length() == result.length && source.length() == result.length
                    && source.lastModified() == modified, "Disk changed during inspection");
            return result;
        }
    }

    private static final class Extent {
        final long start, length;
        Extent(long start, long length) { this.start = start; this.length = length; }
    }

    private static final class Fork {
        final RandomAccessFile file;
        final long length;
        final List<Extent> extents;
        Fork(RandomAccessFile file, long length, List<Extent> extents) {
            this.file = file; this.length = length; this.extents = extents;
        }
        byte[] read(long offset, int count) throws IOException {
            require(offset >= 0 && count >= 0 && offset <= length - count, "Truncated HFS fork");
            byte[] result = new byte[count];
            long logical = 0;
            int copied = 0;
            for (Extent e : extents) {
                long begin = Math.max(logical, offset), end = Math.min(logical + e.length, offset + count);
                if (end > begin) {
                    file.seek(e.start + begin - logical);
                    int size = (int) (end - begin);
                    file.readFully(result, copied, size);
                    copied += size;
                }
                logical += e.length;
            }
            require(copied == count, "Missing HFS extent data");
            return result;
        }
        long[] positions(long offset, int count) throws IOException {
            require(offset >= 0 && count >= 0 && offset <= length - count, "Truncated resource payload");
            long[] positions = new long[count];
            long logical = 0;
            int found = 0;
            for (Extent e : extents) {
                long begin = Math.max(logical, offset), end = Math.min(logical + e.length, offset + count);
                for (long at = begin; at < end; at++) positions[found++] = e.start + at - logical;
                logical += e.length;
            }
            require(found == count, "Missing resource extents");
            return positions;
        }
    }

    private static final class Reader {
        final RandomAccessFile file;
        final long length, allocationStart;
        final int blockSize, allocationBlocks;
        final byte[] mdb, bitmap;
        final Map<String, byte[]> overflow = new HashMap<>();
        final BitSet claimed = new BitSet();
        Reader(RandomAccessFile file) throws IOException {
            this.file = file;
            length = file.length();
            require(length >= 4096 && length <= MAX_DISK && length % 512 == 0, "Unsupported raw disk size");
            mdb = diskBytes(1024, 162);
            require(u16(mdb, 0) == 0x4244, "A bare HFS disk is required");
            int flags = u16(mdb, 10);
            require((flags & 0x100) != 0 && (flags & 0x8080) == 0, "Shut down the Mac normally before changing its desktop");
            blockSize = (int) u32(mdb, 20);
            allocationBlocks = u16(mdb, 18);
            allocationStart = u16(mdb, 28) * 512L;
            require(blockSize == 512 && allocationBlocks > 0 && allocationStart >= 2048
                    && allocationStart + (long) allocationBlocks * blockSize <= length - 1024,
                    "Unsupported HFS allocation layout");
            int bitmapStart = u16(mdb, 14);
            require(bitmapStart >= 3 && bitmapStart * 512L + (allocationBlocks + 7) / 8 <= allocationStart,
                    "Invalid HFS allocation bitmap bounds");
            bitmap = diskBytes(bitmapStart * 512L, (allocationBlocks + 7) / 8);
        }
        byte[] diskBytes(long offset, int count) throws IOException {
            require(offset >= 0 && count >= 0 && offset <= length - count, "Truncated disk data");
            byte[] bytes = new byte[count]; file.seek(offset); file.readFully(bytes); return bytes;
        }
        String key(long cnid, int kind, int block) { return cnid + ":" + kind + ":" + block; }
        Fork fork(long logicalLength, long allocatedLength, byte[] initial, long cnid, int kind) throws IOException {
            require(logicalLength >= 0 && allocatedLength >= logicalLength && allocatedLength <= length
                    && allocatedLength % blockSize == 0, "Invalid HFS fork lengths");
            int wanted = (int) (allocatedLength / blockSize), accumulated = 0;
            List<Extent> extents = new ArrayList<>();
            byte[] entry = initial;
            do {
                require(entry != null && entry.length == 12, "Missing HFS overflow extent");
                int previous = accumulated;
                boolean ended = false;
                for (int i = 0; i < 12; i += 4) {
                    int start = u16(entry, i), count = u16(entry, i + 2);
                    if (count == 0) { require(start == 0, "Invalid empty HFS extent"); ended = true; continue; }
                    require(!ended && start + count <= allocationBlocks, "Invalid HFS extent bounds");
                    int overlap = claimed.nextSetBit(start);
                    require(overlap < 0 || overlap >= start + count, "Overlapping HFS file allocation");
                    for (int block = start; block < start + count; block++)
                        require((bitmap[block / 8] & (0x80 >>> (block % 8))) != 0,
                                "HFS file references an unallocated block");
                    claimed.set(start, start + count);
                    extents.add(new Extent(allocationStart + (long) start * blockSize, (long) count * blockSize));
                    accumulated += count;
                    require(extents.size() <= 256 && accumulated <= wanted, "Invalid or excessive HFS extents");
                }
                if (accumulated == wanted) break;
                require(accumulated > previous, "HFS extent chain made no progress");
                entry = overflow.get(key(cnid, kind, accumulated));
            } while (true);
            return new Fork(file, logicalLength, extents);
        }
        Fork metadata(long size, byte[] initial, long cnid) throws IOException {
            require(size > 0 && size <= MAX_TREE && size % 512 == 0, "Unsupported HFS metadata tree size");
            return fork(size, size, initial, cnid, 0);
        }
        Inspection inspect(File source) throws IOException {
            Fork extentsTree = metadata(u32(mdb, 130), slice(mdb, 134, 12), 3);
            for (byte[] record : tree(extentsTree)) {
                require(record.length >= 20 && (record[0] & 255) == 7, "Invalid HFS overflow record");
                int kind = record[1] & 255;
                require(kind == 0 || kind == 255, "Invalid HFS fork type");
                String key = key(u32(record, 2), kind, u16(record, 6));
                require(overflow.put(key, slice(record, 8, 12)) == null, "Duplicate HFS overflow entry");
            }
            Fork catalog = metadata(u32(mdb, 146), slice(mdb, 150, 12), 4);
            long blessed = u32(mdb, 92), systemId = -1;
            boolean foundFolder = false;
            Fork system = null;
            Set<Long> fileIds = new HashSet<>();
            for (byte[] record : tree(catalog)) {
                int keyLength = record[0] & 255;
                require(keyLength >= 6 && keyLength <= 37 && record.length >= keyLength + 3,
                        "Invalid HFS catalog key");
                long parent = u32(record, 2);
                int nameLength = record[6] & 255;
                require(nameLength <= 31 && (keyLength == 6 + nameLength
                        || keyLength == 7 + nameLength && record[7 + nameLength] == 0),
                        "Invalid HFS catalog name");
                String name = new String(record, 7, nameLength, StandardCharsets.ISO_8859_1);
                byte[] value = slice(record, (keyLength + 2) & ~1, record.length - ((keyLength + 2) & ~1));
                int type = value[0] & 255;
                if (type == 1) {
                    require(value.length >= 70, "Truncated HFS directory");
                    if (u32(value, 6) == blessed) {
                        require(!foundFolder && parent == 2 && name.equals("System Folder"), "Unsupported System Folder blessing");
                        foundFolder = true;
                    }
                } else if (type == 2) {
                    require(value.length >= 102, "Truncated HFS file record");
                    long cnid = u32(value, 20);
                    require(cnid >= 16 && fileIds.add(cnid) && fileIds.size() <= 4096,
                            "Duplicate or excessive HFS file identities");
                    fork(u32(value, 26), u32(value, 30), slice(value, 74, 12), cnid, 0);
                    Fork resources = fork(u32(value, 36), u32(value, 40), slice(value, 86, 12), cnid, 255);
                    if (parent == blessed && name.equals("System")) {
                        require(system == null && ascii(value, 4, "zsys") && ascii(value, 8, "MACS"), "Ambiguous or unsupported System file");
                        system = resources; systemId = cnid;
                    }
                } else require(type == 3 || type == 4, "Unsupported HFS catalog record");
            }
            require(foundFolder && system != null && system.length <= 16 * 1024 * 1024,
                    "The blessed System 7.5.5 file was not found");
            Map<String, Resource> resources = resources(system);
            Resource version = resources.get("vers:1"), mono = resources.get("PAT :16"), color = resources.get("ppat:16");
            require(version != null && mono != null && color != null, "Required System desktop resources are missing");
            require(version.length >= 12 && version.length <= 4096, "Unsupported System version resource");
            byte[] ver = system.read(version.offset, version.length);
            require(ver[0] == 7 && (ver[1] & 255) == 0x55 && ver[6] == 5 && ascii(ver, 7, "7.5.5"),
                    "Desktop appearance currently supports the supplied System 7.5.5 profile only");
            require(mono.length == 8 && color.length == 182, "Unsupported desktop resource sizes");
            byte[] pat = system.read(mono.offset, 8), ppat = system.read(color.offset, 182);
            validatePattern(ppat);
            int nameLength = mdb[36] & 255;
            require(nameLength > 0 && nameLength <= 27, "Invalid HFS volume name");
            String volume = new String(mdb, 37, nameLength, StandardCharsets.ISO_8859_1);
            String identity = "hfs-" + Long.toHexString(u32(mdb, 2)) + "-" + blessed + "-" + systemId + "-" + hex(slice(mdb, 37, nameLength));
            return new Inspection(source, identity, volume, length, pat, ppat,
                    system.positions(mono.offset, 8), system.positions(color.offset, 182));
        }
    }

    private static final class Resource {
        final long offset;
        final int length;
        Resource(long offset, int length) { this.offset = offset; this.length = length; }
    }

    private static Map<String, Resource> resources(Fork fork) throws IOException {
        byte[] header = fork.read(0, 16);
        long dataOffset = u32(header, 0), mapOffset = u32(header, 4), dataLength = u32(header, 8), mapLength = u32(header, 12);
        require(dataOffset >= 16 && dataLength <= fork.length - dataOffset && mapOffset >= 16
                && mapLength >= 30 && mapLength <= 1024 * 1024 && mapLength <= fork.length - mapOffset
                && (dataOffset + dataLength <= mapOffset || mapOffset + mapLength <= dataOffset), "Invalid resource fork bounds");
        byte[] map = fork.read(mapOffset, (int) mapLength);
        int types = u16(map, 24), names = u16(map, 26);
        require(types >= 28 && names >= 28 && names <= map.length, "Invalid resource map offsets");
        int count = u16(map, types) + 1;
        require(count > 0 && count <= 512 && types + 2 + count * 8 <= map.length, "Unsupported resource type count");
        Map<String, Resource> result = new HashMap<>();
        List<Extent> payloads = new ArrayList<>();
        int total = 0;
        for (int t = 0; t < count; t++) {
            int at = types + 2 + t * 8;
            String kind = new String(map, at, 4, StandardCharsets.ISO_8859_1);
            int number = u16(map, at + 4) + 1, references = types + u16(map, at + 6);
            total += number;
            require(total <= 8192 && references >= types + 2 + count * 8
                    && references + (long) number * 12 <= map.length, "Invalid resource reference list");
            for (int n = 0; n < number; n++) {
                int ref = references + n * 12, id = (short) u16(map, ref), name = u16(map, ref + 2);
                if (name != 65535) {
                    require(names + name < map.length, "Invalid resource name offset");
                    require(names + name + 1 + (map[names + name] & 255) <= map.length, "Truncated resource name");
                }
                long relative = u32(map, ref + 4) & 0xffffff;
                require(relative <= dataLength - 4, "Invalid resource data offset");
                long size = u32(fork.read(dataOffset + relative, 4), 0);
                require(size <= Integer.MAX_VALUE && size <= dataLength - relative - 4, "Truncated resource data");
                require(result.put(kind + ":" + id, new Resource(dataOffset + relative + 4, (int) size)) == null,
                        "Duplicate resource type and ID");
                payloads.add(new Extent(relative, size + 4));
            }
        }
        payloads.sort(Comparator.comparingLong(e -> e.start));
        long end = 0;
        for (Extent p : payloads) { require(p.start >= end, "Overlapping resource payloads"); end = p.start + p.length; }
        return result;
    }

    private static List<byte[]> tree(Fork fork) throws IOException {
        byte[] headerNode = fork.read(0, 512);
        require(headerNode[8] == 1, "Missing HFS B-tree header");
        List<byte[]> headerRecords = nodeRecords(headerNode);
        require(headerRecords.size() == 3 && headerRecords.get(0).length >= 30, "Invalid HFS B-tree header records");
        byte[] header = headerRecords.get(0);
        long count = u32(header, 6), first = u32(header, 10), last = u32(header, 14), nodes = u32(header, 22);
        require(u16(header, 18) == 512 && nodes > 0 && nodes <= fork.length / 512 && count <= 65536,
                "Unsupported HFS B-tree layout");
        List<byte[]> records = new ArrayList<>();
        if (count == 0) { require(first == 0 && last == 0, "Invalid empty HFS tree"); return records; }
        Set<Long> visited = new HashSet<>();
        long current = first, previous = 0;
        while (true) {
            require(current > 0 && current < nodes && visited.add(current), "Cyclic or invalid HFS leaf chain");
            byte[] node = fork.read(current * 512, 512);
            require((node[8] & 255) == 255 && node[9] == 1 && u32(node, 4) == previous,
                    "Invalid HFS leaf linkage");
            records.addAll(nodeRecords(node));
            require(records.size() <= count, "HFS leaf record count mismatch");
            long next = u32(node, 0);
            if (current == last) { require(next == 0, "HFS last leaf links onward"); break; }
            previous = current; current = next;
        }
        require(records.size() == count, "HFS leaf record count mismatch");
        return records;
    }

    private static List<byte[]> nodeRecords(byte[] node) throws IOException {
        int count = u16(node, 10);
        require(count <= 128, "Excessive HFS node records");
        List<byte[]> records = new ArrayList<>();
        int previous = u16(node, 510);
        require(previous >= 14, "Invalid HFS node record start");
        for (int i = 0; i < count; i++) {
            int next = u16(node, 508 - i * 2);
            require(next > previous && next <= 512 - (count + 1) * 2, "Invalid HFS node record bounds");
            records.add(slice(node, previous, next - previous)); previous = next;
        }
        return records;
    }

    private static void compareAt(RandomAccessFile file, long[] positions, byte[] expected) throws IOException {
        for (int i = 0; i < positions.length; i++) { file.seek(positions[i]); require(file.readByte() == expected[i], "Staged desktop changed before writing"); }
    }
    private static void writeAt(RandomAccessFile file, long[] positions, byte[] value) throws IOException {
        for (int i = 0; i < positions.length; i++) { file.seek(positions[i]); file.writeByte(value[i]); }
    }
    private static boolean ascii(byte[] bytes, int offset, String text) throws IOException {
        return Arrays.equals(slice(bytes, offset, text.length()), text.getBytes(StandardCharsets.US_ASCII));
    }
    private static byte[] slice(byte[] b, int offset, int length) throws IOException {
        require(offset >= 0 && length >= 0 && offset <= b.length - length, "Truncated structured disk data");
        return Arrays.copyOfRange(b, offset, offset + length);
    }
    private static int u16(byte[] b, int offset) throws IOException {
        require(offset >= 0 && offset <= b.length - 2, "Truncated disk field");
        return (b[offset] & 255) * 256 + (b[offset + 1] & 255);
    }
    private static long u32(byte[] b, int offset) throws IOException {
        return ((long) u16(b, offset) << 16) | u16(b, offset + 2);
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
