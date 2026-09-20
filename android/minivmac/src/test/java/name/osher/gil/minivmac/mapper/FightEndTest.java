package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;

public class FightEndTest {
    @Test public void firesOnceWhenCombatGivesWayToTheWorld() {
        FightEnd end = new FightEnd();
        assertFalse(end.observe(MapMode.EXPLORATION));
        assertFalse(end.observe(MapMode.COMBAT));
        assertFalse(end.observe(MapMode.COMBAT));
        assertFalse("a blink mid-fight is not the end", end.observe(MapMode.UNAVAILABLE));
        assertFalse(end.observe(MapMode.UPDATING));
        assertTrue(end.observe(MapMode.EXPLORATION));
        assertFalse("only once per fight", end.observe(MapMode.EXPLORATION));
        assertFalse(end.observe(MapMode.COMBAT));
        assertTrue(end.observe(MapMode.CAMP));
        assertFalse(end.observe(MapMode.COMBAT));
        assertFalse("a load screen after combat is a load, not a fight ending", end.observe(MapMode.LOADING));
        assertFalse(end.observe(MapMode.EXPLORATION));
        assertFalse(end.observe(null));
    }
}
