package name.osher.gil.minivmac.notebook;

import java.io.IOException;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class NotebookSelectionTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void choosesOnlyTheRememberedCampaign() throws Exception {
        NotebookStore store = new NotebookStore(temp.newFolder());
        store.createNotebook(); NotebookStore.Notebook second = store.createNotebook();
        assertEquals(second.id(), NotebookSelection.choose(store.listNotebooks(), second.id()).id());
        assertEquals("Notebook 1", NotebookSelection.choose(store.listNotebooks(), "").label());
        assertNull(NotebookSelection.choose(Collections.emptyList(), ""));
    }
    @Test public void missingRememberedCampaignNeverFallsBack() throws Exception {
        NotebookStore store = new NotebookStore(temp.newFolder()); store.createNotebook();
        try { NotebookSelection.choose(store.listNotebooks(), "gone"); fail(); } catch (IOException expected) { }
        try { NotebookSelection.choose(Collections.emptyList(), "gone"); fail(); } catch (IOException expected) { }
    }
}
