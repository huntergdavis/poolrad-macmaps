package name.osher.gil.minivmac.hfs;

/**
 * A tiny HFS volume built from the specification, so the reader's offsets are
 * pinned by something other than the reader.
 *
 * Deliberately not a copy of anybody's disk: no game data, no saved game and no
 * file name from a real volume appears here. The layout is the smallest thing
 * that is still genuinely HFS — a master directory block, a catalog B-tree with
 * a header node and one leaf, one folder and the files inside it.
 */
public final class SyntheticVolume {
    public static final int SECTOR = 512, BLOCK = 512, FIRST_BLOCK_SECTOR = 8, BLOCKS = 64;
    public static final int ROOT = 2, FOLDER_ID = 17;
    public static final int CATALOG_BLOCK = 0, CATALOG_BLOCKS = 2;
    public static final int DATA_BLOCK = 4;

    public final byte[] image = new byte[FIRST_BLOCK_SECTOR * SECTOR + BLOCKS * BLOCK];
    private final StringBuilder leaf = new StringBuilder();

    /** One file to place in the folder. */
    public static final class Entry {
        final String name, type, creator;
        final byte[] data, resource;
        final int dataBlock, dataBlocks, resourceBlock, resourceBlocks;
        public Entry(String name, String type, String creator, byte[] data, byte[] resource,
                     int dataBlock, int dataBlocks, int resourceBlock, int resourceBlocks) {
            this.name = name; this.type = type; this.creator = creator;
            this.data = data; this.resource = resource;
            this.dataBlock = dataBlock; this.dataBlocks = dataBlocks;
            this.resourceBlock = resourceBlock; this.resourceBlocks = resourceBlocks;
        }
    }

    public SyntheticVolume(String volumeName, String folderName, Entry... entries) {
        writeMasterDirectoryBlock(volumeName);
        writeCatalog(folderName, entries);
        for (Entry entry : entries) {
            place(entry.data, entry.dataBlock);
            place(entry.resource, entry.resourceBlock);
        }
    }

    public Blocks blocks() { return new Blocks.Array(image); }

    private void place(byte[] content, int block) {
        if (content == null || content.length == 0) return;
        System.arraycopy(content, 0, image, blockOffset(block), content.length);
    }

    public static int blockOffset(int block) { return FIRST_BLOCK_SECTOR * SECTOR + block * BLOCK; }

    private void writeMasterDirectoryBlock(String name) {
        int at = 2 * SECTOR;
        image[at] = 'B'; image[at + 1] = 'D';
        put16(at + 18, BLOCKS);                 // drNmAlBlks
        put32(at + 20, BLOCK);                  // drAlBlkSiz
        put16(at + 28, FIRST_BLOCK_SECTOR);     // drAlBlSt
        image[at + 36] = (byte) name.length();  // drVN
        for (int i = 0; i < name.length(); i++) image[at + 37 + i] = (byte) name.charAt(i);
        put32(at + 146, CATALOG_BLOCKS * BLOCK);          // drCTFlSize
        put16(at + 150, CATALOG_BLOCK); put16(at + 152, CATALOG_BLOCKS);  // drCTExtRec
    }

    private void writeCatalog(String folderName, Entry[] entries) {
        int catalog = blockOffset(CATALOG_BLOCK);
        // Node 0: the B-tree header, naming the node size and the first leaf.
        image[catalog + 8] = 1;                 // ndType: header
        put16(catalog + 10, 3);                 // ndNRecs
        put16(catalog + 14, 1);                 // bthDepth
        put32(catalog + 16, 1);                 // bthRoot
        put32(catalog + 24, 1);                 // bthFNode: the leaf below
        put32(catalog + 28, 1);                 // bthLNode
        put16(catalog + 32, SECTOR);            // bthNodeSize
        put16(catalog + 34, 37);                // bthKeyLen

        // Node 1: one leaf holding the folder and its files.
        int node = catalog + SECTOR;
        image[node + 8] = (byte) 0xff;          // ndType: leaf
        image[node + 9] = 1;                    // ndNHeight
        put16(node + 10, 1 + entries.length);

        int[] offsets = new int[entries.length + 2];
        int at = 14;
        offsets[0] = at;
        at = folderRecord(node, at, folderName);
        for (int i = 0; i < entries.length; i++) {
            offsets[i + 1] = at;
            at = fileRecord(node, at, entries[i]);
        }
        offsets[entries.length + 1] = at;
        for (int i = 0; i < offsets.length; i++) put16(node + SECTOR - 2 * (i + 1), offsets[i]);
    }

    private int folderRecord(int node, int at, String name) {
        int keyLength = 6 + name.length();
        image[node + at] = (byte) keyLength;
        put32(node + at + 2, ROOT);
        image[node + at + 6] = (byte) name.length();
        for (int i = 0; i < name.length(); i++) image[node + at + 7 + i] = (byte) name.charAt(i);
        int data = at + keyLength + 1;
        if ((data & 1) != 0) data++;
        image[node + data] = 1;                  // cdrType: folder
        put32(node + data + 6, FOLDER_ID);       // dirDirID
        return data + 70;                        // CatDataDirRec is 70 bytes
    }

    private int fileRecord(int node, int at, Entry entry) {
        int keyLength = 6 + entry.name.length();
        image[node + at] = (byte) keyLength;
        put32(node + at + 2, FOLDER_ID);
        image[node + at + 6] = (byte) entry.name.length();
        for (int i = 0; i < entry.name.length(); i++)
            image[node + at + 7 + i] = (byte) entry.name.charAt(i);
        int d = at + keyLength + 1;
        if ((d & 1) != 0) d++;
        int base = node + d;
        image[base] = 2;                                          // cdrType: file
        for (int i = 0; i < 4; i++) image[base + 4 + i] = (byte) entry.type.charAt(i);
        for (int i = 0; i < 4; i++) image[base + 8 + i] = (byte) entry.creator.charAt(i);
        put32(base + 20, 1000 + entry.name.hashCode() % 1000);    // filFlNum
        put16(base + 24, entry.dataBlock);                        // filStBlk
        put32(base + 26, entry.data == null ? 0 : entry.data.length);
        put32(base + 30, (long) entry.dataBlocks * BLOCK);
        put16(base + 34, entry.resourceBlock);                    // filRStBlk
        put32(base + 36, entry.resource == null ? 0 : entry.resource.length);
        put32(base + 40, (long) entry.resourceBlocks * BLOCK);
        put16(base + 74, entry.dataBlock); put16(base + 76, entry.dataBlocks);
        put16(base + 86, entry.resourceBlock); put16(base + 88, entry.resourceBlocks);
        return d + 102;                                           // CatDataFilRec is 102 bytes
    }

    private void put16(int at, int value) {
        image[at] = (byte) (value >>> 8); image[at + 1] = (byte) value;
    }

    private void put32(int at, long value) {
        image[at] = (byte) (value >>> 24); image[at + 1] = (byte) (value >>> 16);
        image[at + 2] = (byte) (value >>> 8); image[at + 3] = (byte) value;
    }
}
