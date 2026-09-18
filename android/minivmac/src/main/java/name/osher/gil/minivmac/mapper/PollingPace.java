package name.osher.gil.minivmac.mapper;

/**
 * How often to read the emulated machine, given how long since anything on its
 * screen moved.
 *
 * The companion sampled four regions of guest RAM four times a second for as
 * long as the map was open, whether or not the game was doing anything. Most of
 * a session is not doing anything: the game sits on a message, or a menu, or a
 * prompt, and the party has not moved for a minute. Reading the same bytes
 * sixteen times a second to learn that costs battery on a tablet whose whole
 * appeal is that it lasts for days.
 *
 * So the rate follows the guest. While the screen is changing it stays at the
 * full rate, because that is when the map is worth updating. When the screen
 * has been still for a couple of seconds it drops, and after a long silence it
 * drops again. Any movement at all puts it straight back.
 *
 * Backing off rather than stopping is deliberate. A stopped poll needs
 * something to start it, and being wrong about what that is means a companion
 * that has quietly stopped telling the truth. A slow poll that is wrong about
 * being idle is merely late.
 */
public final class PollingPace {
    /** While the guest's screen is moving. */
    public static final long ACTIVE_MS = 250;
    /** After it has been still long enough to be waiting for the player. */
    public static final long IDLE_MS = 1000;
    /** After it has been still long enough that nobody is at the tablet. */
    public static final long RESTING_MS = 3000;

    public static final long IDLE_AFTER_MS = 2000;
    public static final long RESTING_AFTER_MS = 15000;

    private final long active, idle, resting, idleAfter, restingAfter;
    private boolean seen;
    private long last;

    public PollingPace() {
        this(ACTIVE_MS, IDLE_MS, RESTING_MS, IDLE_AFTER_MS, RESTING_AFTER_MS);
    }

    public PollingPace(long active, long idle, long resting, long idleAfter, long restingAfter) {
        if (active <= 0 || idle < active || resting < idle)
            throw new IllegalArgumentException("Intervals must rise from the active one");
        if (idleAfter <= 0 || restingAfter < idleAfter)
            throw new IllegalArgumentException("Thresholds must rise");
        this.active = active; this.idle = idle; this.resting = resting;
        this.idleAfter = idleAfter; this.restingAfter = restingAfter;
    }

    /** The guest's screen moved. */
    public void sawActivity(long now) { seen = true; last = now; }

    /**
     * How long to wait before the next read.
     *
     * Until something has been seen at all this stays at the full rate: a
     * companion that has just opened knows nothing, and guessing that a machine
     * it has never watched is idle would start it off slow for no reason.
     */
    public long interval(long now) {
        if (!seen) return active;
        // A clock that has gone backwards cannot make the guest look busier.
        long quiet = Math.max(0, now - last);
        return quiet < idleAfter ? active : quiet < restingAfter ? idle : resting;
    }

    /** True when the next read is being held back, so activity should hurry it. */
    public boolean slowed(long now) { return interval(now) > active; }

    /** Start again knowing nothing, as if the pane had just opened. */
    public void reset() { seen = false; last = 0; }
}
