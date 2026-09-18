package name.osher.gil.minivmac.mapper;

/**
 * Where the fog-of-war and footprint switches live, now that each area keeps
 * its own answer.
 *
 * One global pair of switches was wrong in a way that only shows up after a few
 * hours of play: clearing the fog in the slums because you have finished
 * mapping them also cleared it in the kobold caves, where you had not. What you
 * want remembered is not "fog on" but "fog on, here".
 *
 * An area with no answer of its own uses the global one, which is therefore two
 * things at once: the setting for everywhere unvisited, and the default a new
 * area starts from. That keeps a first visit behaving the way the player last
 * chose rather than the way the app was shipped.
 */
public final class ExplorationStyle {
    public static final String FOG = "poolrad_visited_only";
    public static final String FOOTPRINTS = "poolrad_footprints";
    /** The shipped answers, used until the player has expressed one. */
    public static final boolean FOG_DEFAULT = false, FOOTPRINTS_DEFAULT = true;

    private ExplorationStyle() {}

    /** The narrow slice of SharedPreferences this needs, so it can be tested. */
    public interface Stored {
        boolean has(String key);
        boolean read(String key, boolean fallback);
    }

    /**
     * One area's key, or null when no area is identified — in which case there
     * is nothing to remember an answer against and the global one stands.
     */
    public static String key(String base, AreaIdentity area) {
        if (base == null || base.isEmpty()) throw new IllegalArgumentException("No preference base");
        return area == null ? null : base + "." + area.id();
    }

    /** This area's answer if it has one, otherwise the global answer. */
    public static boolean resolve(Stored prefs, String base, AreaIdentity area, boolean shipped) {
        if (prefs == null) throw new IllegalArgumentException("No preferences");
        String own = key(base, area);
        if (own != null && prefs.has(own)) return prefs.read(own, shipped);
        return prefs.read(base, shipped);
    }
}
