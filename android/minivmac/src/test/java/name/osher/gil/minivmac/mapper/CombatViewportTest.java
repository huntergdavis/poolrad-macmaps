package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

public class CombatViewportTest {
    private CombatSnapshot battle(int[][] spots) {
        byte[] packet = new byte[CombatSnapshot.PACKET_SIZE];
        packet[0]='P'; packet[1]='R'; packet[2]='C'; packet[3]='3';
        packet[4]=1; packet[5]=(byte)spots.length;
        for(int i=0;i<spots.length;i++) {
            packet[8+4*i]=(byte)(i==0?1:2);
            packet[9+4*i]=(byte)spots[i][0]; packet[10+4*i]=(byte)spots[i][1];
        }
        CombatSnapshot result=CombatSnapshot.parse(packet);
        assertNotNull(result); return result;
    }
    private void visible(CombatViewport v, CombatSnapshot b) {
        for(CombatSnapshot.Spot p:b.spots()) {
            assertTrue(v.contains(v.left+(p.x+.5f)*v.cell,v.top+(p.y+.5f)*v.cell));
        }
    }
    @Test public void entryFramesAllCombatantsWithLargerMarkers() {
        CombatSnapshot b=battle(new int[][]{{18,10},{20,10},{23,12},{24,14}});
        for(float d:new float[]{1,1.25f,2}) {
            CombatViewport fit=new CombatViewport(950,460,d,1,25,12.5f);
            CombatViewport action=CombatViewport.action(950,460,d,b);
            assertTrue(action.cell>fit.cell);
            visible(action,b);
            assertEquals(50,b.width()); assertEquals(25,b.height());
        }
    }
    @Test public void edgeFightsRemainInsideRealArena() {
        for(int[] edge:new int[][]{{0,0},{49,0},{0,24},{49,24}}) {
            CombatSnapshot b=battle(new int[][]{edge});
            CombatViewport v=CombatViewport.action(950,460,1.25f,b);
            visible(v,b);
            assertTrue(v.left<=v.clipLeft+.001f);
            assertTrue(v.top<=v.clipTop+.001f);
            assertTrue(v.left+50*v.cell>=v.clipRight-.001f);
            assertTrue(v.top+25*v.cell>=v.clipBottom-.001f);
        }
    }
    @Test public void spreadOutFightAndFitShowFullArena() {
        CombatSnapshot b=battle(new int[][]{{0,0},{49,24}});
        CombatViewport v=CombatViewport.action(950,460,1.25f,b);
        assertEquals(1,v.zoom,0); visible(v,b);
        visible(new CombatViewport(950,460,1.25f,1,0,0),b);
    }
    @Test public void manualPanCanReachEveryCornerAtMaximumZoom() {
        for(int[] edge:new int[][]{{0,0},{49,0},{0,24},{49,24}}) {
            CombatViewport v=new CombatViewport(950,460,1.25f,8,edge[0]*100,edge[1]*100);
            visible(v,battle(new int[][]{edge}));
        }
    }
}
