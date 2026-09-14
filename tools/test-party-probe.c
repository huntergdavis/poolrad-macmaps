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
    assert(memcmp(output, "PRP2", 4) == 0 && output[4] == 6);
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
    puts("Party probe: profile, bounds, relocation, linked order, 1-8 members, combat filtering, health, AC/class ranges and failure clearing passed.");
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
        if (row[19] == POOLRAD_PARTY_UNKNOWN_CLASS) fputs("class unknown\n", stderr);
        else fprintf(stderr, "class %u\n", row[19]);
    }
    return fwrite(output, 1, sizeof(output), stdout) == sizeof(output) ? 0 : 1;
}

int main(int argc, char **argv) {
    if (argc == 1) { tests(); return 0; }
    if (argc == 2) return replay(argv[1]);
    fprintf(stderr, "Usage: test-party-probe [private-capture.ram]\n");
    return 1;
}
