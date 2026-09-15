package name.osher.gil.minivmac.journal;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Manual lookups, player-chosen bookmarks, player-checked tasks and
 * player-created flag links, plus the references the running game has actually
 * cited in front of the player. A bookmark is still never a claim that the
 * party encountered that reference, and a checked task is never a claim that
 * the original quest is complete; the encountered list is the one part read
 * from the game, and it records only what the game printed.
 * Belongs to one notebook and travels with that notebook's backup.
 */
public final class JournalHistory {
    public static final int MAX_RECENT = 20, MAX_BOOKMARKS = 99;
    /** The supported journal defines 99 references in total; none can repeat. */
    public static final int MAX_ENCOUNTERED = 99;
    public static final int MAX_LINKS = 256, MAX_LINKS_PER_ENTRY = 8;
    /** Envelope payload ceiling; see the field bounds enforced below. */
    public static final int MAX_BYTES = 12288;

    /** One flag the player placed on a verified area map. Never a guessed location. */
    public static final class Flag {
        public final String areaId;
        public final int x, y;
        public Flag(String areaId, int x, int y) {
            if (areaId == null || !areaId.matches("por-mac-v11-geo-(0|[1-9]|[12][0-9]|3[0-2])")
                    || x < 0 || x > 15 || y < 0 || y > 15) {
                throw new IllegalArgumentException("Invalid journal flag link");
            }
            this.areaId = areaId; this.x = x; this.y = y;
        }
        /** Matches NotebookController's flag map key. */
        public int tile() { return y * 16 + x; }
        @Override public String toString() { return areaId + "@" + x + "," + y; }
        @Override public int hashCode() { return toString().hashCode(); }
        @Override public boolean equals(Object other) {
            return other instanceof Flag && toString().equals(other.toString());
        }
    }

    private final List<JournalBook.Key> recent = new ArrayList<>(), bookmarks = new ArrayList<>();
    private final Set<JournalBook.Key> done = new HashSet<>();
    /** Insertion-ordered: the player sees them in the order the game cited them. */
    private final Set<JournalBook.Key> encountered = new LinkedHashSet<>();
    private final Map<JournalBook.Key, List<Flag>> links = new LinkedHashMap<>();

    public JournalHistory() { }

    /** Reads the pre-0.16.0 preference strings so an existing notebook keeps its history. */
    public JournalHistory(String recent, String bookmarks) {
        parse(recent, this.recent, MAX_RECENT); parse(bookmarks, this.bookmarks, MAX_BOOKMARKS);
    }
    private static void parse(String value, List<JournalBook.Key> result, int max) {
        if (value.length() > 1024) throw new IllegalArgumentException("Oversized journal history");
        if (!value.isEmpty()) for (String part : value.split(",", -1)) {
            JournalBook.Key key = JournalBook.Key.parse(part);
            if (result.contains(key) || result.size() >= max) throw new IllegalArgumentException("Invalid journal history");
            result.add(key);
        }
    }

    public void opened(JournalBook.Key key) {
        recent.remove(key); recent.add(0, key); if (recent.size() > MAX_RECENT) recent.remove(MAX_RECENT);
    }
    /** Removing a bookmark also drops its checked state; the links stay with the entry. */
    public void toggle(JournalBook.Key key) {
        if (bookmarks.remove(key)) { done.remove(key); return; }
        if (bookmarks.size() >= MAX_BOOKMARKS) throw new IllegalStateException("Bookmark limit reached");
        bookmarks.add(key);
    }
    public boolean bookmarked(JournalBook.Key key) { return bookmarks.contains(key); }

    /** A checked task is a bookmark the player ticked off, not a game-verified quest state. */
    public void toggleDone(JournalBook.Key key) {
        if (!bookmarks.contains(key)) throw new IllegalStateException("Only a bookmark can be checked off");
        if (!done.remove(key)) done.add(key);
    }
    public boolean done(JournalBook.Key key) { return done.contains(key); }

    public boolean linked(JournalBook.Key key, Flag flag) {
        List<Flag> existing = links.get(key);
        return existing != null && existing.contains(flag);
    }
    /** Links are player-created cross-references between a reference and one of their own flags. */
    public void toggleLink(JournalBook.Key key, Flag flag) {
        if (flag == null) throw new IllegalArgumentException("Missing flag link");
        List<Flag> existing = links.get(key);
        if (existing != null && existing.remove(flag)) { if (existing.isEmpty()) links.remove(key); return; }
        if (existing == null) existing = new ArrayList<>();
        if (existing.size() >= MAX_LINKS_PER_ENTRY) throw new IllegalStateException("This reference already has eight linked flags");
        if (linkCount() >= MAX_LINKS) throw new IllegalStateException("Flag link limit reached for this notebook");
        existing.add(flag); links.put(key, existing);
    }
    public List<Flag> links(JournalBook.Key key) {
        List<Flag> existing = links.get(key);
        return existing == null ? Collections.<Flag>emptyList() : Collections.unmodifiableList(existing);
    }
    /** Entries the player linked to this exact flag, in the order they were linked. */
    public List<JournalBook.Key> entriesFor(Flag flag) {
        List<JournalBook.Key> result = new ArrayList<>();
        for (Map.Entry<JournalBook.Key, List<Flag>> entry : links.entrySet()) {
            if (entry.getValue().contains(flag)) result.add(entry.getKey());
        }
        return result;
    }
    public int linkCount() {
        int total = 0; for (List<Flag> value : links.values()) total += value.size(); return total;
    }
    /** Drops links to flags the player has since deleted; keeps every other reference. */
    public boolean forgetFlag(Flag flag) {
        boolean changed = false;
        for (Iterator<Map.Entry<JournalBook.Key, List<Flag>>> it = links.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<JournalBook.Key, List<Flag>> entry = it.next();
            if (entry.getValue().remove(flag)) { changed = true; if (entry.getValue().isEmpty()) it.remove(); }
        }
        return changed;
    }

