package name.osher.gil.minivmac.desktop;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DiskAccessGateTest {
    @Test public void maintenanceExcludesEveryOtherOperationUntilClosed() throws Exception {
        DiskAccessGate gate = new DiskAccessGate();
        try (DiskAccessGate.Lease lease = gate.tryBeginMaintenance()) {
            assertNotNull(lease);
            assertTrue(gate.isMaintenanceBusy());
            assertFalse(gate.isEmulationActive());
            assertNull(gate.tryBeginMaintenance());
            assertNull(gate.tryBeginEmulation());
            assertHostIoRefused(gate);
        }
        assertFalse(gate.isMaintenanceBusy());
        try (DiskAccessGate.Lease lease = gate.tryBeginEmulation()) { assertNotNull(lease); }
    }

    @Test public void emulationExcludesAnotherCoreAndMaintenance() throws Exception {
        DiskAccessGate gate = new DiskAccessGate();
        try (DiskAccessGate.Lease lease = gate.tryBeginEmulation()) {
            assertNotNull(lease);
            assertTrue(gate.isEmulationActive());
            assertNull(gate.tryBeginEmulation());
            assertNull(gate.tryBeginMaintenance());
        }
        assertFalse(gate.isEmulationActive());
        try (DiskAccessGate.Lease lease = gate.tryBeginMaintenance()) { assertNotNull(lease); }
    }

    @Test public void eachHostOperationMustFinishBeforeMaintenanceOrNewBoot() throws Exception {
        DiskAccessGate gate = new DiskAccessGate();
        DiskAccessGate.Lease first = gate.beginHostIo();
        DiskAccessGate.Lease second = gate.beginHostIo();
        assertNull(gate.tryBeginMaintenance());
        assertNull(gate.tryBeginEmulation());
        first.close();
        first.close(); // Must not consume the second operation's reservation.
        assertNull(gate.tryBeginMaintenance());
        second.close();
        try (DiskAccessGate.Lease lease = gate.tryBeginMaintenance()) { assertNotNull(lease); }
    }

    @Test public void normalHostIoMayOverlapLiveEmulationAndOutlastItsLease() throws Exception {
        DiskAccessGate gate = new DiskAccessGate();
        DiskAccessGate.Lease emulation = gate.tryBeginEmulation();
        DiskAccessGate.Lease hostIo = gate.beginHostIo();
        emulation.close();
        assertFalse(gate.isEmulationActive());
        assertNull(gate.tryBeginMaintenance());
        hostIo.close();
        try (DiskAccessGate.Lease lease = gate.tryBeginMaintenance()) { assertNotNull(lease); }
    }

    @Test public void closingOldLeaseCannotReleaseNewLease() {
        DiskAccessGate gate = new DiskAccessGate();
        DiskAccessGate.Lease old = gate.tryBeginMaintenance();
        old.close();
        try (DiskAccessGate.Lease current = gate.tryBeginMaintenance()) {
            assertNotNull(current);
            old.close();
            assertTrue(gate.isMaintenanceBusy());
            assertNull(gate.tryBeginEmulation());
        }
    }

    @Test public void poisonCannotBeClearedByCleanupOrRetry() throws Exception {
        DiskAccessGate gate = new DiskAccessGate();
        DiskAccessGate.Lease emulation = gate.tryBeginEmulation();
        DiskAccessGate.Lease hostIo = gate.beginHostIo();
        gate.poison();
        gate.poison();
        hostIo.close();
        emulation.close();
        assertTrue(gate.isPoisoned());
        assertFalse(gate.isEmulationActive());
        assertNull(gate.tryBeginMaintenance());
        assertNull(gate.tryBeginEmulation());
        assertHostIoRefused(gate);
    }

    @Test public void exceptionalOperationReleasesLeaseWithoutChangingOtherState() throws Exception {
        DiskAccessGate gate = new DiskAccessGate();
        try {
            try (DiskAccessGate.Lease ignored = gate.beginHostIo()) {
                throw new IOException("synthetic write failure");
            }
        } catch (IOException expected) {
            assertEquals("synthetic write failure", expected.getMessage());
        }
        assertFalse(gate.isPoisoned());
        try (DiskAccessGate.Lease lease = gate.tryBeginMaintenance()) { assertNotNull(lease); }
    }

    @Test public void heldLeaseDoesNotBlockAnotherThreadsStateCheckOrRelease() throws Exception {
        DiskAccessGate gate = new DiskAccessGate();
        DiskAccessGate.Lease maintenance = gate.tryBeginMaintenance();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> query = worker.submit(() -> {
                assertTrue(gate.isMaintenanceBusy());
                assertNull(gate.tryBeginEmulation());
                assertHostIoRefused(gate);
                maintenance.close();
                return gate.isMaintenanceBusy();
            });
            assertFalse(query.get(1, TimeUnit.SECONDS));
            try (DiskAccessGate.Lease lease = gate.tryBeginEmulation()) { assertNotNull(lease); }
        } finally {
            maintenance.close();
            worker.shutdownNow();
        }
    }

    private static void assertHostIoRefused(DiskAccessGate gate) throws Exception {
        try (DiskAccessGate.Lease ignored = gate.beginHostIo()) {
            fail("Expected host disk operation to be refused");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
