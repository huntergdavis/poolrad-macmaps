package name.osher.gil.minivmac.mapper;
import org.junit.Test;
import static org.junit.Assert.*;
public class PartyPaneLayoutTest {
    @Test public void noPartyUsesWholeMapPane(){PartyPaneLayout p=new PartyPaneLayout(1200,520,1.25f,0);assertEquals(1200,p.mapWidth);assertEquals(520,p.mapHeight);assertEquals(0,p.partyWidth);}
    @Test public void wideScreenUsesRightSideWithoutChangingHeight(){PartyPaneLayout p=new PartyPaneLayout(1200,520,1.25f,8);assertEquals(930,p.mapWidth);assertEquals(270,p.partyWidth);assertEquals(520,p.mapHeight);assertEquals(1,p.columns);assertEquals(8,p.rows);assertEquals(p.mapWidth,p.partyLeft);assertTrue(p.rowHeight>=60);}
    @Test public void panesWithNoRoomAtAllReturnEverythingToTheMap(){for(int[] size:new int[][]{{1200,100},{1,1},{0,0}}){PartyPaneLayout p=new PartyPaneLayout(size[0],size[1],1,6);assertEquals(size[0],p.mapWidth);assertEquals(size[1],p.mapHeight);assertEquals(0,p.rows);assertEquals(0,p.partyWidth);assertEquals(0,p.visibleMembers());assertEquals(-1,p.memberAt(0,0));assertFalse(p.belowMap());}}

    /** A phone-width pane has no room beside the map, but plenty underneath. */
    @Test public void aNarrowPaneMovesThePartyUnderTheMapRatherThanDroppingIt(){
        PartyPaneLayout p=new PartyPaneLayout(360,700,1,6);
        assertTrue("The party vanished on a narrow pane",p.rows>0);
        assertTrue(p.belowMap());
        assertEquals(360,p.mapWidth);
        assertEquals(0,p.partyLeft);
        assertEquals(p.mapHeight,p.partyTop);
        assertEquals(700,p.mapHeight+p.partyHeight);
        assertTrue("The map kept no room",p.mapHeight>0);
        assertEquals(6,p.visibleMembers());
    }
    @Test public void tinyWindowNeverHasNegativeSpace(){PartyPaneLayout p=new PartyPaneLayout(1,1,2,8);assertTrue(p.mapHeight>=0);assertTrue(p.partyHeight>=0);}
    @Test public void allSupportedCountsStayWithinBounds(){for(int count=1;count<=8;count++)for(int w:new int[]{320,600,1200})for(int h:new int[]{100,400,800}){PartyPaneLayout p=new PartyPaneLayout(w,h,1.25f,count);if(p.belowMap()){assertEquals(w,p.mapWidth);assertEquals(h,p.mapHeight+p.partyHeight);}else{assertEquals(h,p.mapHeight);assertEquals(w,p.mapWidth+p.partyWidth);}if(p.rows>0){assertTrue(p.columns>=1&&p.columns<=8);assertEquals(count,p.visibleMembers());assertEquals((count+p.columns-1)/p.columns,p.rows);assertEquals(p.columns*p.columnWidth,p.partyWidth,0.5f);assertTrue(p.rowHeight>=60);for(int i=0;i<count;i++){assertTrue(p.rowTop(i)+p.rowHeight<=h+0.5f);assertTrue(p.columnLeft(i)+p.columnWidth<=w+0.5f);}if(!p.belowMap())assertTrue(p.mapWidth>=350);}else assertEquals(w,p.mapWidth);}}

    /** A party can reach eight with NPCs; the sidebar must not vanish then. */
    @Test public void npcSizedPartiesFallBackToTwoColumnsInsteadOfDisappearing(){
        for(int count=7;count<=8;count++){
            PartyPaneLayout p=new PartyPaneLayout(1200,440,1.25f,count);
            assertTrue("sidebar disappeared for "+count,p.rows>0);
            assertEquals(2,p.columns);
            assertEquals((count+1)/2,p.rows);
            assertEquals(count,p.visibleMembers());
            assertEquals(540,p.partyWidth);
            assertEquals(660,p.mapWidth);
            assertTrue("rows must stay as tall as a short party's",p.rowHeight>=60);
            for(int i=0;i<count;i++)assertEquals(i,p.memberAt(p.columnLeft(i)+1,p.rowTop(i)+p.rowHeight/2));
        }
    }

    @Test public void membersFillTheFirstColumnBeforeTheSecond(){
        PartyPaneLayout p=new PartyPaneLayout(1200,440,1.25f,8);
        for(int i=0;i<4;i++){assertEquals(0,p.columnOf(i));assertEquals(p.partyLeft,p.columnLeft(i),0);}
        for(int i=4;i<8;i++){assertEquals(1,p.columnOf(i));assertEquals(p.partyLeft+p.columnWidth,p.columnLeft(i),0);}
        assertEquals(p.rowTop(0),p.rowTop(4),0);
        assertEquals(p.rowTop(3),p.rowTop(7),0);
    }

