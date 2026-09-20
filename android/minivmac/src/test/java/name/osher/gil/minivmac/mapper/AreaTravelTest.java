package name.osher.gil.minivmac.mapper;
import java.util.Arrays;
import org.junit.Test;
import name.osher.gil.minivmac.notebook.*;
import static org.junit.Assert.*;

public class AreaTravelTest {
    private byte[] packet(int area,int tile,int epoch,int serial,int from,int fromTile) {
        byte[] p=new byte[1228];p[0]='P';p[1]='R';p[2]='M';p[3]='7';
        p[24]=1;p[25]=1;p[26]=1;p[27]=4;p[31]=1;p[32]=1;p[33]=1;p[35]=(byte)area;
        p[130]=(byte)(tile%16);p[131]=(byte)(tile/16);p[176]=(byte)(area+1);
        p[1215]=(byte)epoch;p[1219]=(byte)serial;p[1220]=1;p[1221]=(byte)from;p[1222]=(byte)fromTile;p[1224]=(byte)area;
        return p;
    }
    private AreaTravel parse(byte[] p) {
        byte[] legacy=Arrays.copyOf(p,1200);legacy[3]='1';GeoMap map=PoolRadState.parse(legacy).map;
        String id=""+(p[35]&255);
        AreaIdentity.Catalog catalog=new AreaIdentity.Catalog(new String[]{id+" "+AreaIdentity.fingerprint(map)},new String[]{id+" "+AreaIdentity.prefixFingerprint(map)});
        return AreaTravel.parse(p,catalog);
    }
    @Test public void onlyObservedDirectionWithNativeDepartureAndFirstArrival() {
        ConnectionRecorder recorder=new ConnectionRecorder();
        assertNull(recorder.observe(parse(packet(0,31,1,0,0,0)),100));
        AreaConnections.Edge edge=recorder.observe(parse(packet(20,79,1,1,0,64)),300);
        assertEquals(new AreaConnections.Edge(0,64,20,79),edge);
        assertNull(recorder.observe(parse(packet(20,78,1,1,0,64)),550));
        assertEquals(new AreaConnections.Edge(20,79,0,64),recorder.observe(parse(packet(0,64,1,2,20,79)),800));
    }
    @Test public void reloadChangedEpochAndSkippedAreaNeverConnect() {
        for(int mode=0;mode<4;mode++) {
            ConnectionRecorder recorder=new ConnectionRecorder();
            recorder.observe(parse(packet(0,64,1,0,0,0)),100);
            if(mode==2)recorder.interrupt();
            if(mode==3)recorder.observe(null,200);
            assertNull(recorder.observe(parse(packet(20,79,mode==0?2:1,mode==1?2:1,0,64)),400));
        }
    }
    @Test public void pollingGapNeverConnectsAndProcessingCanBridgeContinuousLoading() {
        ConnectionRecorder recorder=new ConnectionRecorder();
        recorder.observe(parse(packet(0,64,1,0,0,0)),100);
        assertNull(recorder.observe(parse(packet(20,79,1,1,0,64)),2000));
        recorder=new ConnectionRecorder();recorder.observe(parse(packet(0,64,1,0,0,0)),100);
        byte[] busy=packet(20,79,1,1,0,64);busy[26]=0;
        assertNull(recorder.observe(parse(busy),400));
        assertEquals(new AreaConnections.Edge(0,64,20,79),recorder.observe(parse(packet(20,79,1,1,0,64)),700));
    }
    @Test public void malformedLegacyAndUnavailablePacketsCannotAuthorizeTravel() {
        byte[] p=packet(20,79,1,1,0,64);
        assertNotNull(parse(p));
        assertNull(AreaTravel.parse(null));
        assertNull(AreaTravel.parse(Arrays.copyOf(p,1212)));
        for(int index:new int[]{3,25,32,1215,1225}) {
            byte[] bad=p.clone();bad[index]=(byte)(index==1225?1:0);assertNull("field "+index,parse(bad));
        }
        p[1221]=33;assertNull(parse(p));
    }
    @Test public void newProtocolPreservesClockAndPosition(){
        byte[] p=packet(20,79,1,1,0,64);p[1204]=1;p[1208]=2;p[1209]=13;p[1210]=30;
        assertEquals("Day 2 · 1:30 pm",GameClock.parse(p).label());
        assertEquals(15,parse(p).position.x);
    }
}
