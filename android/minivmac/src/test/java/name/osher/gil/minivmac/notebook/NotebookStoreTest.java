package name.osher.gil.minivmac.notebook;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

public class NotebookStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String AREA = "por-mac-v11-geo-0";

    private InkNote example() {
        return new InkNote(Arrays.asList(
                new InkNote.Stroke(false, 0.0125f, new float[]{0f, 0f, 0.3f, 0.6f, 1f, 1f}),
                new InkNote.Stroke(true, 0.04f, new float[]{0.4f, 0.5f, 0.5f, 0.5f}),
                new InkNote.Stroke(false, 0.02f, new float[]{0.4f, 0.5f})));
    }

    private File noteFile(File root, String notebook, String area, int x, int y) {
        return new File(new File(new File(root, notebook), area), (y * 16 + x) + ".ink");
    }

    private void assertSameInk(InkNote expected, InkNote actual) {
        assertEquals(expected.strokes().size(), actual.strokes().size());
        for (int i = 0; i < expected.strokes().size(); i++) {
            InkNote.Stroke first = expected.strokes().get(i);
            InkNote.Stroke second = actual.strokes().get(i);
            assertEquals(first.eraser(), second.eraser());
            assertEquals(first.width(), second.width(), 0f);
            assertArrayEquals(first.points(), second.points(), 0f);
        }
    }

    @Test public void persistsNotebookRecordsAndExactInkAcrossRestart() throws Exception {
        File root = new File(temporary.getRoot(), "notes");
        NotebookStore store = new NotebookStore(root);
        assertTrue(store.listNotebooks().isEmpty());
        NotebookStore.Notebook first = store.createNotebook();
        NotebookStore.Notebook second = store.createNotebook();
        assertEquals("Notebook 1", first.label());
        assertEquals("Notebook 2", second.label());
        assertNotEquals(first.id(), second.id());
        UUID.fromString(first.id());
        store.save(first.id(), AREA, 15, 1, example());
        NotebookStore restarted = new NotebookStore(root);
        List<NotebookStore.Notebook> books = restarted.listNotebooks();
        assertEquals(2, books.size());
        assertEquals(first.id(), books.get(0).id());
        assertEquals(second.label(), books.get(1).label());
        assertSameInk(example(), restarted.read(first.id(), AREA, 15, 1));
        assertEquals(Collections.singleton(31), restarted.listFlags(first.id(), AREA));
    }

    @Test public void isolatesCampaignsAreasAndTilesAndReopensOnReturn() throws Exception {
        NotebookStore store = new NotebookStore(temporary.getRoot());
        String first = store.createNotebook().id();
        String second = store.createNotebook().id();
        String otherArea = "por-mac-v11-geo-32";
        store.save(first, AREA, 2, 4, example());
        assertTrue(store.read(second, AREA, 2, 4).strokes().isEmpty());
        assertTrue(store.read(first, otherArea, 2, 4).strokes().isEmpty());
        assertTrue(store.read(first, AREA, 3, 4).strokes().isEmpty());
        store.save(first, otherArea, 15, 15, InkNote.empty());
        assertEquals(Collections.singleton(255), store.listFlags(first, otherArea));
        assertEquals(Collections.singleton(66), store.listFlags(first, AREA));
        assertSameInk(example(), store.read(first, AREA, 2, 4));
    }

    @Test public void blankFlagsPersistAndDeleteRemovesOnlyTheSelectedLinkedNote() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        store.save(book, AREA, 0, 0, InkNote.empty());
        store.save(book, AREA, 1, 0, example());
        assertEquals(2, store.listFlags(book, AREA).size());
        store.delete(book, AREA, 0, 0);
        assertEquals(Collections.singleton(1), new NotebookStore(root).listFlags(book, AREA));
        assertSameInk(example(), store.read(book, AREA, 1, 0));
        store.delete(book, AREA, 0, 0); // Repeat deletion is harmless.
        store.delete(book, AREA, 1, 0);
        assertTrue(new NotebookStore(root).listFlags(book, AREA).isEmpty());
    }

    @Test public void replacesExistingInkAndLeavesNoPendingFiles() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        store.save(book, AREA, 2, 3, example());
        store.save(book, AREA, 2, 3, InkNote.empty());
        assertTrue(new NotebookStore(root).read(book, AREA, 2, 3).strokes().isEmpty());
        assertEquals(Collections.singleton(50), store.listFlags(book, AREA));
        assertArrayEquals(new String[]{"50.ink"}, noteFile(root, book, AREA, 2, 3).getParentFile().list());
    }

    @Test public void rejectsInvalidIdentitiesAndTileCoordinates() throws Exception {
        NotebookStore store = new NotebookStore(temporary.getRoot());
        String book = store.createNotebook().id();
        for (String area : new String[]{null, "heap-1234", "por-mac-v11-geo-33", "por-mac-v11-geo-00", "../elsewhere"}) {
            assertThrows(IllegalArgumentException.class, () -> store.read(book, area, 0, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> store.read("../elsewhere", AREA, 0, 0));
        assertThrows(IOException.class, () -> store.read(UUID.randomUUID().toString(), AREA, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> store.save(book, AREA, -1, 0, example()));
        assertThrows(IllegalArgumentException.class, () -> store.save(book, AREA, 0, 16, example()));
        assertThrows(IllegalArgumentException.class, () -> store.delete(book, AREA, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> store.read(book, AREA, 0, -1));
    }

    @Test public void truncatedInkIsVisibleAsFailureAndNeverOverwrittenOrDeleted() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        store.save(book, AREA, 2, 3, example());
        File file = noteFile(root, book, AREA, 2, 3);
        try (RandomAccessFile edit = new RandomAccessFile(file, "rw")) { edit.setLength(12); }
        byte[] corrupt = Files.readAllBytes(file.toPath());
        assertThrows(IOException.class, () -> store.read(book, AREA, 2, 3));
        assertThrows(IOException.class, () -> store.listFlags(book, AREA));
        assertThrows(IOException.class, () -> store.save(book, AREA, 2, 3, InkNote.empty()));
        assertThrows(IOException.class, () -> store.delete(book, AREA, 2, 3));
        assertArrayEquals(corrupt, Files.readAllBytes(file.toPath()));
    }

    @Test public void detectsChecksumFailureWithoutHidingTheFlag() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        store.save(book, AREA, 2, 3, example());
        File file = noteFile(root, book, AREA, 2, 3);
        try (RandomAccessFile edit = new RandomAccessFile(file, "rw")) {
            edit.seek(file.length() - 9);
            int previous = edit.readUnsignedByte();
            edit.seek(file.length() - 9);
            edit.writeByte(previous ^ 1);
        }
        assertThrows(IOException.class, () -> new NotebookStore(root).read(book, AREA, 2, 3));
        assertThrows(IOException.class, () -> store.listFlags(book, AREA));
    }

    @Test public void rejectsUnknownVersionsAndOversizedLengthBeforeAllocation() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        store.save(book, AREA, 2, 3, example());
        File file = noteFile(root, book, AREA, 2, 3);
        byte[] original = Files.readAllBytes(file.toPath());
        try (RandomAccessFile edit = new RandomAccessFile(file, "rw")) {
            edit.seek(4); edit.writeInt(99);
        }
        assertThrows(IOException.class, () -> store.read(book, AREA, 2, 3));
        assertThrows(IOException.class, () -> store.save(book, AREA, 2, 3, InkNote.empty()));
        Files.write(file.toPath(), original);
        try (RandomAccessFile edit = new RandomAccessFile(file, "rw")) {
            edit.seek(8); edit.writeInt(Integer.MAX_VALUE);
        }
        assertThrows(IOException.class, () -> store.read(book, AREA, 2, 3));
    }

    @Test public void refusesNotesMovedToAnotherTileOrCampaign() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String first = store.createNotebook().id();
        String second = store.createNotebook().id();
        store.save(first, AREA, 2, 3, example());
        File original = noteFile(root, first, AREA, 2, 3);
        assertTrue(original.renameTo(noteFile(root, first, AREA, 3, 3)));
        assertThrows(IOException.class, () -> store.read(first, AREA, 3, 3));
        store.save(second, AREA, 2, 3, InkNote.empty());
        Files.copy(noteFile(root, first, AREA, 3, 3).toPath(),
                noteFile(root, second, AREA, 3, 3).toPath());
        assertThrows(IOException.class, () -> store.read(second, AREA, 3, 3));
    }

    @Test public void refusesBrokenNotebookMetadataInsteadOfCreatingANewCampaign() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        File metadata = new File(new File(root, book), "notebook.bin");
        try (RandomAccessFile edit = new RandomAccessFile(metadata, "rw")) { edit.setLength(0); }
        assertThrows(IOException.class, store::listNotebooks);
        assertThrows(IOException.class, store::createNotebook);
        assertThrows(IOException.class, () -> store.save(book, AREA, 0, 0, example()));
        assertEquals(0, metadata.length());
        assertEquals(1, root.list().length);
    }

    @Test public void failedSaveDoesNotClaimAFlagWasCreated() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        File areaFolder = new File(new File(root, book), AREA);
        assertTrue(areaFolder.createNewFile());
        assertThrows(IOException.class, () -> store.save(book, AREA, 0, 0, example()));
        assertEquals(0, areaFolder.length());
        assertTrue(areaFolder.isFile());
    }

    @Test public void ignoresOnlyUncommittedTemporaryInkAndRejectsUnknownAreaRecords() throws Exception {
        File root = temporary.getRoot();
        NotebookStore store = new NotebookStore(root);
        String book = store.createNotebook().id();
        store.save(book, AREA, 2, 3, example());
        File folder = noteFile(root, book, AREA, 2, 3).getParentFile();
        assertTrue(new File(folder, ".pending-crashed.tmp").createNewFile());
        assertEquals(Collections.singleton(50), store.listFlags(book, AREA));
        assertTrue(new File(folder, "999.ink").createNewFile());
        assertThrows(IOException.class, () -> store.listFlags(book, AREA));
    }

    @Test public void listsEveryNoteAcrossAreasNewestFirst() throws Exception {
        NotebookStore store = new NotebookStore(temporary.getRoot());
        String book = store.createNotebook().id();
        String otherArea = "por-mac-v11-geo-20";
        store.save(book, AREA, 1, 2, example());          // tile 33
        store.save(book, otherArea, 3, 4, example());      // tile 67
        store.save(book, AREA, 5, 6, example());           // tile 101
        // Stamp distinct times so "newest first" is deterministic.
        noteFile(temporary.getRoot(), book, AREA, 1, 2).setLastModified(1000);
        noteFile(temporary.getRoot(), book, otherArea, 3, 4).setLastModified(2000);
        noteFile(temporary.getRoot(), book, AREA, 5, 6).setLastModified(3000);

        java.util.List<NotebookStore.NoteEntry> notes = store.listNotes(book);
        assertEquals(3, notes.size());
        assertEquals(101, notes.get(0).tile);             // newest
        assertEquals(AREA, notes.get(0).areaId);
        assertEquals(5, notes.get(0).x());
        assertEquals(6, notes.get(0).y());
        assertEquals(otherArea, notes.get(1).areaId);
        assertEquals(33, notes.get(2).tile);              // oldest
    }

    @Test public void noteIndexIsEmptyForAFreshNotebook() throws Exception {
        NotebookStore store = new NotebookStore(temporary.getRoot());
        assertTrue(store.listNotes(store.createNotebook().id()).isEmpty());
    }
}
