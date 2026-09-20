package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

/** Fights, casts and rests read from mode, clock and spell-slot movement alone. */
public class RestTallyTest {
    private static GameClock at(int day, int hour, int minute) { return GameClock.of(day, hour, minute); }
    private static List<RestTally.SpellReading> spells(String name, int ready, int awaiting) {
        return Collections.singletonList(new RestTally.SpellReading(name, ready, awaiting));
    }

    @Test public void aFightCountsOnceHoweverManyCombatFramesFollow() {
        RestTally tally = new RestTally();
        tally.observeMap(MapMode.EXPLORATION, at(1, 0, 10));
        tally.observeMap(MapMode.COMBAT, at(1, 0, 10));
        tally.observeMap(MapMode.COMBAT, at(1, 0, 11));
        tally.observeMap(MapMode.UNAVAILABLE, null);
        tally.observeMap(MapMode.UPDATING, null);
        tally.observeMap(MapMode.COMBAT, at(1, 0, 12));
        assertEquals(1, tally.fights());
        tally.observeMap(MapMode.EXPLORATION, at(1, 0, 13));
        tally.observeMap(MapMode.COMBAT, at(1, 0, 14));
        assertEquals(2, tally.fights());
        assertTrue(tally.describe(), tally.describe().contains("2 fights"));
    }

    @Test public void aCastEmptiesAReadySlotAndOtherMovementIsIgnored() {
        RestTally tally = new RestTally();
        tally.observeSpells(spells("Lara", 3, 1));
        tally.observeSpells(spells("Lara", 2, 1));           // cast one
        tally.observeSpells(spells("Lara", 0, 1));           // cast two more between polls
        assertEquals(3, tally.spells());
        tally.observeSpells(spells("Lara", 0, 2));           // memorize screen: chosen, not cast
        tally.observeSpells(spells("Lara", 0, 0));           // record reset: ready and awaiting both gone
        tally.observeSpells(spells("Tanarakis", 2, 0));      // a different character appears
        tally.observeSpells(spells("Tanarakis", 2, 0));
        assertEquals(3, tally.spells());
        assertTrue(tally.describe(), tally.describe().contains("3 spells cast"));
        tally.observeSpells(spells("Tanarakis", 1, 0));
        assertEquals(4, tally.spells());
    }

    @Test public void memorizingChosenSpellsIsARestAndClearsTheCount() {
        RestTally tally = new RestTally();
        tally.observeMap(MapMode.EXPLORATION, at(1, 0, 10));
        tally.observeMap(MapMode.COMBAT, at(1, 0, 10));
        tally.observeSpells(spells("Lara", 2, 1));
        tally.observeSpells(spells("Lara", 1, 1));
        assertEquals(1, tally.fights()); assertEquals(1, tally.spells());
        tally.observeMap(MapMode.CAMP, at(1, 0, 30));
        tally.observeMap(MapMode.CAMP, at(1, 0, 45));
        tally.observeSpells(spells("Lara", 2, 0));           // awaiting became ready
        assertEquals(RestTally.Anchor.RESTED, tally.anchor());
        assertEquals(0, tally.fights()); assertEquals(0, tally.spells());
        assertEquals(at(1, 0, 45), tally.anchorClock());
        String text = tally.describe();
        assertTrue(text, text.startsWith("Last rest\nDay 1 · 12:45 am"));
        assertTrue(text, text.contains("0 fights\n0 spells cast"));
    }

