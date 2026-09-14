package name.osher.gil.minivmac.mapper;

import java.util.Arrays;

/** A read-only snapshot of the supported Macintosh v1.1 area-map globals. */
public final class PoolRadState {
    public final int x, y, facing;
    public final GeoMap map;
    public final AreaIdentity area;
    private final byte[] geometry;

    private PoolRadState(byte[] sample, AreaIdentity.Catalog identities) {
        x = sample[130] & 255;
        y = sample[131] & 255;
        facing = (sample[132] & 255) / 2;
        geometry = Arrays.copyOfRange(sample, 176, 1200);
        byte[] record = new byte[1026];
        System.arraycopy(geometry, 0, record, 2, geometry.length);
        boolean verifiedPacket = sample[3] == '2';
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
        if (sample == null || sample.length != 1200 || sample[0] != 'P' || sample[1] != 'R'
                || sample[2] != 'M' || (sample[3] != '1' && sample[3] != '2')) return null;
        if(sample[3]=='2') {
            // Native verifies the original map mode and movable state allocation.
            // An explicit untrusted/unknown PRM2 sample may not fall back to PRM1
            // hashing, even if old geometry still happens to match the catalog.
            if(sample[32]!=1 || sample[33]!=1 || sample[34]!=0 || (sample[35]&255)>32) return null;
        }
        int x = sample[130] & 255, y = sample[131] & 255, direction = sample[132] & 255;
        if (x >= 16 || y >= 16 || direction > 6 || (direction & 1) != 0) return null;
        PoolRadState state = new PoolRadState(sample, identities);
        return sample[3]=='2' && state.area==null ? null : state;
    }

    public boolean sameDisplay(PoolRadState other) {
        return other != null && x == other.x && y == other.y && facing == other.facing
                && (area == null ? other.area == null : area.equals(other.area))
                && Arrays.equals(geometry, other.geometry);
    }

    public String positionLabel() { return x + ", " + y + " " + "NESW".charAt(facing); }
}
