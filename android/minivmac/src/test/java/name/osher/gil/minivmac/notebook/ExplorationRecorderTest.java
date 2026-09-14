package name.osher.gil.minivmac.notebook;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class ExplorationRecorderTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String AREA = "por-mac-v11-geo-0";

    @Test public void recordsObservedMovesNotTurnsAndRestartsWithIndependentAnchor() throws Exception {
        NotebookStore store = new NotebookStore(temporary.getRoot());
        String book = store.createNotebook().id();
        ExplorationRecorder recorder = new ExplorationRecorder(store);
        ExplorationTrail first = recorder.observe(book, AREA, 17, 7, 1000);
        assertEquals(-1, first.steps.get(0).from);
        assertSame(first, recorder.observe(book, AREA, 17, 7, 1250));
        ExplorationTrail moved = recorder.observe(book, AREA, 16, 7, 1500);
        assertEquals(17, moved.steps.get(1).from);
        assertEquals(16, moved.steps.get(1).to);
        ExplorationTrail restored = new ExplorationRecorder(new NotebookStore(temporary.getRoot()))
                .observe(book, AREA, 17, 7, 1750);
        assertEquals(-1, restored.steps.get(2).from);
        assertEquals(2, restored.visitedCount());
    }

    @Test public void unknownModeTimeGapsAndNativeEpochChangesBreakContinuity() throws Exception {
        NotebookStore store = new NotebookStore(temporary.getRoot());
        String book = store.createNotebook().id();
        ExplorationRecorder r = new ExplorationRecorder(store);
        r.observe(book, AREA, 0, 1, 1000);
        r.interrupt();
        assertLastAnchor(r.observe(book, AREA, 1, 1, 1250));
        assertLastAnchor(r.observe(book, AREA, 2, 2, 1500));
        assertLastAnchor(r.observe(book, AREA, 3, 2, 3500));
        assertLastAnchor(r.observe(book, AREA, 4, 2, 3500));
        assertLastAnchor(r.observe(book, AREA, 5, 2, 3000));
        assertLastAnchor(r.observe(book, AREA, 10, 2, 3250));
        assertFalse(r.read(book, AREA).visited(6));
    }

    @Test public void isolatesCampaignsAndAreaReturnsWithoutConnectingEdges() throws Exception {
        NotebookStore store = new NotebookStore(temporary.getRoot());
        String first = store.createNotebook().id(), second = store.createNotebook().id();
        String slums = "por-mac-v11-geo-20";
        ExplorationRecorder r = new ExplorationRecorder(store);
        r.observe(first, AREA, 64, 1, 1000);
        assertLastAnchor(r.observe(first, slums, 79, 1, 1250));
        ExplorationTrail returned = r.observe(first, AREA, 64, 1, 1500);
        assertEquals(1, returned.visitedCount()); assertLastAnchor(returned);
        assertEquals(1, r.observe(second, AREA, 65, 1, 1750).visitedCount());
        assertFalse(store.loadExploration(first, AREA).visited(65));
    }

    @Test public void confirmedClearDoesNotClearFlagAndResumesAtAnAnchor() throws Exception {
        NotebookStore store = new NotebookStore(temporary.getRoot());
        String book = store.createNotebook().id();
        store.save(book, AREA, 1, 1, InkNote.empty());
        ExplorationRecorder r = new ExplorationRecorder(store);
        r.observe(book, AREA, 17, 1, 1000); r.observe(book, AREA, 18, 1, 1250);
        ExplorationTrail cleared = r.clear(book, AREA, false);
        assertEquals(2, cleared.visitedCount()); assertTrue(cleared.steps.isEmpty());
        assertLastAnchor(r.observe(book, AREA, 19, 1, 1500));
        assertEquals(0, r.clear(book, AREA, true).visitedCount());
        assertTrue(store.listFlags(book, AREA).contains(17));
    }

    @Test public void corruptedExistingStorageCannotBeOverwrittenByTheRecorder() throws Exception {
        NotebookStore store = new NotebookStore(temporary.getRoot());
        String book = store.createNotebook().id();
        ExplorationRecorder r = new ExplorationRecorder(store);
        r.observe(book, AREA, 17, 1, 1000);
        File file = new File(temporary.getRoot(), book + "/" + AREA + "/exploration.bin");
        try (RandomAccessFile broken = new RandomAccessFile(file, "rw")) { broken.setLength(9); }
        byte[] before = Files.readAllBytes(file.toPath());
        assertThrows(IOException.class, () -> r.observe(book, AREA, 18, 1, 1250));
        assertArrayEquals(before, Files.readAllBytes(file.toPath()));
    }

    private void assertLastAnchor(ExplorationTrail trail) {
        assertEquals(-1, trail.steps.get(trail.steps.size()-1).from);
    }
}
