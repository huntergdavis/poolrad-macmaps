package name.osher.gil.minivmac.mapper;

/** One key per confirmed prompt update. No guessing, bulk paste or blind delayed Return. */
public final class AutomaticWheel {
    private String token, candidate, expected;
    private int sightings;
    private long sentAt, absentSince = -1;
    private boolean blocked;

    /** A held key must not consume an observation that advances the expected prefix. */
    public int observeIfReleased(WheelPrompt prompt, long now, boolean keyHeld) {
        return keyHeld ? 0 : observe(prompt, now);
    }

    /** Returns an ASCII letter, newline for Return, or zero for no input. */
    public int observe(WheelPrompt prompt, long now) {
        if (prompt == null) {
            if (absentSince < 0) absentSince = now;
            candidate = null; sightings = 0;
            // A transient sampling miss must not restart an already-owned prompt.
            if (now - absentSince >= 2000) reset();
            return 0;
        }
        absentSince = -1;
        String next = prompt.token();
        if (!next.equals(token)) {
            if (!prompt.typed.isEmpty()) { token = next; blocked = true; expected = null; return 0; }
            if (!next.equals(candidate)) { candidate = next; sightings = 1; return 0; }
            if (++sightings < 2) return 0;
            token = next; blocked = false; expected = "";
        }
        if (blocked) return 0;
        if (expected != null && !prompt.typed.equalsIgnoreCase(expected)) {
            // The last key may still be travelling through the original Mac event loop.
            String previous = expected.isEmpty() ? "" : expected.substring(0, expected.length() - 1);
            if (prompt.typed.equalsIgnoreCase(previous) && now - sentAt < 2000) return 0;
            blocked = true; return 0;
        }
        if (!prompt.answer.regionMatches(true, 0, prompt.typed, 0, prompt.typed.length())) {
            blocked = true; return 0;
        }
        if (prompt.typed.length() == prompt.answer.length()) {
            blocked = true; return '\n'; // Submit exactly once, only after verified complete readback.
        }
        char key = prompt.answer.charAt(prompt.typed.length());
        expected = prompt.answer.substring(0, prompt.typed.length() + 1); sentAt = now;
        return key;
    }

    /** User input, a dialog or pausing hands the current prompt back to the user. */
    public void suspend() { blocked = true; candidate = null; sightings = 0; }
    public void reset() { token = candidate = expected = null; sightings = 0; blocked = false; absentSince = -1; }
}