    /** An odd count leaves the last cell of the second column empty, not tappable. */
    @Test public void theEmptyCellOfAnOddPartyIsNotACharacterTarget(){
        PartyPaneLayout p=new PartyPaneLayout(1200,440,1.25f,7);
        assertEquals(4,p.rows);
        assertEquals(7,p.visibleMembers());
        assertEquals(-1,p.memberAt(p.partyLeft+p.columnWidth+1,p.rowTop(3)+p.rowHeight/2));
        try{p.rowTop(7);fail("row 7 is not visible");}catch(IllegalArgumentException expected){}
        try{p.columnOf(7);fail("column 7 is not visible");}catch(IllegalArgumentException expected){}
    }

    /** One column is still preferred whenever it genuinely fits. */
    @Test public void oneColumnIsPreferredWhenItFits(){
        PartyPaneLayout tall=new PartyPaneLayout(1200,520,1.25f,8);
        assertEquals(1,tall.columns);assertEquals(8,tall.rows);assertEquals(270,tall.partyWidth);
        for(int count=1;count<=6;count++)assertEquals(1,new PartyPaneLayout(1200,440,1.25f,count).columns);
    }
    @Test public void exactReadableThresholdIsSupported(){
        PartyPaneLayout p=new PartyPaneLayout(496,312,1,6);
        assertEquals(280,p.mapWidth);assertEquals(48,p.rowHeight,0);assertEquals(6,p.rows);
        assertEquals(216f,p.columnWidth,0);
        // One pixel narrower no longer hides the sidebar: the column shrinks by
        // a pixel and the map keeps its floor. That is the point of the change.
        PartyPaneLayout tight=new PartyPaneLayout(495,312,1,6);
        assertEquals(6,tight.rows);assertEquals(215f,tight.columnWidth,0);
        assertEquals(280,tight.mapWidth);
        // A pane one pixel too short for the sidebar no longer loses the party:
        // it moves under the map instead, which is the whole point of the strip.
        PartyPaneLayout shorter=new PartyPaneLayout(496,311,1,6);
        assertTrue(shorter.belowMap());assertEquals(6,shorter.visibleMembers());
        // Likewise a pane too narrow for a 150 column beside the map.
        PartyPaneLayout thinner=new PartyPaneLayout(429,312,1,6);
        assertTrue(thinner.belowMap());assertEquals(6,thinner.visibleMembers());
        // One pixel wider and the sidebar is still preferred over the strip.
        PartyPaneLayout beside=new PartyPaneLayout(430,312,1,6);
        assertEquals(6,beside.rows);assertFalse(beside.belowMap());
        assertEquals(150f,beside.columnWidth,0);
    }
    @Test public void rowHitTestExcludesHeaderMapAndBlankSpace(){PartyPaneLayout p=new PartyPaneLayout(960,700,1,6);assertEquals(1,p.columns);for(int i=0;i<6;i++){assertEquals(i,p.memberAt(p.partyLeft+1,p.rowTop(i)+p.rowHeight/2));assertEquals(i,p.memberAt(p.partyLeft,p.rowTop(i)));}assertEquals(-1,p.memberAt(p.partyLeft-1,p.rowTop(0)));assertEquals(-1,p.memberAt(960,p.rowTop(0)));assertEquals(-1,p.memberAt(p.partyLeft,23));assertEquals(-1,p.memberAt(p.partyLeft,p.rowTop(5)+p.rowHeight));assertEquals(-1,p.memberAt(Float.NaN,Float.NaN));}
    @Test public void largerFontsReceiveSpaceOrCollapse(){assertTrue(new PartyPaneLayout(700,500,1,6,1.8f).belowMap());PartyPaneLayout p=new PartyPaneLayout(1000,700,1,6,1.8f);assertEquals(6,p.rows);assertTrue(p.rowHeight>=48*1.8f);assertEquals(389,p.partyWidth);}
    /**
     * Hunter's Viwoods AiPaper Mini: a 1440-wide panel at roughly density 2.25.
     * The old fixed 216dp column could not sit beside the map's 280dp floor
     * there, so the entire sidebar vanished from four members up. Every
     * supported party size must now keep a sidebar on that geometry.
     */
    @Test public void theRealTabletKeepsItsSidebarAtEveryPartySize(){
        for(float density:new float[]{2.0f,2.25f})
            for(int height:new int[]{530,576,640,720})
                for(int count=1;count<=8;count++){
                    PartyPaneLayout p=new PartyPaneLayout(1440,height,density,count);
                    String at="1440x"+height+" d="+density+" n="+count;
                    assertTrue("sidebar vanished at "+at,p.rows>0);
                    assertEquals(at,count,p.visibleMembers());
                    assertTrue("row too short at "+at,p.rowHeight>=48*density);
                    assertTrue("column too narrow at "+at,p.columnWidth>=150*density);
                    assertTrue("map starved at "+at,p.mapWidth>=280*density);
                    assertTrue("sidebar took over at "+at,p.partyWidth<=1440*0.55f+1);
                    for(int i=0;i<count;i++)
                        assertEquals(at,i,p.memberAt(p.columnLeft(i)+1,p.rowTop(i)+p.rowHeight/2));
                }
    }

