package name.osher.gil.minivmac;

/** One launch attempt. Cancellation wins until native application is committed. */
final class StartupRestoreGate {
    private enum Phase { NEW, WAITING, APPLYING, RELEASED, CANCELLED }
    private Phase phase = Phase.NEW;
    synchronized boolean start() {
        if (phase != Phase.NEW) return false;
        phase = Phase.WAITING; return true;
    }
    synchronized boolean waiting() { return phase == Phase.WAITING; }
    synchronized boolean held() {
        return phase == Phase.NEW || phase == Phase.WAITING || phase == Phase.APPLYING;
    }
    synchronized boolean cancel() {
        if (phase != Phase.NEW && phase != Phase.WAITING) return false;
        phase = Phase.CANCELLED; return true;
    }
    synchronized boolean beginApply() {
        if (phase != Phase.WAITING) return false;
        phase = Phase.APPLYING; return true;
    }
    synchronized void release() {
        if (phase != Phase.CANCELLED) phase = Phase.RELEASED;
    }
}
