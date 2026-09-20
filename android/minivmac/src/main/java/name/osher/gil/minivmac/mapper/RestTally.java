package name.osher.gil.minivmac.mapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fights fought and spells cast since the party last rested, read from what
 * the game already publishes: the mode byte (a fight begins when it turns to
 * combat), the memorized-spell slots (a cast empties one; a rest converts
 * chosen slots to ready) and the game's own clock (a rest in camp advances
 * it). Nothing here writes to the guest. The tally travels with emulator
 * snapshots as a sidecar so a restored machine keeps its own count.
 */
public final class RestTally {
    public enum Anchor { LOADED, RESTED }
    /** Camp time this long counts as a rest even when nothing was memorized. */
    public static final int REST_MINUTES = 60;
    private static final String MAGIC = "PRRT1";

    /** One character's spell slots, as the party packet reports them. */
    public static final class SpellReading {
        public final String name; public final int ready, awaiting;
        public SpellReading(String name, int ready, int awaiting) {
            this.name = name; this.ready = ready; this.awaiting = awaiting;
        }
    }

    private int fights, spells;
    private Anchor anchor = Anchor.LOADED;
    private GameClock anchorClock;
    private int restedMinutes;
    private MapMode lastNamed;
    private GameClock lastClock, campEntry;
    private boolean campRested, loading;
    private GameSignal lastSignal;
    private Map<String, int[]> lastSpells = new HashMap<>();

    public synchronized int fights() { return fights; }
    public synchronized int spells() { return spells; }
    public synchronized Anchor anchor() { return anchor; }
    public synchronized GameClock anchorClock() { return anchorClock; }

    /**
     * A fight begins when the named mode turns to combat; unreadable frames
     * never count. A rest is the game clock jumping an hour or more around
     * camp, or camp time accumulating to an hour. The rest animation can show
     * unreadable frames, which are ignored; wilderness travel moves the clock
     * in big steps too and is excluded. The game's own load screen restarts
     * the count once the next named mode arrives.
     */
    public synchronized void observeMap(MapMode mode, GameClock clock) {
        if (mode == null || mode == MapMode.UNAVAILABLE || mode == MapMode.UPDATING) return;
        if (clock != null && lastClock != null) {
            int jump = clock.minutesSince(lastClock);
            if (jump < 0) reset(Anchor.LOADED, clock);   // the game's clock only runs forward; back means a load
            else if (jump >= REST_MINUTES && mode != MapMode.WILDERNESS && lastNamed != MapMode.WILDERNESS
                    && (mode == MapMode.CAMP || lastNamed == MapMode.CAMP || campEntry != null))
                recordRest(clock, jump);
        }
        if (mode == MapMode.LOADING) loading = true;
        else if (loading) { loading = false; reset(Anchor.LOADED, clock); }   // the game's own load or party setup
        if (mode == MapMode.COMBAT && lastNamed != MapMode.COMBAT) fights++;
        if (mode == MapMode.CAMP) {
            if (campEntry == null) { campEntry = clock; if (lastNamed != MapMode.CAMP) campRested = false; }
            else if (clock != null) {
                int minutes = clock.minutesSince(campEntry);
                if (minutes >= REST_MINUTES) recordRest(clock, minutes);
            }
        } else campEntry = null;
        lastNamed = mode;
        if (clock != null) lastClock = clock;
    }

    /**
     * Party-signal changes never restart the count. The party probe refuses
     * transiently during fights, rests and encounters (the game rewrites its
     * roster), so a "no game" or "no party" reading followed by a party is
     * not a load. Loads are read from the game's own load screen in
     * {@link #observeMap}, from the clock running backwards, or from a
     * snapshot restore; this only remembers the latest signal.
     */
    public synchronized void observeSignal(GameSignal signal) {
        if (signal == null || signal == GameSignal.UNKNOWN) return;
        lastSignal = signal;
    }

    public synchronized void observeParty(PartyState party) {
        if (party == null) return;
        List<SpellReading> readings = new ArrayList<>();
        for (PartyState.Member member : party.members())
            if (member.spellsAvailable())
                readings.add(new SpellReading(member.name, member.spellsReadyTotal(), member.spellsAwaitingRestTotal()));
        observeSpells(readings);
    }

    /**
     * Casting empties one ready slot; resting turns chosen slots ready. Any
     * other movement (a new character, a reset record, a load) is not counted.
     */
    public synchronized void observeSpells(List<SpellReading> readings) {
        Map<String, int[]> next = new HashMap<>();
        boolean memorized = false; int cast = 0;
        for (SpellReading reading : readings) {
            if (reading == null || reading.name == null) continue;
            next.put(reading.name, new int[]{reading.ready, reading.awaiting});
            int[] before = lastSpells.get(reading.name);
            if (before == null) continue;
            int readyDrop = before[0] - reading.ready, awaitingDrop = before[1] - reading.awaiting;
            if (readyDrop > 0 && awaitingDrop == 0) cast += readyDrop;
            else if (readyDrop < 0 && awaitingDrop == -readyDrop) memorized = true;
        }
        lastSpells = next;
        if (memorized) recordRest(lastClock, campEntry != null && lastClock != null
                ? Math.max(0, lastClock.minutesSince(campEntry)) : restedMinutes);
        else spells += cast;
    }

