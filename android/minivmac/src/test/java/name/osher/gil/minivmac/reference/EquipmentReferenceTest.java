package name.osher.gil.minivmac.reference;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;
import static name.osher.gil.minivmac.reference.EquipmentReference.Kind.*;

public class EquipmentReferenceTest {
    private EquipmentReference.Entry entry(String id) {
        for (EquipmentReference.Entry entry : EquipmentReference.all())
            if (entry.id.equals(id)) return entry;
        throw new AssertionError("Missing equipment: " + id);
    }

    @Test public void completeOriginalWeaponAndArmorListsPlusBothAmmunitionTypes() {
        assertEquals(59, EquipmentReference.all().size());
        assertEquals(46, EquipmentReference.browse(WEAPON, null).size());
        assertEquals(11, EquipmentReference.browse(ARMOR, null).size());
        assertEquals(2, EquipmentReference.browse(AMMUNITION, null).size());
        String originalWeaponIds = "hand-axe bardiche bastard-sword battle-axe bec-de-corbin bill-guisarme bo-stick broad-sword club dagger dart fauchard fauchard-fork flail military-fork glaive glaive-guisarme guisarme guisarme-voulge halberd lucern-hammer hammer javelin jo-stick long-sword mace morning-star partisan military-pick awl-pike quarterstaff ranseur scimitar short-sword spear spetum trident two-handed-sword voulge composite-long-bow composite-short-bow long-bow heavy-crossbow light-crossbow short-bow sling";
        Set<String> expected = new HashSet<>();
        for (String id : originalWeaponIds.split(" ")) assertTrue(expected.add(id));
        Set<String> actual = new HashSet<>();
        for (EquipmentReference.Entry row : EquipmentReference.browse(WEAPON, null)) actual.add(row.id);
        assertEquals(expected, actual);
    }

    @Test public void allCategoriesAreFiniteReachableAndPartitionWeapons() {
        Set<EquipmentReference.Entry> seen = new HashSet<>();
        for (EquipmentReference.Group group : EquipmentReference.Group.values()) {
            List<EquipmentReference.Entry> rows = EquipmentReference.browse(WEAPON, group);
            assertFalse(rows.isEmpty());
            for (EquipmentReference.Entry row : rows) {
                assertEquals(group, row.group);
                assertTrue("Duplicate category entry " + row.id, seen.add(row));
            }
        }
        assertEquals(new HashSet<>(EquipmentReference.browse(WEAPON,null)), seen);
        assertEquals(7, EquipmentReference.browse(WEAPON, EquipmentReference.Group.RANGED).size());
    }

    @Test public void uniqueStableIdsAndCompleteDetailsStayAlphabetical() {
        Set<String> ids = new HashSet<>();
        Set<Integer> types = new HashSet<>();
        String previous = "";
        for (EquipmentReference.Entry row : EquipmentReference.all()) {
            assertTrue(row.id.matches("[a-z]+(?:-[a-z]+)*"));
            assertTrue(ids.add(row.id));
            assertTrue(types.add(row.macType));
            assertTrue(previous.compareTo(row.name) <= 0);
            previous = row.name;
            assertFalse(row.summary().isEmpty());
            assertFalse(row.costText().isEmpty());
            assertFalse(row.weightText().isEmpty());
            assertFalse(row.restrictions.isEmpty());
            assertFalse(row.provenance().isEmpty());
            assertTrue(row.weight >= 0);
            assertTrue(row.baseValue >= 0);
            assertTrue(row.bundle > 0);
            if (row.kind == WEAPON) {
                assertTrue(row.smallDamage.matches("[1-3]–[0-9]+"));
                assertTrue(row.largeDamage.matches("[1-3]–[0-9]+"));
                assertTrue(row.hands == 1 || row.hands == 2);
            }
        }
    }

    @Test public void preservesOriginalPrintedWeaponValuesRatherThanModernTables() {
        String[][] checked = {
                {"bardiche","2–8","3–12","2"}, {"bastard-sword","2–8","2–16","2"},
                {"bec-de-corbin","1–8","1–6","2"}, {"bill-guisarme","2–8","1–10","2"},
                {"bo-stick","1–6","1–3","2"}, {"broad-sword","2–8","2–7","1"},
                {"military-fork","1–8","2–8","2"}, {"military-pick","2–5","1–4","1"},
                {"awl-pike","1–6","2–12","1"}, {"spear","1–6","1–8","1"},
                {"long-sword","1–8","1–12","1"}, {"trident","2–7","3–12","1"},
                {"heavy-crossbow","2–5","2–7","2"}, {"light-crossbow","1–4","1–4","2"},
                {"two-handed-sword","1–10","3–18","2"}, {"sling","1–4","1–4","1"}
        };
        for (String[] expected : checked) {
            EquipmentReference.Entry row = entry(expected[0]);
            assertEquals(row.id, expected[1], row.smallDamage);
            assertEquals(row.id, expected[2], row.largeDamage);
            assertEquals(row.id, Integer.parseInt(expected[3]), row.hands);
        }
    }