    @Test public void anHourInCampIsARestButFiveMinutesRoustedIsNot() {
        RestTally tally = new RestTally();
        tally.observeMap(MapMode.EXPLORATION, at(1, 0, 0));
        tally.observeMap(MapMode.COMBAT, at(1, 0, 0));
        tally.observeMap(MapMode.EXPLORATION, at(1, 0, 0));
        tally.observeMap(MapMode.CAMP, at(1, 0, 0));
        tally.observeMap(MapMode.CAMP, at(1, 0, 5));         // the city watch moved us along
        tally.observeMap(MapMode.EXPLORATION, at(1, 0, 5));
        assertEquals(RestTally.Anchor.LOADED, tally.anchor());
        assertEquals(1, tally.fights());
        tally.observeMap(MapMode.CAMP, at(1, 0, 5));
        tally.observeMap(MapMode.CAMP, at(1, 0, 59));
        assertEquals(RestTally.Anchor.LOADED, tally.anchor());
        tally.observeMap(MapMode.CAMP, at(1, 8, 5));         // the full eight hours
        assertEquals(RestTally.Anchor.RESTED, tally.anchor());
        assertEquals(0, tally.fights());
        assertTrue(tally.describe(), tally.describe().contains("Day 1 · 8:05 am · 8 hours rested"));
        tally.observeMap(MapMode.EXPLORATION, at(1, 8, 5));
        tally.observeMap(MapMode.EXPLORATION, at(1, 9, 5));   // an hour of walking, minute by minute
        tally.observeMap(MapMode.COMBAT, at(1, 9, 5));
        String text = tally.describe();
        assertTrue(text, text.contains("1 hour of game time ago"));
        assertTrue(text, text.contains("1 fight\n0 spells cast"));
    }

    @Test public void unreadableFramesDuringTheRestStillCountIt() {
        RestTally tally = new RestTally();
        tally.observeMap(MapMode.EXPLORATION, at(1, 0, 0));
        tally.observeMap(MapMode.COMBAT, at(1, 0, 0));
        tally.observeMap(MapMode.EXPLORATION, at(1, 0, 0));
        tally.observeMap(MapMode.CAMP, at(1, 0, 0));
        tally.observeMap(MapMode.UPDATING, null);             // the rest animation
        tally.observeMap(MapMode.UNAVAILABLE, null);
        tally.observeMap(MapMode.CAMP, at(1, 8, 0));
        assertEquals(RestTally.Anchor.RESTED, tally.anchor());
        assertEquals(0, tally.fights());
        assertTrue(tally.describe(), tally.describe().contains("Day 1 · 8:00 am · 8 hours rested"));
        // A second rest in the same camp extends the anchor and keeps the zeroes.
        tally.observeMap(MapMode.CAMP, at(1, 10, 0));
        assertEquals(at(1, 10, 0), tally.anchorClock());
        assertTrue(tally.describe(), tally.describe().contains("10 hours rested"));
    }

    @Test public void aRestReadOnlyOnTheWayOutOfCampStillCounts() {
        RestTally tally = new RestTally();
        tally.observeMap(MapMode.EXPLORATION, at(1, 0, 0));
        tally.observeMap(MapMode.COMBAT, at(1, 0, 0));
        tally.observeMap(MapMode.CAMP, at(1, 0, 0));
        tally.observeMap(MapMode.EXPLORATION, at(1, 8, 0));   // the first readable frame after resting
        assertEquals(RestTally.Anchor.RESTED, tally.anchor());
        assertEquals(0, tally.fights());
        tally.observeMap(MapMode.COMBAT, at(1, 8, 0));
        assertEquals(1, tally.fights());
    }

    @Test public void wildernessTravelAndUnreadableFramesAreNotRests() {
        RestTally tally = new RestTally();
        tally.observeMap(MapMode.EXPLORATION, at(1, 0, 0));
        tally.observeMap(MapMode.COMBAT, at(1, 0, 0));
        tally.observeMap(MapMode.WILDERNESS, at(1, 0, 0));
        tally.observeMap(MapMode.WILDERNESS, at(1, 6, 0));
        tally.observeMap(MapMode.UNAVAILABLE, null);
        assertEquals(RestTally.Anchor.LOADED, tally.anchor());
        assertEquals(1, tally.fights());
    }

