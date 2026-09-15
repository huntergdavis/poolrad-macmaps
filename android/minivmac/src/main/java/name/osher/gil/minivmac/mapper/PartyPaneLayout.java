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
        int column = (int) Math.ceil(216 * density * scale);
        float header = 24 * density * scale, minimumRow = 48 * density * scale;
        members = count;

        /*
         * A party can reach eight with NPCs, which no longer fits one column on
         * a tablet-height pane. Rather than drop the whole sidebar and leave the
         * player with no party information at exactly the point they have the
         * most to track, fall back to two columns and take the extra width from
         * the map, which keeps every row as wide and as tall as a short party's.
         */
        int wanted = 0, perColumn = 0;
        for (int tryColumns = 1; tryColumns <= 2 && wanted == 0; tryColumns++) {
            int rowsPerColumn = (count + tryColumns - 1) / tryColumns;
            if (count == 0 || rowsPerColumn == 0) break;
            if (width < 280 * density + column * tryColumns) continue;
            if (height < header + rowsPerColumn * minimumRow) continue;
            wanted = tryColumns; perColumn = rowsPerColumn;
        }

        if (wanted == 0) {
            mapWidth=width;mapHeight=height;partyLeft=partyTop=partyWidth=partyHeight=columns=rows=0;
            headerHeight=rowHeight=columnWidth=0;
        } else {
            columns=wanted;rows=perColumn;columnWidth=column;
            partyWidth=column*wanted;partyHeight=height;
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
