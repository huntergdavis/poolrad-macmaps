/*
 * Grouping the opposition by name.
 *
 * The game gives every combatant a character record and names monsters there,
 * so ten orcs are ten records all reading "ORC". These checks are about the
 * grouping and about what it refuses: a name that is not a name, and more
 * kinds than the packet can hold, both report nothing rather than something
 * partial that would disagree with the squares beside it.
 */
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stddef.h>
#include "POOLRAD_COMBAT.h"

static int failures;
static unsigned char ram[0x20000];
static unsigned char out[1 + POOLRAD_COMBAT_FOES_MAX * (POOLRAD_COMBAT_FOE_NAME + 1)];

static void expect(int ok, const char *what) {
    if (!ok) { printf("FAIL %s\n", what); failures++; } else printf("ok %s\n", what);
}

/* Lay a name at a record address and return that address. */
static uint32_t place(uint32_t at, const char *name) {
    memset(ram + at, 0, POOLRAD_COMBAT_FOE_NAME);
    memcpy(ram + at, name, strlen(name));
    return at;
}

static const unsigned char *entry(unsigned index) {
    return out + 1 + index * (POOLRAD_COMBAT_FOE_NAME + 1);
}

/* The probe itself is exercised by test-combat-probe; referenced here only so
 * that including the header does not trip the unused-function warning. */
static void unused(void) { (void) poolrad_combat_probe; }

int main(void) {
    unsigned char kinds[8];
    unused();
    uint32_t records[8];

    /* Six orcs and two kobolds, all standing. */
    for (int i = 0; i < 6; i++) { kinds[i] = POOLRAD_COMBAT_KIND_OTHER; records[i] = place(0x1000 + i * 32, "ORC"); }
    for (int i = 6; i < 8; i++) { kinds[i] = POOLRAD_COMBAT_KIND_OTHER; records[i] = place(0x1000 + i * 32, "KOBOLD"); }
    poolrad_combat_foes(ram, kinds, records, 8, out);
    expect(out[0] == 2, "two kinds of monster are two entries");
    expect(memcmp(entry(0), "ORC", 3) == 0 && entry(0)[POOLRAD_COMBAT_FOE_NAME] == 6, "six orcs");
    expect(memcmp(entry(1), "KOBOLD", 6) == 0 && entry(1)[POOLRAD_COMBAT_FOE_NAME] == 2, "two kobolds");

    /* The party is not the opposition. */
    kinds[0] = POOLRAD_COMBAT_KIND_PARTY;
    poolrad_combat_foes(ram, kinds, records, 8, out);
    expect(entry(0)[POOLRAD_COMBAT_FOE_NAME] == 5, "a party member is not counted as a foe");
    kinds[0] = POOLRAD_COMBAT_KIND_OTHER;

    /* Nor is anyone the game has stopped drawing. */
    kinds[1] = POOLRAD_COMBAT_KIND_DEAD;
    kinds[2] = POOLRAD_COMBAT_KIND_FALLEN;
    poolrad_combat_foes(ram, kinds, records, 8, out);
    expect(entry(0)[POOLRAD_COMBAT_FOE_NAME] == 4, "the dead and the fallen are off the field");
    kinds[1] = kinds[2] = POOLRAD_COMBAT_KIND_OTHER;

    /* Nothing to fight is no entries rather than an empty one. */
    for (int i = 0; i < 8; i++) kinds[i] = POOLRAD_COMBAT_KIND_PARTY;
    poolrad_combat_foes(ram, kinds, records, 8, out);
    expect(out[0] == 0, "a party fighting nobody reports no kinds");
    for (int i = 0; i < 8; i++) kinds[i] = POOLRAD_COMBAT_KIND_OTHER;

    /* A name that is not a name abandons the whole grouping. */
    ram[records[3]] = 0x01;
    poolrad_combat_foes(ram, kinds, records, 8, out);
    expect(out[0] == 0, "a control byte in a name reports nothing at all");
    place(records[3], "ORC");

    /* An unterminated name likewise. */
    memset(ram + records[3], 'X', POOLRAD_COMBAT_FOE_NAME);
    poolrad_combat_foes(ram, kinds, records, 8, out);
    expect(out[0] == 0, "a name filling its whole field reports nothing at all");
    place(records[3], "ORC");

    /* An empty name likewise. */
    memset(ram + records[3], 0, POOLRAD_COMBAT_FOE_NAME);
    poolrad_combat_foes(ram, kinds, records, 8, out);
    expect(out[0] == 0, "an empty name reports nothing at all");
    place(records[3], "ORC");

    /* More kinds than the packet holds: none rather than some, so a partial
     * list is never mistaken for the whole opposition. */
    {
        unsigned char many[16];
        uint32_t manyRecords[16];
        char name[8];
        for (int i = 0; i < 16; i++) {
            many[i] = POOLRAD_COMBAT_KIND_OTHER;
            snprintf(name, sizeof name, "MON%d", i);
            manyRecords[i] = place(0x4000 + i * 32, name);
        }
        poolrad_combat_foes(ram, many, manyRecords, 16, out);
        expect(out[0] == 0, "more kinds than the packet holds reports none");
        poolrad_combat_foes(ram, many, manyRecords, POOLRAD_COMBAT_FOES_MAX, out);
        expect(out[0] == POOLRAD_COMBAT_FOES_MAX, "exactly as many as it holds is fine");
    }

    if (failures) { printf("%d foe-grouping checks failed\n", failures); return 1; }
    printf("foe grouping holds\n");
    return 0;
}