    @Test public void aClockRunningBackwardsOrAFreshPartyIsALoad() {
        RestTally tally = new RestTally();
        tally.observeMap(MapMode.EXPLORATION, at(2, 3, 0));
        tally.observeMap(MapMode.COMBAT, at(2, 3, 0));
        tally.observeMap(MapMode.EXPLORATION, at(1, 0, 10));
        assertEquals(0, tally.fights());
        assertEquals(RestTally.Anchor.LOADED, tally.anchor());
        assertEquals(at(1, 0, 10), tally.anchorClock());
        assertTrue(tally.describe(), tally.describe().contains("Counting since the game was loaded at Day 1 · 12:10 am."));
        tally.observeMap(MapMode.COMBAT, at(1, 0, 10));
        tally.observeSignal(GameSignal.PARTY);
        tally.observeSignal(GameSignal.UNKNOWN);             // a heap refusal says nothing
        tally.observeSignal(GameSignal.NO_PARTY);            // the roster flickers during rests and encounters
        tally.observeSignal(GameSignal.PARTY);
        assertEquals(1, tally.fights());
        tally.observeSignal(GameSignal.NO_GAME);             // the probe's shared guard also refuses mid-fight
        tally.observeSignal(GameSignal.PARTY);
        assertEquals(1, tally.fights());
        tally.observeMap(MapMode.LOADING, null);              // the game's own Load Saved Game or a fresh game
        assertEquals(1, tally.fights());
        tally.observeMap(MapMode.EXPLORATION, at(3, 5, 0));
        assertEquals(0, tally.fights());
        assertEquals(at(3, 5, 0), tally.anchorClock());
    }

    @Test public void theSidecarRoundTripsAndGarbageStartsOver() {
        RestTally tally = new RestTally();
        tally.observeMap(MapMode.CAMP, at(1, 0, 0));
        tally.observeMap(MapMode.CAMP, at(1, 8, 0));
        tally.observeMap(MapMode.EXPLORATION, at(1, 8, 0));
        tally.observeMap(MapMode.COMBAT, at(1, 8, 0));
        tally.observeSpells(spells("Lara", 2, 0));
        tally.observeSpells(spells("Lara", 1, 0));
        byte[] encoded = tally.encode();
        RestTally restored = new RestTally();
        assertTrue(restored.restore(encoded));
        assertEquals(1, restored.fights()); assertEquals(1, restored.spells());
        assertEquals(RestTally.Anchor.RESTED, restored.anchor());
        assertEquals(at(1, 8, 0), restored.anchorClock());
        assertArrayEquals(encoded, restored.encode());
        restored.observeMap(MapMode.EXPLORATION, at(1, 0, 30));   // earlier than the anchor is not a rewind after restore
        assertEquals(1, restored.fights());
        assertFalse(restored.restore("PRRT1\nfights=x\n".getBytes()));
        assertEquals(0, restored.fights());
        assertEquals(RestTally.Anchor.LOADED, restored.anchor());
        assertFalse(new RestTally().restore(null));
        assertFalse(new RestTally().restore("PRRT1\nfights=1\nspells=1\nanchor=RESTED\nclock=1,2\nrested=0\n".getBytes()));
        assertFalse(new RestTally().restore("PRRT1\nfights=1\nspells=1\nanchor=RESTED\nclock=none\n".getBytes()));
    }

    @Test public void durationsReadPlainly() {
        assertEquals("1 minute", RestTally.duration(1));
        assertEquals("45 minutes", RestTally.duration(45));
        assertEquals("8 hours", RestTally.duration(480));
        assertEquals("1 h 05 min", RestTally.duration(65));
        assertEquals("2 days 1 hours", RestTally.duration(49 * 60));
        assertNull(GameClock.of(0, 0, 0)); assertNull(GameClock.of(1, 24, 0));
        assertEquals(-1440, at(1, 0, 0).minutesSince(at(2, 0, 0)));
    }
}
