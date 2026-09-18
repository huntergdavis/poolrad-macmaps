package name.osher.gil.minivmac.mapper;

/**
 * What the guest is doing, as far as the party probe can tell.
 *
 * Automating the game's own menus needs to know the machine's state before
 * sending anything, and the one thing this project has learned expensively is
 * that looking at the screen is not the way. `tools/play.py` once could not tell
 * that Load Saved Game was greyed out, typed a save name into the running game
 * instead, and the party was found altering its marching order.
 *
 * The probe already knows. It reports "game not running" and "no party loaded"
 * as distinct refusals, and a readable party as a party. That is exactly the
 * three-way answer an automated load needs, read from the emulated heap rather
 * than guessed from pixels.
 */
public enum GameSignal {
    /** Nothing readable, or a refusal that says nothing about the machine. */
    UNKNOWN,
    /** The game application is not running: the Finder, or a boot screen. */
    NO_GAME,
    /** The game is running with no party: its title screen. */
    NO_PARTY,
    /** A party is loaded and readable. */
    PARTY;

    private static final int WHY_NO_GAME = 1, WHY_NO_PARTY = 4;

    /** Read the latest party packet for what it says about the machine. */
    public static GameSignal of(byte[] partyPacket) {
        if (partyPacket == null) return UNKNOWN;
        PartyRefusal refusal = PartyRefusal.parse(partyPacket);
        if (refusal != null) {
            if (refusal.code == WHY_NO_GAME) return NO_GAME;
            if (refusal.code == WHY_NO_PARTY) return NO_PARTY;
            // Every other refusal is about the heap, not about whether a game
            // is up. Treating one as "no game" would be how something gets
            // typed into a running game again.
            return UNKNOWN;
        }
        return PartyState.parse(partyPacket) != null ? PARTY : UNKNOWN;
    }

    /** True when the game is up, whether or not anybody is adventuring. */
    public boolean gameRunning() { return this == NO_PARTY || this == PARTY; }
}
