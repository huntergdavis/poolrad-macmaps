package name.osher.gil.minivmac.desktop;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.Assert.*;

/** Synthetic HFS only. No firmware, guest system, or game bytes are test fixtures. */
public class DesktopAppearanceStoreTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private File disks, state, disk;
    private byte[] initial;
    private DiskAccessGate gate;
    private DesktopAppearanceStore store;

    @Before public void setUp() throws Exception {
        disks = temporary.newFolder("disks");
        state = new File(temporary.getRoot(), "appearance");
        disk = new File(disks, "campaign.dsk");
        initial = DesktopDiskTest.fixture();
        Files.write(disk.toPath(), initial);
        gate = new DiskAccessGate();
        store = new DesktopAppearanceStore(disks, state, gate);
    }

    private interface IoAction { void run() throws Exception; }
    private static IOException rejects(IoAction action) throws Exception {
        try { action.run(); fail("Expected an IOException"); }
        catch (IOException expected) { return expected; }
        return null;
    }

    private void noStage() {
        File[] files = disks.listFiles();
        assertNotNull(files);
        for (File file : files) assertFalse("Staging disk was left behind: " + file.getName(),
                file.getName().startsWith(".poolrad-appearance-"));
        if (state.exists()) {
            for (File file : state.listFiles()) assertTrue("Temporary backup was left behind", file.getName().endsWith(".prda"));
        }
    }

    private File backup() {
        File[] files = state.listFiles();
        assertNotNull(files); assertEquals(1, files.length);
        return files[0];
    }

    @Test public void inspectDoesNotCreateBackupOrChangeDisk() throws Exception {
        DesktopAppearanceStore.Snapshot snapshot = store.inspect(disk);
        assertNull(snapshot.originalPat); assertNull(snapshot.originalPpat);
        assertFalse(state.exists());
        assertArrayEquals(initial, Files.readAllBytes(disk.toPath()));
        assertFalse(gate.isMaintenanceBusy());
    }

    @Test public void eachStyleAndRepeatedRestoreRecoverExactOriginalDisk() throws Exception {
        byte[] pat = DesktopDisk.inspect(disk).pat(), ppat = DesktopDisk.inspect(disk).ppat();
        for (DesktopDisk.Style style : DesktopDisk.Style.values()) {
            DesktopAppearanceStore.Snapshot changed = store.apply(disk, style);
            assertArrayEquals(DesktopDisk.patternBits(style), changed.disk.pat());
            assertArrayEquals(pat, changed.originalPat); assertArrayEquals(ppat, changed.originalPpat);
            assertArrayEquals(pat, store.inspect(disk).originalPat);
            assertTrue(backup().length() < 8192);
            store.restore(disk);
            assertArrayEquals(initial, Files.readAllBytes(disk.toPath()));
            store.restore(disk);
            assertArrayEquals(initial, Files.readAllBytes(disk.toPath()));
        }
        noStage();
    }

    @Test public void restoreKeepsNewCampaignDataRatherThanRollingBackDisk() throws Exception {
        store.apply(disk, DesktopDisk.Style.WHITE);
        byte[] current = Files.readAllBytes(disk.toPath());
        int at = DesktopDiskTest.GAME_OFFSET;
        Arrays.fill(current, at, at + 512, (byte) 0x61);
        Files.write(disk.toPath(), current);
        byte[] expected = initial.clone();
        Arrays.fill(expected, at, at + 512, (byte) 0x61);
        store.apply(disk, DesktopDisk.Style.STONE);
        store.restore(disk);
        assertArrayEquals(expected, Files.readAllBytes(disk.toPath()));
        noStage();
    }

    @Test public void originalBackupAndSnapshotsDoNotBecomeTheLastSelectedStyle() throws Exception {
        DesktopAppearanceStore.Snapshot first = store.apply(disk, DesktopDisk.Style.WHITE);
        byte[] original = first.originalPat.clone(), raw = Files.readAllBytes(backup().toPath());
        long modified = backup().lastModified();
        Arrays.fill(first.originalPat, (byte) 0); Arrays.fill(first.originalPpat, (byte) 0);
        store.apply(disk, DesktopDisk.Style.MIST);
        assertArrayEquals(original, store.inspect(disk).originalPat);
        assertArrayEquals(raw, Files.readAllBytes(backup().toPath()));
        assertEquals(modified, backup().lastModified());
        store.restore(disk);
        assertArrayEquals(initial, Files.readAllBytes(disk.toPath()));
    }

    @Test public void restoreWithoutBackupRefusesWithoutCreatingAnything() throws Exception {
        rejects(() -> store.restore(disk));
        assertFalse(state.exists());
        assertArrayEquals(initial, Files.readAllBytes(disk.toPath()));
        noStage();
    }

    @Test public void activeCoreHostIoAndMaintenanceEachRefuseAllOperations() throws Exception {
        try (DiskAccessGate.Lease lease = gate.tryBeginEmulation()) {
            assertNotNull(lease);
            rejects(() -> store.inspect(disk)); rejects(() -> store.apply(disk, DesktopDisk.Style.WHITE));
            rejects(() -> store.restore(disk));
        }
        try (DiskAccessGate.Lease lease = gate.beginHostIo()) {
            rejects(() -> store.inspect(disk)); rejects(() -> store.apply(disk, DesktopDisk.Style.WHITE));
        }
        try (DiskAccessGate.Lease lease = gate.tryBeginMaintenance()) {
            assertNotNull(lease); rejects(() -> store.inspect(disk));
        }
        assertArrayEquals(initial, Files.readAllBytes(disk.toPath()));
        assertFalse(state.exists());
        store.apply(disk, DesktopDisk.Style.WHITE);
        noStage();
    }

    @Test public void interruptedBeforePublishKeepsOriginalAndReusableBackup() throws Exception {
        DesktopAppearanceStore interrupted = new DesktopAppearanceStore(disks, state, gate,
                (source, stage) -> {
                    assertTrue(stage.isFile());
                    assertTrue(backup().isFile());
                    assertTrue(gate.isMaintenanceBusy());
                    throw new IOException("Simulated interruption");
                });
        rejects(() -> interrupted.apply(disk, DesktopDisk.Style.WHITE));
        assertArrayEquals(initial, Files.readAllBytes(disk.toPath()));
        assertNotNull(store.inspect(disk).originalPat);
        assertFalse(gate.isMaintenanceBusy());
        noStage();
        store.apply(disk, DesktopDisk.Style.MIST);
        store.restore(disk);
        assertArrayEquals(initial, Files.readAllBytes(disk.toPath()));
    }

    @Test public void equalLengthEqualMtimeSourceChangeIsCaughtByFullSha() throws Exception {
        byte[] concurrent = initial.clone();
        concurrent[DesktopDiskTest.GAME_OFFSET] ^= 0x55;
        DesktopAppearanceStore interrupted = new DesktopAppearanceStore(disks, state, gate,
                (source, stage) -> {
                    long oldTime = source.lastModified();
                    Files.write(source.toPath(), concurrent);
                    assertTrue(source.setLastModified(oldTime));
                });
        IOException error = rejects(() -> interrupted.apply(disk, DesktopDisk.Style.WHITE));
        assertTrue(error.getMessage().contains("changed"));
        assertArrayEquals(concurrent, Files.readAllBytes(disk.toPath()));
        noStage();
        assertFalse(gate.isMaintenanceBusy());
    }

    @Test public void corruptOriginalBackupIsNeverReplacedOrIgnored() throws Exception {
        store.apply(disk, DesktopDisk.Style.WHITE);
        File backup = backup();
        byte[] corrupt = Files.readAllBytes(backup.toPath());
        corrupt[corrupt.length - 1] ^= 1;
        Files.write(backup.toPath(), corrupt);
        byte[] current = Files.readAllBytes(disk.toPath());
        rejects(() -> store.inspect(disk)); rejects(() -> store.apply(disk, DesktopDisk.Style.MIST));
        rejects(() -> store.restore(disk));
        assertArrayEquals(current, Files.readAllBytes(disk.toPath()));
        assertArrayEquals(corrupt, Files.readAllBytes(backup.toPath()));
        noStage();
    }

    @Test public void checksummedButMalformedBackupStillRefuses() throws Exception {
        store.apply(disk, DesktopDisk.Style.WHITE);
        File backup = backup();
        byte[] bytes = Files.readAllBytes(backup.toPath());
        bytes[7] = 2; // Unsupported version with a correctly recomputed checksum.
        byte[] sha = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(bytes, bytes.length - 32));
        System.arraycopy(sha, 0, bytes, bytes.length - 32, 32);
        Files.write(backup.toPath(), bytes);
        rejects(() -> store.restore(disk));
        assertArrayEquals(bytes, Files.readAllBytes(backup.toPath()));
        noStage();
    }

    @Test public void identicalVolumesAtDifferentPathsHaveSeparateOriginalRecords() throws Exception {
        File second = new File(disks, "other-campaign.dsk");
        Files.write(second.toPath(), initial);
        store.apply(disk, DesktopDisk.Style.WHITE);
        assertNull(store.inspect(second).originalPat);
        store.apply(second, DesktopDisk.Style.STONE);
        assertEquals(2, state.listFiles().length);
        store.restore(disk); store.restore(second);
        assertArrayEquals(initial, Files.readAllBytes(disk.toPath()));
        assertArrayEquals(initial, Files.readAllBytes(second.toPath()));
    }

    @Test public void backupFromAnotherPathCannotBeRelabeledForThisDisk() throws Exception {
        store.apply(disk, DesktopDisk.Style.WHITE);
        byte[] firstRecord = Files.readAllBytes(backup().toPath());
        File second = new File(disks, "other-campaign.dsk");
        Files.write(second.toPath(), initial);
        File firstBackup = backup();
        store.apply(second, DesktopDisk.Style.STONE);
        File otherBackup = Arrays.stream(state.listFiles()).filter(f -> !f.equals(firstBackup)).findFirst().get();
        Files.write(otherBackup.toPath(), firstRecord);
        rejects(() -> store.restore(second));
        assertArrayEquals(firstRecord, Files.readAllBytes(otherBackup.toPath()));
        noStage();
    }

    @Test public void onlyDirectRegularImportedChildrenAreAllowed() throws Exception {
        File outside = temporary.newFile("outside.dsk");
        Files.write(outside.toPath(), initial);
        File nested = new File(disks, "nested"); assertTrue(nested.mkdir());
        File nestedDisk = new File(nested, "nested.dsk"); Files.write(nestedDisk.toPath(), initial);
        rejects(() -> store.apply(outside, DesktopDisk.Style.WHITE));
        rejects(() -> store.apply(nestedDisk, DesktopDisk.Style.WHITE));
        rejects(() -> store.apply(nested, DesktopDisk.Style.WHITE));
        rejects(() -> store.inspect(new File(disks, "missing.dsk")));
        rejects(() -> store.inspect(new File(disks, "../disks/campaign.dsk")));
        assertArrayEquals(initial, Files.readAllBytes(outside.toPath()));
        assertArrayEquals(initial, Files.readAllBytes(disk.toPath()));
    }

    @Test public void targetBackupAndStorageSymlinksRefuseWithoutFollowing() throws Exception {
        File external = temporary.newFile("external.dsk"); Files.write(external.toPath(), initial);
        File linked = new File(disks, "linked.dsk");
        Files.createSymbolicLink(linked.toPath(), external.toPath());
        rejects(() -> store.apply(linked, DesktopDisk.Style.WHITE));
        File otherState = temporary.newFolder("other-state");
        Files.createSymbolicLink(state.toPath(), otherState.toPath());
        rejects(() -> store.apply(disk, DesktopDisk.Style.WHITE));
        Files.delete(state.toPath());
        store.apply(disk, DesktopDisk.Style.WHITE);
        File record = backup(); byte[] raw = Files.readAllBytes(record.toPath());
        File externalRecord = temporary.newFile("external-record"); Files.write(externalRecord.toPath(), raw);
        Files.delete(record.toPath()); Files.createSymbolicLink(record.toPath(), externalRecord.toPath());
        rejects(() -> store.restore(disk));
        assertArrayEquals(raw, Files.readAllBytes(externalRecord.toPath()));
        assertArrayEquals(initial, Files.readAllBytes(external.toPath()));
    }

    @Test public void backupDirectoryCannotOverlapImportedDisksOrBeAFile() throws Exception {
        DesktopAppearanceStore overlap = new DesktopAppearanceStore(disks, new File(disks, "state"), gate);
        rejects(() -> overlap.apply(disk, DesktopDisk.Style.WHITE));
        Files.write(state.toPath(), new byte[] {1});
        rejects(() -> store.apply(disk, DesktopDisk.Style.WHITE));
        assertArrayEquals(initial, Files.readAllBytes(disk.toPath()));
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(state.toPath()));
    }

    @Test public void atomicReplacementLeavesAnExistingReaderOnItsOldInode() throws Exception {
        try (FileInputStream oldReader = new FileInputStream(disk)) {
            store.apply(disk, DesktopDisk.Style.WHITE);
            byte[] old = new byte[initial.length];
            int at = 0;
            while (at < old.length) {
                int count = oldReader.read(old, at, old.length - at);
                assertTrue(count > 0); at += count;
            }
            assertEquals(-1, oldReader.read());
            assertArrayEquals(initial, old);
            assertFalse(Arrays.equals(initial, Files.readAllBytes(disk.toPath())));
        }
        noStage();
    }

    @Test public void overLimitDiskRefusesBeforeCopyOrBackup() throws Exception {
        File large = new File(disks, "oversized.dsk");
        try (RandomAccessFile out = new RandomAccessFile(large, "rw")) {
            out.setLength(128L * 1024 * 1024 + 512);
        }
        rejects(() -> store.apply(large, DesktopDisk.Style.WHITE));
        assertFalse(state.exists()); noStage();
        assertFalse(gate.isMaintenanceBusy());
    }
}
