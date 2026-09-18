package name.osher.gil.minivmac.hfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Enough HFS to find the game's own saved games on a guest disk image, copy
 * them off, and put them back.
 *
 * Deliberately not a filesystem. It reads the master directory block, walks the
 * catalog B-tree's leaves, resolves a path to a folder, and reads a file's two
 * forks from the three extent descriptors the catalog record carries. It
 * allocates nothing, creates nothing and deletes nothing.
 *
 * Writing is in place only: the bytes of a fork may be replaced where they
 * already sit, and the logical length in the catalog record updated to match.
 * A fork that would need more blocks than it already owns is refused. That is
 * the whole of it, and the restriction is the point -- allocating blocks means
 * touching the volume bitmap and possibly the catalog's shape, and getting that
 * wrong on a 64 MB disk with somebody's campaign on it is not a bug you can
 * apologise for. Every saved game this is for is the same size as the one it
 * replaces.
 *
 * A fork whose three extents do not cover its length lives partly in the
 * extents overflow file, which this does not read. Such a fork is refused
 * rather than returned short, because a truncated saved game that looks whole
 * is worse than one that will not open.
 */
public final class HfsVolume {
    private static final int MDB_OFFSET = 1024;
    private static final int SIGNATURE = 0x4244;   // 'BD'
    /** The catalog's root folder is always CNID 2. */
    public static final int ROOT = 2;

    private final Blocks blocks;
    private final int allocationBlockSize, firstAllocationBlock, allocationBlocks;
    private final String volumeName;
    private final byte[] catalog;
    /** Where the catalog's own blocks are, so a record can be written back. */
    private final HfsFile.Extent[] catalogExtents;

    private HfsVolume(Blocks blocks, int allocationBlockSize, int firstAllocationBlock,
                      int allocationBlocks, String volumeName, byte[] catalog,
                      HfsFile.Extent[] catalogExtents) {
        this.blocks = blocks; this.allocationBlockSize = allocationBlockSize;
        this.firstAllocationBlock = firstAllocationBlock; this.allocationBlocks = allocationBlocks;
        this.volumeName = volumeName; this.catalog = catalog; this.catalogExtents = catalogExtents;
    }

    public String volumeName() { return volumeName; }
    public int allocationBlockSize() { return allocationBlockSize; }

    public static HfsVolume open(Blocks blocks) throws IOException {
        byte[] mdb = new byte[512];
        blocks.read(MDB_OFFSET, mdb, 0, mdb.length);
        if (u16(mdb, 0) != SIGNATURE) throw new IOException("Not an HFS volume");
        int allocationBlocks = u16(mdb, 18);
        long blockSize = u32(mdb, 20);
        int firstBlock = u16(mdb, 28);
        if (blockSize <= 0 || blockSize % 512 != 0 || blockSize > 1 << 20)
            throw new IOException("Improbable allocation block size: " + blockSize);
        int nameLength = mdb[36] & 255;
        if (nameLength > 27) throw new IOException("Improbable volume name length: " + nameLength);
        String name = macRoman(mdb, 37, nameLength);

        long catalogLength = u32(mdb, 146);
        if (catalogLength <= 0 || catalogLength > 64L << 20)
            throw new IOException("Improbable catalog size: " + catalogLength);
        HfsFile.Extent[] catalogExtents = extents(mdb, 150);

        HfsVolume reading = new HfsVolume(blocks, (int) blockSize, firstBlock, allocationBlocks,
                name, null, catalogExtents);
        byte[] catalog = reading.readFork(catalogExtents, catalogLength, "catalog");
        return new HfsVolume(blocks, (int) blockSize, firstBlock, allocationBlocks,
                name, catalog, catalogExtents);
    }

    // ---- the catalog ----------------------------------------------------

    /** One leaf record, and where in the image it lives. */
    private static final class Leaf {
        final byte[] bytes; final long offset;
        Leaf(byte[] bytes, long offset) { this.bytes = bytes; this.offset = offset; }
    }