    @Test public void completeArmorTranscriptionKeepsWeightSeparateFromPrice() {
        String[] ids = {"no-armor","small-shield","leather","padded","studded-leather","ring-mail","scale-mail","chain-mail","splint-mail","banded-mail","plate-mail"};
        int[][] printed = {{0,10,0},{50,9,0},{150,8,12},{100,8,9},{200,7,9},{250,7,9},{400,6,6},{300,5,9},{400,4,6},{350,4,9},{450,3,6}};
        for (int i = 0; i < ids.length; i++) {
            EquipmentReference.Entry row = entry(ids[i]);
            assertEquals(ids[i], printed[i][0], row.printedWeight);
            assertEquals(ids[i], printed[i][1], row.armorClass);
            assertEquals(ids[i], printed[i][2], row.maxMovement);
        }
        assertEquals(450, entry("plate-mail").weight);
        assertEquals(400, entry("plate-mail").baseValue);
        assertEquals(1, entry("small-shield").hands);
        assertTrue(entry("small-shield").summary().contains("improves by 1"));
        assertTrue(entry("small-shield").notes.contains("not a replacement AC of 9"));
    }

    @Test public void ordinaryMacintoshRecordWeightAndValueTranscriptionIsComplete() {
        // Authored facts from private ITEM1.DAX block 53; type 45 from ITEM5.DAX block 33.
        // Columns: item type, weight, stored gp value. No game bytes embedded.
        int[][] sourceFacts = {
                {1,75,5},{2,50,1},{3,125,7},{4,100,6},{5,150,6},{6,15,1},{7,30,1},{8,10,2},
                {9,5,0},{10,60,3},{11,80,8},{12,150,3},{13,75,4},{14,75,6},{15,100,10},
                {16,80,5},{17,150,7},{18,175,9},{19,150,7},{20,50,1},{21,20,0},{22,40,1},
                {23,100,8},{24,125,5},{25,80,10},{26,60,8},{27,80,3},{28,3,0},{29,50,4},
                {30,40,15},{31,50,1},{32,50,3},{33,50,1},{34,100,25},{35,75,10},{36,60,15},
                {37,35,8},{38,250,30},{39,50,4},{40,125,2},{41,80,100},{42,50,75},{43,100,60},
                {44,50,15},{45,80,20},{46,50,12},{47,2,0},{50,150,5},{51,100,4},{52,200,15},
                {53,250,30},{54,400,45},{55,300,75},{56,400,80},{57,350,90},{58,450,400},
                {59,100,15},{73,4,0}
        };
        assertEquals(58, sourceFacts.length);
        Set<Integer> checked = new HashSet<>();
        for (int[] facts : sourceFacts) {
            boolean found = false;
            for (EquipmentReference.Entry row : EquipmentReference.all()) {
                if (row.macType != facts[0]) continue;
                assertTrue(checked.add(row.macType));
                assertEquals(row.id, facts[1], row.weight);
                assertEquals(row.id, facts[2], row.baseValue);
                found = true;
            }
            assertTrue("Missing Macintosh item type " + facts[0], found);
        }
    }

    @Test public void discrepanciesAndPrintedPermissionsStayVisible() {
        for (String id : new String[]{"bo-stick","military-pick","awl-pike","spear","heavy-crossbow","sling","small-shield"})
            assertFalse(id, entry(id).macDifference.isEmpty());
        assertTrue(entry("awl-pike").macDifference.contains("2 hands"));
        assertTrue(entry("military-pick").macDifference.contains("2–7 / 2–8"));
        assertTrue(entry("small-shield").weightText().contains("printed armor table says 50"));
        assertEquals("Fighter, Cleric", entry("hammer").restrictions);
        assertEquals("Fighter", entry("lucern-hammer").restrictions);
        assertEquals("Fighter, Thief", entry("scimitar").restrictions);
        assertEquals("Fighter, Thief", entry("sling").restrictions);
        assertEquals("Fighter, Cleric, Thief", entry("leather").restrictions);
        assertTrue(entry("dart").notes.contains("class prose"));
        assertTrue(EquipmentReference.DISAGREEMENTS.contains("not been verified in play"));
    }

    @Test public void missileBundlesAndStoredZeroAreNotClaimedFree() {
        assertEquals(4, entry("dart").bundle);
        assertEquals(2, entry("javelin").bundle);
        assertEquals(10, entry("arrows").bundle);
        assertEquals(20, entry("quarrels").bundle);
        assertTrue(entry("arrows").weightText().contains("40 for the listed bundle"));
        assertTrue(entry("quarrels").weightText().contains("60 for the listed bundle"));
        for (EquipmentReference.Entry row : EquipmentReference.all()) {
            if (row.baseValue == 0 && row.macType != -1)
                assertTrue(row.id, row.costText().contains("Do not assume free"));
        }
        assertTrue(entry("no-armor").costText().contains("Not applicable"));
        assertTrue(entry("heavy-crossbow").notes.contains("readied quarrels"));
        assertTrue(entry("long-bow").notes.contains("readied arrows"));
    }

    @Test public void catalogAndBrowseResultsAreImmutableAndRejectInvalidGroups() {
        assertThrows(UnsupportedOperationException.class, () -> EquipmentReference.all().clear());
        assertThrows(UnsupportedOperationException.class, () -> EquipmentReference.browse(WEAPON,null).clear());
        assertThrows(IllegalArgumentException.class, () -> EquipmentReference.browse(null,null));
        assertThrows(IllegalArgumentException.class, () -> EquipmentReference.browse(ARMOR, EquipmentReference.Group.MELEE));
        assertThrows(IllegalArgumentException.class, () -> EquipmentReference.browse(AMMUNITION, EquipmentReference.Group.RANGED));
    }
}
