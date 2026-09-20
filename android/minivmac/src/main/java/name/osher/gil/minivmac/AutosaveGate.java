package name.osher.gil.minivmac;

/**
 * Decides whether a timer autosave is worth taking. Pure Java. Anything that
 * could change the machine since the last protected point counts as activity:
 * a guest key or mouse press, a party, map or message sample that differs
 * from the last one, a disk write, or a mount change. A successful snapshot
 * of any kind (automatic, quick, named) or a completed restore is a protected
 * point; an autosave is skipped only when nothing has moved since it.
 *
 * Failed or unknown outcomes never skip: a save is requested until one lands.
 */
public final class AutosaveGate {
    private long activity;          // grows on every observed change
    private long requested = -1;    // activity counter when the pending save was requested
    private long protectedAt = -1;  // activity counter covered by the last successful snapshot
    private long lastInput = Long.MIN_VALUE, lastDisk = Long.MIN_VALUE;
    private int skipped, saved;
    private String lastReason = "";

    public synchronized void noteActivity(String why) { activity++; lastReason = why; }

    /** Monotonic counters from the core: any movement is activity. */
    public synchronized void observeInput(long inputEvents) {
        if (lastInput != Long.MIN_VALUE && inputEvents != lastInput) noteActivity("guest input");
        lastInput = inputEvents;
    }
    public synchronized void observeDisks(long diskRevision) {
        if (lastDisk != Long.MIN_VALUE && diskRevision != lastDisk) noteActivity("disk change");
        lastDisk = diskRevision;
    }

    /** True when a timer autosave should be taken now. */
    public synchronized boolean shouldSave() {
        return protectedAt < 0 || activity != protectedAt;
    }

    /** The caller decided to request a save at this activity level. */
    public synchronized void requested() { requested = activity; }
    /** A snapshot of any kind was published; it covers everything up to its request. */
    public synchronized void saved(boolean automatic) {
        protectedAt = requested >= 0 ? requested : activity;
        requested = -1;
        if (automatic) saved++;
    }
    /** A restore just landed: the loaded snapshot already holds this exact state. */
    public synchronized void restored() { protectedAt = activity; requested = -1; }
    public synchronized void failed() { requested = -1; }
    public synchronized void skipped() { skipped++; }

    public synchronized int skippedCount() { return skipped; }
    public synchronized int savedCount() { return saved; }
    public synchronized String lastReason() { return lastReason; }
}
