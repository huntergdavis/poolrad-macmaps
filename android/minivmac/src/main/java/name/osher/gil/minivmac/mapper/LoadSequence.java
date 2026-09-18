package name.osher.gil.minivmac.mapper;

/**
 * Quitting the game, starting it again, and loading a chosen save — as a
 * sequence of things to send and things to wait for, with no Android in it.
 *
 * The game greys out Load Saved Game the moment a game is running, and only
 * Quit stays enabled, so loading at any time means quitting first. The owner
 * asked for exactly that, and for it to be invisible: the caller covers the
 * guest while this runs.
 *
 * Every step is sent as a keyboard equivalent out of the game's own MENU
 * resources — `Cmd-Q` to quit, `Cmd-O` for the Finder's Open, `Cmd-L` to load —
 * so nothing here depends on where a menu is drawn or whether an item looks
 * grey. See GUEST_MENU_KEYS.md.
 *
 * What it will not do is send anything while the machine is in a state it did
 * not expect. Each step names the {@link GameSignal} it is waiting for, and a
 * step that does not get there inside its own patience fails the whole sequence
 * rather than pressing on. That is the difference between this and the run that
 * typed a save name into a live game.
 */
public final class LoadSequence {
    /** What the caller should do now. */
    public enum Kind {
        /** Hold Command and press this letter. */
        COMMAND_KEY,
        /** Type this text, then Return. */
        TYPE_LINE,
        /**
         * Type this text and nothing else. The Finder selects by what you
         * type, and Return there means rename, not open -- which is exactly
         * how this once renamed a journal and opened it instead of the game.
         */
        TYPE_ONLY,
        /** Send nothing; come back when something changes or time passes. */
        WAIT,
        /** The party is loaded. */
        FINISHED,
        /** Stop. Nothing further is sent, and the message says why. */
        FAILED
    }

    public static final class Instruction {
        public final Kind kind;
        public final char key;
        public final String text;
        public final String message;
        Instruction(Kind kind, char key, String text, String message) {
            this.kind = kind; this.key = key; this.text = text; this.message = message;
        }
        @Override public String toString() {
            return kind + (kind == Kind.COMMAND_KEY ? " Cmd-" + key
                    : text != null ? " \"" + text + "\"" : "")
                    + (message == null ? "" : ": " + message);
        }
    }

    private enum Step { QUIT, RELAUNCH, OPEN_DIALOG, FOLDER, SAVE, DONE, FAILED }

    /** How long each step may take before the sequence gives up. */
    public static final long QUIT_PATIENCE = 20_000;
    public static final long RELAUNCH_PATIENCE = 60_000;
    public static final long DIALOG_PATIENCE = 8_000;
    public static final long LOAD_PATIENCE = 60_000;
    /** A dialog's appearance cannot be read from the heap, so it is timed. */
    public static final long DIALOG_SETTLE = 2_500;
    /**
     * How long to leave the game alone after it appears.
     *
     * The probe sees the game's globals the moment they exist, which is well
     * before it is drawing menus and ready to be asked for one. Sending Cmd-L
     * into that gap loses it silently, and the sequence then waits out its
     * patience for a dialog that was never opened.
     */
    public static final long READY_SETTLE = 6_000;
    public static final long TYPING_SETTLE = 2_000;

    private final String application, folder, save;
    private Step step = Step.QUIT;
    private long stepBegan = Long.MIN_VALUE;
    private boolean sent;
    private String failure;

    public LoadSequence(String application, String folder, String save) {
        if (application == null || application.isEmpty()) throw new IllegalArgumentException("No application name");
        if (folder == null || folder.isEmpty()) throw new IllegalArgumentException("No folder name");
        if (save == null || save.isEmpty()) throw new IllegalArgumentException("No save name");
        this.application = application; this.folder = folder; this.save = save;
    }