    /** Every leaf record in the catalog B-tree, in order. */
    private List<Leaf> leafRecords() throws IOException {
        List<Leaf> records = new ArrayList<>();
        if (catalog == null || catalog.length < 512) throw new IOException("No catalog to read");
        int nodeSize = u16(catalog, 14 + 18);
        int firstLeaf = (int) u32(catalog, 14 + 10);
        if (nodeSize < 512 || nodeSize > 32768 || Integer.bitCount(nodeSize) != 1)
            throw new IOException("Improbable catalog node size: " + nodeSize);
        int node = firstLeaf, guard = 0;
        int nodes = catalog.length / nodeSize;
        while (node != 0 && guard++ <= nodes) {
            long base = (long) node * nodeSize;
            if (base + nodeSize > catalog.length) throw new IOException("Catalog node past the file");
            int at = (int) base;
            int forward = (int) u32(catalog, at);
            int count = u16(catalog, at + 10);
            if (count < 0 || count > nodeSize / 4) throw new IOException("Improbable record count");
            int[] offsets = new int[count + 1];
            for (int i = 0; i <= count; i++) offsets[i] = u16(catalog, at + nodeSize - 2 * (i + 1));
            for (int i = 0; i < count; i++) {
                int from = offsets[i], to = offsets[i + 1];
                if (from < 14 || to < from || to > nodeSize) continue;
                byte[] record = new byte[to - from];
                System.arraycopy(catalog, at + from, record, 0, record.length);
                records.add(new Leaf(record, base + from));
            }
            node = forward;
        }
        return records;
    }

    /** The folder id `name` has inside `parent`, or -1. */
    public int folder(int parent, String name) throws IOException {
        for (Leaf leaf : leafRecords()) {
            Catalog entry = Catalog.of(leaf.bytes);
            if (entry == null || entry.kind != 1) continue;
            if (entry.parent == parent && entry.name.equalsIgnoreCase(name))
                return (int) u32(leaf.bytes, entry.dataAt + 6);
        }
        return -1;
    }

    /** Walk a colon path from the root, e.g. "Pool Of Radiance:PoolRadSave". */
    public int folderAt(String path) throws IOException {
        int at = ROOT;
        for (String part : path.split(":")) {
            if (part.isEmpty()) continue;
            at = folder(at, part);
            if (at < 0) return -1;
        }
        return at;
    }

    /** Every file directly inside a folder. */
    public List<HfsFile> files(int parent) throws IOException {
        List<HfsFile> files = new ArrayList<>();
        for (Leaf leaf : leafRecords()) {
            byte[] record = leaf.bytes;
            Catalog entry = Catalog.of(record);
            if (entry == null || entry.kind != 2 || entry.parent != parent) continue;
            int d = entry.dataAt;
            if (d + 98 > record.length) throw new IOException("Truncated file record for " + entry.name);
            files.add(new HfsFile(entry.name,
                    (int) u32(record, d + 20), entry.parent,
                    macRoman(record, d + 4, 4), macRoman(record, d + 8, 4),
                    u32(record, d + 26), u32(record, d + 30),
                    u32(record, d + 36), u32(record, d + 40),
                    extents(record, d + 74), extents(record, d + 86),
                    leaf.offset + d));
        }
        return files;
    }

    // ---- forks ----------------------------------------------------------

    public byte[] readDataFork(HfsFile file) throws IOException {
        return readFork(file.dataExtents, file.dataLength, file.name + " data fork");
    }

    public byte[] readResourceFork(HfsFile file) throws IOException {
        return readFork(file.resourceExtents, file.resourceLength, file.name + " resource fork");
    }

    private byte[] readFork(HfsFile.Extent[] extents, long length, String what) throws IOException {
        if (length == 0) return new byte[0];
        if (length < 0 || length > 64L << 20) throw new IOException("Improbable length for " + what);
        long covered = 0;
        for (HfsFile.Extent extent : extents) covered += (long) extent.count * allocationBlockSize;
        if (covered < length)
            throw new IOException(what + " continues in the extents overflow file, which is not read");
        byte[] out = new byte[(int) length];
        int written = 0;
        for (HfsFile.Extent extent : extents) {
            for (int i = 0; i < extent.count && written < out.length; i++) {
                int take = Math.min(allocationBlockSize, out.length - written);
                blocks.read(blockOffset(extent.start + i), out, written, take);
                written += take;
            }
        }
        return out;
    }

    /**
     * Replace a data fork where it already sits. Refuses anything that would
     * need a block the file does not already own.
     */
    public void writeDataForkInPlace(HfsFile file, byte[] content) throws IOException {
        writeForkInPlace(file, content, true);
    }

    /** The same for the resource fork, which a Macintosh file is half made of. */
    public void writeResourceForkInPlace(HfsFile file, byte[] content) throws IOException {
        writeForkInPlace(file, content, false);
    }

    /**
     * Whether a file could take these two forks back without needing a block it
     * does not already own. Asked before writing either, so a restore never
     * gets half way and stops.
     */
    public boolean fits(HfsFile file, byte[] data, byte[] resource) {
        return data.length <= owned(file.dataExtents) && resource.length <= owned(file.resourceExtents);
    }

    private long owned(HfsFile.Extent[] extents) {
        long total = 0;
        for (HfsFile.Extent extent : extents) total += (long) extent.count * allocationBlockSize;
        return total;
    }

