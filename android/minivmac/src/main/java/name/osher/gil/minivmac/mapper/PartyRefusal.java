package name.osher.gil.minivmac.mapper;

/**
 * Why the native probe would not report a party.
 *
 * The probe walks the game's own roster of character records and stops the
 * moment any structure does not check out. From outside, every one of those
 * refusals used to look identical to "no game is running", which is how a
 * tablet could show a live map beside an empty party pane for weeks with
 * nothing to go on. These are facts about the emulated heap, not game content:
 * a refusal code and how many roster links had already been accepted.
 */
public final class PartyRefusal {
    /** Ten bytes: "PRPX", the code, the link count, the rejected value. */
    private static final int SIZE = 10;
    private static final String[] REASONS = {
            "reading",                    // 0: should never be sent
            "game not running",           // 1
            "globals too small",          // 2
            "roster head out of range",   // 3
            "no party loaded",            // 4
            "bad roster handle",          // 5
            "record out of range",        // 6
            "heap block rejected",        // 7
            "roster loops",               // 8
            "slot unassigned",            // 9
            "too many combatants",        // 10
            "two members in one slot",    // 11
            "name rejected",              // 12
            "hit points rejected",        // 13
            "roster has no members",      // 14
    };

    public final int code, links;
    /**
     * The heap address or Memory Manager block header the check rejected, or
     * zero where no single value explains it. Structure, not game content, and
     * the difference between diagnosing this in one round trip and guessing.
     */
    public final long detail;

    private PartyRefusal(int code, int links, long detail) {
        this.code = code; this.links = links; this.detail = detail;
    }

    /** Null for anything that is not a refusal packet, including a real party. */
    public static PartyRefusal parse(byte[] packet) {
        if (packet == null || packet.length != SIZE) return null;
        if (packet[0] != 'P' || packet[1] != 'R' || packet[2] != 'P' || packet[3] != 'X') return null;
        long detail = ((long) (packet[6] & 255) << 24) | ((packet[7] & 255) << 16)
                | ((packet[8] & 255) << 8) | (packet[9] & 255);
        return new PartyRefusal(packet[4] & 255, packet[5] & 255, detail);
    }

    /** Short enough for the map caption, specific enough to act on. */
    public String label() {
        StringBuilder text = new StringBuilder(
                code < REASONS.length ? REASONS[code] : "refused " + code);
        // The link count only means something once the walk has started.
        if (links > 0) text.append(" after ").append(links).append(links == 1 ? " link" : " links");
        if (detail != 0) text.append(" [").append(Long.toHexString(detail)).append(']');
        return text.toString();
    }
}
