package name.osher.gil.minivmac.notebook;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.CRC32;
import static org.junit.Assert.*;

public class NoteTemplateTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String AREA = "por-mac-v11-geo-0";

    @Test public void everyTemplateAndInkSurviveRestartAndBackup() throws Exception {
        File root = temporary.newFolder("source");
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        InkNote.Stroke stroke = new InkNote.Stroke(false,.01f,new float[]{.6f,.2f,.9f,.8f});
        for (NoteTemplate template : NoteTemplate.values())
            store.save(book,AREA,template.ordinal(),0,
                    new InkNote(Collections.singletonList(stroke),template),NoteIcon.TREASURE);
        ByteArrayOutputStream backup = new ByteArrayOutputStream();
        store.exportNotebook(book,backup);
        NotebookStore restored = new NotebookStore(temporary.newFolder("restored"));
        assertEquals(book,restored.importNotebook(new ByteArrayInputStream(backup.toByteArray())).id());
        for (NotebookStore reader : Arrays.asList(new NotebookStore(root),restored))
            for (NoteTemplate template : NoteTemplate.values()) {
                InkNote note = reader.read(book,AREA,template.ordinal(),0);
                assertEquals(template,note.template());
                assertEquals(NoteIcon.TREASURE,reader.readIcon(book,AREA,template.ordinal(),0));
                assertEquals(1,note.strokes().size());
                assertArrayEquals(stroke.points(),note.strokes().get(0).points(),0);
            }
    }

    @Test public void emptyTemplatePagesSurviveBackupWithoutSyntheticInk() throws Exception {
        NotebookStore store = new NotebookStore(temporary.newFolder("source"));
        String book = store.createNotebook().id();
        store.save(book,AREA,4,2,InkNote.empty().withTemplate(NoteTemplate.MAP_FRAME));
        ByteArrayOutputStream backup = new ByteArrayOutputStream();store.exportNotebook(book,backup);
        NotebookStore restored = new NotebookStore(temporary.newFolder("restored"));
        restored.importNotebook(new ByteArrayInputStream(backup.toByteArray()));
        InkNote note = restored.read(book,AREA,4,2);
        assertEquals(NoteTemplate.MAP_FRAME,note.template());
        assertTrue(note.strokes().isEmpty());
        assertEquals(NoteTemplate.PLAIN,restored.read(book,AREA,5,2).template());
    }

    @Test public void choosingPaperPreservesInkAndItsRedoBranch() {
        InkHistory history = new InkHistory(InkNote.empty());
        history.beginStroke(false,.01f,.6f,.4f);history.appendPoint(.9f,.4f);history.finishStroke();
        assertTrue(history.setTemplate(NoteTemplate.GRID));
        assertTrue(history.undo());assertEquals(NoteTemplate.GRID,history.getNote().template());
        assertTrue(history.setTemplate(NoteTemplate.RULED));assertTrue(history.redo());
        assertEquals(1,history.getNote().strokes().size());
        assertEquals(NoteTemplate.RULED,history.getNote().template());
        history.reset(history.getNote());
        assertEquals(NoteTemplate.RULED,history.getNote().template());
        assertFalse(history.setTemplate(NoteTemplate.RULED));
    }

    @Test public void unknownTemplateIsRejectedWithoutOverwritingTheFile() throws Exception {
        File root = temporary.newFolder("source");NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        store.save(book,AREA,2,3,InkNote.empty());
        File file = new File(new File(new File(root,book),AREA),"50.ink");
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try(DataOutputStream out = new DataOutputStream(payload)) {
            out.writeUTF(book);out.writeUTF(AREA);out.writeByte(2);out.writeByte(3);
            out.writeUTF(NoteIcon.FLAG.id());out.writeUTF("future-template");out.writeInt(0);
        }
        CRC32 crc = new CRC32();crc.update(payload.toByteArray());
        try(DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeInt(0x50524e49);out.writeInt(3);out.writeInt(payload.size());
            out.write(payload.toByteArray());out.writeLong(crc.getValue());
        }
        byte[] before = Files.readAllBytes(file.toPath());
        try {store.read(book,AREA,2,3);fail("Accepted unknown paper");}catch(IOException expected){assertEquals("Unknown note template",expected.getCause().getMessage());}
        try {store.save(book,AREA,2,3,InkNote.empty());fail("Overwrote unknown paper");}catch(IOException expected){}
        assertArrayEquals(before,Files.readAllBytes(file.toPath()));
    }
}
