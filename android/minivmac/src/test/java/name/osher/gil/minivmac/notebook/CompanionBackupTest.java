package name.osher.gil.minivmac.notebook;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** The whole-companion backup round-trips notebooks and fog switches (F63). */
public class CompanionBackupTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private static final String AREA = "por-mac-v11-geo-0";

    private InkNote ink() {
        return new InkNote(Arrays.asList(new InkNote.Stroke(false, 0.02f, new float[]{0.1f, 0.1f, 0.9f, 0.9f})));
    }

    private byte[] backup(NotebookStore store, Map<String, Boolean> fog) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompanionBackup.write(out, store, fog);
        return out.toByteArray();
    }

    @Test public void restoresEveryNotebookWithItsNotesAndTheFogSwitches() throws Exception {
        NotebookStore source = new NotebookStore(tmp.newFolder("source"));
        NotebookStore.Notebook a = source.createNotebook();
        NotebookStore.Notebook b = source.createNotebook();
        source.save(a.id(), AREA, 1, 2, ink());
        source.save(b.id(), AREA, 3, 4, ink());
        Map<String, Boolean> fog = new LinkedHashMap<>();
        fog.put("poolrad_visited_only", true);
        fog.put("poolrad_footprints." + AREA, false);

        byte[] archive = backup(source, fog);

        NotebookStore restored = new NotebookStore(tmp.newFolder("restored"));
        CompanionBackup.Restored result = CompanionBackup.read(new ByteArrayInputStream(archive), restored);

        assertEquals(2, result.notebooks);
        assertEquals(0, result.skipped);
        assertEquals(2, restored.listNotebooks().size());
        // Original ids survive, so a note is exactly where it was.
        assertEquals(java.util.Collections.singleton(2 * 16 + 1), restored.listFlags(a.id(), AREA));
        assertEquals(java.util.Collections.singleton(4 * 16 + 3), restored.listFlags(b.id(), AREA));
        assertEquals(Boolean.TRUE, result.fog.get("poolrad_visited_only"));
        assertEquals(Boolean.FALSE, result.fog.get("poolrad_footprints." + AREA));
    }

    @Test public void leavesAnAlreadyPresentNotebookUntouched() throws Exception {
        NotebookStore source = new NotebookStore(tmp.newFolder("source2"));
        NotebookStore.Notebook a = source.createNotebook();
        source.save(a.id(), AREA, 5, 6, ink());
        byte[] archive = backup(source, new LinkedHashMap<>());

        // The destination already holds that same notebook (same id): it is skipped, not duplicated.
        File destRoot = tmp.newFolder("dest2");
        NotebookStore dest = new NotebookStore(destRoot);
        CompanionBackup.read(new ByteArrayInputStream(archive), dest);
        CompanionBackup.Restored again = CompanionBackup.read(new ByteArrayInputStream(archive), dest);

        assertEquals(0, again.notebooks);
        assertEquals(1, again.skipped);
        assertEquals(1, dest.listNotebooks().size());
    }
}
