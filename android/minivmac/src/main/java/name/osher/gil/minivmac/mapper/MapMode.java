package name.osher.gil.minivmac.mapper;

/** Companion presentation state, not a command or mutation of the original game. */
public enum MapMode {
    EXPLORATION("Exploring", "Live party position on the local area map."),
    UPDATING("Updating", "Waiting for a verified local movement position; use the original game below."),
    COMBAT("Combat", "Tactical combat is separate from the local area map. Walking position and footprints are paused."),
    CAMP("Camp", "The party is in camp. Walking position and footprints are paused."),
    WILDERNESS("Wilderness", "Outdoor travel does not use a supported local area map."),
    LOADING("Loading / setup", "The original game is loading or preparing a party. Waiting for a settled local area."),
    UNAVAILABLE("Position unavailable", "No verified game position is available. The last authenticated map may remain visible.");

    private final String label, explanation;

    MapMode(String label, String explanation) {
        this.label = label; this.explanation = explanation;
    }

    public String label() { return label; }
    public String explanation() { return explanation; }
}
