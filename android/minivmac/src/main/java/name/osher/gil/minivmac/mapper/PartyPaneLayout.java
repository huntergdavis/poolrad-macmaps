package name.osher.gil.minivmac.mapper;

/** Splits only the existing upper companion pane; the guest allocation never changes. */
public final class PartyPaneLayout {
    public final int mapWidth, mapHeight, partyLeft, partyTop, partyWidth, partyHeight, columns, rows;
    public final float headerHeight, rowHeight;
    public PartyPaneLayout(int width, int height, float density, int count) {
        this(width, height, density, count, 1);
    }
    public PartyPaneLayout(int width, int height, float density, int count, float fontScale) {
        if (width < 0 || height < 0 || Float.isNaN(density) || Float.isInfinite(density) || density <= 0 || count < 0 || count > 8)
            throw new IllegalArgumentException("Invalid companion pane bounds");
        if (Float.isNaN(fontScale) || Float.isInfinite(fontScale) || fontScale <= 0)
            throw new IllegalArgumentException("Invalid companion font scale");
        float scale = Math.max(1, fontScale);
        int sidebar = (int)Math.ceil(216 * density * scale);
        float header = 24 * density * scale, minimumRow = 48 * density * scale;
        if (count == 0 || width < 280 * density + sidebar || height < header + count * minimumRow) {
            mapWidth=width;mapHeight=height;partyLeft=partyTop=partyWidth=partyHeight=columns=rows=0;
            headerHeight=rowHeight=0;
        } else {
            partyWidth=sidebar;partyHeight=height;columns=1;rows=count;
            mapWidth=width-partyWidth;mapHeight=height;partyLeft=mapWidth;partyTop=0;
            headerHeight=header;
            rowHeight=Math.min(64*density*scale, (height-header)/count);
        }
    }

    public float rowTop(int index) {
        if (index < 0 || index >= rows) throw new IllegalArgumentException("No such visible party row");
        return partyTop + headerHeight + index * rowHeight;
    }

    /** Header, blank space below the rows, and the map are never character targets. */
    public int memberAt(float x, float y) {
        if (rows == 0 || !(x >= partyLeft && x < partyLeft+partyWidth
                && y >= partyTop+headerHeight && y < partyTop+headerHeight+rows*rowHeight)) return -1;
        return (int)((y-partyTop-headerHeight)/rowHeight);
    }
}
