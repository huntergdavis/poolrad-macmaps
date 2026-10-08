package name.osher.gil.minivmac;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class DiskCheckpointStoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void writeAt(RandomAccessFile file, long offset, int value) throws IOException {
        byte[] block = new byte[4096];
        Arrays.fill(block, (byte)value);
        file.seek(offset);
        file.write(block);
    }

    private static void refused(Checked work) throws Exception {
        try { work.run(); fail("Expected disk recovery refusal"); }
        catch (IOException expected) { }
    }
    private interface Checked { void run() throws Exception; }

    @Test public void quickSavesShareOneBaseAndKeepOnlyChangedBlocks() throws Exception {
        File disks = tmp.newFolder("disks"), saves = tmp.newFolder("saves");
        File image = new File(disks, "disk1.dsk");
        try (RandomAccessFile file = new RandomAccessFile(image, "rw")) {
            file.setLength(2 * 1024 * 1024);
            writeAt(file, 4096, 21);
            DiskSnapshotGuard guard = new DiskSnapshotGuard();
            guard.mounted(0, file, true, image);
            DiskCheckpointStore checkpoints = new DiskCheckpointStore(saves, disks);
            SaveStateStore states = new SaveStateStore(saves);
            File first = states.reserveQuick(1000);
            DiskSnapshotGuard.Fingerprint firstHash = checkpoints.capture(guard.capture(), first);
            states.write(first, new byte[10000], firstHash);
            states.finishQuick(first, 1000);
            byte[] firstBytes = Files.readAllBytes(image.toPath());
            assertEquals(firstHash, guard.capture().fingerprint());

            guard.beforeWrite();
            writeAt(file, 4096, 74);
            writeAt(file, 1024 * 1024, 33);
            File second = states.reserveQuick(2000);
            DiskSnapshotGuard.Fingerprint secondHash = checkpoints.capture(guard.capture(), second);
            states.write(second, new byte[10000], secondHash);
            states.finishQuick(second, 2000);
            assertTrue("Only two changed blocks should be stored",
                    checkpoints.sidecar(second).length() < 20 * 1024);
            assertEquals(1, new File(saves, "disk-bases")
                    .listFiles((dir, name) -> name.endsWith(".gz")).length);
            assertNotEquals(firstHash, secondHash);

            byte[] current = Files.readAllBytes(image.toPath());
            refused(() -> guard.verify(firstHash));
            try (DiskCheckpointStore.Prepared prepared = checkpoints.prepare(first, firstHash)) {
                assertArrayEquals(firstBytes, Files.readAllBytes(prepared.disks.get(0).staged.toPath()));
                DiskRestoreSwap swap = new DiskRestoreSwap(prepared);
                swap.begin();
                assertArrayEquals(firstBytes, Files.readAllBytes(image.toPath()));
                // A rejected machine restore must put the running disk back.
                swap.rollback();
                assertArrayEquals(current, Files.readAllBytes(image.toPath()));
            }
            try (DiskCheckpointStore.Prepared prepared = checkpoints.prepare(first, firstHash)) {
                DiskRestoreSwap swap = new DiskRestoreSwap(prepared);
                swap.begin();
                swap.commit();
                assertArrayEquals(firstBytes, Files.readAllBytes(image.toPath()));
            }
            assertTrue(states.delete(second));
            assertFalse(checkpoints.hasCheckpoint(second));
            assertEquals(1, new File(saves, "disk-bases")
                    .listFiles((dir, name) -> name.endsWith(".gz")).length);
            try (DiskCheckpointStore.Prepared prepared = checkpoints.prepare(first, firstHash)) {
                assertArrayEquals(firstBytes, Files.readAllBytes(prepared.disks.get(0).staged.toPath()));
            }
        }
    }

    @Test public void anUnmountedAndMissingDiskCanBeRestoredFromItsQuickSave() throws Exception {
        File disks = tmp.newFolder("disks"), saves = tmp.newFolder("saves");
        File image = new File(disks, "disk1.dsk");
        DiskSnapshotGuard.Fingerprint fingerprint;
        DiskCheckpointStore checkpoints = new DiskCheckpointStore(saves, disks);
        File save = new File(saves, "quick.prqs");
        try (RandomAccessFile file = new RandomAccessFile(image, "rw")) {
            file.setLength(256 * 1024);
            writeAt(file, 8192, 91);
            DiskSnapshotGuard guard = new DiskSnapshotGuard();
            guard.mounted(0, file, true, image);
            fingerprint = checkpoints.capture(guard.capture(), save);
        }
        byte[] saved = Files.readAllBytes(image.toPath());
        assertTrue(image.delete());
        try (DiskCheckpointStore.Prepared prepared = checkpoints.prepare(save, fingerprint)) {
            DiskRestoreSwap swap = new DiskRestoreSwap(prepared);
            swap.begin();
            swap.commit();
        }
        assertArrayEquals(saved, Files.readAllBytes(image.toPath()));
        try (RandomAccessFile mounted = new RandomAccessFile(image, "rw")) {
            DiskSnapshotGuard nextProcess = new DiskSnapshotGuard();
            nextProcess.mounted(0, mounted, true, image);
            assertTrue(nextProcess.isCurrent(nextProcess.verify(fingerprint)));
        }
    }

    @Test public void staleCaptureAndCorruptDeltaCannotPublishOrReplaceADisk() throws Exception {
        File disks = tmp.newFolder("disks"), saves = tmp.newFolder("saves");
        File image = new File(disks, "disk1.dsk");
        File save = new File(saves, "quick.prqs");
        DiskCheckpointStore checkpoints = new DiskCheckpointStore(saves, disks);
        try (RandomAccessFile file = new RandomAccessFile(image, "rw")) {
            file.setLength(128 * 1024);
            DiskSnapshotGuard guard = new DiskSnapshotGuard();
            guard.mounted(0, file, true, image);
            DiskSnapshotGuard.Ticket stale = guard.capture();
            guard.beforeWrite();
            refused(() -> checkpoints.capture(stale, save));
            assertFalse(checkpoints.hasCheckpoint(save));
            DiskSnapshotGuard.Fingerprint hash = checkpoints.capture(guard.capture(), save);
            byte[] original = Files.readAllBytes(image.toPath());
            byte[] bundle = Files.readAllBytes(checkpoints.sidecar(save).toPath());
            Files.write(checkpoints.sidecar(save).toPath(), Arrays.copyOf(bundle, bundle.length / 2));
            refused(() -> checkpoints.prepare(save, hash));
            assertArrayEquals(original, Files.readAllBytes(image.toPath()));
        }
    }
}
