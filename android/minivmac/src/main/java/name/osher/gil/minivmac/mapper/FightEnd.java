package name.osher.gil.minivmac.mapper;

/**
 * Fires once when the named mode leaves combat for the world, camp or the
 * wilderness. Unreadable and updating frames are the probe blinking, not the
 * fight ending; a load screen after combat is not a fight ending either.
 */
public final class FightEnd {
    private MapMode lastNamed;

    public boolean observe(MapMode mode) {
        if (mode == null || mode == MapMode.UNAVAILABLE || mode == MapMode.UPDATING) return false;
        boolean ended = lastNamed == MapMode.COMBAT
                && (mode == MapMode.EXPLORATION || mode == MapMode.CAMP || mode == MapMode.WILDERNESS);
        lastNamed = mode;
        return ended;
    }
}
