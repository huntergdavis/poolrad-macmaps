package name.osher.gil.minivmac.reference;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;
import static name.osher.gil.minivmac.reference.SpellReference.Caster.*;

public class SpellReferenceTest {
    private SpellReference.Spell spell(SpellReference.Caster caster, int level, String name) {
        for (SpellReference.Spell spell : SpellReference.filter(caster, level, name))
            if (spell.name.equals(name)) return spell;
        throw new AssertionError("Missing " + caster + " " + level + " " + name);
    }

    @Test public void coversTheOriginal54ChartRowsPlusFlaggedResistCold() {
        assertEquals(55, SpellReference.all().size());
        assertEquals(8, SpellReference.filter(CLERIC,1,"").size());
        assertEquals(7, SpellReference.filter(CLERIC,2,"").size());
        assertEquals(9, SpellReference.filter(CLERIC,3,"").size());
        assertEquals(13, SpellReference.filter(MAGIC_USER,1,"").size());
        assertEquals(7, SpellReference.filter(MAGIC_USER,2,"").size());
        assertEquals(11, SpellReference.filter(MAGIC_USER,3,"").size());
        Set<String> identities = new HashSet<>();
        int chartRows = 0;
        for (SpellReference.Spell spell : SpellReference.all()) {
            assertTrue(identities.add(spell.heading()));
            if (spell.inChart) chartRows++;
            assertFalse(spell.effect.isEmpty());
            assertFalse(spell.range.isEmpty());
            assertFalse(spell.duration.isEmpty());
            assertFalse(spell.targeting.isEmpty());
            assertFalse(spell.usable.isEmpty());
            assertFalse(spell.provenance().isEmpty());
        }
        assertEquals(54, chartRows);
        assertFalse(spell(CLERIC,1,"Resist Cold").inChart);
        assertTrue(spell(CLERIC,1,"Resist Cold").notes.contains("omitted"));
    }

    @Test public void filtersClassLevelAndNameTogetherWithoutLosingSharedNames() {
        assertEquals(7, SpellReference.filter(null,0,"protection from").size());
        // Four first-level protections, two radius spells, and Normal Missiles.
        assertEquals(1, SpellReference.filter(CLERIC,2,"hold").size());
        assertEquals(0, SpellReference.filter(CLERIC,1,"hold").size());
        assertEquals(2, SpellReference.filter(null,0," hold person ").size());
        assertEquals(3, SpellReference.filter(MAGIC_USER,3,"10'").size());
        assertTrue(SpellReference.filter(null,0,"zzzz").isEmpty());
        assertEquals(55, SpellReference.filter(null,0,null).size());
    }

    @Test public void nameMatchingDoesNotDependOnDeviceLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals("Magic Missile", SpellReference.filter(MAGIC_USER,1,"MISSILE").get(0).name);
        } finally { Locale.setDefault(previous); }
    }

    @Test public void preservesClassSpecificHoldDispelAndProtectionParameters() {
        SpellReference.Spell clericHold = spell(CLERIC,2,"Hold Person");
        SpellReference.Spell mageHold = spell(MAGIC_USER,3,"Hold Person");
        assertEquals("6 squares", clericHold.range);
        assertEquals("12 squares", mageHold.range);
        assertTrue(clericHold.targeting.startsWith("1–3"));
        assertTrue(mageHold.targeting.startsWith("1–4"));
        assertEquals("4 rounds + 1 round / level", clericHold.duration);
        assertEquals("2 rounds / level", mageHold.duration);
        assertEquals("6 squares", spell(CLERIC,3,"Dispel Magic").range);
        assertEquals("12 squares", spell(MAGIC_USER,3,"Dispel Magic").range);
        assertEquals("3 rounds / level", spell(CLERIC,1,"Protection from Evil").duration);
        assertEquals("2 rounds / level", spell(MAGIC_USER,1,"Protection from Evil").duration);
    }

    @Test public void preservesOriginalGameCombatAndMenuRules() {
        assertTrue(spell(MAGIC_USER,3,"Fireball").targeting.contains("3-square radius"));
        assertTrue(spell(MAGIC_USER,3,"Lightning Bolt").notes.contains("rebounds"));
        assertEquals("9 squares + 1 square / level", spell(MAGIC_USER,3,"Slow").range);
        assertEquals("6 turns / level", spell(MAGIC_USER,2,"Strength").duration);
        assertTrue(spell(MAGIC_USER,2,"Knock").usable.contains("(E,D)"));
        assertTrue(spell(CLERIC,1,"Detect Magic").usable.contains("(E,C,T)"));
        assertEquals("Combat (C)", spell(CLERIC,2,"Silence 15' Radius").usable);
        assertTrue(spell(CLERIC,2,"Slow Poison").effect.contains("remains lethal"));
    }

    @Test public void sourceConflictsStayVisibleInsteadOfInventingRules() {
        assertTrue(spell(CLERIC,3,"Bestow Curse").duration.contains("1 turn / level"));
        assertTrue(spell(CLERIC,3,"Bestow Curse").duration.contains("Until removed"));
        assertTrue(spell(MAGIC_USER,1,"Charm Person").notes.contains("days or weeks"));
        assertTrue(spell(MAGIC_USER,1,"Reduce").duration.contains("Not specified"));
        assertTrue(spell(MAGIC_USER,2,"Detect Invisibility").range.contains("20 feet"));
        assertTrue(spell(MAGIC_USER,2,"Stinking Cloud").notes.contains("inconsistent"));
    }

    @Test public void callersCannotMutateTheSharedCatalogOrFilterResults() {
        assertThrows(UnsupportedOperationException.class, () -> SpellReference.all().clear());
        List<SpellReference.Spell> filtered = SpellReference.filter(CLERIC,1,"");
        assertThrows(UnsupportedOperationException.class, () -> filtered.clear());
        assertThrows(IllegalArgumentException.class, () -> SpellReference.filter(null,4,""));
        assertThrows(IllegalArgumentException.class, () -> SpellReference.filter(null,-1,""));
    }
}
