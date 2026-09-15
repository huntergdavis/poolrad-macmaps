package name.osher.gil.minivmac.mapper;

/** Separates verified game status from optional, independently authenticated local geometry. */
public final class MapObservation {
    private static final MapObservation UNAVAILABLE = new MapObservation(null, MapMode.UNAVAILABLE);
    public final PoolRadState state;
    public final MapMode mode;

    private MapObservation(PoolRadState state, MapMode mode) {
        this.state = state; this.mode = mode;
    }

    /** Always returns an observation; unavailable or status-only observations have no geometry. */
    public static MapObservation parse(byte[] packet) { return parse(packet, null); }

    // Synthetic catalogs exercise the real parser without distributing game maps.
    static MapObservation parse(byte[] packet, AreaIdentity.Catalog identities) {
        // Length first: a truncated packet must read as unavailable, never throw.
        if (packet == null || packet.length < 4) return UNAVAILABLE;
        if (packet[0] != 'P' || packet[1] != 'R' || packet[2] != 'M') return UNAVAILABLE;
        // PRM5 appends one search byte to PRM4; every earlier version keeps its size.
        if (packet.length != (packet[3] == '5' ? 1204 : 1200)) return UNAVAILABLE;
        if (packet[3] == '4' || packet[3] == '5') return current(packet, identities);
        if (packet[3] != '1' && packet[3] != '2' && packet[3] != '3') return UNAVAILABLE;
        PoolRadState state = PoolRadState.parse(packet, identities);
        if (state == null) return UNAVAILABLE;
        // Legacy diagnostic maps retain their original display behavior. Their
        // unchecked diagnostic tail must never become new movement metadata.
        if (packet[3] != '3') return new MapObservation(state, MapMode.EXPLORATION);
        if (packet[25] != 1 || (packet[26] != 0 && packet[26] != 1)) return UNAVAILABLE;
        if (packet[26] == 1)
            return state.explorationSafe ? new MapObservation(state, MapMode.EXPLORATION) : UNAVAILABLE;
        switch (packet[27] & 255) {
            case 2: return new MapObservation(state, MapMode.CAMP);
            case 5: return new MapObservation(state, MapMode.COMBAT);
            case 4: return new MapObservation(state, MapMode.UPDATING);
            default: return UNAVAILABLE;
        }
    }

    private static MapObservation current(byte[] packet, AreaIdentity.Catalog identities) {
        if (packet[25] != 1 || (packet[26] != 0 && packet[26] != 1)
                || (packet[33] != 0 && packet[33] != 1)) return UNAVAILABLE;
        int mode = packet[24] & 255, engine = packet[27] & 255;
        if (mode < 1 || mode > 6 || (mode != 1 && packet[26] != 0)) return UNAVAILABLE;
        if (packet[32] < 1 || packet[32] > 4) return UNAVAILABLE;
        if ((mode == 1 && engine != 4) || (mode == 2 && engine != 5)
                || (mode == 3 && engine != 2) || (mode == 4 && engine != 3)
                || (mode == 5 && engine > 7) || (mode == 6 && engine != 4)) return UNAVAILABLE;
        if (packet[33] == 0) {
            if (packet[26] != 0 || !statusOnly(packet)) return UNAVAILABLE;
        } else if (mode != 1) return UNAVAILABLE;
        if (mode == 1) {
            PoolRadState state = packet[33] == 1 ? PoolRadState.parse(packet, identities) : null;
            if (state == null || state.area == null) return UNAVAILABLE;
            if (packet[26] == 1)
                return state.explorationSafe && state.continuityToken != 0
                        ? new MapObservation(state, MapMode.EXPLORATION) : UNAVAILABLE;
            // Native mode 1 independently validates the local display position.
            // Printing a story or processing input need not hide the map; only
            // explicit safe movement metadata can authorize footprint recording.
            return new MapObservation(state, MapMode.EXPLORATION);
        }
        MapMode presentation = mode == 2 ? MapMode.COMBAT : mode == 3 ? MapMode.CAMP
                : mode == 4 ? MapMode.WILDERNESS : mode == 5 ? MapMode.LOADING : MapMode.UPDATING;
        return new MapObservation(null, presentation);
    }

    /** PRM4 v1 deliberately strips stale local geometry from non-local status packets. */
    private static boolean statusOnly(byte[] packet) {
        if ((packet[34] & 255) != 255 || (packet[35] & 255) != 255) return false;
        for (int at = 40; at < packet.length; at++) {
            // A status-only packet prints no position line, so PRM5's search
            // byte must say unavailable rather than a cleared "not searching".
            int expected = at >= 130 && at <= 132 ? 255 : at == 1200 ? 255 : 0;
            if ((packet[at] & 255) != expected) return false;
        }
        return true;
    }
}
