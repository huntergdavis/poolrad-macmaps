package name.osher.gil.minivmac.mapper;

import java.util.Arrays;

/** A read-only snapshot of the supported Macintosh v1.1 area-map globals. */
public final class PoolRadState {
    public final int x, y, facing;
    public final GeoMap map;
    public final AreaIdentity area;
    private final byte[] geometry;

    private PoolRadState(byte[] sample) {
        x = sample[130] & 255;
        y = sample[131] & 255;
        facing = (sample[132] & 255) / 2;
        geometry = Arrays.copyOfRange(sample, 176, 1200);
        byte[] record = new byte[1026];
        System.arraycopy(geometry, 0, record, 2, geometry.length);
        map = new GeoMap(-1, record); // Area ID is not yet validated; never guess it.
        area = AreaIdentity.resolve(map); // Exact known geometry only; unknown is not a notebook key.
    }

    public static PoolRadState parse(byte[] sample) {
        if (sample == null || sample.length != 1200 || sample[0] != 'P' || sample[1] != 'R'
                || sample[2] != 'M' || sample[3] != '1') return null;
        int x = sample[130] & 255, y = sample[131] & 255, direction = sample[132] & 255;
        if (x >= 16 || y >= 16 || direction > 6 || (direction & 1) != 0) return null;
        return new PoolRadState(sample);
    }

    public boolean sameDisplay(PoolRadState other) {
        return other != null && x == other.x && y == other.y && facing == other.facing
                && Arrays.equals(geometry, other.geometry);
    }

    public String positionLabel() { return x + ", " + y + " " + "NESW".charAt(facing); }
}
