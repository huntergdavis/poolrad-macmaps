package name.osher.gil.minivmac.mapper;

/**
 * Keeps the last good reading on screen through a brief unreadable patch.
 *
 * The probes take a snapshot of the emulated machine every 250ms, and the game
 * is not obliged to be in a readable state at that instant: it moves heap
 * blocks, rewrites records and redraws. Each of those frames used to blank the
 * party, the position or the battle for one poll, which reads as a flicker
 * several times a minute rather than as the momentary truth it is. In a battle
 * it is worse: the game rewrites those same records continuously, so the party
 * and the battlefield can blink many times a second.
 *
 * So an unreadable frame changes nothing on screen until the reading has been
 * unreadable for {@link #HOLD_MS}. After that it is not a blink, it is a fact,
 * and the pane says so.
 *
 * This holds the <em>display</em> only. Exploration recording still hears every
 * interruption as it happens, because a held position must never become a
 * footprint the party did not walk.
 */
public final class ReadingHold {
    /** How long a reading survives being unreadable. */
    public static final long HOLD_MS = 5000;

    private final long hold;
    private boolean everRead;
    private long lastRead;

    public ReadingHold() { this(HOLD_MS); }

    public ReadingHold(long holdMillis) {
        if (holdMillis < 0) throw new IllegalArgumentException("Negative hold");
        hold = holdMillis;
    }

    /**
     * @param readable whether this frame produced a usable reading
     * @param now a monotonic clock in milliseconds
     * @return true when the caller should apply this frame; false to keep what
     *         is already on screen
     */
    public boolean accept(boolean readable, long now) {
        if (readable) { everRead = true; lastRead = now; return true; }
        // Nothing has ever been read, so there is nothing to hold on to.
        if (!everRead) return true;
        // A clock that has gone backwards cannot extend a hold.
        if (now >= lastRead && now - lastRead < hold) return false;
        everRead = false;
        return true;
    }

    /** Drop the reading at once: the pane is going away, not blinking. */
    public void reset() { everRead = false; lastRead = 0; }

    /** True while a previous reading is standing in for an unreadable one. */
    public boolean holding() { return everRead; }
}
