package name.osher.gil.minivmac.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the running game has placed each combatant on its own tactical grid.
 *
 * <p>This is an overview of squares the game has already drawn on its Combat
 * View, nothing more. It carries no health, no names, no initiative and no
 * terrain, so it cannot reveal anything the player is not already looking at,
 * and it never suggests a move.
 */
public final class CombatSnapshot {
    /** The roster reader's own ceiling; the packet can hold no more. */
    public static final int MAX_COMBATANTS = 71;
    public static final int ENTRIES_SIZE = 8 + MAX_COMBATANTS * 4;
    /** PRC2 appended the acting character's name, NUL padded. */
    public static final int ACTOR_BYTES = 16;
    /**
     * PRC3 appends who the party is fighting: a count, then that many
     * {16-byte NUL-padded name, one-byte tally} pairs, grouped by name and
     * counting only those still standing.
     */
    public static final int FOES_MAX = 8, FOE_NAME = 16;
    public static final int FOES_OUT = ENTRIES_SIZE + ACTOR_BYTES;
    public static final int PACKET_SIZE = FOES_OUT + 1 + FOES_MAX * (FOE_NAME + 1);
    /**
     * The supported game's fixed arena, decoded from CODE 9 allocation and
     * CODE 10 coordinate validation (docs/COMBAT_MEMORY.md). These are not the
     * occupied bounds or the scrolling Combat View's seven-square viewport.
     */
    public static final int ARENA_WIDTH = 50, ARENA_HEIGHT = 25;

    /** One combatant's square. Party membership comes from the roster order. */
    public static final class Spot {
        public final boolean party;
        public final int x, y;
        /**
         * One of yours, down where they fell: unconscious, dying, dead or
         * petrified. Drawn as a cross, because these are the ones worth walking
         * to. A monster in the same state is not sent at all -- the game's own
         * Combat View stops drawing it, and a marker left where an enemy no
         * longer is was the whole of F16.
         */
        public final boolean fallen;
        /**
         * The game's own condition for this combatant, or -1 if it did not read
         * as one. Carried so the pane can say "Dying" rather than group every
         * way of being down under one word: the difference between someone you
         * can still bandage and someone you cannot is the whole point.
         */
        public final int condition;
        Spot(boolean party, int x, int y) { this(party, false, x, y, -1); }
        Spot(boolean party, boolean fallen, int x, int y, int condition) {
            this.party = party; this.fallen = fallen; this.x = x; this.y = y;
            this.condition = condition;
        }
        /** Down, and still worth reaching: unconscious or dying. */
        public boolean savable() { return condition == 4 || condition == 5; }
        /** A word for this state, or null when there is nothing to say. */
        public String stateLabel() {
            switch (condition) {
                case 4: return "Unconscious";
                case 5: return "Dying";
                case 6: return "Dead";
                case 7: return "Petrified";
                default: return null;
            }
        }
        @Override public String toString() {
            return (fallen ? "fallen " : party ? "party " : "other ") + x + "," + y;
        }
    }

    private final List<Spot> spots;
    private final List<Foe> foes;
    public final int left = 0, top = 0, right = ARENA_WIDTH - 1, bottom = ARENA_HEIGHT - 1;
    /**
     * Whose turn it is, by name, or null when the game is not saying. Read from
     * the game's own Combat Message window rather than worked out, so it is the
     * same name the player is looking at.
     */
    public final String acting;

    private CombatSnapshot(List<Spot> spots, String acting, List<Foe> foes) {
        this.acting = acting;
        this.foes = Collections.unmodifiableList(foes);
        this.spots = Collections.unmodifiableList(spots);

    }

    /** True when this name is the one the game says is acting. */
    public boolean isActing(String name) {
        return acting != null && acting.equals(name);
    }

    /**
     * Everything the overview draws or announces, including roster order
     * (which ties party markers to their rows). Health and condition words
     * arrive separately through PartyState; here conditions only change the
     * fallen marker's shape and the down/lost counts.
     */
    public boolean sameDisplay(CombatSnapshot other) {
        if (other == null || !java.util.Objects.equals(acting, other.acting)
                || spots.size() != other.spots.size() || foes.size() != other.foes.size()) return false;
        for (int i = 0; i < spots.size(); i++) {
            Spot a = spots.get(i), b = other.spots.get(i);
            if (a.party != b.party || a.fallen != b.fallen || a.x != b.x || a.y != b.y
                    || (a.fallen && a.savable() != b.savable())) return false;
        }
        for (int i = 0; i < foes.size(); i++) {
            Foe a = foes.get(i), b = other.foes.get(i);
            if (!a.name.equals(b.name) || a.standing != b.standing) return false;
        }
        return true;
    }

