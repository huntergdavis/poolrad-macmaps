package name.osher.gil.minivmac.mapper;

/** A single immutable 16x16 GEO map. Directions are north, east, south, west. */
public final class GeoMap {
    /** Map symbols, not a promise that a scripted edge can be crossed. */
    public enum EdgeKind { OPEN, WALL, DOORWAY }
    public static final int WIDTH = 16;
    public final int id;
    private final byte[] data;

    GeoMap(int id, byte[] data) {
        if (data.length != 1026) throw new IllegalArgumentException("Expected a 1,026-byte GEO record");
        this.id = id;
        this.data = data.clone();
    }

    private int index(int x, int y, int direction) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= WIDTH || direction < 0 || direction > 3)
            throw new IllegalArgumentException("Tile or direction outside map");
        return y * WIDTH + x;
    }

    public int wall(int x, int y, int direction) {
        int tile = index(x, y, direction);
        int value = data[2 + tile + (direction >= 2 ? 256 : 0)] & 255;
        return (direction % 2 == 0 ? value >>> 4 : value) & 15;
    }

    public int door(int x, int y, int direction) {
        int tile = index(x, y, direction);
        return ((data[770 + tile] & 255) >>> (direction * 2)) & 3;
    }

    /**
     * Mac CODE6's directional accessor checks the wall surface before the
     * fourth-plane bits. With no surface those bits do not create a doorway.
     * Keep all nonzero door states visually neutral: textures are not lock or
     * secret-door labels, and the game's two sides can have different states.
     * Raw accessors/data stay unchanged for diagnostics and stable identity.
     */
    public EdgeKind edgeKind(int x, int y, int direction) {
        if (wall(x, y, direction) == 0) return EdgeKind.OPEN;
        return door(x, y, direction) == 0 ? EdgeKind.WALL : EdgeKind.DOORWAY;
    }

    public byte[] copyData() { return data.clone(); }
}
