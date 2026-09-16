/* Synthetic party fixture tests; optional read-only private-capture replay.
 * No ROM, disk, game map, character record or code bytes are embedded here.
 */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
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

/* Writes one name-part string and points table entry `index` at it. */
static void name_part(unsigned index, const char *text) {
    static uint32_t next = 0x9000;
    uint32_t table = fixture_a5 - POOLRAD_PARTY_ITEM_NAME_TABLE_BACK;
    size_t n = strlen(text);
    memcpy(ram + next, text, n + 1);
    put32(table + index * 4, next);
    next += (uint32_t) (n + 2);
}
/* Readies an item in `slot` for `member`, composed from up to three parts. */
static uint32_t ready_item(unsigned member, unsigned slot, uint32_t item,
        unsigned p1, unsigned p2, unsigned p3) {
    uint32_t handle = 0xb000 + member * 0x20 + slot * 4;
    put32(member_record(member) + POOLRAD_PARTY_READIED_OFFSET + slot * 4, handle);
    put32(handle, item);
    ram[item + POOLRAD_PARTY_ITEM_PART_OFFSET + 1] = (unsigned char) p1;
    ram[item + POOLRAD_PARTY_ITEM_PART_OFFSET + 2] = (unsigned char) p2;
    ram[item + POOLRAD_PARTY_ITEM_PART_OFFSET + 3] = (unsigned char) p3;
    ram[item + POOLRAD_PARTY_ITEM_SUPPRESS_OFFSET] = 0;
    return item;
}
static const unsigned char *equip_of(unsigned member) {
    return output + POOLRAD_PARTY_SPELL_SIZE + member * POOLRAD_PARTY_EQUIP_STRIDE;
}
static void equip_unavailable(unsigned member) {
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(equip_of(member)[0] == POOLRAD_PARTY_EQUIP_UNAVAILABLE);
    for (unsigned i = 1; i < 1 + 2 * POOLRAD_PARTY_NAME_BYTES; i++) assert(equip_of(member)[i] == 0);
    assert(output[24] == 10 && output[25] == 10); /* Health survives missing items. */
}
static void equip_names(unsigned member, const char *weapon, const char *armor) {
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(equip_of(member)[0] == 0);
    assert(strcmp((const char *) equip_of(member) + 1, weapon) == 0);
    assert(strcmp((const char *) equip_of(member) + 1 + POOLRAD_PARTY_NAME_BYTES, armor) == 0);
}

