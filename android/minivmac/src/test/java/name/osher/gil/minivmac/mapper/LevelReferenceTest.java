package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import static org.junit.Assert.*;
import static name.osher.gil.minivmac.mapper.LevelReference.CharacterClass.*;
import static name.osher.gil.minivmac.mapper.LevelReference.Race.*;
import static name.osher.gil.minivmac.mapper.LevelReference.Save.*;

public class LevelReferenceTest {
    @Test public void originalXpThresholdsIncludeTheExtraPoint() {
        assertEquals(1501, LevelReference.level(CLERIC, 2).minimumXp);
        assertEquals(2001, LevelReference.level(FIGHTER, 2).minimumXp);
        assertEquals(2501, LevelReference.level(MAGIC_USER, 2).minimumXp);
        assertEquals(1251, LevelReference.level(THIEF, 2).minimumXp);
        assertEquals(27501, LevelReference.level(CLERIC, 6).minimumXp);
        assertEquals(125001, LevelReference.level(FIGHTER, 8).minimumXp);
        assertEquals(40001, LevelReference.level(MAGIC_USER, 6).minimumXp);
        assertEquals(110001, LevelReference.level(THIEF, 9).minimumXp);
        assertEquals(18001, LevelReference.level(FIGHTER, 5).minimumXp);
        assertEquals(42501, LevelReference.level(THIEF, 7).minimumXp);
    }

    @Test public void onlyTheOriginalFourClassesAnd29TrainableLevelsExist() {
        assertEquals(4, LevelReference.CharacterClass.values().length);
        int count = 0;
        for (LevelReference.CharacterClass characterClass : LevelReference.CharacterClass.values()) {
            int previous = -1;
            for (int i = 1; i <= LevelReference.trainingCeiling(characterClass); i++) {
                LevelReference.Level level = LevelReference.level(characterClass, i);
                assertTrue(level.minimumXp > previous); previous = level.minimumXp;
                assertEquals(i, level.number); count++;
                for (LevelReference.Save save : LevelReference.Save.values()) assertTrue(level.savingThrow(save) >= 1 && level.savingThrow(save) <= 20);
            }
        }
        assertEquals(29, count);
    }

    @Test public void printedRaceCeilingsRemainSeparateFromGameTrainingLimits() {
        assertEquals(9, LevelReference.racialCeiling(DWARF, FIGHTER));
        assertEquals(8, LevelReference.gameCeiling(DWARF, FIGHTER));
        assertEquals(7, LevelReference.gameCeiling(ELF, FIGHTER));
        assertEquals(11, LevelReference.racialCeiling(ELF, MAGIC_USER));
        assertEquals(6, LevelReference.gameCeiling(ELF, MAGIC_USER));
        assertEquals(5, LevelReference.gameCeiling(HALF_ELF, CLERIC));
        assertEquals(6, LevelReference.gameCeiling(GNOME, FIGHTER));
        assertEquals(6, LevelReference.gameCeiling(HALFLING, FIGHTER));
        assertEquals(LevelReference.UNAVAILABLE, LevelReference.gameCeiling(DWARF, CLERIC));
        assertEquals(LevelReference.UNAVAILABLE, LevelReference.gameCeiling(ELF, CLERIC));
        for (LevelReference.Race race : LevelReference.Race.values()) {
            assertEquals(LevelReference.UNLIMITED, LevelReference.racialCeiling(race, THIEF));
            assertEquals(9, LevelReference.gameCeiling(race, THIEF));
        }
    }

    @Test public void combatBreakpointsUseTheGameTable() {
        assertEquals(20, LevelReference.level(FIGHTER, 1).thac0);
        assertEquals(19, LevelReference.level(FIGHTER, 2).thac0);
        assertEquals(13, LevelReference.level(FIGHTER, 8).thac0);
        assertEquals(2, LevelReference.level(FIGHTER, 6).attacksPerTwoRounds);
        assertEquals(3, LevelReference.level(FIGHTER, 7).attacksPerTwoRounds);
        assertEquals("3/2", LevelReference.level(FIGHTER, 8).attacksPerRound());
        assertEquals(20, LevelReference.level(CLERIC, 3).thac0);
        assertEquals(18, LevelReference.level(CLERIC, 4).thac0);
        assertEquals(19, LevelReference.level(MAGIC_USER, 6).thac0);
        assertEquals(19, LevelReference.level(THIEF, 5).thac0);
        assertEquals(19, LevelReference.level(THIEF, 8).thac0);
        assertEquals(16, LevelReference.level(THIEF, 9).thac0);
    }

