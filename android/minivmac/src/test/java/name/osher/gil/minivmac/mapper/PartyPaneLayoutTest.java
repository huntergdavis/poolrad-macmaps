package name.osher.gil.minivmac.mapper;
import org.junit.Test;
import static org.junit.Assert.*;
public class PartyPaneLayoutTest {
    @Test public void noPartyUsesWholeMapPane(){PartyPaneLayout p=new PartyPaneLayout(1200,520,1.25f,0);assertEquals(1200,p.mapWidth);assertEquals(520,p.mapHeight);assertEquals(0,p.partyWidth);}
    @Test public void wideScreenUsesRightSideWithoutChangingHeight(){PartyPaneLayout p=new PartyPaneLayout(1200,520,1.25f,8);assertEquals(930,p.mapWidth);assertEquals(270,p.partyWidth);assertEquals(520,p.mapHeight);assertEquals(1,p.columns);assertEquals(8,p.rows);assertEquals(p.mapWidth,p.partyLeft);assertTrue(p.rowHeight>=60);}
    @Test public void narrowAndShortPanesReturnWholeAllocationToMap(){for(int[] size:new int[][]{{360,700},{1200,100},{1,1},{0,0}}){PartyPaneLayout p=new PartyPaneLayout(size[0],size[1],1,6);assertEquals(size[0],p.mapWidth);assertEquals(size[1],p.mapHeight);assertEquals(0,p.rows);assertEquals(0,p.partyWidth);assertEquals(0,p.visibleMembers());assertEquals(-1,p.memberAt(0,0));}}
    @Test public void tinyWindowNeverHasNegativeSpace(){PartyPaneLayout p=new PartyPaneLayout(1,1,2,8);assertTrue(p.mapHeight>=0);assertTrue(p.partyHeight>=0);}
    @Test public void allSupportedCountsStayWithinBounds(){for(int count=1;count<=8;count++)for(int w:new int[]{320,600,1200})for(int h:new int[]{100,400,800}){PartyPaneLayout p=new PartyPaneLayout(w,h,1.25f,count);assertEquals(h,p.mapHeight);assertEquals(w,p.mapWidth+p.partyWidth);if(p.rows>0){assertTrue(p.columns==1||p.columns==2);assertEquals(count,p.visibleMembers());assertEquals((count+p.columns-1)/p.columns,p.rows);assertEquals(p.columns*p.columnWidth,p.partyWidth,0.5f);assertTrue(p.rowHeight>=60);for(int i=0;i<count;i++){assertTrue(p.rowTop(i)+p.rowHeight<=h);assertTrue(p.columnLeft(i)+p.columnWidth<=w+0.5f);}assertTrue(p.mapWidth>=350);}else assertEquals(w,p.mapWidth);}}

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
    @Test public void exactReadableThresholdIsSupported(){PartyPaneLayout p=new PartyPaneLayout(496,312,1,6);assertEquals(280,p.mapWidth);assertEquals(48,p.rowHeight,0);assertEquals(6,p.rows);assertEquals(0,new PartyPaneLayout(495,312,1,6).rows);assertEquals(0,new PartyPaneLayout(496,311,1,6).rows);}
    @Test public void rowHitTestExcludesHeaderMapAndBlankSpace(){PartyPaneLayout p=new PartyPaneLayout(960,700,1,6);assertEquals(1,p.columns);for(int i=0;i<6;i++){assertEquals(i,p.memberAt(p.partyLeft+1,p.rowTop(i)+p.rowHeight/2));assertEquals(i,p.memberAt(p.partyLeft,p.rowTop(i)));}assertEquals(-1,p.memberAt(p.partyLeft-1,p.rowTop(0)));assertEquals(-1,p.memberAt(960,p.rowTop(0)));assertEquals(-1,p.memberAt(p.partyLeft,23));assertEquals(-1,p.memberAt(p.partyLeft,p.rowTop(5)+p.rowHeight));assertEquals(-1,p.memberAt(Float.NaN,Float.NaN));}
    @Test public void largerFontsReceiveSpaceOrCollapse(){assertEquals(0,new PartyPaneLayout(700,500,1,6,1.8f).rows);PartyPaneLayout p=new PartyPaneLayout(1000,700,1,6,1.8f);assertEquals(6,p.rows);assertTrue(p.rowHeight>=48*1.8f);assertEquals(389,p.partyWidth);}
    @Test(expected=IllegalArgumentException.class) public void invalidDensityRejected(){new PartyPaneLayout(1,1,Float.NaN,1);}
    @Test(expected=IllegalArgumentException.class) public void invalidCountRejected(){new PartyPaneLayout(1,1,1,9);}
    @Test(expected=IllegalArgumentException.class) public void invalidFontScaleRejected(){new PartyPaneLayout(1,1,1,1,Float.POSITIVE_INFINITY);}
}