    private void writeForkInPlace(HfsFile file, byte[] content, boolean dataFork) throws IOException {
        if (content == null) throw new IOException("Nothing to write");
        HfsFile.Extent[] extents = dataFork ? file.dataExtents : file.resourceExtents;
        long owned = owned(extents);
        if (content.length > owned)
            throw new IOException(file.name + " would need " + content.length
                    + " bytes but owns only " + owned + "; this does not allocate blocks");
        int written = 0;
        for (HfsFile.Extent extent : extents) {
            for (int i = 0; i < extent.count && written < content.length; i++) {
                int take = Math.min(allocationBlockSize, content.length - written);
                blocks.write(blockOffset(extent.start + i), content, written, take);
                written += take;
            }
        }
        // The catalog's own idea of how long the fork is, updated last, so a
        // failure part-way leaves a short file rather than a long lie. The
        // record's offset is inside the catalog fork, which is itself scattered
        // across the volume, so it has to be written the same way as any other
        // fork rather than at that offset in the image.
        byte[] length = new byte[4];
        put32(length, content.length);
        writeIntoFork(catalogExtents, file.recordOffset + (dataFork ? 26 : 36), length);
    }

    /**
     * Write a few bytes at an offset within a fork, following its extents. The
     * catalog is scattered like any other file, and a record can sit across the
     * seam between two of its blocks.
     */
    private void writeIntoFork(HfsFile.Extent[] extents, long offset, byte[] content) throws IOException {
        if (offset < 0) throw new IOException("Negative offset into a fork");
        long at = 0;
        int written = 0;
        for (HfsFile.Extent extent : extents) {
            for (int i = 0; i < extent.count && written < content.length; i++) {
                long blockStart = at, blockEnd = at + allocationBlockSize;
                at = blockEnd;
                long from = offset + written;
                if (from >= blockEnd || from < blockStart) continue;
                int inside = (int) (from - blockStart);
                int take = Math.min(allocationBlockSize - inside, content.length - written);
                blocks.write(blockOffset(extent.start + i) + inside, content, written, take);
                written += take;
            }
        }
        if (written != content.length)
            throw new IOException("A catalog record lies outside the catalog's own blocks");
    }

    private long blockOffset(int block) throws IOException {
        if (block < 0 || block >= allocationBlocks)
            throw new IOException("Allocation block " + block + " is outside the volume");
        return (long) firstAllocationBlock * 512 + (long) block * allocationBlockSize;
    }

    // ---- bytes ----------------------------------------------------------

    /** One catalog record's key and where its data begins. */
    private static final class Catalog {
        final int parent, kind, dataAt;
        final String name;
        private Catalog(int parent, String name, int kind, int dataAt) {
            this.parent = parent; this.name = name; this.kind = kind; this.dataAt = dataAt;
        }
        static Catalog of(byte[] record) {
            if (record.length < 8) return null;
            int keyLength = record[0] & 255;
            if (keyLength < 6 || keyLength + 1 > record.length) return null;
            int parent = (int) u32(record, 2);
            int nameLength = record[6] & 255;
            if (7 + nameLength > record.length || nameLength > 31) return null;
            int at = keyLength + 1;
            if ((at & 1) != 0) at++;   // records are even-aligned
            if (at >= record.length) return null;
            return new Catalog(parent, macRoman(record, 7, nameLength), record[at] & 255, at);
        }
    }

    private static HfsFile.Extent[] extents(byte[] data, int at) {
        HfsFile.Extent[] out = new HfsFile.Extent[3];
        for (int i = 0; i < 3; i++)
            out[i] = new HfsFile.Extent(u16(data, at + i * 4), u16(data, at + i * 4 + 2));
        return out;
    }

    private static int u16(byte[] data, int at) {
        return ((data[at] & 255) << 8) | (data[at + 1] & 255);
    }

    private static long u32(byte[] data, int at) {
        return ((long) (data[at] & 255) << 24) | ((data[at + 1] & 255) << 16)
                | ((data[at + 2] & 255) << 8) | (data[at + 3] & 255);
    }

    private static void put32(byte[] data, long value) {
        data[0] = (byte) (value >>> 24); data[1] = (byte) (value >>> 16);
        data[2] = (byte) (value >>> 8); data[3] = (byte) value;
    }

    /**
     * Mac Roman, near enough: every character a Macintosh file name in this
     * game uses is ASCII, and anything above it is shown as its own byte value
     * rather than guessed at.
     */
    private static String macRoman(byte[] data, int at, int length) {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length && at + i < data.length; i++) {
            int value = data[at + i] & 255;
            text.append(value < 0x80 ? (char) value : (char) ('' + value));
        }
        return text.toString();
    }
}
