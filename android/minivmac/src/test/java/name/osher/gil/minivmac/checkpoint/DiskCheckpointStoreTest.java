package name.osher.gil.minivmac.checkpoint;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import name.osher.gil.minivmac.desktop.DiskAccessGate;
import static org.junit.Assert.*;

/** Synthetic disk files only; no ROM, game disk or emulator is involved. */
public class DiskCheckpointStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3};

    private File disk(String name, String content) throws IOException {
        File file = new File(temporary.getRoot(), name);
        Files.write(file.toPath(), content.getBytes("UTF-8"));
        return file;
    }
    private byte[] sha256(byte[] value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value);
    }
    private DiskCheckpointStore store(DiskAccessGate gate) throws IOException {
        return new DiskCheckpointStore(new File(temporary.getRoot(), "checkpoints"), gate);
    }
    private DiskCheckpointStore store() throws IOException { return store(new DiskAccessGate()); }

    @Test public void aCheckpointCopiesTheExactBytesAndRecordsItsLabels() throws Exception {
        DiskCheckpointStore store = store();
        File disk = disk("disk1.dsk", "campaign contents");
        DiskCheckpointStore.Checkpoint saved = store.create(disk, "New Phlan", "Notebook 1", PNG);

        assertEquals("disk1.dsk", saved.diskName);
        assertEquals("New Phlan", saved.areaLabel);
        assertEquals("Notebook 1", saved.notebookLabel);
        assertEquals(disk.length(), saved.diskBytes);
        assertArrayEquals(sha256("campaign contents".getBytes("UTF-8")), saved.digest);
        assertTrue(saved.hasThumbnail());
        assertArrayEquals(PNG, saved.thumbnail());
        assertTrue(store.verify(saved.id));

        List<DiskCheckpointStore.Checkpoint> listed = store.list();
        assertEquals(1, listed.size());
        assertEquals(saved.id, listed.get(0).id);
        assertArrayEquals(saved.digest, listed.get(0).digest);
        assertArrayEquals(PNG, listed.get(0).thumbnail());
        assertEquals(saved.shortDigest(), listed.get(0).shortDigest());

        // The original disk is never touched, and nothing lands beside it.
        assertEquals("campaign contents", new String(Files.readAllBytes(disk.toPath()), "UTF-8"));
        assertEquals(1, temporary.getRoot().listFiles((d, n) -> n.startsWith(".pending-")).length == 0 ? 1 : 0);
    }

    @Test public void anEmulatingGuestBlocksEveryCheckpointOperation() throws Exception {
        DiskAccessGate gate = new DiskAccessGate();
        DiskCheckpointStore store = store(gate);
        File disk = disk("disk1.dsk", "running");
        DiskCheckpointStore.Checkpoint saved = store.create(disk, "New Phlan", "Notebook 1", null);

        DiskAccessGate.Lease emulation = gate.tryBeginEmulation();
        assertNotNull(emulation);
        for (Runnable attempt : new Runnable[]{
                () -> { try { store.create(disk, "a", "b", null); fail("saved while emulating"); }
                        catch (IOException expected) { assertTrue(expected.getMessage().contains("Shut down the Mac")); } },
                () -> { try { store.restore(saved.id, disk); fail("restored while emulating"); }
                        catch (IOException expected) { assertTrue(expected.getMessage().contains("Shut down the Mac")); } },
                () -> { try { store.delete(saved.id); fail("deleted while emulating"); }
                        catch (IOException expected) { assertTrue(expected.getMessage().contains("Shut down the Mac")); } }}) {
            attempt.run();
        }
        assertEquals(1, store.list().size());
        assertEquals("running", new String(Files.readAllBytes(disk.toPath()), "UTF-8"));

        emulation.close();
        assertNotNull(store.create(disk, "New Phlan", "Notebook 1", null));
    }

    @Test public void restoringReplacesTheDiskAndLeavesAnUndoCheckpoint() throws Exception {
        DiskCheckpointStore store = store();
        File disk = disk("disk1.dsk", "original campaign");
        DiskCheckpointStore.Checkpoint first = store.create(disk, "New Phlan", "Notebook 1", PNG);

        Files.write(disk.toPath(), "later campaign".getBytes("UTF-8"));
        DiskCheckpointStore.Checkpoint safety = store.restore(first.id, disk);

        assertEquals("original campaign", new String(Files.readAllBytes(disk.toPath()), "UTF-8"));
        assertArrayEquals(sha256("later campaign".getBytes("UTF-8")), safety.digest);
        assertEquals("Before restoring a checkpoint", safety.areaLabel);
        assertTrue(store.verify(safety.id) && store.verify(first.id));
        assertEquals(2, store.list().size());

        // The safety copy really does undo the restore.
        store.restore(safety.id, disk);
        assertEquals("later campaign", new String(Files.readAllBytes(disk.toPath()), "UTF-8"));
        assertEquals(3, store.list().size());
    }

    @Test public void newestCheckpointsAreListedFirstAndTheLimitIsEnforced() throws Exception {
        DiskCheckpointStore store = store();
        File disk = disk("disk1.dsk", "x");
        String[] ids = new String[DiskCheckpointStore.MAX_CHECKPOINTS];
        for (int i = 0; i < ids.length; i++) {
            Files.write(disk.toPath(), ("state " + i).getBytes("UTF-8"));
            ids[i] = store.create(disk, "Area " + i, "Notebook 1", null).id;
            Thread.sleep(2); // Distinct wall-clock stamps for a stable order.
        }
        try { store.create(disk, "one too many", "Notebook 1", null); fail("exceeded the checkpoint limit"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("limit reached")); }
        // Restoring needs room for its safety copy and says so rather than silently dropping it.
        try { store.restore(ids[0], disk); fail("restored with no room for a safety copy"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("Delete a checkpoint first")); }

        List<DiskCheckpointStore.Checkpoint> listed = store.list();
        assertEquals(DiskCheckpointStore.MAX_CHECKPOINTS, listed.size());
        assertEquals(ids[ids.length - 1], listed.get(0).id);
        assertEquals(ids[0], listed.get(listed.size() - 1).id);
        for (int i = 0; i + 1 < listed.size(); i++)
            assertTrue(listed.get(i).createdAt >= listed.get(i + 1).createdAt);

        store.delete(ids[0]);
        assertEquals(DiskCheckpointStore.MAX_CHECKPOINTS - 1, store.list().size());
        assertNotNull(store.create(disk, "room again", "Notebook 1", null));
    }

    @Test public void aDamagedCheckpointIsRefusedRatherThanRestored() throws Exception {
        DiskCheckpointStore store = store();
        File disk = disk("disk1.dsk", "original campaign");
        DiskCheckpointStore.Checkpoint saved = store.create(disk, "New Phlan", "Notebook 1", null);
        File folder = new File(new File(temporary.getRoot(), "checkpoints"), saved.id);
        File payload = new File(folder, "disk.img");

        Files.write(payload.toPath(), "tampered campaign".getBytes("UTF-8"));
        assertFalse(store.verify(saved.id));
        Files.write(disk.toPath(), "current campaign".getBytes("UTF-8"));
        try { store.restore(saved.id, disk); fail("restored a damaged checkpoint"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("no longer verifies")); }
        assertEquals("The live disk must survive a refused restore",
                "current campaign", new String(Files.readAllBytes(disk.toPath()), "UTF-8"));
        assertEquals("A refused restore must not consume a checkpoint slot", 1, store.list().size());
    }

    @Test public void damagedOrForeignDetailsAreAnErrorRatherThanAnEmptyList() throws Exception {
        DiskCheckpointStore store = store();
        File disk = disk("disk1.dsk", "campaign");
        DiskCheckpointStore.Checkpoint saved = store.create(disk, "New Phlan", "Notebook 1", PNG);
        File root = new File(temporary.getRoot(), "checkpoints");
        File meta = new File(new File(root, saved.id), "checkpoint.bin");
        byte[] good = Files.readAllBytes(meta.toPath());

        byte[] flipped = good.clone(); flipped[flipped.length - 2] ^= 0x20;
        Files.write(meta.toPath(), flipped);
        try { store.list(); fail("accepted a damaged record"); } catch (IOException expected) { }

        Files.write(meta.toPath(), Arrays.copyOf(good, good.length - 3));
        try { store.list(); fail("accepted a truncated record"); } catch (IOException expected) { }

        Files.write(meta.toPath(), good);
        assertEquals(1, store.list().size());

        // A folder whose name is not the recorded id cannot be passed off as one.
        File moved = new File(root, "00000000-0000-0000-0000-000000000001");
        assertTrue(new File(root, saved.id).renameTo(moved));
        try { store.list(); fail("accepted a renamed checkpoint"); } catch (IOException expected) { }
    }

    @Test public void unusableDisksLabelsAndThumbnailsAreRejectedBeforeAnyCopy() throws Exception {
        DiskCheckpointStore store = store();
        File disk = disk("disk1.dsk", "campaign");
        File empty = disk("empty.dsk", "");

        try { store.create(null, "a", "b", null); fail("null disk"); } catch (IOException expected) { }
        try { store.create(empty, "a", "b", null); fail("empty disk"); } catch (IOException expected) { }
        try { store.create(new File(temporary.getRoot(), "absent.dsk"), "a", "b", null); fail("missing disk"); }
        catch (IOException expected) { }
        try { store.create(temporary.getRoot(), "a", "b", null); fail("directory as disk"); } catch (IOException expected) { }

        StringBuilder tooLong = new StringBuilder();
        while (tooLong.length() <= DiskCheckpointStore.MAX_LABEL_CHARS) tooLong.append('x');
        try { store.create(disk, tooLong.toString(), "b", null); fail("oversized label"); } catch (IOException expected) { }
        try { store.create(disk, "badlabel", "b", null); fail("control character in label"); } catch (IOException expected) { }
        try { store.create(disk, "a", "b", new byte[]{1, 2, 3, 4, 5, 6, 7, 8}); fail("non-PNG thumbnail"); }
        catch (IOException expected) { }
        try { store.create(disk, "a", "b", new byte[DiskCheckpointStore.MAX_THUMBNAIL_BYTES + 1]); fail("oversized thumbnail"); }
        catch (IOException expected) { }

        assertTrue("A rejected request must not create storage", store.list().isEmpty());
        DiskCheckpointStore.Checkpoint blank = store.create(disk, "  ", null, null);
        assertEquals("Not recorded", blank.areaLabel);
        assertEquals("Not recorded", blank.notebookLabel);
        assertFalse(blank.hasThumbnail());
        assertNull(blank.thumbnail());
    }

    @Test public void unknownCheckpointIdsCannotReachOutsideTheStore() throws Exception {
        DiskCheckpointStore store = store();
        File disk = disk("disk1.dsk", "campaign");
        store.create(disk, "New Phlan", "Notebook 1", null);
        for (String id : new String[]{null, "", "..", "../checkpoints", "not-a-uuid",
                "00000000-0000-0000-0000-000000000000", "/etc"}) {
            try { store.delete(id); fail("deleted with id " + id); } catch (IOException expected) { }
            try { store.restore(id, disk); fail("restored with id " + id); } catch (IOException expected) { }
            try { store.verify(id); fail("verified id " + id); } catch (IOException expected) { }
        }
        assertEquals("campaign", new String(Files.readAllBytes(disk.toPath()), "UTF-8"));
        assertEquals("Rejected ids must not create safety copies", 1, store.list().size());
    }
}
