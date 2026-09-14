/* Synthetic party fixture tests; optional read-only private-capture replay.
 * No ROM, disk, game map, character record or code bytes are embedded here.
 */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include "../android/minivmac/src/main/jni/src/POOLRAD_PARTY.h"

static unsigned char ram[65536], output[POOLRAD_PARTY_SIZE];
static uint32_t fixture_a5, fixture_handles, fixture_records;

static void put32(uint32_t address, uint32_t value) {
    ram[address] = value >> 24; ram[address + 1] = value >> 16;
    ram[address + 2] = value >> 8; ram[address + 3] = value;
}

static uint32_t member_handle(unsigned member) { return fixture_handles + member * 4; }
static uint32_t member_record(unsigned member) { return fixture_records + member * 0x140; }

static void fixture(uint32_t a5, uint32_t handles, uint32_t records, unsigned count) {
    static const char name[] = "Pool of Radiance v1.1";
    uint32_t globals = a5 - POOLRAD_GLOBALS_BACK;
    fixture_a5 = a5; fixture_handles = handles; fixture_records = records;
    memset(ram, 0, sizeof(ram));
    ram[0x910] = sizeof(name) - 1; memcpy(ram + 0x911, name, sizeof(name) - 1);
    put32(0x904, a5);
    put32(globals, 0x1000); put32(0x1000, 0x7000); put32(0x6ff8, 0x80000408);
    ram[0x7000] = 0x12; ram[globals + 82] = 15; ram[globals + 83] = 1; ram[globals + 84] = 6;
    put32(a5 - POOLRAD_PARTY_HEAD_BACK, count ? handles : 0);
    for (unsigned i = 0; i < count; i++) {
        uint32_t record = member_record(i);
        put32(member_handle(i), record); put32(record - 8, 0x82000138);
        snprintf((char *) ram + record, 16, "Hero %u", i + 1);
        ram[record + POOLRAD_PARTY_CURRENT_HP_OFFSET] = 10 + i;
        ram[record + POOLRAD_PARTY_MAX_HP_OFFSET] = 10 + i;
        ram[record + POOLRAD_PARTY_SLOT_OFFSET] = i % POOLRAD_PARTY_MAX_MEMBERS;
        ram[record + POOLRAD_PARTY_AC_OFFSET] = 60 + i;
        ram[record + POOLRAD_PARTY_CLASS_OFFSET] = i % (POOLRAD_PARTY_LAST_CLASS + 1);
        put32(record + POOLRAD_PARTY_NEXT_OFFSET, i + 1 < count ? member_handle(i + 1) : 0);
    }
}

static void unavailable(void) {
    memset(output, 0xff, sizeof(output));
    assert(!poolrad_party_probe(ram, sizeof(ram), output));
    for (unsigned i = 0; i < sizeof(output); i++) assert(output[i] == 0);
}

static void effects_fixture(unsigned count) {
    fixture(0xe000, 0x2000, 0x3000, 1);
    put32(member_record(0) + POOLRAD_PARTY_EFFECT_HEAD_OFFSET, count ? 0x5000 : 0);
    for (unsigned i = 0; i < count; i++) {
        uint32_t node = 0x8000 + i * 32;
        put32(0x5000 + i * 4, node); put32(node - 8, 0x82000014);
        ram[node] = i ? 31 : 55;
        put32(node + 6, i + 1 < count ? 0x5000 + (i + 1) * 4 : 0);
    }
}

static void effects_unknown(void) {
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(output[24] == 10 && output[25] == 10);
    assert(output[POOLRAD_PARTY_BASE_SIZE + 1] == POOLRAD_PARTY_UNKNOWN_EFFECTS);
}

