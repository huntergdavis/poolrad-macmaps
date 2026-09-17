/* Synthetic RAM only: no ROM, game bytes, or copyrighted battle data. */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "../android/minivmac/src/main/jni/src/POOLRAD_COMBAT.h"

/* The combat reader includes the party reader for its record layout; this test
 * exercises only the former, so keep the compiler quiet about the latter. */
static void poolrad_unused(void) { (void) poolrad_party_probe; (void) poolrad_walk_probe;
    (void) poolrad_display_probe; (void) poolrad_walk_observe; (void) poolrad_walk_reset; }

static unsigned char ram[1 << 20], out[POOLRAD_COMBAT_SIZE];
static uint32_t g_a5, g_table, g_records[POOLRAD_COMBAT_MAX];
static void put32(size_t at, uint32_t v) {
    ram[at] = v >> 24; ram[at+1] = v >> 16; ram[at+2] = v >> 8; ram[at+3] = v;
}
/* One relocatable heap block holding a character record, shaped exactly as the
 * roster reader demands: 24-bit tag 8, logical 302 plus header and correction. */
/* Distinct squares for every combatant, always inside the grid ceiling. */
static unsigned char expected_x(unsigned i) { return (unsigned char)((3 + i) % 64); }
static unsigned char expected_y(unsigned i) { return (unsigned char)((20 + i * 3) % 64); }
static void record(uint32_t at, unsigned slot, uint32_t next_handle) {
    /* 302 + 8 is not a multiple of four, so the allocator's own low-nibble
     * size correction of two is part of a legitimate block. */
    put32(at - 8, 0x82000000u | (POOLRAD_PARTY_RECORD_SIZE + 8 + 2));
    ram[at + POOLRAD_PARTY_SLOT_OFFSET] = (unsigned char) slot;
    /* Alive. Zero here means killed, and the killed are left out of the
     * overview: they keep their place in the chain and their last square, and
     * drawing them put a marker where an enemy no longer was. */
    ram[at + POOLRAD_PARTY_CURRENT_HP_OFFSET] = 5;
    put32(at + POOLRAD_PARTY_NEXT_OFFSET, next_handle);
}
/* `party` combatants in slots 0..party-1, then `others` monsters in slot 8. */
static void fixture(unsigned party, unsigned others) {
    unsigned total = party + others, i;
    memset(ram, 0, sizeof ram);
    const char name[] = "Pool of Radiance v1.1";
    ram[0x910] = sizeof(name) - 1; memcpy(ram + 0x911, name, sizeof(name) - 1);
    g_a5 = 0x9000; put32(0x904, g_a5);
    g_table = g_a5 - POOLRAD_COMBAT_TABLE_BACK;
    ram[g_a5 - POOLRAD_COMBAT_COUNT_BACK] = (unsigned char) total;
    for (i = 0; i < total; i++) {
        unsigned char *e = ram + g_table + i * POOLRAD_COMBAT_STRIDE;
        e[0] = (unsigned char) i; e[1] = i == 0 ? 0 : 1;
        e[2] = expected_x(i); e[3] = expected_y(i);
    }
    /* The sentinel one past the end is part of the contract. */
    memset(ram + g_table + total * POOLRAD_COMBAT_STRIDE, 0, POOLRAD_COMBAT_STRIDE);
    for (i = 0; i < total; i++) {
        uint32_t rec = 0x20000 + i * 0x200, handle = 0x10000 + i * 8;
        g_records[i] = rec;
        put32(handle, rec);
        record(rec, i < party ? i : 8, i + 1 < total ? 0x10000 + (i + 1) * 8 : 0);
    }
    put32(g_a5 - POOLRAD_PARTY_HEAD_BACK, 0x10000);
}
static void unavailable(void) {
    assert(memcmp(out, "PRC1", 4) == 0);
    assert(out[POOLRAD_COMBAT_STATUS_OUT] == POOLRAD_COMBAT_UNAVAILABLE);
    assert(out[POOLRAD_COMBAT_COUNT_OUT] == 0);
    for (int i = POOLRAD_COMBAT_ENTRY_OUT; i < POOLRAD_COMBAT_SIZE; i++) assert(out[i] == 0);
}

