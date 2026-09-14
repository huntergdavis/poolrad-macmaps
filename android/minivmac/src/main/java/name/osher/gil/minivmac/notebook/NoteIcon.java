package name.osher.gil.minivmac.notebook;

/** Player-chosen map symbols, never automatically inferred from game data. */
public enum NoteIcon {
    FLAG("flag", "Flag"),
    SMITHY("smithy", "Smithy"),
    TEMPLE("temple", "Temple"),
    INN("inn", "Inn"),
    SHOP("shop", "Shop"),
    MONSTER("monster", "Monster"),
    HIDDEN_WALL("hidden-wall", "Hidden wall"),
    DISTRICT("district", "District / slums"),
    TREASURE("treasure", "Treasure");

    private final String id, label;

    NoteIcon(String id, String label) { this.id = id; this.label = label; }

    public String id() { return id; }
    public String label() { return label; }

    /** Stored IDs are explicit strings, not reorder-sensitive enum ordinals. */
    public static NoteIcon fromId(String id) {
        for (NoteIcon icon : values()) if (icon.id.equals(id)) return icon;
        throw new IllegalArgumentException("Unrecognized note icon");
    }
}
