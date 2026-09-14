package name.osher.gil.minivmac.notebook;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ExplorationTrailTest {
    private ExplorationTrail.Step last(ExplorationTrail trail) { return trail.steps.get(trail.steps.size()-1); }

    @Test public void emptyHistoryDoesNotInventExplorationAndValidatesTiles() {
        ExplorationTrail trail=ExplorationTrail.empty();
        assertEquals(0,trail.visitedCount()); assertTrue(trail.steps.isEmpty());
        for(int tile=0;tile<256;tile++) assertFalse(trail.visited(tile));
        assertThrows(IllegalArgumentException.class,()->trail.visited(-1));
        assertThrows(IllegalArgumentException.class,()->trail.visited(256));
        assertThrows(IllegalArgumentException.class,()->trail.record(256,0));
        assertThrows(IllegalArgumentException.class,()->trail.record(-1,0));
    }

    @Test public void firstObservationAnchorsAndTurnsDoNotAddMovement() {
        ExplorationTrail trail=ExplorationTrail.empty().record(17,-1);
        assertEquals(1,trail.visitedCount()); assertTrue(trail.visited(17));
        assertEquals(-1,last(trail).from); assertEquals(17,last(trail).to);
        assertSame(trail,trail.record(17,17));
        assertFalse(ExplorationTrail.empty().visited(17));
    }

    @Test public void followsObservedMotionIncludingBacktrackingRatherThanFacing() {
        ExplorationTrail trail=ExplorationTrail.empty().record(17,-1).record(18,17).record(17,18);
        assertEquals(2,trail.visitedCount()); assertEquals(3,trail.steps.size());
        assertEquals(17,trail.steps.get(1).from);assertEquals(18,trail.steps.get(1).to);
        assertEquals(18,last(trail).from);assertEquals(17,last(trail).to);
        for(int neighbor:new int[]{1,16,18,33}) {
            ExplorationTrail moved=ExplorationTrail.empty().record(17,-1).record(neighbor,17);
            assertEquals(17,last(moved).from);assertEquals(neighbor,last(moved).to);
        }
    }

    @Test public void gapsDiagonalsAndRowWrapsNeverDrawAnInterpolatedPath() {
        for(int[] pair:new int[][]{{15,16},{16,15},{0,255},{255,0},{17,34},{1,9},{16,48}}) {
            ExplorationTrail trail=ExplorationTrail.empty().record(pair[0],-1).record(pair[1],pair[0]);
            assertEquals(-1,last(trail).from);assertEquals(2,trail.visitedCount());
        }
        ExplorationTrail trail=ExplorationTrail.empty().record(17,-1).record(18,19);
        assertEquals(-1,last(trail).from);assertFalse(trail.visited(19));
    }

    @Test public void resumedObservationMustNotBorrowHistoricalContinuity() {
        ExplorationTrail trail=ExplorationTrail.empty().record(17,-1).record(18,17);
        for(int invalid:new int[]{-1,-10,256,Integer.MAX_VALUE}) {
            ExplorationTrail resumed=trail.record(19,invalid);
            assertEquals(-1,last(resumed).from);
        }
        ExplorationTrail samePosition=trail.record(18,-1);
        assertEquals(3,samePosition.steps.size());assertEquals(-1,last(samePosition).from);
        assertEquals(2,samePosition.visitedCount());
        assertEquals(18,last(samePosition.record(19,18)).from);
    }

    @Test public void repeatedStationaryPauseResumeDoesNotSpamOrEvictHistory() {
        ExplorationTrail travelled=ExplorationTrail.empty().record(17,-1).record(18,17);
        ExplorationTrail paused=travelled.record(18,-1);
        assertEquals(3,paused.steps.size());assertEquals(-1,last(paused).from);
        for(int i=0;i<ExplorationTrail.MAX_STEPS+1;i++) {
            assertSame(paused,paused.record(18,-1));
            assertSame(paused,paused.record(18,18));
        }
        assertEquals(3,paused.steps.size());assertEquals(2,paused.visitedCount());
        assertEquals(travelled.steps.get(0),paused.steps.get(0));
        assertEquals(travelled.steps.get(1),paused.steps.get(1));
        ExplorationTrail first=ExplorationTrail.empty().record(17,-1);
        assertSame(first,first.record(17,-1));
    }

    @Test public void compactedAnchorsStillRequireVerifiedPreviousTileForNextMovement() {
        ExplorationTrail anchor=ExplorationTrail.empty().record(17,-1);
        ExplorationTrail compacted=anchor.record(17,-1);
        ExplorationTrail unknown=compacted.record(18,-1);
        assertEquals(2,unknown.steps.size());assertEquals(-1,last(unknown).from);
        assertEquals(18,last(unknown).to);
        ExplorationTrail connected=compacted.record(18,17);
        assertEquals(2,connected.steps.size());assertEquals(17,last(connected).from);
        ExplorationTrail newBreak=connected.record(18,-1);
        assertEquals(3,newBreak.steps.size());assertEquals(-1,last(newBreak).from);
        assertSame(newBreak,newBreak.record(18,-1));
    }

    @Test public void boundsRecentTrailWithoutForgettingPreviouslyVisitedTiles() {
        ExplorationTrail trail=ExplorationTrail.empty();
        for(int tile=0;tile<256;tile++) trail=trail.record(tile,tile-1);
        for(int i=0;i<600;i++) trail=trail.record(i%2,i==0?255:1-i%2);
        assertEquals(256,trail.visitedCount());assertEquals(256,trail.steps.size());
        for(int tile=0;tile<256;tile++) assertTrue(trail.visited(tile));
        assertEquals(0,last(trail).from);assertEquals(1,last(trail).to);
        assertEquals(trail,ExplorationTrail.restore(trail.copyVisited(),trail.steps));
    }

    @Test public void clearingFootprintsKeepsCoverageButFreshObservationStillAnchors() {
        ExplorationTrail original=ExplorationTrail.empty().record(17,-1).record(18,17);
        ExplorationTrail cleared=original.clearTrail();
        assertEquals(2,cleared.visitedCount());assertTrue(cleared.steps.isEmpty());
        assertSame(cleared,cleared.clearTrail());assertEquals(2,original.steps.size());
        assertEquals(-1,last(cleared.record(19,18)).from);
        assertEquals(0,ExplorationTrail.empty().visitedCount());
    }

    @Test public void stateAndStepsAreImmutableDefensiveValues() {
        ExplorationTrail original=ExplorationTrail.empty().record(17,-1).record(18,17);
        byte[] bits=original.copyVisited();List<ExplorationTrail.Step> steps=new ArrayList<>(original.steps);
        ExplorationTrail restored=ExplorationTrail.restore(bits,steps);
        bits[2]=0;steps.clear();
        assertEquals(original,restored);assertEquals(original.hashCode(),restored.hashCode());
        assertThrows(UnsupportedOperationException.class,()->restored.steps.clear());
        assertNotEquals(original,original.clearTrail());assertNotEquals(original,null);
        assertNotEquals(original.steps.get(0),original.steps.get(1));
    }

    @Test public void rejectsInvalidRestoredSegmentsCoverageAndOversizedHistory() {
        byte[] all=new byte[32];java.util.Arrays.fill(all,(byte)255);
        assertThrows(IllegalArgumentException.class,()->new ExplorationTrail.Step(15,16));
        assertThrows(IllegalArgumentException.class,()->new ExplorationTrail.Step(17,17));
        assertThrows(IllegalArgumentException.class,()->new ExplorationTrail.Step(-2,17));
        assertThrows(IllegalArgumentException.class,()->ExplorationTrail.restore(new byte[31],Collections.emptyList()));
        assertThrows(IllegalArgumentException.class,()->ExplorationTrail.restore(new byte[32],
                Collections.singletonList(new ExplorationTrail.Step(-1,17))));
        assertThrows(IllegalArgumentException.class,()->ExplorationTrail.restore(all,
                java.util.Arrays.asList(new ExplorationTrail.Step(-1,17),new ExplorationTrail.Step(19,18))));
        assertThrows(IllegalArgumentException.class,()->ExplorationTrail.restore(all,
                Collections.nCopies(257,new ExplorationTrail.Step(-1,17))));
        assertThrows(IllegalArgumentException.class,()->ExplorationTrail.restore(all,Collections.singletonList(null)));
    }
}
