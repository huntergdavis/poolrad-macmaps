package name.osher.gil.minivmac.mapper;

/** Splits only the existing upper companion pane; the guest allocation never changes. */
public final class PartyPaneLayout {
    public final int mapWidth, mapHeight, partyLeft, partyTop, partyWidth, partyHeight, columns, rows;
    public final float headerHeight, rowHeight, columnWidth;
    private final int members;

    public PartyPaneLayout(int width, int height, float density, int count) {
        this(width, height, density, count, 1);
    }

    public PartyPaneLayout(int width, int height, float density, int count, float fontScale) {
        if (width < 0 || height < 0 || Float.isNaN(density) || Float.isInfinite(density) || density <= 0 || count < 0 || count > 8)
            throw new IllegalArgumentException("Invalid companion pane bounds");
        if (Float.isNaN(fontScale) || Float.isInfinite(fontScale) || fontScale <= 0)
            throw new IllegalArgumentException("Invalid companion font scale");
        float scale = Math.max(1, fontScale);
        int preferred = (int) Math.ceil(216 * density * scale);
        int narrowest = (int) Math.ceil(150 * density * scale);
        float header = 24 * density * scale, minimumRow = 48 * density * scale;
        float mapFloor = 280 * density;
        members = count;

        /*
         * Two things the old fixed layout got wrong, both of which ended with
         * the player seeing no party at all.
         *
         * A party reaches eight with NPCs, which will not fit one column on a
         * tablet-height pane, so fall back to two columns rather than dropping
         * the sidebar at exactly the point there is most to track.
         *
         * And the column was a fixed 216dp, which quietly hid the whole sidebar
         * on a high-density screen that is not very wide: Hunter's 1440px panel
         * at density 2.25 cannot seat 280dp of map plus 216dp of party, so even
         * six members showed nothing. Shrink the column toward 150dp before
         * giving up, and never let the sidebar take more than 55% of the pane.
         */
        int wanted = 0, perColumn = 0, chosen = 0;
        for (int tryColumns = 1; tryColumns <= 2 && wanted == 0; tryColumns++) {
            int rowsPerColumn = (count + tryColumns - 1) / tryColumns;
            if (count == 0 || rowsPerColumn == 0) break;
            if (height < header + rowsPerColumn * minimumRow) continue;
            int room = (int) Math.min(width - mapFloor, width * 0.55f);
            if (room <= 0) continue;
            int columnWidth = Math.min(preferred, room / tryColumns);
            if (columnWidth < narrowest) continue;
            wanted = tryColumns; perColumn = rowsPerColumn; chosen = columnWidth;
        }

        if (wanted == 0) {
            mapWidth=width;mapHeight=height;partyLeft=partyTop=partyWidth=partyHeight=columns=rows=0;
            headerHeight=rowHeight=columnWidth=0;
        } else {
            columns=wanted;rows=perColumn;columnWidth=chosen;
            partyWidth=chosen*wanted;partyHeight=height;
            mapWidth=width-partyWidth;mapHeight=height;partyLeft=mapWidth;partyTop=0;
            headerHeight=header;
            rowHeight=Math.min(64*density*scale, (height-header)/perColumn);
        }
    }

    /** Members fill the first column top to bottom, then the second. */
    public int columnOf(int index) {
        requireVisible(index);
        return index / rows;
    }

    public float columnLeft(int index) {
        return partyLeft + columnOf(index) * columnWidth;
    }

    public float rowTop(int index) {
        requireVisible(index);
        return partyTop + headerHeight + (index % rows) * rowHeight;
    }

    private void requireVisible(int index) {
        if (index < 0 || index >= visibleMembers()) throw new IllegalArgumentException("No such visible party row");
    }

    /** Never more members than the pane actually has room to draw. */
    public int visibleMembers() { return Math.min(members, columns * rows); }

    /** Header, blank space below the rows, and the map are never character targets. */
    public int memberAt(float x, float y) {
        if (rows == 0 || !(x >= partyLeft && x < partyLeft+partyWidth
                && y >= partyTop+headerHeight && y < partyTop+headerHeight+rows*rowHeight)) return -1;
        int column = (int)((x-partyLeft)/columnWidth);
        if (column >= columns) return -1;
        int index = column*rows + (int)((y-partyTop-headerHeight)/rowHeight);
        return index < visibleMembers() ? index : -1;
    }
}
