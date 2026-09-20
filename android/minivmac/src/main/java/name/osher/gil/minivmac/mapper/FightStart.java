package name.osher.gil.minivmac.mapper;

/**
 * Fires once when the named mode turns to combat from the world, camp or the
 * wilderness. Unreadable and updating frames in between never fire it again;
 * a fight that follows a load screen still counts, because the fight is real.
 */
public final class FightStart {
    private MapMode lastNamed;

    public boolean observe(MapMode mode) {
        if (mode == null || mode == MapMode.UNAVAILABLE || mode == MapMode.UPDATING) return false;
        boolean started = mode == MapMode.COMBAT && lastNamed != null && lastNamed != MapMode.COMBAT;
        lastNamed = mode;
        return started;
    }
}