    @Test public void savingThrowCategoriesAreNotTransposed() {
        LevelReference.Level cleric = LevelReference.level(CLERIC, 1);
        assertEquals(10, cleric.savingThrow(DEATH));
        assertEquals(13, cleric.savingThrow(PETRIFICATION));
        assertEquals(14, cleric.savingThrow(WAND));
        assertEquals(16, cleric.savingThrow(BREATH));
        assertEquals(15, cleric.savingThrow(SPELL));
        LevelReference.Level mage = LevelReference.level(MAGIC_USER, 6);
        assertEquals(13, mage.savingThrow(DEATH));
        assertEquals(11, mage.savingThrow(PETRIFICATION));
        assertEquals(9, mage.savingThrow(WAND));
        assertEquals(13, mage.savingThrow(BREATH));
        assertEquals(10, mage.savingThrow(SPELL));
    }

    @Test public void thiefPercentagesAndBackstabFollowTheirOwnProgression() {
        int[][] expected = {{25,20,85}, {29,25,86}, {33,30,87}, {37,35,88}, {42,40,90},
                {47,45,92}, {52,50,94}, {57,55,96}, {62,60,98}};
        for (int i = 0; i < expected.length; i++) {
            LevelReference.ThiefSkills skills = LevelReference.thiefSkills(i + 1);
            assertArrayEquals(expected[i], new int[]{skills.openLocks, skills.findRemoveTraps, skills.climbWalls});
        }
        assertEquals(2, LevelReference.thiefSkills(4).backstabMultiplier);
        assertEquals(3, LevelReference.thiefSkills(5).backstabMultiplier);
        assertEquals(3, LevelReference.thiefSkills(8).backstabMultiplier);
        assertEquals(4, LevelReference.thiefSkills(9).backstabMultiplier);
    }

    @Test public void hitDiceExcludeConstitutionAndUnsupportedLaterLevels() {
        assertEquals("8d10", LevelReference.level(FIGHTER, 8).hitDice());
        assertEquals("6d8", LevelReference.level(CLERIC, 6).hitDice());
        assertEquals("6d4", LevelReference.level(MAGIC_USER, 6).hitDice());
        assertEquals("9d6", LevelReference.level(THIEF, 9).hitDice());
    }

    @Test public void clericTurningUsesTheOriginalAppendixMinimums() {
        assertEquals(8, LevelReference.Undead.values().length);
        assertEquals(1, LevelReference.Undead.WIGHT.minimumClericLevel);
        assertEquals(3, LevelReference.Undead.WRAITH.minimumClericLevel);
        assertEquals(4, LevelReference.Undead.MUMMY.minimumClericLevel);
        assertEquals(5, LevelReference.Undead.SPECTRE.minimumClericLevel);
        assertEquals(6, LevelReference.Undead.VAMPIRE.minimumClericLevel);
        int firstLevel = 0;
        for (LevelReference.Undead undead : LevelReference.Undead.values())
            if (undead.minimumClericLevel <= 1) firstLevel++;
        assertEquals(4, firstLevel);
    }

    @Test public void invalidRequestsCannotSilentlySelectAnotherClassOrLevel() {
        for (LevelReference.CharacterClass characterClass : LevelReference.CharacterClass.values()) {
            assertThrows(IllegalArgumentException.class, () -> LevelReference.level(characterClass, 0));
            assertThrows(IllegalArgumentException.class, () -> LevelReference.level(characterClass, LevelReference.trainingCeiling(characterClass) + 1));
        }
        assertThrows(IllegalArgumentException.class, () -> LevelReference.thiefSkills(0));
        assertThrows(IllegalArgumentException.class, () -> LevelReference.thiefSkills(10));
        assertThrows(IllegalArgumentException.class, () -> LevelReference.level(null, 1));
        assertThrows(IllegalArgumentException.class, () -> LevelReference.racialCeiling(null, FIGHTER));
    }
}