/* Writes a spell-table entry: byte +1 of the 16-byte record is its level. */
static void spell_level(unsigned id, unsigned level) {
    ram[fixture_a5 - POOLRAD_PARTY_SPELL_TABLE_BACK + id * POOLRAD_PARTY_SPELL_ENTRY + 1] =
            (unsigned char) level;
}
static const unsigned char *spells_of(unsigned member) {
    return output + POOLRAD_PARTY_CONDITION_SIZE + member * POOLRAD_PARTY_SPELL_STRIDE;
}
static void spells_unavailable(unsigned member) {
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(spells_of(member)[0] == POOLRAD_PARTY_SPELLS_UNAVAILABLE);
    for (unsigned i = 1; i < POOLRAD_PARTY_SPELL_STRIDE; i++) assert(spells_of(member)[i] == 0);
    assert(output[24] == 10 && output[25] == 10); /* Health survives missing spells. */
}

static void combat_fixture(unsigned members, unsigned combatants) {
    /* Keep the largest fixture clear of A5 globals and the geometry block. */
    fixture(0x6000, 0x2000, 0x8000, members + combatants);
    for (unsigned i = members; i < members + combatants; i++) {
        uint32_t record = member_record(i);
        ram[record + POOLRAD_PARTY_SLOT_OFFSET] = 8;
        snprintf((char *) ram + record, 16, "Enemy %u", i - members + 1);
    }
}