    public boolean finished() { return step == Step.DONE || step == Step.FAILED; }
    public boolean failed() { return step == Step.FAILED; }
    public String failure() { return failure; }
    /** For a progress line: what is happening now, in plain words. */
    public String describe() {
        switch (step) {
            case QUIT: return "Closing the game";
            case RELAUNCH: return "Starting the game again";
            case OPEN_DIALOG: case FOLDER: case SAVE: return "Loading " + save;
            case DONE: return "Loaded " + save;
            default: return failure == null ? "Stopped" : failure;
        }
    }

    /**
     * @param signal what the probe says the machine is doing
     * @param now a monotonic clock in milliseconds
     */
    public Instruction next(GameSignal signal, long now) {
        if (step == Step.DONE) return new Instruction(Kind.FINISHED, ' ', null, "Loaded " + save);
        if (step == Step.FAILED) return new Instruction(Kind.FAILED, ' ', null, failure);
        if (stepBegan == Long.MIN_VALUE) stepBegan = now;
        long waited = Math.max(0, now - stepBegan);

        switch (step) {
            case QUIT:
                // Already at the Finder: nothing to quit.
                if (signal == GameSignal.NO_GAME) return advance(Step.RELAUNCH, now);
                if (!sent) { sent = true; return command('Q'); }
                if (waited > QUIT_PATIENCE)
                    return fail("The game did not close. It may be asking something on screen; "
                            + "nothing else was sent.");
                return holdOn();

            case RELAUNCH:
                if (signal.gameRunning()) return advance(Step.OPEN_DIALOG, now);
                // Type-select only: no Return, which the Finder reads as rename.
                if (!sent) { sent = true; return typeOnly(application); }
                if (waited > RELAUNCH_PATIENCE)
                    return fail("The game did not start again. Nothing was loaded.");
                // The Finder opens what type-select highlighted.
                if (waited > TYPING_SETTLE) return command('O');
                return holdOn();

            case OPEN_DIALOG:
                /*
                 * Loading is only offered with no party in play, which is what
                 * the game itself enforces. If a party is somehow already
                 * loaded, quitting did not take, and pressing on would type a
                 * save name into a running game.
                 */
                if (signal == GameSignal.PARTY)
                    return fail("A game is still running, so nothing was typed.");
                // Let it finish coming up before asking it for a menu.
                if (waited < READY_SETTLE) return holdOn();
                if (!sent) { sent = true; return command('L'); }
                if (waited > READY_SETTLE + DIALOG_SETTLE) return advance(Step.FOLDER, now);
                if (waited > READY_SETTLE + DIALOG_PATIENCE) return fail("The load dialog never appeared.");
                return holdOn();

            case FOLDER:
                if (!sent) { sent = true; return type(folder); }
                if (waited > DIALOG_SETTLE) return advance(Step.SAVE, now);
                return holdOn();

            case SAVE:
                if (signal == GameSignal.PARTY) { step = Step.DONE; return
                        new Instruction(Kind.FINISHED, ' ', null, "Loaded " + save); }
                if (!sent) { sent = true; return type(save); }
                if (waited > LOAD_PATIENCE)
                    return fail("The save did not open. The game may have refused the name.");
                return holdOn();

            default:
                return fail("Lost track of what was happening.");
        }
    }

    private Instruction advance(Step to, long now) {
        step = to; stepBegan = now; sent = false;
        return holdOn();
    }
    private Instruction holdOn() { return new Instruction(Kind.WAIT, ' ', null, null); }
    private Instruction command(char key) { return new Instruction(Kind.COMMAND_KEY, key, null, null); }
    private Instruction type(String text) { return new Instruction(Kind.TYPE_LINE, ' ', text, null); }
    private Instruction typeOnly(String text) { return new Instruction(Kind.TYPE_ONLY, ' ', text, null); }
    private Instruction fail(String why) {
        step = Step.FAILED; failure = why;
        return new Instruction(Kind.FAILED, ' ', null, why);
    }
}
