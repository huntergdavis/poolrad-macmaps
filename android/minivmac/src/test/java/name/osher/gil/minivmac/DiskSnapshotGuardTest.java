package name.osher.gil.minivmac;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class DiskSnapshotGuardTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File disk() throws Exception {
        File f = tmp.newFile();
        byte[] bytes = new byte[700000];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte)(i * 13);
        Files.write(f.toPath(), bytes); return f;
    }
    private interface Failure { void run() throws Exception; }
    private static void refused(Failure action) throws Exception {
        try { action.run(); fail("Expected refusal"); } catch (IOException expected) { }
    }
    private static byte[] encode(DiskSnapshotGuard.Fingerprint f) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); f.writeTo(out); return out.toByteArray();
    }

    @Test public void theRevisionCountMovesOnEveryMountOrWrite() throws java.io.IOException {
        DiskSnapshotGuard guard = new DiskSnapshotGuard();
        long start = guard.revisionCount();
        guard.beforeWrite();
        assertEquals(start + 1, guard.revisionCount());
        guard.unmounted(0);
        assertEquals(start + 2, guard.revisionCount());
        guard.stopped();
        assertEquals(start + 3, guard.revisionCount());
    }

    @Test public void fullContentsRoundTripWithoutMovingTheGuestFilePointer() throws Exception {
        File f = disk(); byte[] before = Files.readAllBytes(f.toPath());
        try (RandomAccessFile file = new RandomAccessFile(f, "rw")) {
            DiskSnapshotGuard guard = new DiskSnapshotGuard(); guard.mounted(3, file, true);
            file.seek(123);
            DiskSnapshotGuard.Fingerprint fingerprint = guard.capture().fingerprint();
            assertEquals(123, file.getFilePointer());
            assertArrayEquals(before, Files.readAllBytes(f.toPath()));
            assertEquals(fingerprint, DiskSnapshotGuard.Fingerprint.readFrom(new ByteArrayInputStream(encode(fingerprint))));
            assertTrue(guard.isCurrent(guard.verify(fingerprint)));
        }
    }

    @Test public void sameSizeEditAtEndIsRefusedEvenWithoutAWriteNotification() throws Exception {
        try (RandomAccessFile file = new RandomAccessFile(disk(), "rw")) {
            DiskSnapshotGuard guard = new DiskSnapshotGuard(); guard.mounted(0, file, true);
            DiskSnapshotGuard.Fingerprint before = guard.capture().fingerprint();
            file.seek(file.length() - 1); file.write(99);
            refused(() -> guard.verify(before));
        }
    }

    @Test public void failedWriteAttemptInvalidatesCaptureAndAlreadyVerifiedRestore() throws Exception {
        try (RandomAccessFile file = new RandomAccessFile(disk(), "r")) {
            DiskSnapshotGuard guard = new DiskSnapshotGuard(); guard.mounted(0, file, false);
            DiskSnapshotGuard.Ticket ticket = guard.capture();
            DiskSnapshotGuard.Fingerprint fingerprint = ticket.fingerprint();
            DiskSnapshotGuard.Verified proof = guard.verify(fingerprint);
            guard.beforeWrite();
            refused(() -> file.write(99));
            assertFalse(guard.isCurrent(proof));
            refused(ticket::fingerprint);
            assertTrue(guard.isCurrent(guard.verify(fingerprint)));
        }
    }

    @Test public void driveSlotWriteProtectionAndEjectionArePartOfTheMatch() throws Exception {
        try (RandomAccessFile file = new RandomAccessFile(disk(), "rw")) {
            DiskSnapshotGuard guard = new DiskSnapshotGuard(); guard.mounted(0, file, true);
            DiskSnapshotGuard.Fingerprint fingerprint = guard.capture().fingerprint();
            DiskSnapshotGuard.Verified proof = guard.verify(fingerprint);
            guard.unmounted(0); assertFalse(guard.isCurrent(proof));
            refused(() -> guard.verify(fingerprint));
            guard.mounted(1, file, true); refused(() -> guard.verify(fingerprint));
            guard.unmounted(1); guard.mounted(0, file, false); refused(() -> guard.verify(fingerprint));
            guard.mounted(0, file, true); assertTrue(guard.isCurrent(guard.verify(fingerprint)));
        }
    }

    @Test public void identicalMountedCopiesMatchButProofsCannotCrossCoreInstances() throws Exception {
        File a = disk(), b = disk();
        try (RandomAccessFile first = new RandomAccessFile(a, "rw");
             RandomAccessFile second = new RandomAccessFile(b, "rw")) {
            DiskSnapshotGuard one = new DiskSnapshotGuard(), two = new DiskSnapshotGuard();
            one.mounted(0, first, true); two.mounted(0, second, true);
            DiskSnapshotGuard.Fingerprint fingerprint = one.capture().fingerprint();
            assertTrue(two.isCurrent(two.verify(fingerprint)));
            assertFalse(two.isCurrent(one.verify(fingerprint)));
            DiskSnapshotGuard.Verified proof = one.verify(fingerprint);
            one.stopped(); assertFalse(one.isCurrent(proof));
            refused(() -> one.verify(fingerprint));
        }
    }

    @Test public void closedHandlesAndMissingVerificationCannotProduceASafeSnapshot() throws Exception {
        DiskSnapshotGuard guard = new DiskSnapshotGuard();
        RandomAccessFile file = new RandomAccessFile(disk(), "rw");
        guard.mounted(0, file, true); file.close();
        refused(() -> guard.capture().fingerprint());
        refused(() -> guard.verify(null));
        assertFalse(guard.isCurrent(null));
    }

    @Test public void canonicalMetadataRejectsTruncationBadCountsAndDuplicateSlots() throws Exception {
        byte[] valid;
        try (RandomAccessFile file = new RandomAccessFile(disk(), "r")) {
            DiskSnapshotGuard guard = new DiskSnapshotGuard(); guard.mounted(0, file, false);
            valid = encode(guard.capture().fingerprint());
        }
        for (int n = 0; n < valid.length; n++) {
            byte[] shortBytes = Arrays.copyOf(valid, n);
            refused(() -> DiskSnapshotGuard.Fingerprint.readFrom(new ByteArrayInputStream(shortBytes)));
        }
        for (int field : new int[]{0, 1, 2, 3}) {
            byte[] bad = valid.clone();
            bad[field] = (byte)(field == 0 ? 33 : field == 1 ? 32 : field == 2 ? 2 : 128);
            refused(() -> DiskSnapshotGuard.Fingerprint.readFrom(new ByteArrayInputStream(bad)));
        }
        byte[] duplicate = Arrays.copyOf(valid, valid.length * 2 - 1);
        duplicate[0] = 2;
        System.arraycopy(valid, 1, duplicate, valid.length, valid.length - 1);
        refused(() -> DiskSnapshotGuard.Fingerprint.readFrom(new ByteArrayInputStream(duplicate)));
    }

    @Test public void eachDiffCarriesItsOwnDiskFingerprint() throws Exception {
        try (RandomAccessFile file = new RandomAccessFile(disk(), "rw")) {
            DiskSnapshotGuard guard = new DiskSnapshotGuard(); guard.mounted(0, file, true);
            DiskSnapshotGuard.Fingerprint one = guard.capture().fingerprint();
            SaveStateStore store = new SaveStateStore(tmp.newFolder());
            byte[] state = new byte[10000];
            File first = store.write("first", state, one);
            guard.beforeWrite(); file.seek(17); file.write(73);
            DiskSnapshotGuard.Fingerprint two = guard.capture().fingerprint();
            state[9000] = 37;
            File second = store.write("second", state, two);
            assertEquals(one, store.readSnapshot(first).disks);
            assertEquals(two, store.readSnapshot(second).disks);
            assertNotEquals(one, two);
            assertEquals(0, store.read(first)[9000]);
            assertEquals(37, store.read(second)[9000]);
        }
    }

    @Test public void gzipTrailerIsFinishedAndDescriptorOpenAtBothSyncs() throws Exception {
        int[] syncs = {0};
        File folder = tmp.newFolder();
        SaveStateStore store = new SaveStateStore(folder, out -> {
            assertTrue(out.getFD().valid());
            File[] partials = folder.listFiles((dir, name) -> name.endsWith(".part"));
            assertEquals(1, partials.length);
            byte[] bytes = Files.readAllBytes(partials[0].toPath());
            int n = bytes.length;
            int gzipSize = (bytes[n-4] & 255) | ((bytes[n-3] & 255) << 8)
                    | ((bytes[n-2] & 255) << 16) | ((bytes[n-1] & 255) << 24);
            assertEquals(10000, gzipSize);
            out.getFD().sync(); syncs[0]++;
        });
        File saved = store.write("durable", new byte[10000], DiskSnapshotGuard.Fingerprint.empty());
        assertEquals(2, syncs[0]); assertEquals(10000, store.read(saved).length);
    }

    @Test public void failedSyncCannotPublishOrRotateASnapshot() throws Exception {
        File folder = tmp.newFolder();
        SaveStateStore good = new SaveStateStore(folder);
        File previous = good.writeQuick(new byte[10000], 1, DiskSnapshotGuard.Fingerprint.empty());
        SaveStateStore broken = new SaveStateStore(folder, out -> { throw new IOException("sync failed"); });
        refused(() -> broken.writeQuick(new byte[10000], 2, DiskSnapshotGuard.Fingerprint.empty()));
        assertEquals(Arrays.asList(previous), good.quickSaves());
        assertEquals(10000, good.read(previous).length);
        assertEquals(1, new File(folder, "quick-history").listFiles().length);
    }
}