static void tests(void) {
    fixture(0xe000, 0x2000, 0x3000, 6);
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(memcmp(output, "PRP4", 4) == 0 && output[4] == 6);
    for (unsigned i = 0; i < 6; i++) {
        unsigned row = 8 + i * POOLRAD_PARTY_ROW_SIZE;
        assert(memcmp(output + row, ram + member_record(i), 6) == 0);
        assert(output[row + 16] == 10 + i && output[row + 17] == 10 + i);
        assert(output[row + 18] == (unsigned char) (0 - i) && output[row + 19] == i);
    }
    for (unsigned i = 8 + 6 * POOLRAD_PARTY_ROW_SIZE; i < sizeof(output); i++) assert(output[i] == 0);

    // Same logical record under every representable size correction for this
    // four-byte-aligned MacII heap; no game data is copied into these fixtures.
    for (unsigned correction = 0; correction < 16; correction++) {
        fixture(0xe000, 0x2000, 0x3000, 1);
        unsigned physical = POOLRAD_PARTY_RECORD_SIZE + 8 + correction;
        put32(member_record(0) - 8, ((0x80u | correction) << 24) | physical);
        if ((physical & 3) == 0) {
            assert(poolrad_party_probe(ram, sizeof(ram), output) && output[24] == 10);
            // Contents of allocator padding must not affect names/stats.
            memset(ram + member_record(0) + POOLRAD_PARTY_RECORD_SIZE, 0xa5, correction);
            assert(poolrad_party_probe(ram, sizeof(ram), output) && output[24] == 10);
        } else unavailable();
    }
    fixture(0xe000, 0x2000, 0x3000, 6);
    put32(member_record(0) - 8, 0x8600013c); // Fresh SampleParty's valid allocation shape.
    assert(poolrad_party_probe(ram, sizeof(ram), output) && output[4] == 6);
    put32(member_record(0) - 8, 0x8200013c); unavailable(); // Physical growth without correction.
    fixture(0xe000, 0x2000, 0x3000, 1);
    put32(member_record(0) - 8, 0x86000138); unavailable(); // Correction exceeds logical record.
    for (unsigned tag = 0; tag < 16; tag++) {
        if (tag == 8) continue;
        fixture(0xe000, 0x2000, 0x3000, 1);
        put32(member_record(0) - 8, (tag << 28) | 0x02000138);
        unavailable(); // Free/nonrelocatable/unsupported-reserved header flags.
    }

    // Exercise every source-byte value: actual Mac encoding, not tabletop caps.
    fixture(0xe000, 0x2000, 0x3000, 1);
    for (unsigned value = 0; value <= 255; value++) {
        ram[member_record(0) + POOLRAD_PARTY_AC_OFFSET] = value;
        ram[member_record(0) + POOLRAD_PARTY_CLASS_OFFSET] = value;
        assert(poolrad_party_probe(ram, sizeof(ram), output));
        assert(output[26] == (value <= 187 ? (unsigned char) (60 - value) : POOLRAD_PARTY_UNKNOWN_AC));
        assert(output[27] == (value <= 17 ? value : POOLRAD_PARTY_UNKNOWN_CLASS));
        assert(output[24] == 10 && output[25] == 10); // Unknown details retain valid HP.
    }

    fixture(0xe000, 0x2000, 0x3000, 1);
    for (unsigned value = 0; value <= 255; value++) {
        ram[member_record(0) + POOLRAD_PARTY_CONDITION_OFFSET] = value;
        assert(poolrad_party_probe(ram, sizeof(ram), output));
        assert(output[POOLRAD_PARTY_BASE_SIZE] == (value <= 8 ? value : 255));
        assert(output[24] == 10); // Status is independent of current health.
    }
    effects_fixture(1);
    for (unsigned id = 0; id <= 255; id++) {
        ram[0x8000] = id;
        assert(poolrad_party_probe(ram, sizeof(ram), output));
        assert(output[POOLRAD_PARTY_BASE_SIZE + 1] == (id == 55 ? 1 : id == 31 || id == 51 || id == 52 || id == 53 ? 2 : 0));
    }
    effects_fixture(2);
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(output[POOLRAD_PARTY_BASE_SIZE + 1] == 3);
    effects_fixture(64);
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(output[POOLRAD_PARTY_BASE_SIZE + 1] == 3);
    effects_fixture(65); effects_unknown();
    effects_fixture(2); put32(0x8026, 0x5000); effects_unknown(); // Handle cycle.
    effects_fixture(2); put32(0x5004, 0x8000); effects_unknown(); // Record alias.
    effects_fixture(2); put32(0x5004, 0xffff); effects_unknown(); // Bounded bad pointer.
    effects_fixture(2); put32(0x8026, 0xffffff); effects_unknown(); // No partial poison claim.
    effects_fixture(1); put32(0x7ff8, 0x82000018); effects_unknown(); // Wrong logical allocation.
    for (unsigned pad = 0; pad < 16; pad++) {
        effects_fixture(1);
        put32(0x7ff8, ((0x80u | pad) << 24) | (18 + pad));
        assert(poolrad_party_probe(ram, sizeof(ram), output));
        assert(output[POOLRAD_PARTY_BASE_SIZE + 1] == ((18 + pad) % 4 ? 255 : 1));
    }

    /* Memorized-spell readiness: empty, ready and awaiting-rest slots. */
    fixture(0xe000, 0x2000, 0x3000, 1);
    for (unsigned id = 1; id < 128; id++) spell_level(id, (id % 3) + 1);
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    for (unsigned i = 0; i < POOLRAD_PARTY_SPELL_STRIDE; i++) assert(spells_of(0)[i] == 0);

    /* Every slot filled, alternating ready and awaiting, across all three levels. */
    fixture(0xe000, 0x2000, 0x3000, 1);
    for (unsigned id = 1; id < 128; id++) spell_level(id, (id % 3) + 1);
    for (unsigned slot = 0; slot < POOLRAD_PARTY_SPELL_SLOTS; slot++) {
        unsigned id = slot + 1;
        ram[member_record(0) + POOLRAD_PARTY_SPELL_OFFSET + slot] =
                (unsigned char) ((slot % 2) ? id | 0x80 : id);
    }
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    {
        unsigned ready[3] = {0}, waiting[3] = {0};
        for (unsigned slot = 0; slot < POOLRAD_PARTY_SPELL_SLOTS; slot++) {
            unsigned id = slot + 1, level = (id % 3) + 1;
            if (slot % 2) waiting[level - 1]++; else ready[level - 1]++;
        }
        assert(spells_of(0)[0] == 0);
        for (unsigned level = 0; level < 3; level++) {
            assert(spells_of(0)[1 + level] == ready[level]);
            assert(spells_of(0)[4 + level] == waiting[level]);
        }
        assert(spells_of(0)[7] == 0);
    }

    /* Bit 7 alone distinguishes awaiting rest from ready for the same spell. */
    for (unsigned id = 1; id < 128; id++) {
        fixture(0xe000, 0x2000, 0x3000, 1);
        for (unsigned n = 1; n < 128; n++) spell_level(n, ((n + 1) % 3) + 1);
        unsigned level = ((id + 1) % 3) + 1;
        ram[member_record(0) + POOLRAD_PARTY_SPELL_OFFSET] = (unsigned char) id;
        assert(poolrad_party_probe(ram, sizeof(ram), output));
        assert(spells_of(0)[1 + level - 1] == 1 && spells_of(0)[4 + level - 1] == 0);
        ram[member_record(0) + POOLRAD_PARTY_SPELL_OFFSET] = (unsigned char) (id | 0x80);
        assert(poolrad_party_probe(ram, sizeof(ram), output));
        assert(spells_of(0)[1 + level - 1] == 0 && spells_of(0)[4 + level - 1] == 1);
    }

    /* An out-of-range spell level is unavailable, never counted or clamped. */
    for (unsigned level = 0; level < 256; level++) {
        if (level >= 1 && level <= POOLRAD_PARTY_SPELL_LEVELS) continue;
        fixture(0xe000, 0x2000, 0x3000, 1);
        for (unsigned n = 1; n < 128; n++) spell_level(n, 1);
        spell_level(9, level);
        ram[member_record(0) + POOLRAD_PARTY_SPELL_OFFSET] = 9;
        spells_unavailable(0);
    }

    /* Slot 0x80 is awaiting rest for spell id 0, whose level must still be valid. */
    fixture(0xe000, 0x2000, 0x3000, 1);
    for (unsigned n = 0; n < 128; n++) spell_level(n, 2);
    ram[member_record(0) + POOLRAD_PARTY_SPELL_OFFSET] = 0x80;
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(spells_of(0)[5] == 1 && spells_of(0)[2] == 0);

    /* Members keep independent counts and unavailability. */
    fixture(0xe000, 0x2000, 0x3000, 3);
    for (unsigned n = 1; n < 128; n++) spell_level(n, 1);
    spell_level(40, 9); /* Only the third member's spell has a bad level. */
    ram[member_record(0) + POOLRAD_PARTY_SPELL_OFFSET] = 5;
    ram[member_record(1) + POOLRAD_PARTY_SPELL_OFFSET] = 6 | 0x80;
    ram[member_record(2) + POOLRAD_PARTY_SPELL_OFFSET] = 40;
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(spells_of(0)[0] == 0 && spells_of(0)[1] == 1 && spells_of(0)[4] == 0);
    assert(spells_of(1)[0] == 0 && spells_of(1)[1] == 0 && spells_of(1)[4] == 1);
    assert(spells_of(2)[0] == POOLRAD_PARTY_SPELLS_UNAVAILABLE);

    /* The whole 128-entry table must be in range before any level is read. */
    fixture(0xe000, 0x2000, 0x3000, 1);
    for (unsigned n = 1; n < 128; n++) spell_level(n, 1);
    ram[member_record(0) + POOLRAD_PARTY_SPELL_OFFSET] = 3;
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(spells_of(0)[0] == 0 && spells_of(0)[1] == 1);
    assert(fixture_a5 - POOLRAD_PARTY_SPELL_TABLE_BACK
            + 128 * POOLRAD_PARTY_SPELL_ENTRY <= sizeof(ram));

    fixture(0xf000, 0x2400, 0x4800, 8); // All bases relocate; no fixed capture address is used.
    assert(poolrad_party_probe(ram, sizeof(ram), output) && output[4] == 8);
    put32(fixture_a5 - 20898, member_handle(4)); // Selected member is not the list head.
    assert(poolrad_party_probe(ram, sizeof(ram), output) && output[8 + 16] == 10);
    ram[member_record(0) + POOLRAD_PARTY_CURRENT_HP_OFFSET] = 3;
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(output[24] == 3 && output[25] == 10); // Synthetic damage changes current only.
    ram[member_record(0) + POOLRAD_PARTY_CURRENT_HP_OFFSET] = 10;
    assert(poolrad_party_probe(ram, sizeof(ram), output) && output[24] == 10);
    ram[member_record(0) + POOLRAD_PARTY_CURRENT_HP_OFFSET] = 0;
    assert(poolrad_party_probe(ram, sizeof(ram), output) && output[24] == 0); // Zero HP is valid.

    fixture(0xe000, 0x2000, 0x3000, 3);
    put32(fixture_a5 - POOLRAD_PARTY_HEAD_BACK, member_handle(2));
    put32(member_record(2) + POOLRAD_PARTY_NEXT_OFFSET, member_handle(0));
    put32(member_record(1) + POOLRAD_PARTY_NEXT_OFFSET, 0);
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(output[4] == 3 && output[24] == 12 && output[44] == 10 && output[64] == 11);

    fixture(0xe000, 0x2000, 0x3000, 9); unavailable();
    combat_fixture(6, 10); // Real first-orc-combat shape, entirely synthetic bytes.
    assert(poolrad_party_probe(ram, sizeof(ram), output) && output[4] == 6);
    for (unsigned i = 0; i < 6; i++) assert(output[8 + i * 20 + 16] == 10 + i);
    for (unsigned i = 8 + 6 * 20; i < sizeof(output); i++) assert(output[i] == 0);
    ram[member_record(0) + POOLRAD_PARTY_CURRENT_HP_OFFSET] = 4;
    assert(poolrad_party_probe(ram, sizeof(ram), output) && output[24] == 4);
    ram[member_record(0) + POOLRAD_PARTY_CURRENT_HP_OFFSET] = 10;
    assert(poolrad_party_probe(ram, sizeof(ram), output) && output[24] == 10);
    // Enemy health is neither displayed nor mistaken for party validity.
    ram[member_record(6) + POOLRAD_PARTY_MAX_HP_OFFSET] = 0;
    assert(poolrad_party_probe(ram, sizeof(ram), output) && output[4] == 6);
    combat_fixture(8, 63);
    assert(poolrad_party_probe(ram, sizeof(ram), output) && output[4] == 8);
    combat_fixture(8, 64); unavailable(); // Complete linked walk remains bounded.
    combat_fixture(1, 64); unavailable(); // Independent monster-count bound.
    combat_fixture(0, 10); unavailable(); // Enemies alone are not a loaded party.
    combat_fixture(6, 10);
    ram[member_record(4) + POOLRAD_PARTY_SLOT_OFFSET] = 0; unavailable();
    combat_fixture(6, 10);
    ram[member_record(6) + POOLRAD_PARTY_SLOT_OFFSET] = 0xff; unavailable();
    combat_fixture(6, 10);
    put32(member_record(15) + POOLRAD_PARTY_NEXT_OFFSET, member_handle(6)); unavailable();
    combat_fixture(6, 10);
    put32(member_handle(8), member_record(7)); unavailable();
    combat_fixture(6, 10);
    put32(member_record(9) - 8, 0x82000130); unavailable();
    combat_fixture(6, 10);
    put32(member_record(15) + POOLRAD_PARTY_NEXT_OFFSET, 0x00ffffff); unavailable();
    combat_fixture(3, 2);
    // Filtering follows member order even if nonparty links are interleaved.
    put32(fixture_a5 - POOLRAD_PARTY_HEAD_BACK, member_handle(3));
    put32(member_record(3) + POOLRAD_PARTY_NEXT_OFFSET, member_handle(2));
    put32(member_record(2) + POOLRAD_PARTY_NEXT_OFFSET, member_handle(0));
    put32(member_record(0) + POOLRAD_PARTY_NEXT_OFFSET, member_handle(4));
    put32(member_record(4) + POOLRAD_PARTY_NEXT_OFFSET, member_handle(1));
    put32(member_record(1) + POOLRAD_PARTY_NEXT_OFFSET, 0);
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(output[4] == 3 && output[24] == 12 && output[44] == 10 && output[64] == 11);
    fixture(0xe000, 0x2000, 0x3000, 0); unavailable();
    fixture(0xe000, 0x2000, 0x3000, 1); ram[0x911] = 'X'; unavailable();
    fixture(0xe000, 0x2000, 0x3000, 1); memset(ram + 0x7000, 0, 1024); unavailable();
    fixture(0xe000, 0x2000, 0x3000, 1); ram[fixture_a5 - POOLRAD_GLOBALS_BACK + 82] = 16; unavailable();
    fixture(0xe000, 0x2000, 0x3000, 1);
    assert(!poolrad_party_probe(NULL, sizeof(ram), output));
    assert(!poolrad_party_probe(ram, sizeof(ram), NULL));
    for (size_t size = 0; size < 0x930; size++) assert(!poolrad_party_probe(ram, size, output));
    assert(!poolrad_party_probe(ram, member_record(0) + POOLRAD_PARTY_RECORD_SIZE - 1, output));
    fixture(0xe000, 0x2000, 0xfe00, 1);
    // All map/A5/head fields remain present. Require the logical record AND
    // its complete physical allocation, without reading allocator padding.
    assert(!poolrad_party_probe(ram, 0xfe00 + POOLRAD_PARTY_RECORD_SIZE - 1, output));
    assert(!poolrad_party_probe(ram, 0xfe00 + POOLRAD_PARTY_RECORD_SIZE, output));
    assert(!poolrad_party_probe(ram, 0xfe00 + POOLRAD_PARTY_RECORD_SIZE + 1, output));
    assert(poolrad_party_probe(ram, 0xfe00 + POOLRAD_PARTY_RECORD_SIZE + 2, output));
    put32(member_record(0) - 8, 0x8600013c);
    assert(!poolrad_party_probe(ram, 0xfe00 + POOLRAD_PARTY_RECORD_SIZE + 5, output));
    assert(poolrad_party_probe(ram, 0xfe00 + POOLRAD_PARTY_RECORD_SIZE + 6, output));
    ram[member_record(0) + POOLRAD_PARTY_CURRENT_HP_OFFSET] = 200;
    ram[member_record(0) + POOLRAD_PARTY_MAX_HP_OFFSET] = 255;
    assert(poolrad_party_probe(ram, sizeof(ram), output) && output[24] == 200 && output[25] == 255);

    for (unsigned field = 0; field < 3; field++) {
        const uint32_t bad[] = {1, 0x0ffe, 0xfffe, 0x00ffffff, 0x100000};
        for (unsigned i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) {
            fixture(0xe000, 0x2000, 0x3000, 2);
            uint32_t at = field == 0 ? fixture_a5 - POOLRAD_PARTY_HEAD_BACK
                    : field == 1 ? member_handle(0) : member_record(0) + POOLRAD_PARTY_NEXT_OFFSET;
            put32(at, bad[i]); unavailable();
        }
    }
    fixture(0xe000, 0x2000, 0x3000, 2); put32(member_record(1) + POOLRAD_PARTY_NEXT_OFFSET, member_handle(0)); unavailable();
    fixture(0xe000, 0x2000, 0x3000, 2); put32(member_handle(1), member_record(0)); unavailable();
    fixture(0xe000, 0x2000, 0x3000, 1); put32(member_record(0) - 8, 0x82000130); unavailable();
    fixture(0xe000, 0x2000, 0x3000, 1); ram[member_record(0) + POOLRAD_PARTY_MAX_HP_OFFSET] = 0; unavailable();
    fixture(0xe000, 0x2000, 0x3000, 1); ram[member_record(0) + POOLRAD_PARTY_CURRENT_HP_OFFSET] = 11; unavailable();
    fixture(0xe000, 0x2000, 0x3000, 1); memset(ram + member_record(0), 'A', 16); unavailable();
    fixture(0xe000, 0x2000, 0x3000, 1); memset(ram + member_record(0), 0, 16); unavailable();
    fixture(0xe000, 0x2000, 0x3000, 1); memcpy(ram + member_record(0), "   \0", 4); unavailable();
    fixture(0xe000, 0x2000, 0x3000, 1); ram[member_record(0)] = 0x1b; unavailable();
    fixture(0xe000, 0x2000, 0x3000, 1); ram[member_record(0)] = 0x7f; unavailable();
    fixture(0xe000, 0x2000, 0x3000, 1); ram[member_record(0)] = 0x8e;
    assert(poolrad_party_probe(ram, sizeof(ram), output) && output[8] == 0x8e);
    puts("Party probe: profile, bounds, relocation, linked order, combat filtering, health, AC/class, conditions, bounded effects, spell readiness and failure clearing passed.");
}

