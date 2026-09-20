package name.osher.gil.minivmac.notebook;

/** Stable persisted paper choices. Guides belong to the writing half, never to the ink. */
public enum NoteTemplate {
    PLAIN("plain", "Plain paper"),
    GRID("grid", "Blank grid"),
    RULED("ruled", "Ruled list"),
    MAP_FRAME("map-frame", "Blank map frame");

    private final String id, label;
    NoteTemplate(String id, String label) { this.id = id; this.label = label; }
    public String id() { return id; }
    public String label() { return label; }
    public static NoteTemplate fromId(String id) {
        for (NoteTemplate value : values()) if (value.id.equals(id)) return value;
        throw new IllegalArgumentException("Unknown note template");
    }
}
