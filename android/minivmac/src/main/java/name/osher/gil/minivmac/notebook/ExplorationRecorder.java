package name.osher.gil.minivmac.notebook;

import java.io.IOException;

/** Ordered companion observations only; never reads or writes an emulated save. */
public final class ExplorationRecorder {
    public static final long MAX_GAP_MS = 1250;
    private final NotebookStore store;
    private String book, area;
    private ExplorationTrail trail;
    private int previous = -1;
    private long lastTime, lastContinuity;

    public ExplorationRecorder(NotebookStore store) { this.store = store; }

    /** Called on unavailable samples, paused sampling, or changed campaigns. */
    public void interrupt() { previous = -1; }

    private void select(String run, String key) throws IOException {
        if (run.equals(book) && key.equals(area) && trail != null) return;
        interrupt();
        ExplorationTrail loaded = store.loadExploration(run, key);
        book = run; area = key; trail = loaded;
    }

    /**
     * Only verified local exploration samples belong here. Continuity is an
     * opaque native token, not an elapsed-time estimate or saved previous tile.
     * Missing samples and non-adjacent movement start a new segment.
     */
    public ExplorationTrail observe(String run, String key, int tile,
            long continuity, long elapsedMs) throws IOException {
        select(run, key);
        int from = previous;
        if (elapsedMs <= lastTime || elapsedMs - lastTime > MAX_GAP_MS
                || continuity != lastContinuity) from = -1;
        ExplorationTrail next = trail.record(tile, from);
        try {
            if (next != trail) store.saveExploration(run, key, next);
        } catch (IOException | RuntimeException failure) {
            interrupt(); // Never claim an unsaved step was recorded.
            throw failure;
        }
        trail = next; previous = tile; lastTime = elapsedMs; lastContinuity = continuity;
        return trail;
    }

    public ExplorationTrail read(String run, String key) throws IOException {
        select(run, key); return trail;
    }

    /** Caller must first obtain explicit confirmation for this particular area. */
    public ExplorationTrail clear(String run, String key, boolean coverage) throws IOException {
        select(run, key);
        ExplorationTrail cleared = coverage ? ExplorationTrail.empty() : trail.clearTrail();
        store.saveExploration(run, key, cleared);
        trail = cleared; interrupt(); return trail;
    }

    /** Notebook restore/removal can replace the directory behind a cached UUID. */
    public void forget() { interrupt(); book = area = null; trail = null; }
}
