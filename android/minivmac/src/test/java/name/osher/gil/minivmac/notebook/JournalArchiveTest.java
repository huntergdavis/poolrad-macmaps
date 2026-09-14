package name.osher.gil.minivmac.notebook;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import name.osher.gil.minivmac.journal.JournalBook;
import name.osher.gil.minivmac.journal.JournalHistory;
import static org.junit.Assert.*;

/** Journal lookups, tasks and flag links must travel with the notebook backup. */
public class JournalArchiveTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String AREA = "por-mac-v11-geo-0", OTHER = "por-mac-v11-geo-32";

    private JournalBook.Key key(int kind, int number) { return new JournalBook.Key(kind, number); }
    private JournalHistory.Flag flag(String area, int x, int y) { return new JournalHistory.Flag(area, x, y); }
    private File file(File root, String id, String path) { return new File(new File(root, id), path); }
    private byte[] export(NotebookStore store, String id) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); store.exportNotebook(id, out); return out.toByteArray();
    }
    private JournalHistory populated() {
        JournalHistory history = new JournalHistory();
        history.opened(key(0, 12)); history.opened(key(1, 78)); history.opened(key(2, 5));
        history.toggle(key(1, 78)); history.toggleDone(key(1, 78));
        history.toggle(key(0, 12));
        history.toggleLink(key(0, 12), flag(AREA, 4, 9));
        history.toggleLink(key(2, 5), flag(OTHER, 11, 2));
        return history;
    }
    private void assertSame(JournalHistory expected, JournalHistory actual) {
        assertEquals(expected.recent(), actual.recent());
        assertEquals(expected.bookmarks(), actual.bookmarks());
        for (JournalBook.Key key : expected.bookmarks()) assertEquals(expected.done(key), actual.done(key));
        assertEquals(expected.linkCount(), actual.linkCount());
        assertEquals(expected.links(key(0, 12)), actual.links(key(0, 12)));
        assertEquals(expected.links(key(2, 5)), actual.links(key(2, 5)));
    }

    @Test public void aBackupCarriesLookupsBookmarksTasksAndFlagLinks() throws Exception {
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore first = new NotebookStore(source), second = new NotebookStore(target);
        String id = first.createNotebook().id();
        first.save(id, AREA, 4, 9, InkNote.empty(), NoteIcon.TEMPLE);
        JournalHistory history = populated();
        first.saveJournal(id, history);
        byte[] stored = Files.readAllBytes(file(source, id, "journal.bin").toPath());

        byte[] archive = export(first, id);
        assertEquals(id, second.importNotebook(new ByteArrayInputStream(archive)).id());
        assertArrayEquals("Restored journal bytes must be identical",
                stored, Files.readAllBytes(file(target, id, "journal.bin").toPath()));
        assertSame(history, second.loadJournal(id));
        assertEquals(NoteIcon.TEMPLE, second.readIcon(id, AREA, 4, 9));
    }

    @Test public void aNotebookWithNoJournalHistoryStillBacksUpAndRestores() throws Exception {
        File source = temporary.newFolder("source"), target = temporary.newFolder("target");
        NotebookStore first = new NotebookStore(source), second = new NotebookStore(target);
        String id = first.createNotebook().id();
        first.save(id, AREA, 1, 1, InkNote.empty(), NoteIcon.FLAG);
        assertFalse(file(source, id, "journal.bin").exists());
        assertTrue(first.loadJournal(id).isEmpty());

        second.importNotebook(new ByteArrayInputStream(export(first, id)));
        assertFalse(file(target, id, "journal.bin").exists());
        assertTrue(second.loadJournal(id).isEmpty());
    }

    @Test public void savingRewritesInPlaceAndKeepsOtherNotebooksSeparate() throws Exception {
        File root = temporary.newFolder("root");
        NotebookStore store = new NotebookStore(root);
        String first = store.createNotebook().id(), second = store.createNotebook().id();
        store.saveJournal(first, populated());
        JournalHistory other = new JournalHistory(); other.opened(key(0, 1));
        store.saveJournal(second, other);

        assertSame(populated(), store.loadJournal(first));
        assertEquals(Arrays.asList(key(0, 1)), store.loadJournal(second).recent());
        assertEquals(0, store.loadJournal(second).linkCount());

        JournalHistory replaced = new JournalHistory(); replaced.opened(key(2, 23));
        store.saveJournal(first, replaced);
        assertEquals(Arrays.asList(key(2, 23)), store.loadJournal(first).recent());
        assertEquals(Arrays.asList(key(0, 1)), store.loadJournal(second).recent());
    }

    @Test public void aDamagedRecordIsAnErrorRatherThanASilentlyEmptyHistory() throws Exception {
        File root = temporary.newFolder("root");
        NotebookStore store = new NotebookStore(root);
        String id = store.createNotebook().id();
        store.saveJournal(id, populated());
        File stored = file(root, id, "journal.bin");
        byte[] good = Files.readAllBytes(stored.toPath());

        byte[] flipped = good.clone(); flipped[flipped.length - 1] ^= 0x40;
        Files.write(stored.toPath(), flipped);
        try { store.loadJournal(id); fail("checksum failure accepted"); } catch (IOException expected) { }
        try { store.exportNotebook(id, new ByteArrayOutputStream()); fail("damaged record was backed up"); }
        catch (IOException expected) { }

        Files.write(stored.toPath(), Arrays.copyOf(good, good.length - 4));
        try { store.loadJournal(id); fail("truncation accepted"); } catch (IOException expected) { }

        Files.write(stored.toPath(), good);
        assertSame(populated(), store.loadJournal(id));
    }

    @Test public void aJournalRecordFromAnotherNotebookIsRefused() throws Exception {
        File root = temporary.newFolder("root");
        NotebookStore store = new NotebookStore(root);
        String first = store.createNotebook().id(), second = store.createNotebook().id();
        store.saveJournal(first, populated());
        Files.copy(file(root, first, "journal.bin").toPath(), file(root, second, "journal.bin").toPath());
        try { store.loadJournal(second); fail("foreign journal record accepted"); } catch (IOException expected) { }
        assertSame(populated(), store.loadJournal(first));
    }

    @Test public void deletingANotebookRemovesItsJournalRecordToo() throws Exception {
        File root = temporary.newFolder("root");
        NotebookStore store = new NotebookStore(root);
        String id = store.createNotebook().id();
        store.saveJournal(id, populated());
        assertTrue(file(root, id, "journal.bin").exists());
        store.deleteNotebook(id);
        assertFalse(new File(root, id).exists());
        assertTrue(store.listNotebooks().isEmpty());
    }
}