int main(void) {
    poolrad_unused();
    /* Six party members and ten monsters, the shape of a real battle. */
    fixture(6, 10);
    unsigned char original[sizeof(ram)]; memcpy(original, ram, sizeof ram);
    assert(poolrad_combat_probe(ram, sizeof ram, out));
    assert(out[POOLRAD_COMBAT_STATUS_OUT] == POOLRAD_COMBAT_PRESENT);
    assert(out[POOLRAD_COMBAT_COUNT_OUT] == 16);
    for (unsigned i = 0; i < 16; i++) {
        const unsigned char *row = out + POOLRAD_COMBAT_ENTRY_OUT + i * 4;
        assert(row[0] == (i < 6 ? POOLRAD_COMBAT_KIND_PARTY : POOLRAD_COMBAT_KIND_OTHER));
        assert(row[1] == expected_x(i) && row[2] == expected_y(i) && row[3] == 0);
    }
    assert(memcmp(original, ram, sizeof ram) == 0); // Never writes to guest RAM.

    /* No battle is unavailable, not an empty battlefield. */
    fixture(6, 10);
    ram[g_a5 - POOLRAD_COMBAT_COUNT_BACK] = 0;
    assert(poolrad_combat_probe(ram, sizeof ram, out)); unavailable();

    /* A count that disagrees with the roster is refused in both directions. */
    for (int delta = -2; delta <= 2; delta++) {
        if (delta == 0) continue;
        fixture(6, 10);
        ram[g_a5 - POOLRAD_COMBAT_COUNT_BACK] = (unsigned char)(16 + delta);
        assert(poolrad_combat_probe(ram, sizeof ram, out));
        unavailable();
    }

    /* Every entry states its own index; one that lies rejects the table. */
    for (unsigned i = 0; i < 16; i++) {
        fixture(6, 10);
        ram[g_table + i * POOLRAD_COMBAT_STRIDE] = (unsigned char)(i + 1);
        assert(poolrad_combat_probe(ram, sizeof ram, out)); unavailable();
    }

    /* The flag byte is carried, not interpreted, but only 0 and 1 are known. */
    for (unsigned value = 2; value < 256; value++) {
        fixture(6, 10);
        ram[g_table + 4 * POOLRAD_COMBAT_STRIDE + 1] = (unsigned char) value;
        assert(poolrad_combat_probe(ram, sizeof ram, out)); unavailable();
    }

    /* Coordinates past the grid ceiling are not squares. */
    for (unsigned axis = 2; axis <= 3; axis++) {
        for (unsigned value = POOLRAD_COMBAT_MAX_COORDINATE + 1; value < 256; value += 17) {
            fixture(6, 10);
            ram[g_table + 9 * POOLRAD_COMBAT_STRIDE + axis] = (unsigned char) value;
            assert(poolrad_combat_probe(ram, sizeof ram, out)); unavailable();
        }
        /* The ceiling itself is a legal square. */
        fixture(6, 10);
        ram[g_table + 9 * POOLRAD_COMBAT_STRIDE + axis] = POOLRAD_COMBAT_MAX_COORDINATE;
        assert(poolrad_combat_probe(ram, sizeof ram, out));
        assert(out[POOLRAD_COMBAT_STATUS_OUT] == POOLRAD_COMBAT_PRESENT);
    }

    /* A missing sentinel means the table did not end where the count said. */
    for (unsigned axis = 2; axis <= 3; axis++) {
        fixture(6, 10);
        ram[g_table + 16 * POOLRAD_COMBAT_STRIDE + axis] = 1;
        assert(poolrad_combat_probe(ram, sizeof ram, out)); unavailable();
    }

    /* A broken roster chain takes the whole sample with it. */
    fixture(6, 10);
    put32(g_a5 - POOLRAD_PARTY_HEAD_BACK, 0);
    assert(poolrad_combat_probe(ram, sizeof ram, out)); unavailable();
    fixture(6, 10);
    put32(g_records[7] + POOLRAD_PARTY_NEXT_OFFSET, 0x10000); // a cycle
    assert(poolrad_combat_probe(ram, sizeof ram, out)); unavailable();
    fixture(6, 10);
    put32(g_records[3] - 8, 0x70000000); // wrong heap tag
    assert(poolrad_combat_probe(ram, sizeof ram, out)); unavailable();
    fixture(6, 10);
    ram[g_records[2] + POOLRAD_PARTY_SLOT_OFFSET] = 0xff; // unassigned slot
    assert(poolrad_combat_probe(ram, sizeof ram, out)); unavailable();

    /* An unpadded record is a record. The combat reader validates the same
     * heap blocks as the party reader, and shared the same wrong belief that a
     * block size must be a multiple of four; Hunter's tablet allocates these
     * with no size correction at all, so the whole battle went with it. */
    fixture(6, 10);
    for (unsigned i = 0; i < 16; i++)
        put32(g_records[i] - 8, 0x80000000u | (POOLRAD_PARTY_RECORD_SIZE + 8));
    assert(poolrad_combat_probe(ram, sizeof ram, out));
    assert(out[POOLRAD_COMBAT_COUNT_OUT] == 16);
    // An odd physical size is still not a block.
    fixture(6, 10);
    put32(g_records[3] - 8, 0x81000000u | (POOLRAD_PARTY_RECORD_SIZE + 9));
    assert(poolrad_combat_probe(ram, sizeof ram, out)); unavailable();

    /* A monster-only or party-only battle is still a battle. */
    fixture(6, 0);
    assert(poolrad_combat_probe(ram, sizeof ram, out));
    assert(out[POOLRAD_COMBAT_COUNT_OUT] == 6);
    for (unsigned i = 0; i < 6; i++)
        assert(out[POOLRAD_COMBAT_ENTRY_OUT + i * 4] == POOLRAD_COMBAT_KIND_PARTY);

    /* The largest roster the reader accepts fits exactly. */
    fixture(POOLRAD_PARTY_MAX_MEMBERS, POOLRAD_COMBAT_MAX - POOLRAD_PARTY_MAX_MEMBERS);
    assert(poolrad_combat_probe(ram, sizeof ram, out));
    assert(out[POOLRAD_COMBAT_COUNT_OUT] == POOLRAD_COMBAT_MAX);
    fixture(6, 10);
    ram[g_a5 - POOLRAD_COMBAT_COUNT_BACK] = POOLRAD_COMBAT_MAX + 1;
    assert(poolrad_combat_probe(ram, sizeof ram, out)); unavailable();

    /* Another application frontmost is not this game's battle. */
    fixture(6, 10);
    ram[0x910] = 6; memcpy(ram + 0x911, "Finder", 6);
    assert(!poolrad_combat_probe(ram, sizeof ram, out));
    assert(!poolrad_combat_probe(NULL, 0, out));
    fixture(6, 10);
    assert(!poolrad_combat_probe(ram, sizeof ram, NULL));
    assert(!poolrad_combat_probe(ram, 0x92f, out));

    /* The dead keep their place in the chain and the table, and the game's own
     * Combat View stops drawing them. The overview must do the same, or a
     * marker sits where an enemy used to be -- which is what a player sees as
     * a square on one of their own people. */
    fixture(6, 10);
    ram[g_records[9] + POOLRAD_PARTY_CONDITION_OFFSET] = 6;    /* an orc dies */
    assert(poolrad_combat_probe(ram, sizeof ram, out));
    assert(out[POOLRAD_COMBAT_STATUS_OUT] == POOLRAD_COMBAT_PRESENT);
    assert(out[POOLRAD_COMBAT_COUNT_OUT] == 15);
    /* Everyone else keeps their own square: the pairing is by position in the
     * chain, so leaving one out must not shift anybody's coordinates. */
    {
        unsigned drawn = 0;
        for (unsigned i = 0; i < 16; i++) {
            if (i == 9) continue;
            const unsigned char *row = out + POOLRAD_COMBAT_ENTRY_OUT + drawn * 4;
            assert(row[0] == (i < 6 ? POOLRAD_COMBAT_KIND_PARTY : POOLRAD_COMBAT_KIND_OTHER));
            assert(row[1] == expected_x(i));
            assert(row[2] == expected_y(i));
            drawn++;
        }
        assert(drawn == 15);
    }

    /* One of your own, down where they fell, is kept and marked. These are the
     * squares a player walks to in order to bandage somebody, so losing them
     * would be worse than the bug that started this. */
    for (unsigned condition = 4; condition <= 7; condition++) {
        fixture(6, 10);
        ram[g_records[2] + POOLRAD_PARTY_CONDITION_OFFSET] = (unsigned char) condition;
        assert(poolrad_combat_probe(ram, sizeof ram, out));
        assert(out[POOLRAD_COMBAT_COUNT_OUT] == 16);
        const unsigned char *row = out + POOLRAD_COMBAT_ENTRY_OUT + 2 * 4;
        assert(row[0] == POOLRAD_COMBAT_KIND_FALLEN);
        assert(row[1] == expected_x(2) && row[2] == expected_y(2));
    }

    /* A monster in the same state is still left out: the game stops drawing it. */
    for (unsigned condition = 4; condition <= 7; condition++) {
        fixture(6, 10);
        ram[g_records[9] + POOLRAD_PARTY_CONDITION_OFFSET] = (unsigned char) condition;
        assert(poolrad_combat_probe(ram, sizeof ram, out));
        assert(out[POOLRAD_COMBAT_COUNT_OUT] == 15);
    }

    /* Off the field entirely is nobody, yours or theirs. */
    for (unsigned condition = 2; condition <= 8; condition += 6) {
        fixture(6, 10);
        ram[g_records[2] + POOLRAD_PARTY_CONDITION_OFFSET] = (unsigned char) condition;
        ram[g_records[9] + POOLRAD_PARTY_CONDITION_OFFSET] = (unsigned char) condition;
        assert(poolrad_combat_probe(ram, sizeof ram, out));
        assert(out[POOLRAD_COMBAT_COUNT_OUT] == 14);
    }

    /* Standing conditions change nothing. */
    for (unsigned condition = 0; condition <= 3; condition++) {
        if (condition == 2) continue;
        fixture(6, 10);
        for (unsigned i = 0; i < 16; i++)
            ram[g_records[i] + POOLRAD_PARTY_CONDITION_OFFSET] = (unsigned char) condition;
        assert(poolrad_combat_probe(ram, sizeof ram, out));
        assert(out[POOLRAD_COMBAT_COUNT_OUT] == 16);
        assert(out[POOLRAD_COMBAT_ENTRY_OUT] == POOLRAD_COMBAT_KIND_PARTY);
    }

    /* With no readable condition it falls back on hit points, as it used to. */
    for (unsigned condition = 9; condition < 256; condition++) {
        fixture(6, 10);
        ram[g_records[2] + POOLRAD_PARTY_CONDITION_OFFSET] = (unsigned char) condition;
        ram[g_records[2] + POOLRAD_PARTY_CURRENT_HP_OFFSET] = 0;
        assert(poolrad_combat_probe(ram, sizeof ram, out));
        assert(out[POOLRAD_COMBAT_COUNT_OUT] == 16);
        assert(out[POOLRAD_COMBAT_ENTRY_OUT + 2 * 4] == POOLRAD_COMBAT_KIND_FALLEN);
    }

    /* Nothing but the dead is not a battle. */
    fixture(6, 10);
    for (unsigned i = 0; i < 16; i++)
        ram[g_records[i] + POOLRAD_PARTY_CONDITION_OFFSET] = 8;   /* all gone */
    assert(poolrad_combat_probe(ram, sizeof ram, out));
    unavailable();

    puts("Combat probe: roster-checked grid table, index and sentinel guards, bounds passed.");
    return 0;
}