    /**
     * Records a reference the running game cited. Returns true only the first
     * time, so a message the player re-reads does not announce itself twice.
     * Never called with a guessed number: see {@link JournalCitation}.
     */
    public boolean encounter(JournalBook.Key key) {
        if (key == null) throw new IllegalArgumentException("Missing encountered reference");
        if (encountered.contains(key)) return false;
        if (encountered.size() >= MAX_ENCOUNTERED) return false;
        encountered.add(key);
        return true;
    }
    public boolean encountered(JournalBook.Key key) { return encountered.contains(key); }
    public List<JournalBook.Key> encountered() { return Collections.unmodifiableList(new ArrayList<>(encountered)); }

    public List<JournalBook.Key> recent() { return Collections.unmodifiableList(recent); }
    public List<JournalBook.Key> bookmarks() { return Collections.unmodifiableList(bookmarks); }
    public boolean isEmpty() {
        return recent.isEmpty() && bookmarks.isEmpty() && links.isEmpty() && encountered.isEmpty();
    }

    private static String encode(List<JournalBook.Key> keys) {
        StringBuilder out = new StringBuilder();
        for (JournalBook.Key k : keys) { if (out.length() > 0) out.append(','); out.append(k); }
        return out.toString();
    }
    public String recentValue() { return encode(recent); }
    public String bookmarkValue() { return encode(bookmarks); }

    private static JournalBook.Key key(DataInputStream in) throws IOException {
        int kind = in.readUnsignedByte(), number = in.readUnsignedByte();
        try { return new JournalBook.Key(kind, number); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid stored journal reference", invalid); }
    }
    private static void writeKey(DataOutputStream out, JournalBook.Key key) throws IOException {
        out.writeByte(key.kind); out.writeByte(key.number);
    }

    /** Strict: a damaged record is rejected so the previous one can be retained, never silently emptied. */
    public static JournalHistory read(DataInputStream in) throws IOException {
        JournalHistory result = new JournalHistory();
        int recentCount = in.readUnsignedByte();
        if (recentCount > MAX_RECENT) throw new IOException("Too many recent journal lookups");
        for (int i = 0; i < recentCount; i++) {
            JournalBook.Key key = key(in);
            if (result.recent.contains(key)) throw new IOException("Duplicate recent journal lookup");
            result.recent.add(key);
        }
        int bookmarkCount = in.readUnsignedByte();
        if (bookmarkCount > MAX_BOOKMARKS) throw new IOException("Too many journal bookmarks");
        for (int i = 0; i < bookmarkCount; i++) {
            JournalBook.Key key = key(in);
            int checked = in.readUnsignedByte();
            if (checked > 1 || result.bookmarks.contains(key)) throw new IOException("Invalid journal bookmark");
            result.bookmarks.add(key); if (checked == 1) result.done.add(key);
        }
        int linkCount = in.readUnsignedShort();
        if (linkCount > MAX_LINKS) throw new IOException("Too many journal flag links");
        for (int i = 0; i < linkCount; i++) {
            JournalBook.Key key = key(in);
            final JournalHistory.Flag flag;
            try { flag = new Flag(in.readUTF(), in.readUnsignedByte(), in.readUnsignedByte()); }
            catch (IllegalArgumentException invalid) { throw new IOException("Invalid stored journal flag link", invalid); }
            List<Flag> existing = result.links.get(key);
            if (existing == null) result.links.put(key, existing = new ArrayList<>());
            if (existing.contains(flag) || existing.size() >= MAX_LINKS_PER_ENTRY)
                throw new IOException("Duplicate or oversized journal flag link");
            existing.add(flag);
        }
        // Optional since 0.24.0: a notebook written before the encountered list
        // simply ends here, and must keep loading rather than being rejected.
        int encounteredCount = in.read();
        if (encounteredCount >= 0) {
            if (encounteredCount > MAX_ENCOUNTERED) throw new IOException("Too many encountered references");
            for (int i = 0; i < encounteredCount; i++) {
                JournalBook.Key key = key(in);
                if (!result.encountered.add(key)) throw new IOException("Duplicate encountered reference");
            }
        }
        if (in.read() != -1) throw new IOException("Trailing journal history data");
        return result;
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeByte(recent.size());
        for (JournalBook.Key key : recent) writeKey(out, key);
        out.writeByte(bookmarks.size());
        for (JournalBook.Key key : bookmarks) { writeKey(out, key); out.writeByte(done.contains(key) ? 1 : 0); }
        out.writeShort(linkCount());
        for (Map.Entry<JournalBook.Key, List<Flag>> entry : links.entrySet()) {
            for (Flag flag : entry.getValue()) {
                writeKey(out, entry.getKey()); out.writeUTF(flag.areaId);
                out.writeByte(flag.x); out.writeByte(flag.y);
            }
        }
        out.writeByte(encountered.size());
        for (JournalBook.Key key : encountered) writeKey(out, key);
    }
}
