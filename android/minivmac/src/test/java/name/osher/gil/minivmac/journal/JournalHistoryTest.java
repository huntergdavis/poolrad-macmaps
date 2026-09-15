package name.osher.gil.minivmac.journal;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.*;

/**
 * Player-made history, plus the one part that is read from the running game:
 * the references it cited in its own words. Even there, nothing here invents a
 * number; the citations come from {@link JournalCitation}.
 */
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

        // Every truncation is rejected but one: dropping the final byte leaves
        // exactly a pre-0.24.0 record, which has no encountered list and must
        // keep loading. See aNotebookWrittenBeforeEncounteredListsStillLoads.
        int legacyLength = good.length - 1;
        assertEquals("the dropped byte is the empty encountered count", 0, good[legacyLength]);
        for (int cut = 0; cut < good.length; cut++) {
            if (cut == legacyLength) { assertNotNull(decode(Arrays.copyOf(good, cut))); continue; }
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

    @Test public void encounteredReferencesSurviveTheRoundTripInTheOrderTheGameCitedThem() throws Exception {
        JournalHistory history = new JournalHistory();
        assertTrue(history.isEmpty());
        assertTrue(history.encounter(key(2, 15)));
        assertTrue(history.encounter(key(2, 3)));
        assertFalse("A repeated citation must not be listed twice", history.encounter(key(2, 15)));
        assertFalse(history.isEmpty());
        assertTrue(history.encountered(key(2, 15)));
        assertFalse(history.encountered(key(2, 4)));

        JournalHistory copy = roundTrip(history);
        assertEquals(Arrays.asList(key(2, 15), key(2, 3)), copy.encountered());
        assertTrue(copy.encountered(key(2, 3)));
        assertFalse(copy.encountered(key(0, 3)));
    }

    @Test public void anEncounterIsNotABookmarkAndDoesNotBecomeOne() throws Exception {
        JournalHistory history = new JournalHistory();
        history.encounter(key(2, 15));
        assertFalse(history.bookmarked(key(2, 15)));
        assertTrue(history.recent().isEmpty());
        assertTrue(history.bookmarks().isEmpty());

        // The player may still bookmark it themselves; the two lists stay separate.
        history.toggle(key(2, 15));
        assertTrue(history.bookmarked(key(2, 15)));
        assertTrue(history.encountered(key(2, 15)));
        JournalHistory copy = roundTrip(history);
        assertTrue(copy.bookmarked(key(2, 15)));
        assertTrue(copy.encountered(key(2, 15)));
    }

    @Test public void theEncounteredListIsBoundedAndNeverThrowsAtTheCeiling() {
        JournalHistory history = new JournalHistory();
        int added = 0;
        for (int number = 1; number <= 58; number++) if (history.encounter(key(0, number))) added++;
        for (int number = 1; number <= 23; number++) if (history.encounter(key(2, number))) added++;
        for (int number : JournalBook.PROCLAMATIONS) if (history.encounter(key(1, number))) added++;
        assertEquals("The whole book fits exactly", JournalHistory.MAX_ENCOUNTERED, added);
        assertEquals(JournalHistory.MAX_ENCOUNTERED, history.encountered().size());
    }

    @Test public void aNotebookWrittenBeforeEncounteredListsStillLoads() throws Exception {
        // A pre-0.24.0 record simply ends after the flag links.
        JournalHistory history = new JournalHistory();
        history.opened(key(0, 7)); history.toggle(key(2, 3));
        byte[] full = encode(history);
        byte[] legacy = Arrays.copyOf(full, full.length - 1); // drop the encountered count
        JournalHistory loaded = decode(legacy);
        assertEquals(Arrays.asList(key(0, 7)), loaded.recent());
        assertEquals(Arrays.asList(key(2, 3)), loaded.bookmarks());
        assertTrue(loaded.encountered().isEmpty());
    }

    @Test public void adamagedEncounteredSectionIsRejectedRatherThanPartlyLoaded() throws Exception {
        JournalHistory history = new JournalHistory();
        history.encounter(key(2, 15));
        byte[] good = encode(history);
        byte[] truncated = Arrays.copyOf(good, good.length - 1);
        try { decode(truncated); fail("A cut-off encountered reference was accepted"); }
        catch (IOException expected) { }

        byte[] duplicate = Arrays.copyOf(good, good.length + 2);
        duplicate[good.length - 2] = 2; // count becomes 2 entries
        duplicate[good.length - 3] = 2;
        // Rebuild explicitly: count 2, then the same key twice.
        byte[] rebuilt = Arrays.copyOf(good, good.length + 2);
        rebuilt[good.length - 3] = 2;
        rebuilt[good.length - 2] = 2; rebuilt[good.length - 1] = 15;
        rebuilt[good.length] = 2; rebuilt[good.length + 1] = 15;
        try { decode(rebuilt); fail("A duplicate encountered reference was accepted"); }
        catch (IOException expected) { }

        byte[] trailing = Arrays.copyOf(good, good.length + 1);
        try { decode(trailing); fail("Trailing bytes were accepted"); }
        catch (IOException expected) { }
    }
}
