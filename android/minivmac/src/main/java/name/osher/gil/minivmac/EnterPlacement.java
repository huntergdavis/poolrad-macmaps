package name.osher.gil.minivmac;

/** Stable preference values; unknown values retain the established map shortcut. */
public enum EnterPlacement {
    MAP_RIGHT("map_right", "Bottom right of the map"),
    SCREEN_RIGHT("screen_right", "Bottom right of the screen"),
    MAP_LEFT("map_left", "Bottom left of the map"),
    SCREEN_LEFT("screen_left", "Bottom left of the screen"),
    OFF("off", "Off");

    public final String value, label;
    EnterPlacement(String value, String label) { this.value = value; this.label = label; }
    public boolean onMap() { return this == MAP_RIGHT || this == MAP_LEFT; }
    public boolean onScreen() { return this == SCREEN_RIGHT || this == SCREEN_LEFT; }
    public static EnterPlacement parse(String value) {
        for (EnterPlacement placement : values()) if (placement.value.equals(value)) return placement;
        return MAP_RIGHT;
    }
}
