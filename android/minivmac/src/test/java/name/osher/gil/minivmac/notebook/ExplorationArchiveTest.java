package name.osher.gil.minivmac.notebook;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Collections;
import static org.junit.Assert.*;

public class ExplorationArchiveTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static final String AREA="por-mac-v11-geo-0", OTHER="por-mac-v11-geo-32";
    private ExplorationTrail trail() { return ExplorationTrail.empty().record(17,-1).record(18,17).record(17,18).record(100,-1); }
    private File file(File root,String id,String path) { return new File(new File(root,id),path); }
    private byte[] bytes(File file) throws IOException { return Files.readAllBytes(file.toPath()); }
    private byte[] export(NotebookStore store,String id) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();store.exportNotebook(id,out);return out.toByteArray();
    }
    private byte[] rawArchive(String id,byte[] metadata,String[] paths,byte[][] records) throws Exception {
        ByteArrayOutputStream body=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(body);
        out.writeInt(0x50524e41);out.writeInt(1);out.writeUTF(id);out.writeInt(1+paths.length);
        out.writeUTF("notebook.bin");out.writeInt(metadata.length);out.write(metadata);
        for(int i=0;i<paths.length;i++) { out.writeUTF(paths[i]);out.writeInt(records[i].length);out.write(records[i]); }
        out.write(MessageDigest.getInstance("SHA-256").digest(body.toByteArray()));return body.toByteArray();
    }

    @Test public void backupRestoresExactExplorationBytesAndNotesAcrossAreas() throws Exception {
        File source=temporary.newFolder("source"),target=temporary.newFolder("target");
        NotebookStore first=new NotebookStore(source),second=new NotebookStore(target);String id=first.createNotebook().id();
        first.save(id,AREA,1,1,InkNote.empty(),NoteIcon.TEMPLE);
        first.saveExploration(id,AREA,trail());first.saveExploration(id,OTHER,trail().clearTrail());
        byte[] firstHistory=bytes(file(source,id,AREA+"/exploration.bin"));
        byte[] otherHistory=bytes(file(source,id,OTHER+"/exploration.bin"));
        byte[] flag=bytes(file(source,id,AREA+"/17.ink"));
        byte[] archive=export(first,id);
        assertEquals(1,ByteBuffer.wrap(archive).getInt(4)); // Existing PRNA format remains readable.
        assertEquals(id,second.importNotebook(new ByteArrayInputStream(archive)).id());
        assertEquals(trail(),second.loadExploration(id,AREA));assertEquals(trail().clearTrail(),second.loadExploration(id,OTHER));
        assertArrayEquals(firstHistory,bytes(file(target,id,AREA+"/exploration.bin")));
        assertArrayEquals(otherHistory,bytes(file(target,id,OTHER+"/exploration.bin")));
        assertArrayEquals(flag,bytes(file(target,id,AREA+"/17.ink")));
        assertEquals(Collections.singleton(17),second.listFlags(id,AREA));
    }

    @Test public void oldBackupsWithoutExplorationRemainReadableAndStartEmpty() throws Exception {
        NotebookStore first=new NotebookStore(temporary.newFolder("source")),second=new NotebookStore(temporary.newFolder("target"));
        String id=first.createNotebook().id();first.save(id,AREA,1,1,InkNote.empty());
        second.importNotebook(new ByteArrayInputStream(export(first,id)));
        assertEquals(ExplorationTrail.empty(),second.loadExploration(id,AREA));
        assertEquals(Collections.singleton(17),second.listFlags(id,AREA));
    }

    @Test public void corruptExplorationPreventsLossyExportOrNotebookDeletionButNotFlagReading() throws Exception {
        File source=temporary.newFolder();NotebookStore store=new NotebookStore(source);String id=store.createNotebook().id();
        store.save(id,AREA,1,1,InkNote.empty());store.saveExploration(id,AREA,trail());
        File history=file(source,id,AREA+"/exploration.bin");byte[] bad=bytes(history);bad[bad.length-1]^=1;
        Files.write(history.toPath(),bad);
        assertThrows(IOException.class,()->export(store,id));
        assertThrows(IOException.class,()->store.deleteNotebook(id));
        assertArrayEquals(bad,bytes(history));assertEquals(Collections.singleton(17),store.listFlags(id,AREA));
        assertEquals(id,store.listNotebooks().get(0).id());
    }

    @Test public void checksummedArchiveCannotHideBadFutureDuplicatedOrMisplacedExploration() throws Exception {
        File source=temporary.newFolder("source"),target=temporary.newFolder("target");
        NotebookStore first=new NotebookStore(source),second=new NotebookStore(target);
        String id=first.createNotebook().id(),kept=second.createNotebook().id();
        second.saveExploration(kept,AREA,trail());byte[] untouched=export(second,kept);
        first.saveExploration(id,AREA,trail());
        byte[] history=bytes(file(source,id,AREA+"/exploration.bin")),bad=history.clone(),future=history.clone();
        bad[bad.length-1]^=1;ByteBuffer.wrap(future).putInt(4,2);
        byte[] metadata=bytes(file(source,id,"notebook.bin"));
        String path=AREA+"/exploration.bin";
        byte[][] archives={
                rawArchive(id,metadata,new String[]{path},new byte[][]{bad}),
                rawArchive(id,metadata,new String[]{path},new byte[][]{future}),
                rawArchive(id,metadata,new String[]{OTHER+"/exploration.bin"},new byte[][]{history}),
                rawArchive(id,metadata,new String[]{path,path},new byte[][]{history,history}),
                rawArchive(id,metadata,new String[]{path},new byte[][]{new byte[1045]}),
                rawArchive(id,metadata,new String[]{path+".future"},new byte[][]{history})};
        for(byte[] archive:archives) {
            assertThrows(IOException.class,()->second.importNotebook(new ByteArrayInputStream(archive)));
            assertEquals(1,second.listNotebooks().size());assertArrayEquals(untouched,export(second,kept));
            assertFalse(new File(target,id).exists());
        }
        assertArrayEquals(new String[0],temporary.getRoot().list((parent,name)->name.startsWith(".poolrad-notebook-")));
    }

    @Test public void notebookRemovalRetiresExplorationTogetherWithOnlyItsOwnNotes() throws Exception {
        File root=temporary.newFolder();NotebookStore store=new NotebookStore(root);
        String removed=store.createNotebook().id(),kept=store.createNotebook().id();
        store.saveExploration(removed,AREA,trail());store.saveExploration(kept,AREA,trail().clearTrail());
        store.save(removed,AREA,1,1,InkNote.empty());
        byte[] restore=export(store,removed),untouched=export(store,kept);
        store.deleteNotebook(removed);
        assertFalse(new File(root,removed).exists());assertArrayEquals(untouched,export(store,kept));
        assertEquals(1,store.listNotebooks().size());
        store.importNotebook(new ByteArrayInputStream(restore));
        assertEquals(trail(),store.loadExploration(removed,AREA));assertEquals(Collections.singleton(17),store.listFlags(removed,AREA));
    }
}
