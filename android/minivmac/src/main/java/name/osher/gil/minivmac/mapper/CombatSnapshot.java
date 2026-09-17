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
    public static final int PACKET_SIZE = 8 + MAX_COMBATANTS * 4;
    /** Coordinates the native reader will accept at all. */
    public static final int MAX_COORDINATE = 63;

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
        Spot(boolean party, int x, int y) { this(party, false, x, y); }
        Spot(boolean party, boolean fallen, int x, int y) {
            this.party = party; this.fallen = fallen; this.x = x; this.y = y;
        }
        @Override public String toString() {
            return (fallen ? "fallen " : party ? "party " : "other ") + x + "," + y;
        }
    }

    private final List<Spot> spots;
    public final int left, top, right, bottom;

    private CombatSnapshot(List<Spot> spots) {
        this.spots = Collections.unmodifiableList(spots);
        int l = MAX_COORDINATE, t = MAX_COORDINATE, r = 0, b = 0;
        for (Spot spot : spots) {
            l = Math.min(l, spot.x); r = Math.max(r, spot.x);
            t = Math.min(t, spot.y); b = Math.max(b, spot.y);
        }
        left = l; top = t; right = r; bottom = b;
    }

    public List<Spot> spots() { return spots; }
    public int size() { return spots.size(); }
    /** Inclusive width and height of the squares actually occupied. */
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
        if (packet[0] != 'P' || packet[1] != 'R' || packet[2] != 'C' || packet[3] != '1') return null;
        int status = packet[4] & 255, count = packet[5] & 255;
        if ((packet[6] | packet[7]) != 0) return null;
        if (status == 255) {
            // Unavailable must not smuggle squares along with it.
            if (count != 0) return null;
            for (int at = 8; at < packet.length; at++) if (packet[at] != 0) return null;
            return null;
        }
        if (status != 1 || count < 1 || count > MAX_COMBATANTS) return null;
        List<Spot> spots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int at = 8 + i * 4;
            int kind = packet[at] & 255, x = packet[at + 1] & 255, y = packet[at + 2] & 255;
            if (kind != 1 && kind != 2 && kind != 4) return null;
            if (x > MAX_COORDINATE || y > MAX_COORDINATE || packet[at + 3] != 0) return null;
            spots.add(new Spot(kind == 1 || kind == 4, kind == 4, x, y));
        }
        // Rows past the declared count belong to no one.
        for (int at = 8 + count * 4; at < packet.length; at++) if (packet[at] != 0) return null;
        return new CombatSnapshot(spots);
    }

    /** Plain wording for the header; never a tactical suggestion. */
    public String summary() {
        int party = partyCount(), fallen = fallenCount();
        return party + " of yours · " + (size() - party) + " other"
                + (size() - party == 1 ? "" : "s")
                + (fallen == 0 ? "" : " · " + fallen + " down");
    }
}
