package name.osher.gil.minivmac.mapper;

/** Splits only the existing upper companion pane; the guest allocation never changes. */
public final class PartyPaneLayout {
    public final int mapWidth, mapHeight, partyLeft, partyTop, partyWidth, partyHeight, columns, rows;
    public PartyPaneLayout(int width, int height, float density, int count) {
        if (width < 0 || height < 0 || Float.isNaN(density) || Float.isInfinite(density) || density <= 0 || count < 0 || count > 8)
            throw new IllegalArgumentException("Invalid companion pane bounds");
        if (count == 0) {
            mapWidth=width;mapHeight=height;partyLeft=partyTop=partyWidth=partyHeight=columns=rows=0;
        } else if (width >= 560 * density) {
            partyWidth=Math.round(220*density);partyHeight=height;columns=1;rows=count;
            mapWidth=width-partyWidth;mapHeight=height;partyLeft=mapWidth;partyTop=0;
        } else {
            columns=2;rows=(count+1)/2;partyWidth=width;partyHeight=Math.min(height/2,Math.round((rows*38+20)*density));
            mapWidth=width;mapHeight=height-partyHeight;partyLeft=0;partyTop=mapHeight;
        }
    }
}
