package name.osher.gil.minivmac.notebook;
import java.io.*;
import java.nio.file.Files;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
public class AreaConnectionsTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final AreaConnections.Edge out=new AreaConnections.Edge(0,64,20,79);
    @Test public void persistenceDeduplicationAndCampaignIsolation() throws Exception {
        NotebookStore store=new NotebookStore(temp.getRoot());String one=store.createNotebook().id(),two=store.createNotebook().id();
        store.recordConnection(one,out);store.recordConnection(one,out);
        assertEquals(1,new NotebookStore(temp.getRoot()).loadConnections(one).edges.size());
        assertTrue(store.loadConnections(two).edges.isEmpty());
        store.recordConnection(one,new AreaConnections.Edge(20,79,0,64));
        assertEquals(2,store.loadConnections(one).edges.size());
        store.recordConnection(one,new AreaConnections.Edge(0,65,20,80));
        assertEquals(3,store.loadConnections(one).edges.size());
    }
    @Test public void backupRoundTripIncludesConnectionsAndOldBooksStartEmpty() throws Exception {
        NotebookStore store=new NotebookStore(temp.newFolder("original"));String id=store.createNotebook().id();
        store.recordConnection(id,out);ByteArrayOutputStream bytes=new ByteArrayOutputStream();store.exportNotebook(id,bytes);
        NotebookStore restored=new NotebookStore(temp.newFolder("restored"));
        restored.importNotebook(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(out,restored.loadConnections(id).edges.get(0));
        restored.deleteNotebook(id);assertTrue(restored.listNotebooks().isEmpty());
    }
    @Test public void corruptRecordIsNeverOverwrittenOrExported() throws Exception {
        NotebookStore store=new NotebookStore(temp.getRoot());String id=store.createNotebook().id();store.recordConnection(id,out);
        File file=new File(new File(temp.getRoot(),id),"connections.bin");byte[] broken=Files.readAllBytes(file.toPath());broken[broken.length-1]^=1;Files.write(file.toPath(),broken);
        try{store.recordConnection(id,new AreaConnections.Edge(20,79,0,64));fail();}catch(IOException expected){}
        assertArrayEquals(broken,Files.readAllBytes(file.toPath()));
        try{store.exportNotebook(id,new ByteArrayOutputStream());fail();}catch(IOException expected){}
    }
    @Test public void invalidRecordsRefuseDuplicatesAndTrailingData() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();new AreaConnections().add(out).write(new DataOutputStream(bytes));bytes.write(0);
        try{AreaConnections.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));fail();}catch(IOException expected){}
    }
}
