package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

public class FightStartTest {
    @Test public void firesOnceWhenTheWorldGivesWayToCombat() {
        FightStart start = new FightStart();
        assertFalse("the first frame the companion sees is not a fight starting", start.observe(MapMode.COMBAT));
        assertFalse(start.observe(MapMode.EXPLORATION));
        assertTrue(start.observe(MapMode.COMBAT));
        assertFalse("still the same fight", start.observe(MapMode.COMBAT));
        assertFalse(start.observe(MapMode.UNAVAILABLE));
        assertFalse(start.observe(MapMode.UPDATING));
        assertFalse("a blink mid-fight is not a new fight", start.observe(MapMode.COMBAT));
        assertFalse(start.observe(MapMode.EXPLORATION));
        assertFalse(start.observe(MapMode.CAMP));
        assertTrue("an ambush while resting", start.observe(MapMode.COMBAT));
        assertFalse(start.observe(MapMode.LOADING));
        assertTrue("a fight right after a load is still a fight", start.observe(MapMode.COMBAT));
        assertFalse(start.observe(null));
    }
}
