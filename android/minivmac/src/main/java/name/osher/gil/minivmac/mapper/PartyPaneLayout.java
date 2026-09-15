package name.osher.gil.minivmac.mapper;

/** Splits only the existing upper companion pane; the guest allocation never changes. */
public final class PartyPaneLayout {
    public final int mapWidth, mapHeight, partyLeft, partyTop, partyWidth, partyHeight, columns, rows;
    public final float headerHeight, rowHeight, columnWidth;
    /** Columns narrower than this, in dp, drop the armour-class readout. */
    public static final float COMPACT_COLUMN = 150;
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
         * Three things the old fixed layout got wrong, all of which ended with
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
         *
         * And that still was not enough. At density 2.625 and above, a 1440px
         * panel has no side-by-side layout at all: 280dp of map floor plus two
         * 150dp columns simply do not fit the width, at any pane height. A
         * 292 PPI tablet reports exactly that. So when nothing fits beside the
         * map, put the party in a strip underneath it instead of hiding it.
         * Losing some map height beats losing the whole party.
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

        /*
         * The strip under the map. Widest columns first, because more columns
         * means fewer rows and a shorter strip, which leaves the map more room.
         */
        // A strip spans the whole pane, so its columns can be tighter than a
        // sidebar's; below COMPACT_COLUMN the row drops its armour-class
        // readout to keep the name, health and bar legible.
        int stripNarrowest = (int) Math.ceil(118 * density * scale);
        // Whatever happens, the map keeps a quarter of the pane and 64 pixels.
        float mapKeeps = Math.max(64, height * 0.25f);
        int stripColumns = 0, stripRows = 0;
        float stripWidth = 0, stripHeight = 0;
        if (wanted == 0 && count > 0 && width > 0) {
            /*
             * Prefer the shortest strip, so the map keeps the most height; and
             * among equally short ones prefer the fewest columns, so six
             * members make three wide pairs rather than four with a gap.
             */
            for (int tryColumns = 1; tryColumns <= Math.min(count, (int) (width / stripNarrowest)); tryColumns++) {
                float each = width / (float) tryColumns;
                if (each < stripNarrowest) continue;
                int rowsPerColumn = (count + tryColumns - 1) / tryColumns;
                if (stripColumns > 0 && rowsPerColumn >= stripRows) continue;
                float least = header + rowsPerColumn * minimumRow;
                if (least > height * 0.75f || height - least < mapKeeps) continue;
                // Grow the rows toward comfortable only while the map can spare it.
                float roomy = Math.min(height * 0.75f, header + rowsPerColumn * 64 * density * scale);
                stripColumns = tryColumns; stripRows = rowsPerColumn; stripWidth = each;
                stripHeight = height - roomy >= mapKeeps ? roomy : least;
            }
        }

        if (wanted == 0 && stripColumns > 0) {
            columns=stripColumns;rows=stripRows;columnWidth=stripWidth;
            partyWidth=(int) Math.floor(stripColumns * stripWidth);
            partyHeight=(int) Math.floor(stripHeight);
            partyLeft=0;partyTop=height-partyHeight;
            mapWidth=width;mapHeight=height-partyHeight;
            headerHeight=header;
            rowHeight=(partyHeight-header)/stripRows;
        } else if (wanted == 0) {
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

    /** True when the party sits under the map rather than beside it. */
    public boolean belowMap() { return rows > 0 && partyTop > 0; }

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