    /** The full-width column is still used whenever there is room for it. */
    @Test public void theWideColumnIsKeptWhenItFits(){
        PartyPaneLayout roomy=new PartyPaneLayout(1200,440,1.25f,6);
        assertEquals(1,roomy.columns);
        assertEquals(270,roomy.partyWidth);
        assertEquals(270f,roomy.columnWidth,0);
    }

    @Test(expected=IllegalArgumentException.class) public void invalidDensityRejected(){new PartyPaneLayout(1,1,Float.NaN,1);}
    @Test(expected=IllegalArgumentException.class) public void invalidCountRejected(){new PartyPaneLayout(1,1,1,9);}
    @Test(expected=IllegalArgumentException.class) public void invalidFontScaleRejected(){new PartyPaneLayout(1,1,1,1,Float.POSITIVE_INFINITY);}

    /**
     * The reported bug. A 292 PPI panel reports a high density, and at 2.625 or
     * more a 1440px pane has no side-by-side layout at all: the 280dp map floor
     * plus two 150dp columns exceed the width whatever the height. Every one of
     * these showed no party at all before the strip existed.
     */
    @Test public void theHighDensityTabletAlwaysShowsItsPartySomewhere() {
        for (float density : new float[]{2f, 2.25f, 2.625f, 3f}) {
            for (int height : new int[]{520, 640, 760, 860}) {
                for (int count = 1; count <= 8; count++) {
                    PartyPaneLayout p = new PartyPaneLayout(1440, height, density, count);
                    String where = "density " + density + " height " + height + " count " + count;
                    assertTrue("No party at all at " + where, p.rows > 0);
                    assertEquals("Not every member drawn at " + where, count, p.visibleMembers());
                    assertTrue("The map was squeezed out at " + where, p.mapHeight > 0 && p.mapWidth > 0);
                    for (int i = 0; i < count; i++) {
                        assertTrue(p.rowTop(i) >= p.partyTop + p.headerHeight - 0.5f);
                        assertTrue(p.rowTop(i) + p.rowHeight <= height + 0.5f);
                        assertTrue(p.columnLeft(i) + p.columnWidth <= 1440 + 0.5f);
                        assertEquals(i, p.memberAt(p.columnLeft(i) + 1, p.rowTop(i) + p.rowHeight / 2));
                    }
                }
            }
        }
    }

    /**
     * Hunter's own tablet, measured off the screenshots he took on 2026-09-15
     * rather than guessed at: a 1440x1742 panel whose companion pane is
     * 1440x684, and whose header baseline sits 44px below the pane top, which
     * pins density at exactly 2.0 (the view draws that baseline at 22dp).
     *
     * The screenshots showed no party. They were taken on v0.18.0 or earlier --
     * the two-line "North up . N walked" caption went away in v0.19.0 -- where
     * a single fixed 216dp column needed 24 + 6*48 dp of height, so any font
     * scale at or above 1.10 dropped the sidebar outright. E-ink tablets very
     * commonly run a bumped font scale, so sweep it here: at his geometry the
     * party has to appear at every scale a device can ask for.
     */
    @Test public void hunterTabletShowsThePartyAtEveryFontScale() {
        for (float scale : new float[]{1f, 1.1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f}) {
            for (int count = 1; count <= 8; count++) {
                PartyPaneLayout p = new PartyPaneLayout(1440, 684, 2f, count, scale);
                String where = "1440x684 @2.0 x" + scale + " count " + count;
                assertTrue("No party at all at " + where, p.rows > 0);
                assertEquals("Not every member drawn at " + where, count, p.visibleMembers());
                assertTrue("The map was squeezed out at " + where, p.mapHeight > 0 && p.mapWidth > 0);
                for (int i = 0; i < count; i++)
                    assertEquals(where, i, p.memberAt(p.columnLeft(i) + 1, p.rowTop(i) + p.rowHeight / 2));
            }
        }
    }

    /** A strip under the map is still tappable, and the map above it is not. */
    @Test public void theStripUnderTheMapHitTestsLikeTheSidebar() {
        PartyPaneLayout p = new PartyPaneLayout(1440, 760, 3f, 6);
        assertTrue(p.belowMap());
        assertEquals(3, p.columns);
        assertEquals(2, p.rows);
        assertEquals(-1, p.memberAt(20, p.partyTop - 1));
        assertEquals(-1, p.memberAt(20, p.partyTop + p.headerHeight - 1));
        assertEquals(0, p.memberAt(20, p.partyTop + p.headerHeight + 1));
        assertEquals(1, p.memberAt(20, p.rowTop(1) + 1));
        assertEquals(2, p.memberAt(p.columnLeft(2) + 1, p.rowTop(2) + 1));
        assertEquals(-1, p.memberAt(20, 760));
    }
}
