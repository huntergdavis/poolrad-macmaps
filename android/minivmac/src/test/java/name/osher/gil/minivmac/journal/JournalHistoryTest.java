package name.osher.gil.minivmac.journal;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Player-made history only; nothing here is detected from the running game. */
public class JournalHistoryTest {
    private static final String AREA = "por-mac-v11-geo-0", OTHER = "por-mac-v11-geo-32";
    private JournalBook.Key key(int kind, int number) { return new JournalBook.Key(kind, number); }
    private JournalHistory.Flag flag(String area, int x, int y) { return new JournalHistory.Flag(area, x, y); }

    private byte[] encode(JournalHistory history) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) { history.write(out); }
        return bytes.toByteArray();
    }
    private JournalHistory decode(byte[] payload) throws IOException {
        return JournalHistory.read(new DataInputStream(new ByteArrayInputStream(payload)));
    }
    private JournalHistory roundTrip(JournalHistory history) throws IOException { return decode(encode(history)); }

    @Test public void everyKindOfHistorySurvivesTheBinaryRoundTrip() throws Exception {
        JournalHistory history = new JournalHistory();
        history.opened(key(0, 7)); history.opened(key(1, 59)); history.opened(key(2, 3));
        history.toggle(key(1, 59)); history.toggleDone(key(1, 59));
        history.toggle(key(0, 7));
        history.toggleLink(key(0, 7), flag(AREA, 1, 2));
        history.toggleLink(key(0, 7), flag(OTHER, 15, 15));
        history.toggleLink(key(2, 3), flag(AREA, 0, 0));

        JournalHistory copy = roundTrip(history);
        assertEquals(history.recent(), copy.recent());
        assertEquals(history.bookmarks(), copy.bookmarks());
        assertTrue(copy.done(key(1, 59)));
        assertFalse(copy.done(key(0, 7)));
        assertEquals(Arrays.asList(flag(AREA, 1, 2), flag(OTHER, 15, 15)), copy.links(key(0, 7)));
        assertEquals(Arrays.asList(key(0, 7)), copy.entriesFor(flag(AREA, 1, 2)));
        assertEquals(3, copy.linkCount());
        assertArrayEquals(encode(history), encode(copy));
    }

    @Test public void anEmptyHistoryIsStillReadableAndReportsItself() throws Exception {
        JournalHistory empty = new JournalHistory();
        assertTrue(empty.isEmpty());
        JournalHistory copy = roundTrip(empty);
        assertTrue(copy.isEmpty());
        assertTrue(copy.recent().isEmpty() && copy.bookmarks().isEmpty() && copy.linkCount() == 0);
        empty.opened(key(0, 1));
        assertFalse(empty.isEmpty());
    }

    @Test public void the0140PreferenceHistoryStillLoadsForMigration() throws Exception {
        JournalHistory legacy = new JournalHistory("0:5,1:64", "2:1");
        assertEquals(Arrays.asList(key(0, 5), key(1, 64)), legacy.recent());
        assertEquals(Arrays.asList(key(2, 1)), legacy.bookmarks());
        assertFalse(legacy.done(key(2, 1)));
        assertEquals(0, legacy.linkCount());
        assertEquals(legacy.recent(), roundTrip(legacy).recent());
        for (String broken : Arrays.asList("0:1,0:1", "0:1,", "1:1", "future-format")) {
            try { new JournalHistory(broken, ""); fail(broken); } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void checkingOffRequiresABookmarkAndUnbookmarkingClearsIt() {
        JournalHistory history = new JournalHistory();
        try { history.toggleDone(key(0, 1)); fail("checked a reference that is not a task"); }
        catch (IllegalStateException expected) { }
        history.toggle(key(0, 1)); history.toggleDone(key(0, 1));
        assertTrue(history.done(key(0, 1)));
        history.toggleDone(key(0, 1)); assertFalse(history.done(key(0, 1)));
        history.toggleDone(key(0, 1)); assertTrue(history.done(key(0, 1)));
        history.toggle(key(0, 1));
        assertFalse(history.bookmarked(key(0, 1)));
        assertFalse("An unbookmarked reference must not stay checked off", history.done(key(0, 1)));
    }

    @Test public void linksToggleIndependentlyAndDeletedFlagsAreForgotten() {
        JournalHistory history = new JournalHistory();
        history.toggleLink(key(0, 1), flag(AREA, 3, 4));
        history.toggleLink(key(0, 2), flag(AREA, 3, 4));
        history.toggleLink(key(0, 1), flag(AREA, 5, 6));
        assertEquals(Arrays.asList(key(0, 1), key(0, 2)), history.entriesFor(flag(AREA, 3, 4)));
        assertTrue(history.linked(key(0, 1), flag(AREA, 3, 4)));
        assertFalse("The same tile in another area is a different flag",
                history.linked(key(0, 1), flag(OTHER, 3, 4)));

        history.toggleLink(key(0, 1), flag(AREA, 3, 4));
        assertFalse(history.linked(key(0, 1), flag(AREA, 3, 4)));
        assertTrue("Unlinking one entry must not disturb another", history.linked(key(0, 2), flag(AREA, 3, 4)));

        assertTrue(history.forgetFlag(flag(AREA, 3, 4)));
        assertTrue(history.entriesFor(flag(AREA, 3, 4)).isEmpty());
        assertEquals("Other links survive a deleted flag", Arrays.asList(flag(AREA, 5, 6)), history.links(key(0, 1)));
        assertFalse(history.forgetFlag(flag(AREA, 3, 4)));
    }

    @Test public void recentBookmarkAndLinkLimitsAreEnforced() {
        JournalHistory history = new JournalHistory();
        for (int n = 1; n <= 30; n++) history.opened(key(0, n));
        assertEquals(JournalHistory.MAX_RECENT, history.recent().size());
        assertEquals(key(0, 30), history.recent().get(0));
        history.opened(key(0, 20));
        assertEquals(key(0, 20), history.recent().get(0));
        assertEquals(JournalHistory.MAX_RECENT, history.recent().size());

        for (int n = 1; n <= JournalHistory.MAX_BOOKMARKS; n++) history.toggle(key(0, Math.min(n, 58)));
        // Alternating toggles on the clamped tail leave a legal, bounded set.
        assertTrue(history.bookmarks().size() <= JournalHistory.MAX_BOOKMARKS);

        JournalHistory links = new JournalHistory();
        for (int i = 0; i < JournalHistory.MAX_LINKS_PER_ENTRY; i++) links.toggleLink(key(0, 1), flag(AREA, i, 0));
        try { links.toggleLink(key(0, 1), flag(AREA, 9, 0)); fail("ninth link accepted"); }
        catch (IllegalStateException expected) { }
        assertEquals(JournalHistory.MAX_LINKS_PER_ENTRY, links.links(key(0, 1)).size());
    }

    @Test public void invalidFlagsAreRejectedRatherThanStoredApproximately() {
        for (Object[] bad : new Object[][]{{null, 0, 0}, {"", 0, 0}, {"por-mac-v11-geo-33", 0, 0},
                {"../escape", 0, 0}, {AREA, -1, 0}, {AREA, 16, 0}, {AREA, 0, -1}, {AREA, 0, 16}}) {
            try { new JournalHistory.Flag((String) bad[0], (Integer) bad[1], (Integer) bad[2]); fail(Arrays.toString(bad)); }
            catch (IllegalArgumentException expected) { }
        }
        assertEquals(4 * 16 + 3, flag(AREA, 3, 4).tile());
    }

    @Test public void damagedRecordsAreRejectedSoTheStoredOneCanBeKept() throws Exception {
        JournalHistory history = new JournalHistory();
        history.opened(key(0, 1)); history.toggle(key(0, 1)); history.toggleDone(key(0, 1));
        history.toggleLink(key(0, 1), flag(AREA, 2, 2));
        byte[] good = encode(history);
        assertNotNull(decode(good));

        for (int cut = 0; cut < good.length; cut++) {
            try { decode(Arrays.copyOf(good, cut)); fail("truncation at " + cut + " accepted"); }
            catch (IOException expected) { }
        }
        byte[] extra = Arrays.copyOf(good, good.length + 1);
        try { decode(extra); fail("trailing byte accepted"); } catch (IOException expected) { }

        byte[] duplicateRecent = new byte[]{2, 0, 1, 0, 1, 0, 0, 0};
        try { decode(duplicateRecent); fail("duplicate recent accepted"); } catch (IOException expected) { }
        byte[] badKind = new byte[]{1, 9, 1, 0, 0, 0};
        try { decode(badKind); fail("unknown reference kind accepted"); } catch (IOException expected) { }
        byte[] badNumber = new byte[]{1, 1, 60, 0, 0, 0};
        try { decode(badNumber); fail("non-proclamation number accepted"); } catch (IOException expected) { }
        byte[] tooManyRecent = new byte[]{(byte) (JournalHistory.MAX_RECENT + 1)};
        try { decode(tooManyRecent); fail("oversized recent list accepted"); } catch (IOException expected) { }
        byte[] badChecked = new byte[]{0, 1, 0, 1, 2, 0, 0};
        try { decode(badChecked); fail("non-boolean checked flag accepted"); } catch (IOException expected) { }
    }
}
