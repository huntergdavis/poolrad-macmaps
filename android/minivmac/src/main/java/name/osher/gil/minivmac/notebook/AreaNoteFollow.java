package name.osher.gil.minivmac.notebook;

/** One pending destination, replaced by the latest verified area entry. */
public final class AreaNoteFollow {
    private String lastArea;
    private String pendingArea;
    private int entryTile;

    public void observe(String area, int tile) {
        // Loading, camp, combat and unreadable frames are not new area entries.
        if (area == null || area.equals(lastArea)) return;
        lastArea = pendingArea = area;
        entryTile = tile;
    }

    public boolean pending(String area) {
        return area != null && area.equals(pendingArea);
    }

    public int tile(int remembered) {
        return remembered >= 0 && remembered < 256 ? remembered : entryTile;
    }

    public void opened(String area) {
        if (pending(area)) pendingArea = null;
    }

    public void reset() { lastArea = pendingArea = null; }

    public static String preferenceKey(String notebook, String area) {
        return "notebook.last-page." + notebook + "." + area;
    }
}
