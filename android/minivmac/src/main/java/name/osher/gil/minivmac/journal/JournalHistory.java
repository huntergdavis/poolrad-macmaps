package name.osher.gil.minivmac.journal;

import java.util.*;

/** Manual lookups, not claims that an event was encountered. Persist per notebook. */
public final class JournalHistory {
    private final List<JournalBook.Key> recent = new ArrayList<>(), bookmarks = new ArrayList<>();
    public JournalHistory(String recent, String bookmarks) {
        parse(recent, this.recent, 20); parse(bookmarks, this.bookmarks, 99);
    }
    private static void parse(String value, List<JournalBook.Key> result, int max) {
        if (value.length() > 1024) throw new IllegalArgumentException("Oversized journal history");
        if (!value.isEmpty()) for (String part : value.split(",", -1)) {
            JournalBook.Key key = JournalBook.Key.parse(part);
            if (result.contains(key) || result.size() >= max) throw new IllegalArgumentException("Invalid journal history");
            result.add(key);
        }
    }
    public void opened(JournalBook.Key key) { recent.remove(key); recent.add(0,key); if (recent.size() > 20) recent.remove(20); }
    public void toggle(JournalBook.Key key) { if (!bookmarks.remove(key)) bookmarks.add(key); }
    public boolean bookmarked(JournalBook.Key key) { return bookmarks.contains(key); }
    public List<JournalBook.Key> recent() { return Collections.unmodifiableList(recent); }
    public List<JournalBook.Key> bookmarks() { return Collections.unmodifiableList(bookmarks); }
    private static String encode(List<JournalBook.Key> keys) {
        StringBuilder out = new StringBuilder();
        for (JournalBook.Key k : keys) { if (out.length() > 0) out.append(','); out.append(k); }
        return out.toString();
    }
    public String recentValue() { return encode(recent); }
    public String bookmarkValue() { return encode(bookmarks); }
}
