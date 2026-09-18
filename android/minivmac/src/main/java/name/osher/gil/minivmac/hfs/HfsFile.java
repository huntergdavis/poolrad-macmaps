package name.osher.gil.minivmac.hfs;

import java.util.Arrays;

/** One file in an HFS catalog, as the catalog describes it. */
public final class HfsFile {
    /** Where a fork's blocks are: up to three {startBlock, blockCount} pairs. */
    public static final class Extent {
        public final int start, count;
        Extent(int start, int count) { this.start = start; this.count = count; }
        @Override public String toString() { return start + "+" + count; }
    }

    public final String name;
    public final int id, parent;
    public final String type, creator;
    public final long dataLength, dataPhysical, resourceLength, resourcePhysical;
    final Extent[] dataExtents, resourceExtents;
    /** Where in the image this file's catalog record sits, for length updates. */
    final long recordOffset;

    HfsFile(String name, int id, int parent, String type, String creator,
            long dataLength, long dataPhysical, long resourceLength, long resourcePhysical,
            Extent[] dataExtents, Extent[] resourceExtents, long recordOffset) {
        this.name = name; this.id = id; this.parent = parent;
        this.type = type; this.creator = creator;
        this.dataLength = dataLength; this.dataPhysical = dataPhysical;
        this.resourceLength = resourceLength; this.resourcePhysical = resourcePhysical;
        this.dataExtents = dataExtents; this.resourceExtents = resourceExtents;
        this.recordOffset = recordOffset;
    }

    public Extent[] dataExtents() { return Arrays.copyOf(dataExtents, dataExtents.length); }
    public Extent[] resourceExtents() { return Arrays.copyOf(resourceExtents, resourceExtents.length); }

    @Override public String toString() {
        return name + " (" + type + "/" + creator + ", " + dataLength + " + " + resourceLength + ")";
    }
}
