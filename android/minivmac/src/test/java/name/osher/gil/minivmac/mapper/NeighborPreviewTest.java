package name.osher.gil.minivmac.mapper;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import name.osher.gil.minivmac.notebook.*;
import static org.junit.Assert.*;
public class NeighborPreviewTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private GeoMap geometry(int id) {
        byte[] data=new byte[1026];Arrays.fill(data,2,514,(byte)0x11);data[770+79]=1;
        return new GeoMap(id,data);
    }
    private ExplorationTrail walked() {return ExplorationTrail.empty().record(79,-1).record(78,79);}
    private byte[] bytes(ExploredMap map) throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();map.write(new DataOutputStream(b));return b.toByteArray();
    }
    @Test public void onlyWalkedCellEdgesAreStoredAndRendered() throws Exception {
        ExploredMap observed=new ExploredMap().observe(geometry(20),walked());
        byte[] encoded=bytes(observed);assertEquals(288,encoded.length);
        for(int tile=0;tile<256;tile++) if(tile!=78 && tile!=79) assertEquals(0,encoded[32+tile]);
        GeoMap safe=observed.geometry(20,walked());
        assertEquals(GeoMap.EdgeKind.DOORWAY,safe.edgeKind(15,4,0));
        assertEquals(GeoMap.EdgeKind.WALL,safe.edgeKind(14,4,0));
        assertEquals(GeoMap.EdgeKind.OPEN,safe.edgeKind(13,4,1));
        NeighborPreview p=new NeighborPreview(new AreaConnections.Edge(0,64,20,79),observed,walked(),"");
        assertEquals(2,p.count());assertFalse(p.visible(77));
        assertSame(observed,observed.observe(geometry(20),walked()));
    }
    @Test public void resetCoverageHidesPreviouslyRememberedCells() {
        ExploredMap observed=new ExploredMap().observe(geometry(20),walked());
        NeighborPreview p=new NeighborPreview(new AreaConnections.Edge(0,64,20,79),observed,ExplorationTrail.empty(),"");
        assertEquals(0,p.count());assertEquals(GeoMap.EdgeKind.OPEN,p.map.edgeKind(15,4,0));
    }
    @Test public void malformedHiddenCellsInvalidEdgesAndTrailingBytesAreRejected() throws Exception {
        for(int kind=0;kind<3;kind++) {
            byte[] data=bytes(new ExploredMap());
            if(kind==0)data[32]=1;
            if(kind==1){data[0]=1;data[32]=3;}
            if(kind==2)data=Arrays.copyOf(data,289);
            try {ExploredMap.read(new DataInputStream(new ByteArrayInputStream(data)));fail();}catch(IOException expected){}
        }
    }
    private PoolRadState position(int tile,boolean safe) {
        byte[] p=new byte[1228];p[0]='P';p[1]='R';p[2]='M';p[3]='7';p[24]=1;p[25]=1;p[26]=(byte)(safe?1:0);p[27]=4;p[31]=1;p[32]=1;p[33]=1;
        p[130]=(byte)(tile%16);p[131]=(byte)(tile/16);p[176]=1;p[1215]=1;
        byte[] legacy=Arrays.copyOf(p,1200);legacy[3]='1';GeoMap map=PoolRadState.parse(legacy).map;
        AreaIdentity.Catalog c=new AreaIdentity.Catalog(new String[]{"0 "+AreaIdentity.fingerprint(map)},new String[]{"0 "+AreaIdentity.prefixFingerprint(map)});
        return MapObservation.parse(p,c).state;
    }
    @Test public void onlyOutgoingDiscoveredPassagesAtExactSafeSquareQualify() throws Exception {
        AreaConnections connections=new AreaConnections().add(new AreaConnections.Edge(0,64,20,79))
                .add(new AreaConnections.Edge(1,64,0,65)).add(new AreaConnections.Edge(0,64,2,12));
        assertEquals(2,NeighborPreview.exits(connections,position(64,true)).size());
        assertTrue(NeighborPreview.exits(connections,position(65,true)).isEmpty());
        assertTrue(NeighborPreview.exits(connections,position(64,false)).isEmpty());
        assertTrue(NeighborPreview.exits(connections,null).isEmpty());
    }
    @Test public void restartBackupAndCampaignIsolation() throws Exception {
        NotebookStore store=new NotebookStore(temp.newFolder("source"));String id=store.createNotebook().id(),other=store.createNotebook().id();
        String area="por-mac-v11-geo-20";
        assertFalse(store.loadExploredMap(id,area).known(79));
        store.saveExploredMap(id,area,new ExploredMap().observe(geometry(20),walked()));
        store.saveExploration(id,area,walked());
        assertTrue(store.listFlagIcons(id,area).isEmpty());
        assertTrue(store.listNotes(id).isEmpty());
        assertFalse(store.loadExploredMap(other,area).known(79));
        assertFalse(store.loadExploredMap(id,"por-mac-v11-geo-0").known(79));
        assertTrue(new NotebookStore(new File(temp.getRoot(),"source")).loadExploredMap(id,area).known(79));
        ByteArrayOutputStream backup=new ByteArrayOutputStream();store.exportNotebook(id,backup);
        NotebookStore restored=new NotebookStore(temp.newFolder("restored"));restored.importNotebook(new ByteArrayInputStream(backup.toByteArray()));
        assertArrayEquals(bytes(store.loadExploredMap(id,area)),bytes(restored.loadExploredMap(id,area)));
        restored.deleteNotebook(id);assertTrue(restored.listNotebooks().isEmpty());
    }
    @Test public void corruptRememberedMapCannotBeOverwrittenOrExported() throws Exception {
        NotebookStore store=new NotebookStore(temp.getRoot());String id=store.createNotebook().id(),area="por-mac-v11-geo-20";
        store.saveExploredMap(id,area,new ExploredMap().observe(geometry(20),walked()));
        Path file=new File(temp.getRoot(),id+"/"+area+"/explored-map.bin").toPath();byte[] corrupt=Files.readAllBytes(file);corrupt[corrupt.length-1]^=1;Files.write(file,corrupt);
        try {store.saveExploredMap(id,area,new ExploredMap());fail();}catch(IOException expected){}
        assertArrayEquals(corrupt,Files.readAllBytes(file));
        assertTrue(store.listFlagIcons(id,area).isEmpty());
        try {store.exportNotebook(id,new ByteArrayOutputStream());fail();}catch(IOException expected){}
    }
}