/* Gives `member` one class at `level` and writes that class's threshold row. */
static void give_class(unsigned member, unsigned slot, unsigned level, const int32_t *row) {
    uint32_t table = fixture_a5 - POOLRAD_PARTY_TRAIN_TABLE_BACK;
    ram[member_record(member) + POOLRAD_PARTY_LEVEL_OFFSET + slot] = (unsigned char) level;
    if (row) for (unsigned i = 0; i < POOLRAD_PARTY_TRAIN_LEVELS; i++)
        put32(table + slot * POOLRAD_PARTY_TRAIN_CLASS_STRIDE + i * 4, (uint32_t) row[i]);
}
static void set_experience(unsigned member, uint32_t value) {
    put32(member_record(member) + POOLRAD_PARTY_EXPERIENCE_OFFSET, value);
}
static const unsigned char *train_of(unsigned member) {
    return output + POOLRAD_PARTY_EQUIP_SIZE + member * POOLRAD_PARTY_TRAIN_STRIDE;
}
static uint32_t be32(const unsigned char *p) {
    return ((uint32_t) p[0] << 24) | (p[1] << 16) | (p[2] << 8) | p[3];
}
static void train_unavailable(unsigned member) {
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(train_of(member)[0] == POOLRAD_PARTY_TRAIN_UNAVAILABLE);
    for (unsigned i = 1; i < POOLRAD_PARTY_TRAIN_STRIDE; i++) assert(train_of(member)[i] == 0);
    assert(output[24] == 10 && output[25] == 10);
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

/* Every refusal must name itself. A silent refusal is what let a live map sit
 * beside an empty party pane with nothing to diagnose.
 */
static void refuses(unsigned code, unsigned links, uint32_t detail) {
    unsigned char why[6]; memset(why, 0xee, sizeof(why));
    memset(output, 0xff, sizeof(output));
    assert(!poolrad_party_probe_why(ram, sizeof(ram), output, why));
    for (unsigned i = 0; i < sizeof(output); i++) assert(output[i] == 0);
    assert(why[0] == code);
    assert(why[1] == links);
    assert(((uint32_t) why[2] << 24 | (uint32_t) why[3] << 16
            | (uint32_t) why[4] << 8 | why[5]) == detail);
}

static void tests(void) {
    fixture(0xe000, 0x2000, 0x3000, 6);
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(memcmp(output, "PRP6", 4) == 0 && output[4] == 6);
    for (unsigned i = 0; i < 6; i++) {
        unsigned row = 8 + i * POOLRAD_PARTY_ROW_SIZE;
        assert(memcmp(output + row, ram + member_record(i), 6) == 0);
        assert(output[row + 16] == 10 + i && output[row + 17] == 10 + i);
        assert(output[row + 18] == (unsigned char) (0 - i) && output[row + 19] == i);
    }
    // Unused rows stay zero. The per-member blocks that follow the rows have
    // their own emptiness rules and are checked with their own features.
    for (unsigned i = 8 + 6 * POOLRAD_PARTY_ROW_SIZE; i < POOLRAD_PARTY_BASE_SIZE; i++)
        assert(output[i] == 0);
    for (unsigned member = 6; member < POOLRAD_PARTY_MAX_MEMBERS; member++) {
        for (unsigned i = 0; i < 2; i++)
            assert(output[POOLRAD_PARTY_BASE_SIZE + member * 2 + i] == 0);
        for (unsigned i = 0; i < POOLRAD_PARTY_SPELL_STRIDE; i++)
            assert(output[POOLRAD_PARTY_CONDITION_SIZE + member * POOLRAD_PARTY_SPELL_STRIDE + i] == 0);
        for (unsigned i = 0; i < POOLRAD_PARTY_EQUIP_STRIDE; i++)
            assert(output[POOLRAD_PARTY_SPELL_SIZE + member * POOLRAD_PARTY_EQUIP_STRIDE + i] == 0);
        for (unsigned i = 0; i < POOLRAD_PARTY_TRAIN_STRIDE; i++)
            assert(output[POOLRAD_PARTY_EQUIP_SIZE + member * POOLRAD_PARTY_TRAIN_STRIDE + i] == 0);
    }

    /* Same logical record under every representable size correction. A Mac
     * heap block's size is even, not a multiple of four; this sweep used to
     * demand four, which rejected every unpadded record and is exactly why
     * Hunter's party never appeared. No game data is copied into the fixtures.
     */
    for (unsigned correction = 0; correction < 16; correction++) {
        fixture(0xe000, 0x2000, 0x3000, 1);
        unsigned physical = POOLRAD_PARTY_RECORD_SIZE + 8 + correction;
        put32(member_record(0) - 8, ((0x80u | correction) << 24) | physical);
        if ((physical & 1) == 0) {
            assert(poolrad_party_probe(ram, sizeof(ram), output) && output[24] == 10);
            // Contents of allocator padding must not affect names/stats.
            memset(ram + member_record(0) + POOLRAD_PARTY_RECORD_SIZE, 0xa5, correction);
            assert(poolrad_party_probe(ram, sizeof(ram), output) && output[24] == 10);
        } else unavailable();
    }

    /* The header Hunter's tablet actually reported, verbatim: relocatable, no
     * size correction, physical 310 for the 302-byte record. The canonical
     * allocation, and the one the old alignment test threw away.
     */
    fixture(0xe000, 0x2000, 0x3000, 6);
    for (unsigned i = 0; i < 6; i++) put32(member_record(i) - 8, 0x80000136);
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(memcmp(output, "PRP6", 4) == 0 && output[4] == 6);
    for (unsigned i = 0; i < 6; i++)
        assert(memcmp(output + 8 + i * POOLRAD_PARTY_ROW_SIZE, ram + member_record(i), 6) == 0);
    // An odd physical size is still not a block, whatever the correction says.
    fixture(0xe000, 0x2000, 0x3000, 1);
    put32(member_record(0) - 8, 0x81000137); unavailable();
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
    // Effect nodes take the same correction: even is a block, odd is not.
    for (unsigned pad = 0; pad < 16; pad++) {
        effects_fixture(1);
        put32(0x7ff8, ((0x80u | pad) << 24) | (18 + pad));
        assert(poolrad_party_probe(ram, sizeof(ram), output));
        assert(output[POOLRAD_PARTY_BASE_SIZE + 1] == ((18 + pad) % 2 ? 255 : 1));
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

    /* Readied weapon and armor names, composed from the game's own part table. */
    fixture(0xe000, 0x2000, 0x3000, 1);
    name_part(36, "Long Sword"); name_part(57, "Banded"); name_part(48, "Mail");
    ready_item(0, POOLRAD_PARTY_WEAPON_SLOT, 0xa000, 0, 0, 36);
    ready_item(0, POOLRAD_PARTY_ARMOR_SLOT, 0xa100, 0, 48, 57);
    /* Parts append from n=3 down to n=1, exactly as CODE3 +0x064e does. */
    equip_names(0, "Long Sword", "Banded Mail");

    /* An empty slot is genuinely empty, not unavailable. */
    fixture(0xe000, 0x2000, 0x3000, 1);
    name_part(12, "Flail");
    ready_item(0, POOLRAD_PARTY_WEAPON_SLOT, 0xa000, 0, 0, 12);
    equip_names(0, "Flail", "");

    /* Every suppression bit hides exactly its own part. */
    for (unsigned mask = 0; mask < 8; mask++) {
        fixture(0xe000, 0x2000, 0x3000, 1);
        name_part(1, "One"); name_part(2, "Two"); name_part(3, "Three");
        ready_item(0, POOLRAD_PARTY_WEAPON_SLOT, 0xa000, 1, 2, 3);
        ram[0xa000 + POOLRAD_PARTY_ITEM_SUPPRESS_OFFSET] = (unsigned char) mask;
        char expected[64]; expected[0] = 0;
        for (unsigned n = 3; n >= 1; n--) {
            if ((mask >> (3 - n)) & 1) continue;
            if (expected[0]) strcat(expected, " ");
            strcat(expected, n == 3 ? "Three" : n == 2 ? "Two" : "One");
        }
        if (expected[0] == 0) equip_unavailable(0); /* All parts hidden is unknown. */
        else equip_names(0, expected, "");
    }

    /* A purged item block is unavailable, never an empty hand. */
    fixture(0xe000, 0x2000, 0x3000, 1);
    name_part(36, "Long Sword");
    ready_item(0, POOLRAD_PARTY_WEAPON_SLOT, 0xa000, 0, 0, 36);
    put32(0xb000, 0); equip_unavailable(0);

    /* Odd, low and out-of-range handles and item pointers are all refused. */
    for (unsigned i = 0; i < 4; i++) {
        static const uint32_t bad[4] = {0xb001, 0x10, 0xfffffe, 0xffffff};
        fixture(0xe000, 0x2000, 0x3000, 1);
        name_part(36, "Long Sword");
        ready_item(0, POOLRAD_PARTY_WEAPON_SLOT, 0xa000, 0, 0, 36);
        put32(member_record(0) + POOLRAD_PARTY_READIED_OFFSET, bad[i]);
        equip_unavailable(0);
        fixture(0xe000, 0x2000, 0x3000, 1);
        name_part(36, "Long Sword");
        ready_item(0, POOLRAD_PARTY_WEAPON_SLOT, 0xa000, 0, 0, 36);
        put32(0xb000, bad[i] == 0xb001 ? 0xa001 : bad[i]);
        equip_unavailable(0);
    }

    /* Control bytes inside a name part are refused rather than emitted; a NUL
     * is simply the terminator and still yields a valid, shorter name.
     */
    for (unsigned byte = 0; byte < 0x20; byte++) {
        fixture(0xe000, 0x2000, 0x3000, 1);
        name_part(36, "Long Sword");
        ready_item(0, POOLRAD_PARTY_WEAPON_SLOT, 0xa000, 0, 0, 36);
        uint32_t at = poolrad_u32(ram + fixture_a5 - POOLRAD_PARTY_ITEM_NAME_TABLE_BACK + 36 * 4);
        ram[at + 2] = (unsigned char) byte;
        if (byte == 0) equip_names(0, "Lo", "");
        else equip_unavailable(0);
    }
    /* A part that is empty at its first byte has no name at all. */
    fixture(0xe000, 0x2000, 0x3000, 1);
    name_part(36, "Long Sword");
    ready_item(0, POOLRAD_PARTY_WEAPON_SLOT, 0xa000, 0, 0, 36);
    {
        uint32_t at = poolrad_u32(ram + fixture_a5 - POOLRAD_PARTY_ITEM_NAME_TABLE_BACK + 36 * 4);
        ram[at] = 0; equip_unavailable(0);
    }
    fixture(0xe000, 0x2000, 0x3000, 1);
    name_part(36, "Long Sword");
    ready_item(0, POOLRAD_PARTY_WEAPON_SLOT, 0xa000, 0, 0, 36);
    {
        uint32_t at = poolrad_u32(ram + fixture_a5 - POOLRAD_PARTY_ITEM_NAME_TABLE_BACK + 36 * 4);
        ram[at] = 0x7f; equip_unavailable(0);
    }

    /* A name that will not fit the emitted field is unavailable, not truncated. */
    fixture(0xe000, 0x2000, 0x3000, 1);
    name_part(5, "ABCDEFGHIJKLMNOPQRSTUVWXYZ012345");
    ready_item(0, POOLRAD_PARTY_WEAPON_SLOT, 0xa000, 0, 0, 5);
    equip_unavailable(0);
    fixture(0xe000, 0x2000, 0x3000, 1);
    name_part(5, "ABCDEFGHIJKLMNOPQRSTUVWXYZ01234"); /* Exactly 31 fits. */
    ready_item(0, POOLRAD_PARTY_WEAPON_SLOT, 0xa000, 0, 0, 5);
    equip_names(0, "ABCDEFGHIJKLMNOPQRSTUVWXYZ01234", "");

    /* One unreadable member never blanks another's equipment. */
    fixture(0xe000, 0x2000, 0x3000, 3);
    name_part(36, "Long Sword"); name_part(12, "Flail");
    ready_item(0, POOLRAD_PARTY_WEAPON_SLOT, 0xa000, 0, 0, 36);
    ready_item(2, POOLRAD_PARTY_WEAPON_SLOT, 0xa200, 0, 0, 12);
    put32(member_record(1) + POOLRAD_PARTY_READIED_OFFSET, 0xa001); /* Odd handle. */
    assert(poolrad_party_probe(ram, sizeof(ram), output));
    assert(equip_of(0)[0] == 0 && strcmp((const char *) equip_of(0) + 1, "Long Sword") == 0);
    assert(equip_of(1)[0] == POOLRAD_PARTY_EQUIP_UNAVAILABLE);
    assert(equip_of(2)[0] == 0 && strcmp((const char *) equip_of(2) + 1, "Flail") == 0);

    /* Training: the shared experience and the game's own next-level threshold.
     * The Fighter row below is the one actually read from a private capture.
     */
    {
        static const int32_t fighter[POOLRAD_PARTY_TRAIN_LEVELS] = {
            0, 0, 2001, 4001, 8001, 18001, 35001, 70001, 125001, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
        static const int32_t thief[POOLRAD_PARTY_TRAIN_LEVELS] = {
            0, 0, 1251, 2501, 5001, 10001, 20001, 42501, 70001, 110001,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
        fixture(0xe000, 0x2000, 0x3000, 1);
        give_class(0, 2, 1, fighter);
        set_experience(0, 2134);
        assert(poolrad_party_probe(ram, sizeof(ram), output));
        assert(train_of(0)[0] == 1);
        assert(be32(train_of(0) + 1) == 2134);
        assert(train_of(0)[5] == 2 && train_of(0)[6] == 1);
        assert(be32(train_of(0) + 7) == 2001); /* Level 2 needs 2001, so eligible. */
        for (unsigned i = 11; i < POOLRAD_PARTY_TRAIN_STRIDE; i++) assert(train_of(0)[i] == 0);

        /* Every level reads the threshold the game would compare against. */
        for (unsigned level = 1; level <= 8; level++) {
            fixture(0xe000, 0x2000, 0x3000, 1);
            give_class(0, 2, level, fighter);
            set_experience(0, 1);
            assert(poolrad_party_probe(ram, sizeof(ram), output));
            uint32_t expected = fighter[level + 1] < 0 ? 0 : (uint32_t) fighter[level + 1];
            assert(be32(train_of(0) + 7) == expected);
            assert(train_of(0)[6] == level);
        }

        /* A multiclass character reports each class separately. */
        fixture(0xe000, 0x2000, 0x3000, 1);
        give_class(0, 2, 1, fighter);
        give_class(0, 6, 1, thief);
        set_experience(0, 970);
        assert(poolrad_party_probe(ram, sizeof(ram), output));
        assert(train_of(0)[0] == 2);
        assert(train_of(0)[5] == 2 && be32(train_of(0) + 7) == 2001);
        assert(train_of(0)[11] == 6 && be32(train_of(0) + 13) == 1251);

        /* More classes than the block holds is unavailable, never truncated. */
        fixture(0xe000, 0x2000, 0x3000, 1);
        for (unsigned slot = 0; slot < 4; slot++) give_class(0, slot, 1, fighter);
        set_experience(0, 1);
        train_unavailable(0);

        /* A level past the table's end is unavailable rather than read beyond. */
        for (unsigned level = POOLRAD_PARTY_TRAIN_LEVELS - 1; level <= 255; level++) {
            fixture(0xe000, 0x2000, 0x3000, 1);
            give_class(0, 2, level, fighter);
            train_unavailable(0);
            if (level == 255) break;
        }

        /* A character with no class at all is unavailable, not an empty list. */
        fixture(0xe000, 0x2000, 0x3000, 1);
        set_experience(0, 500);
        train_unavailable(0);

        /* Members keep independent training blocks. */
        fixture(0xe000, 0x2000, 0x3000, 2);
        give_class(0, 2, 1, fighter);
        give_class(1, 6, 3, thief);
        set_experience(0, 2134); set_experience(1, 6000);
        assert(poolrad_party_probe(ram, sizeof(ram), output));
        assert(be32(train_of(0) + 1) == 2134 && be32(train_of(0) + 7) == 2001);
        assert(be32(train_of(1) + 1) == 6000 && train_of(1)[6] == 3);
        assert(be32(train_of(1) + 7) == 5001);
    }

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
    for (unsigned i = 8 + 6 * 20; i < POOLRAD_PARTY_BASE_SIZE; i++) assert(output[i] == 0);
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

    /* Named refusals. Each fixture breaks exactly one check, and the probe must
     * report that check and how many roster links it had already accepted.
     */
    fixture(0xe000, 0x2000, 0x3000, 6);
    unsigned char accepted[6]; memset(accepted, 0xee, sizeof(accepted));
    assert(poolrad_party_probe_why(ram, sizeof(ram), output, accepted));
    for (unsigned i = 0; i < sizeof(accepted); i++) assert(accepted[i] == 0);

    fixture(0xe000, 0x2000, 0x3000, 0);
    refuses(POOLRAD_PARTY_WHY_NO_ROSTER, 0, 0);

    fixture(0xe000, 0x2000, 0x3000, 6);
    ram[0x910] = 0; // The shared profile guard, before anything party-specific.
    refuses(POOLRAD_PARTY_WHY_NO_GAME, 0, 0);

    fixture(0xe000, 0x2000, 0x3000, 6);
    put32(fixture_a5 - POOLRAD_PARTY_HEAD_BACK, 3); // Odd, and below the heap.
    refuses(POOLRAD_PARTY_WHY_HANDLE, 0, 3);

    fixture(0xe000, 0x2000, 0x3000, 6);
    put32(member_handle(2), 0x1001); // Third record: two links already accepted.
    refuses(POOLRAD_PARTY_WHY_RECORD, 2, 0x1001);

    fixture(0xe000, 0x2000, 0x3000, 6);
    put32(member_record(1) - 8, 0x82000140); // Right tag, wrong physical size.
    refuses(POOLRAD_PARTY_WHY_BLOCK, 1, 0x82000140);

    fixture(0xe000, 0x2000, 0x3000, 6);
    put32(member_record(3) + POOLRAD_PARTY_NEXT_OFFSET, member_handle(0));
    refuses(POOLRAD_PARTY_WHY_LOOP, 4, 0);

    fixture(0xe000, 0x2000, 0x3000, 6);
    ram[member_record(0) + POOLRAD_PARTY_SLOT_OFFSET] = 0xff;
    refuses(POOLRAD_PARTY_WHY_SLOT_UNSET, 1, member_record(0));

    fixture(0xe000, 0x2000, 0x3000, 6);
    ram[member_record(4) + POOLRAD_PARTY_SLOT_OFFSET] = 0;
    refuses(POOLRAD_PARTY_WHY_SLOT_CLASH, 5, 0x0f00);

    fixture(0xe000, 0x2000, 0x3000, 6);
    ram[member_record(2)] = 0x01;
    refuses(POOLRAD_PARTY_WHY_NAME, 3, 0x0001);

    fixture(0xe000, 0x2000, 0x3000, 6);
    ram[member_record(1) + POOLRAD_PARTY_MAX_HP_OFFSET] = 0;
    refuses(POOLRAD_PARTY_WHY_HEALTH, 2, 0x0b00);

    combat_fixture(0, 3); // A roster of monsters only holds no party at all.
    refuses(POOLRAD_PARTY_WHY_EMPTY, 3, 0);

    // The reason is optional: every existing caller passes NULL and must not crash.
    fixture(0xe000, 0x2000, 0x3000, 0);
    assert(!poolrad_party_probe_why(ram, sizeof(ram), output, NULL));
    assert(!poolrad_party_probe(ram, sizeof(ram), output));

    puts("Party probe: profile, bounds, relocation, linked order, combat filtering, health, AC/class, conditions, bounded effects, spell readiness, readied equipment, training thresholds, named refusals and failure clearing passed.");
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
            else fprintf(stderr, "ready %u/%u/%u; awaiting rest %u/%u/%u; ",
                    sp[1], sp[2], sp[3], sp[4], sp[5], sp[6]);
        }
        {
            const unsigned char *eq = output + POOLRAD_PARTY_SPELL_SIZE + i * POOLRAD_PARTY_EQUIP_STRIDE;
            if (eq[0] == POOLRAD_PARTY_EQUIP_UNAVAILABLE) fputs("equipment unavailable; ", stderr);
            else fprintf(stderr, "weapon '%s'; armor '%s'; ",
                    eq + 1, eq + 1 + POOLRAD_PARTY_NAME_BYTES);
            fprintf(stderr, "movement %u; carrying %u; ",
                    eq[1 + 2 * POOLRAD_PARTY_NAME_BYTES],
                    (unsigned) ((eq[2 + 2 * POOLRAD_PARTY_NAME_BYTES] << 8)
                            | eq[3 + 2 * POOLRAD_PARTY_NAME_BYTES]));
        }
        {
            const unsigned char *tr = output + POOLRAD_PARTY_EQUIP_SIZE + i * POOLRAD_PARTY_TRAIN_STRIDE;
            if (tr[0] == POOLRAD_PARTY_TRAIN_UNAVAILABLE) fputs("training unavailable\n", stderr);
            else {
                unsigned xp = (tr[1]<<24)|(tr[2]<<16)|(tr[3]<<8)|tr[4];
                fprintf(stderr, "xp %u;", xp);
                for (unsigned c = 0; c < tr[0]; c++) {
                    const unsigned char *t = tr + 5 + c * 6;
                    unsigned need = (t[2]<<24)|(t[3]<<16)|(t[4]<<8)|t[5];
                    fprintf(stderr, " class%u L%u next %u%s", t[0], t[1], need,
                            need && xp >= need ? " READY" : "");
                }
                fputc(10, stderr);
            }
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
