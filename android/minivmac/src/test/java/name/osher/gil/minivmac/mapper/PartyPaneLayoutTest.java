package name.osher.gil.minivmac.mapper;
import org.junit.Test;
import static org.junit.Assert.*;
public class PartyPaneLayoutTest {
    @Test public void noPartyUsesWholeMapPane(){PartyPaneLayout p=new PartyPaneLayout(1200,520,1.25f,0);assertEquals(1200,p.mapWidth);assertEquals(520,p.mapHeight);assertEquals(0,p.partyWidth);}
    @Test public void wideScreenUsesRightSideWithoutChangingHeight(){PartyPaneLayout p=new PartyPaneLayout(1200,520,1.25f,8);assertEquals(925,p.mapWidth);assertEquals(275,p.partyWidth);assertEquals(520,p.mapHeight);assertEquals(1,p.columns);assertEquals(8,p.rows);assertEquals(p.mapWidth,p.partyLeft);}
    @Test public void narrowScreenReservesBoundedTwoColumnStrip(){PartyPaneLayout p=new PartyPaneLayout(360,270,1,7);assertEquals(2,p.columns);assertEquals(4,p.rows);assertEquals(135,p.partyHeight);assertEquals(270,p.mapHeight+p.partyHeight);assertEquals(p.mapHeight,p.partyTop);}
    @Test public void tinyWindowNeverHasNegativeSpace(){PartyPaneLayout p=new PartyPaneLayout(1,1,2,8);assertTrue(p.mapHeight>=0);assertTrue(p.partyHeight>=0);}
    @Test public void allSupportedCountsStayWithinBounds(){for(int count=1;count<=8;count++)for(int w:new int[]{320,600,1200}){PartyPaneLayout p=new PartyPaneLayout(w,480,1.25f,count);assertTrue(p.partyLeft+p.partyWidth<=w);assertTrue(p.partyTop+p.partyHeight<=480);assertTrue(p.columns*p.rows>=count);}}
}
