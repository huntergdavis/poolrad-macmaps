/*
 * The character record's tail, as structure rather than as a list of numbers.
 *
 * Every offset the probe uses was found independently, by reading the game's
 * own code and diffing captures. Aligning them against the documented DOS
 * record (see docs/RECORD_ALIGNMENT.md) showed they are not independent at all:
 * they are one DOS field after another at a constant shift. These checks pin
 * that structure, so an offset cannot drift without the alignment being
 * reconsidered rather than silently abandoned.
 */
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stddef.h>
#include "POOLRAD_PARTY.h"

static int failures;

static void expect(int ok, const char *what) {
    if (!ok) { printf("FAIL %s\n", what); failures++; }
    else printf("ok %s\n", what);
}

int main(void) {
    /* The head is the DOS record unshifted: the name is sixteen bytes, the
     * memorised spells are the twenty-one at 0x17, the class is at 0x2f and
     * the maximum hit points at 0x32. */
    expect(POOLRAD_PARTY_SPELL_OFFSET == 0x17, "memorised spells sit where DOS puts them");
    expect(POOLRAD_PARTY_SPELL_SLOTS == 21, "and there are as many of them as DOS has");
    expect(POOLRAD_PARTY_CLASS_OFFSET == 0x2f, "class is unshifted");
    expect(POOLRAD_PARTY_MAX_HP_OFFSET == 0x32, "maximum hit points are unshifted");

    /* The tail is DOS + 12, field by field. Encumbrance is two bytes and the
     * eight that follow are what DOS calls heap and the Macintosh spends on
     * two handles. */
    expect(POOLRAD_PARTY_ENCUMBRANCE_OFFSET + 2 == POOLRAD_PARTY_NEXT_OFFSET,
           "the handles begin immediately after the two encumbrance bytes");
    expect(POOLRAD_PARTY_NEXT_OFFSET + 8 == POOLRAD_PARTY_CONDITION_OFFSET,
           "two four-byte handles fill DOS's eight heap bytes");

    /* Condition and quick are the first and fourth bytes of one four-byte
     * combat-status field; +0x119 and +0x11a are the other two. */
    expect(POOLRAD_PARTY_CONDITION_OFFSET + 3 == POOLRAD_PARTY_QUICK_OFFSET,
           "quick is the fourth byte of the combat-status field condition starts");
    expect(POOLRAD_PARTY_CONDITION_OFFSET + 5 == POOLRAD_PARTY_AC_OFFSET,
           "armour class follows combat status and the to-hit byte");

    /* Current hit points and movement are adjacent in both records, and the
     * record ends one byte after movement, padded to an even length. */
    expect(POOLRAD_PARTY_CURRENT_HP_OFFSET + 1 == POOLRAD_PARTY_MOVEMENT_OFFSET,
           "movement follows current hit points, as in DOS");
    expect(POOLRAD_PARTY_MOVEMENT_OFFSET + 1 < POOLRAD_PARTY_RECORD_SIZE,
           "the record has room for the movement byte");
    expect(POOLRAD_PARTY_RECORD_SIZE % 2 == 0,
           "the record is an even length, as a 68k port pads it");
    expect(POOLRAD_PARTY_RECORD_SIZE == 285 + 16 + 1,
           "302 is DOS's 285 plus the tail's sixteen plus one byte of padding");

    /* Everything the probe reads has to be inside the record. */
    expect(POOLRAD_PARTY_READIED_OFFSET < POOLRAD_PARTY_RECORD_SIZE, "readied items are inside the record");
    expect(POOLRAD_PARTY_SPELL_OFFSET + POOLRAD_PARTY_SPELL_SLOTS < POOLRAD_PARTY_RECORD_SIZE,
           "the spell array is inside the record");
    expect(POOLRAD_PARTY_LEVEL_OFFSET < POOLRAD_PARTY_RECORD_SIZE, "the level is inside the record");
    expect(POOLRAD_PARTY_EXPERIENCE_OFFSET + 4 <= POOLRAD_PARTY_RECORD_SIZE,
           "experience is inside the record");

    if (failures) { printf("%d alignment checks failed\n", failures); return 1; }
    printf("record alignment holds\n");
    return 0;
}
