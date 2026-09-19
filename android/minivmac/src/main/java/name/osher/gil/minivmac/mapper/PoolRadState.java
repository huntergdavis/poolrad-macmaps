package name.osher.gil.minivmac.mapper;

import java.util.Arrays;

/** A read-only snapshot of the supported Macintosh v1.1 area-map globals. */
public final class PoolRadState {
    public final int x, y, facing;
    public final GeoMap map;
    public final AreaIdentity area;
    public final boolean hasExplorationMetadata, explorationSafe, explorationProcessing;
    public final long continuityToken;
    /**
     * True only when the game's own position line would read " search".
     * Null-safe default is false; older packets carry no search byte at all,
     * which is reported as not searching rather than as searching.
     */
    public final boolean searching;
    private final byte[] geometry;

    private PoolRadState(byte[] sample, AreaIdentity.Catalog identities) {
        x = sample[130] & 255;
        y = sample[131] & 255;
        facing = (sample[132] & 255) / 2;
        // Byte 1200 exists only in PRM5; 255 means the record was unreadable.
        searching = sample.length > 1200 && sample[1200] == 1;
        geometry = Arrays.copyOfRange(sample, 176, 1200);
        byte[] record = new byte[1026];
        System.arraycopy(geometry, 0, record, 2, geometry.length);
        boolean verifiedPacket = sample[3] != '1';
        hasExplorationMetadata = sample[3] >= '3' && sample[3] <= '6';
        explorationSafe = hasExplorationMetadata && sample[25] == 1 && sample[26] == 1 && sample[27] == 4;
        // Not a position observation. A later settled sample must still share
        // the native epoch; native load/menu/script guards advance it even if
        // the renderer remains the local-view engine throughout the change.
        explorationProcessing = hasExplorationMetadata && sample[25] == 1 && sample[26] == 0 && sample[27] == 4;
        continuityToken = ((sample[28] & 255L) << 24) | ((sample[29] & 255L) << 16)
                | ((sample[30] & 255L) << 8) | (sample[31] & 255L);
        int recordId = verifiedPacket ? ((sample[34]&255)<<8) | (sample[35]&255) : -1;
        map = new GeoMap(recordId, record);
        if(verifiedPacket) area = identities == null ? AreaIdentity.resolveMutable(recordId,map)
                : identities.resolveMutable(recordId,map);
        else area = identities == null ? AreaIdentity.resolve(map) : identities.resolve(map);
    }

    public static PoolRadState parse(byte[] sample) {
        return parse(sample, null);
    }

    // Package-private catalog injection keeps regression fixtures synthetic.
    static PoolRadState parse(byte[] sample, AreaIdentity.Catalog identities) {
        if (sample == null || sample.length < 4) return null;
        if (sample[0] != 'P' || sample[1] != 'R' || sample[2] != 'M'
                || (sample[3] != '1' && sample[3] != '2' && sample[3] != '3'
                    && sample[3] != '4' && sample[3] != '5' && sample[3] != '6')) return null;
        // PRM5 appends one search byte; every earlier version keeps its size.
        if (sample.length != packetSize(sample[3])) return null;
        // PRM4's other modes are status-only, never local coordinates or geometry.
        // MapObservation handles those without making an area snapshot.
        boolean walkMeta = sample[3] >= '4' && sample[3] <= '6';
        if (walkMeta && (sample[24] != 1 || sample[25] != 1
                || (sample[26] != 0 && sample[26] != 1) || sample[27] != 4)) return null;
        if (walkMeta && sample[26] == 1
                && (sample[28] | sample[29] | sample[30] | sample[31]) == 0) return null;
        if(sample[3]!='1') {
            // Native verifies the original map mode and movable state allocation.
            // An explicit untrusted/unknown PRM2 sample may not fall back to PRM1
            // hashing, even if old geometry still happens to match the catalog.
            if(sample[32]!=1 || sample[33]!=1 || sample[34]!=0 || (sample[35]&255)>32) return null;
        }
        int x = sample[130] & 255, y = sample[131] & 255, direction = sample[132] & 255;
        if (x >= 16 || y >= 16 || direction > 6 || (direction & 1) != 0) return null;
        PoolRadState state = new PoolRadState(sample, identities);
        return sample[3]!='1' && state.area==null ? null : state;
    }

    static int packetSize(byte version) {
        return version == '6' ? 1212 : version == '5' ? 1204 : 1200;
    }

    public boolean sameDisplay(PoolRadState other) {
        return other != null && x == other.x && y == other.y && facing == other.facing
                && hasExplorationMetadata == other.hasExplorationMetadata && explorationSafe == other.explorationSafe
                && (area == null ? other.area == null : area.equals(other.area))
                && Arrays.equals(geometry, other.geometry);
    }

    public String positionLabel() { return x + ", " + y + " " + "NESW".charAt(facing); }
    /**
     * The same line with the game's own search marker appended. One letter, to
     * mirror what the original prints, not a second status panel.
     */
    public String positionLabelWithSearch() {
        return searching ? positionLabel() + " S" : positionLabel();
    }
}