static int replay(const char *path) {
    FILE *input = fopen(path, "rb");
    if (!input) { perror(path); return 1; }
    if (fseek(input, 0, SEEK_END) != 0) { fclose(input); return 1; }
    long length = ftell(input);
    if (length < 0 || length > 16 * 1024 * 1024 || fseek(input, 0, SEEK_SET) != 0) {
        fclose(input); return 1;
    }
    unsigned char *capture = malloc((size_t) length + 1);
    if (!capture) { fclose(input); return 1; }
    size_t got = fread(capture, 1, (size_t) length, input);
    int closed = fclose(input);
    if (got != (size_t) length || closed != 0) { free(capture); return 1; }
    int found = poolrad_party_probe(capture, (size_t) length, output);
    free(capture);
    if (!found) { fprintf(stderr, "Party unavailable in %s\n", path); return 2; }
    for (unsigned i = 0; i < output[4]; i++) {
        const unsigned char *row = output + 8 + i * POOLRAD_PARTY_ROW_SIZE;
        fprintf(stderr, "%u. %s: %u/%u HP; ", i + 1, row, row[16], row[17]);
        if (row[18] == POOLRAD_PARTY_UNKNOWN_AC) fputs("AC unknown; ", stderr);
        else fprintf(stderr, "AC %d; ", row[18] < 128 ? row[18] : (int) row[18] - 256);
        if (row[19] == POOLRAD_PARTY_UNKNOWN_CLASS) fputs("class unknown; ", stderr);
        else fprintf(stderr, "class %u; ", row[19]);
        fprintf(stderr,"condition %u; tracked effects %u; ", output[POOLRAD_PARTY_BASE_SIZE + i * 2], output[POOLRAD_PARTY_BASE_SIZE + i * 2 + 1]);
        {
            const unsigned char *sp = output + POOLRAD_PARTY_CONDITION_SIZE + i * POOLRAD_PARTY_SPELL_STRIDE;
            if (sp[0] == POOLRAD_PARTY_SPELLS_UNAVAILABLE) fputs("spells unavailable\n", stderr);
            else fprintf(stderr, "ready %u/%u/%u; awaiting rest %u/%u/%u\n",
                    sp[1], sp[2], sp[3], sp[4], sp[5], sp[6]);
        }
    }
    return fwrite(output, 1, sizeof(output), stdout) == sizeof(output) ? 0 : 1;
}

int main(int argc, char **argv) {
    if (argc == 1) { tests(); return 0; }
    if (argc == 2) return replay(argv[1]);
    fprintf(stderr, "Usage: test-party-probe [private-capture.ram]\n");
    return 1;
}