    /**
     * The name field, or null if it is not one.
     *
     * An empty field is nobody acting and reads as "". A field that is not a
     * name at all -- control bytes, or anything written after the terminator --
     * rejects the whole packet, the way every other reader here rejects rather
     * than half-decodes: if that field is wrong, the rest is suspect too.
     */
    private static String readActor(byte[] packet) {
        int length = 0;
        while (length < ACTOR_BYTES && packet[ENTRIES_SIZE + length] != 0) length++;
        for (int at = ENTRIES_SIZE + length; at < FOES_OUT; at++)
            if (packet[at] != 0) return null;
        for (int i = 0; i < length; i++) {
            int c = packet[ENTRIES_SIZE + i] & 255;
            if (c < 0x20 || c > 0x7e) return null;
        }
        return new String(packet, ENTRIES_SIZE, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    /** One kind of monster the party is fighting, and how many are standing. */
    public static final class Foe {
        public final String name;
        public final int standing;
        Foe(String name, int standing) { this.name = name; this.standing = standing; }
        @Override public String toString() { return standing + " " + name; }
    }

    /**
     * Who the party is fighting, grouped by name, or empty when the probe
     * would not vouch for the grouping.
     */
    private static List<Foe> readFoes(byte[] packet) {
        int kinds = packet[FOES_OUT] & 255;
        if (kinds > FOES_MAX) return null;
        List<Foe> foes = new ArrayList<>(kinds);
        for (int i = 0; i < kinds; i++) {
            int at = FOES_OUT + 1 + i * (FOE_NAME + 1);
            int length = 0;
            while (length < FOE_NAME && packet[at + length] != 0) length++;
            if (length == 0 || length == FOE_NAME) return null;
            for (int c = 0; c < length; c++) {
                int letter = packet[at + c] & 255;
                if (letter < 0x20 || letter > 0x7e) return null;
            }
            for (int c = length; c < FOE_NAME; c++) if (packet[at + c] != 0) return null;
            int standing = packet[at + FOE_NAME] & 255;
            if (standing == 0) return null;
            foes.add(new Foe(new String(packet, at, length,
                    java.nio.charset.StandardCharsets.US_ASCII), standing));
        }
        // Anything past the kinds reported belongs to nobody.
        for (int at = FOES_OUT + 1 + kinds * (FOE_NAME + 1); at < packet.length; at++)
            if (packet[at] != 0) return null;
        return foes;
    }

    public List<Foe> foes() { return foes; }

    /**
     * What the party is fighting, in words: "12 GOBLIN", or "8 GOBLIN · 4 ORC"
     * when there is more than one kind, or a plain count when the probe would
     * not vouch for the names.
     */
    public String opposition() {
        int others = size() - partyCount();
        if (foes.isEmpty()) return others + " other" + (others == 1 ? "" : "s");
        StringBuilder text = new StringBuilder();
        for (Foe foe : foes) {
            if (text.length() > 0) text.append(" \u00b7 ");
            text.append(foe.standing).append(' ').append(foe.name);
        }
        return text.toString();
    }

    public List<Spot> spots() { return spots; }
    public int size() { return spots.size(); }
    /** Full arena size, unchanged when combatants move or leave the fight. */
    public int width() { return right - left + 1; }
    public int height() { return bottom - top + 1; }
    /** Your own, down where they fell and worth reaching. */
    public int fallenCount() {
        int count = 0;
        for (Spot spot : spots) if (spot.fallen) count++;
        return count;
    }

    public int partyCount() {
        int total = 0; for (Spot spot : spots) if (spot.party) total++; return total;
    }

    /** Null unless a battle is running and the whole packet validates. */
    public static CombatSnapshot parse(byte[] packet) {
        if (packet == null || packet.length != PACKET_SIZE) return null;
        if (packet[0] != 'P' || packet[1] != 'R' || packet[2] != 'C' || packet[3] != '3') return null;
        int status = packet[4] & 255, count = packet[5] & 255;
        if ((packet[6] | packet[7]) != 0) return null;
        if (status == 255) {
            // Unavailable must not smuggle squares along with it.
            if (count != 0) return null;
            for (int at = 8; at < ENTRIES_SIZE; at++) if (packet[at] != 0) return null;
            return null;
        }
        if (status != 1 || count < 1 || count > MAX_COMBATANTS) return null;
        List<Spot> spots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int at = 8 + i * 4;
            int kind = packet[at] & 255, x = packet[at + 1] & 255, y = packet[at + 2] & 255;
            int condition = packet[at + 3] & 255;
            if (condition > 8 && condition != 255) return null;
            if (condition == 255) condition = -1;
            if (kind != 1 && kind != 2 && kind != 4) return null;
            // The fourth byte used to have to be zero. It now carries the
            // condition, which is checked above instead.
            if (x >= ARENA_WIDTH || y >= ARENA_HEIGHT) return null;
            if (kind == 4 && condition >= 0 && (condition < 4 || condition > 7)) return null;
            spots.add(new Spot(kind == 1 || kind == 4, kind == 4, x, y, condition));
        }
        // Rows past the declared count belong to no one.
        for (int at = 8 + count * 4; at < ENTRIES_SIZE; at++) if (packet[at] != 0) return null;
        String actor = readActor(packet);
        if (actor == null) return null;
        List<Foe> foes = readFoes(packet);
        if (foes == null) return null;
        return new CombatSnapshot(spots, actor.isEmpty() ? null : actor, foes);
    }

    /** Plain wording for the header; never a tactical suggestion. */
    public String summary() {
        int party = partyCount();
        int savable = 0, lost = 0;
        for (Spot spot : spots) if (spot.fallen) { if (spot.savable()) savable++; else lost++; }
        return party + " of yours · " + opposition()
                + (savable == 0 ? "" : " · " + savable + " down")
                + (lost == 0 ? "" : " · " + lost + " lost");
    }
}