    private void recordRest(GameClock at, int minutes) {
        boolean fresh = !(campRested && anchor == Anchor.RESTED);
        if (fresh) { fights = 0; spells = 0; restedMinutes = 0; }
        campRested = true;
        anchor = Anchor.RESTED;
        if (at != null) anchorClock = at;
        restedMinutes = Math.max(restedMinutes, minutes);
    }

    /** Start over: after a load, or when a restored snapshot carried no tally. */
    public synchronized void reset(Anchor why, GameClock at) {
        fights = 0; spells = 0; anchor = why == null ? Anchor.LOADED : why; anchorClock = at;
        restedMinutes = 0; campRested = false; campEntry = null;
        lastSpells = new HashMap<>();
        lastClock = at;
    }

    public synchronized String describe() {
        StringBuilder out = new StringBuilder();
        if (anchor == Anchor.RESTED) {
            out.append("Last rest\n");
            out.append(anchorClock == null ? "Rest completed" : anchorClock.label());
            if (restedMinutes > 0) out.append(" · ").append(duration(restedMinutes)).append(" rested");
        } else {
            out.append("Last rest\nNo completed rest seen yet. Counting since the game was loaded");
            if (anchorClock != null) out.append(" at ").append(anchorClock.label());
            out.append('.');
        }
        if (anchorClock != null && lastClock != null) {
            int ago = lastClock.minutesSince(anchorClock);
            if (ago > 0) out.append('\n').append(duration(ago)).append(" of game time ago");
        }
        out.append("\n\nSince then\n");
        out.append(fights).append(fights == 1 ? " fight" : " fights").append('\n');
        out.append(spells).append(spells == 1 ? " spell cast" : " spells cast");
        return out.toString();
    }

    static String duration(int minutes) {
        if (minutes < 60) return minutes + (minutes == 1 ? " minute" : " minutes");
        int hours = minutes / 60, rest = minutes % 60;
        if (hours >= 48) return String.format(Locale.US, "%d days %d hours", hours / 24, hours % 24);
        return rest == 0 ? hours + (hours == 1 ? " hour" : " hours")
                : String.format(Locale.US, "%d h %02d min", hours, rest);
    }

    /** Small text sidecar for a snapshot; the machine state itself is untouched. */
    public synchronized byte[] encode() {
        StringBuilder out = new StringBuilder(MAGIC).append('\n');
        out.append("fights=").append(fights).append('\n');
        out.append("spells=").append(spells).append('\n');
        out.append("anchor=").append(anchor.name()).append('\n');
        out.append("clock=").append(anchorClock == null ? "none"
                : anchorClock.day + "," + anchorClock.hour + "," + anchorClock.minute).append('\n');
        out.append("rested=").append(restedMinutes).append('\n');
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Adopt a snapshot's tally after its machine state was restored. Garbage restarts the count. */
    public synchronized boolean restore(byte[] encoded) {
        Parsed parsed = parse(encoded);
        if (parsed == null) { reset(Anchor.LOADED, null); return false; }
        reset(parsed.anchor, parsed.clock);
        fights = parsed.fights; spells = parsed.spells; restedMinutes = parsed.rested;
        lastClock = null;   // the first live clock must not read as a rewind
        return true;
    }

    private static final class Parsed { int fights, spells, rested; Anchor anchor; GameClock clock; }

    private static Parsed parse(byte[] encoded) {
        if (encoded == null || encoded.length < MAGIC.length() + 1 || encoded.length > 4096) return null;
        String[] lines = new String(encoded, StandardCharsets.UTF_8).split("\n");
        if (!lines[0].equals(MAGIC)) return null;
        Parsed parsed = new Parsed(); parsed.anchor = null; int seen = 0;
        try {
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].isEmpty()) continue;
                int eq = lines[i].indexOf('=');
                if (eq <= 0) return null;
                String key = lines[i].substring(0, eq), value = lines[i].substring(eq + 1);
                switch (key) {
                    case "fights": parsed.fights = Integer.parseInt(value); break;
                    case "spells": parsed.spells = Integer.parseInt(value); break;
                    case "rested": parsed.rested = Integer.parseInt(value); break;
                    case "anchor": parsed.anchor = Anchor.valueOf(value); break;
                    case "clock":
                        if (!value.equals("none")) {
                            String[] parts = value.split(",");
                            if (parts.length != 3) return null;
                            parsed.clock = GameClock.of(Integer.parseInt(parts[0]),
                                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                            if (parsed.clock == null) return null;
                        }
                        break;
                    default: return null;
                }
                seen++;
            }
        } catch (IllegalArgumentException malformed) { return null; }
        if (seen != 5 || parsed.anchor == null || parsed.fights < 0 || parsed.spells < 0
                || parsed.rested < 0) return null;
        return parsed;
    }
}
