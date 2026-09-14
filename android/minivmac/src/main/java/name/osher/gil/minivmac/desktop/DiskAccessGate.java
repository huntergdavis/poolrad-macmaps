package name.osher.gil.minivmac.desktop;

import java.io.IOException;

/** Short state transitions only: no monitor is held while emulating or copying. */
public final class DiskAccessGate {
    public static final DiskAccessGate GLOBAL = new DiskAccessGate();

    private boolean emulation, maintenance, poisoned;
    private int hostIo;

    public synchronized Lease tryBeginEmulation() {
        if (poisoned || emulation || maintenance || hostIo != 0) return null;
        emulation = true;
        return new Lease(this, Kind.EMULATION);
    }

    public synchronized Lease tryBeginMaintenance() {
        if (poisoned || emulation || maintenance || hostIo != 0) return null;
        maintenance = true;
        return new Lease(this, Kind.MAINTENANCE);
    }

    /** Existing normal host operations may run alongside emulation, never maintenance. */
    public synchronized Lease beginHostIo() throws IOException {
        if (poisoned) throw new IOException("Disk access could not be closed safely; restart the app before changing disks");
        if (maintenance) throw new IOException("Desktop appearance is updating the disk; try again when it finishes");
        hostIo++;
        return new Lease(this, Kind.HOST_IO);
    }

    public synchronized boolean isMaintenanceBusy() { return maintenance; }
    public synchronized boolean isEmulationActive() { return emulation; }
    public synchronized boolean isPoisoned() { return poisoned; }

    /** Unknown handle-close state is not repairable by releasing a lease. */
    public synchronized void poison() { poisoned = true; }

    private enum Kind { EMULATION, MAINTENANCE, HOST_IO }

    public static final class Lease implements AutoCloseable {
        private final DiskAccessGate owner;
        private final Kind kind;
        private boolean closed;

        private Lease(DiskAccessGate owner, Kind kind) {
            this.owner = owner;
            this.kind = kind;
        }

        @Override public void close() {
            synchronized (owner) {
                if (closed) return;
                closed = true;
                switch (kind) {
                    case EMULATION: owner.emulation = false; break;
                    case MAINTENANCE: owner.maintenance = false; break;
                    case HOST_IO: owner.hostIo--; break;
                    default: throw new AssertionError(kind);
                }
            }
        }
    }
}
