package name.osher.gil.minivmac.notebook;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import name.osher.gil.minivmac.journal.MessageHistory;
import static name.osher.gil.minivmac.journal.MessageHistoryTest.message;
import static org.junit.Assert.*;

public class MessageArchiveTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();

    @Test public void messagesRoundTripWithNotebookAndRemovalLeavesOtherCampaignAlone() throws Exception {
        File source=temporary.newFolder(), destination=temporary.newFolder();
        NotebookStore a=new NotebookStore(source), b=new NotebookStore(destination);
        String first=a.createNotebook().id(), second=a.createNotebook().id();
        MessageHistory log=new MessageHistory(); log.observe(message("A private campaign message.",true),123);
        a.saveMessages(first,log.encode());
        assertTrue(a.loadMessages(second).entries().isEmpty());
        ByteArrayOutputStream archive=new ByteArrayOutputStream(); a.exportNotebook(first,archive);
        b.importNotebook(new ByteArrayInputStream(archive.toByteArray()));
        assertArrayEquals(Files.readAllBytes(new File(source,first+"/messages.bin").toPath()),
                Files.readAllBytes(new File(destination,first+"/messages.bin").toPath()));
        assertTrue(b.loadMessages(first).entries().get(0).truncated);
        assertEquals(123,b.loadMessages(first).entries().get(0).observedAt);
        a.deleteNotebook(first);
        assertFalse(new File(source,first).exists());
        assertEquals(second,a.listNotebooks().get(0).id());
    }

    @Test public void oldNotebookWithoutMessagesStillExportsAndImports() throws Exception {
        NotebookStore a=new NotebookStore(temporary.newFolder()), b=new NotebookStore(temporary.newFolder());
        String id=a.createNotebook().id();
        ByteArrayOutputStream archive=new ByteArrayOutputStream(); a.exportNotebook(id,archive);
        b.importNotebook(new ByteArrayInputStream(archive.toByteArray()));
        assertTrue(b.loadMessages(id).entries().isEmpty());
    }

    @Test public void foreignAndDamagedLogsAreRejectedWithoutReplacingGoodData() throws Exception {
        File root=temporary.newFolder(); NotebookStore store=new NotebookStore(root);
        String a=store.createNotebook().id(), b=store.createNotebook().id();
        MessageHistory log=new MessageHistory(); log.observe(message("Original",false),1);
        store.saveMessages(a,log.encode());
        File original=new File(root,a+"/messages.bin"), foreign=new File(root,b+"/messages.bin");
        byte[] good=Files.readAllBytes(original.toPath());
        Files.write(foreign.toPath(),good);
        try { store.loadMessages(b); fail(); } catch(IOException expected) { }
        byte[] bad=good.clone(); bad[bad.length-1]^=1; Files.write(original.toPath(),bad);
        try { store.loadMessages(a); fail(); } catch(IOException expected) { }
        try { store.exportNotebook(a,new ByteArrayOutputStream()); fail(); } catch(IOException expected) { }
        Files.write(original.toPath(),good);
        try { store.saveMessages(a,new byte[]{0,0,0,1}); fail(); } catch(IOException expected) { }
        assertArrayEquals(good,Files.readAllBytes(original.toPath()));
    }
}
