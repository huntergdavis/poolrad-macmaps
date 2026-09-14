/* Synthetic RAM only: no ROM, game bytes, or copyrighted map fixtures. */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include "../android/minivmac/src/main/jni/src/POOLRAD.h"

static unsigned char ram[65536], output[POOLRAD_PROBE_SIZE];
static void put32(size_t address, uint32_t value) {
    ram[address] = value >> 24; ram[address + 1] = value >> 16;
    ram[address + 2] = value >> 8; ram[address + 3] = value;
}
static void fixture(uint32_t globals, uint32_t handle, uint32_t map) {
    memset(ram, 0, sizeof(ram));
    const char name[] = "Pool of Radiance v1.1";
    ram[0x910] = sizeof(name) - 1; memcpy(ram + 0x911, name, sizeof(name) - 1);
    put32(0x904, globals + POOLRAD_GLOBALS_BACK);
    put32(globals, handle); put32(handle, map); put32(map - 8, 0x80000408);
    ram[globals + 82] = 15; ram[globals + 83] = 1; ram[globals + 84] = 6;
    for (int i = 0; i < 1024; i++) ram[map + i] = (unsigned char)i;
    uint32_t a5 = globals + POOLRAD_GLOBALS_BACK;
    put32(a5 - POOLRAD_STATE_BACK, 0x2200);
    put32(0x2200, 0x6000); put32(0x5ff8, 0x80000808);
    ram[a5 - POOLRAD_MODE_BACK] = 1;
    ram[0x618b] = 20;
    ram[a5 - POOLRAD_ENGINE_BACK] = 4;
    if (a5 >= POOLRAD_INPUT_TAG_BACK) ram[a5 - POOLRAD_INPUT_TAG_BACK] = 0x56;
    ram[a5 - POOLRAD_MENU_STATE_BACK + 1] = 2;
}
static void walk_tests(void) {
    const uint32_t a5 = 0x8000 + POOLRAD_GLOBALS_BACK;
    poolrad_walk_tracker tracker = {0};
    fixture(0x8000, 0x2000, 0x4000);
    unsigned char original[sizeof(ram)];
    memcpy(original, ram, sizeof(ram));
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output));
    assert(memcmp(output, "PRM3", 4) == 0);
    assert(output[25] == 1 && output[26] == 1 && output[27] == 4);
    assert(poolrad_u32(output + 28) == 1 && tracker.epoch == 1);
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    assert(tracker.epoch == 1 && memcmp(original, ram, sizeof(ram)) == 0);
    /* A System 7 minor switch swaps low-memory app/A5 without moving the game.
     * Tick gaps preserve the token, but never become a fabricated UI sample. */
    ram[0x910] = 6; memcpy(ram + 0x911, "Finder", 6);
    put32(0x904, 0x3000);
    for (int tick = 0; tick < 12; tick++) poolrad_walk_observe(ram, sizeof(ram), &tracker);
    assert(tracker.epoch == 1 && tracker.a5 == a5 && !tracker.discontinuity);
    memcpy(ram, original, sizeof(ram));
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && output[26]);
    assert(tracker.epoch == 1);
    /* Ordinary action processing is unavailable if sampled, but must not
     * advance the epoch at every core tick and erase every real step. */
    ram[a5 - POOLRAD_INPUT_TAG_BACK] = 0;
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    assert(tracker.epoch == 1);
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && !output[26]);
    ram[0x8052] = 14;
    ram[0x8054] = 0; // A turn alone is not a continuity break or a footstep.
    ram[a5 - POOLRAD_INPUT_TAG_BACK] = 0x56;
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && output[26]);
    assert(tracker.epoch == 1);
    for (unsigned value = 0; value < 256; value++) {
        ram[a5 - POOLRAD_INPUT_TAG_BACK] = value;
        assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output));
        assert(output[26] == (value == 0x56) && tracker.epoch == 1);
    }
    ram[a5 - POOLRAD_INPUT_TAG_BACK] = 0x56;
    for (unsigned value = 0; value < 256; value++) {
        ram[a5 - POOLRAD_PENDING_INPUT_BACK] = value;
        assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output));
        assert(output[26] == (value == 0) && tracker.epoch == 1);
    }
    ram[a5 - POOLRAD_PENDING_INPUT_BACK] = 0;
    /* A real hard break must survive intervening background-process ticks. */
    ram[a5 - POOLRAD_ENGINE_BACK] = 2;
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    assert(tracker.epoch == 2 && tracker.discontinuity);
    ram[0x910] = 6; memcpy(ram + 0x911, "Finder", 6);
    put32(0x904, 0x3000);
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    assert(tracker.epoch == 2 && tracker.discontinuity);
    fixture(0x8000, 0x2000, 0x4000);
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && output[26]);
    assert(tracker.epoch == 2 && !tracker.discontinuity);
    /* By contrast, an actual requested Finder sample emits nothing and breaks
     * the route. Ignoring a tick cannot relax the map-delivery profile guard. */
    ram[0x910] = 6; memcpy(ram + 0x911, "Finder", 6);
    assert(!poolrad_walk_probe(ram, sizeof(ram), &tracker, output));
    assert(tracker.epoch > 2 && tracker.discontinuity);
    fixture(0x8000, 0x2000, 0x4000);
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    uint32_t before_invalid = tracker.epoch;
    put32(0x904, 1); // The game name with corrupt A5 is NOT a background gap.
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    assert(tracker.epoch > before_invalid && tracker.discontinuity);
    const unsigned bad_names[] = {0, 32, 255};
    for (unsigned i = 0; i < sizeof(bad_names) / sizeof(bad_names[0]); i++) {
        fixture(0x8000, 0x2000, 0x4000);
        poolrad_walk_observe(ram, sizeof(ram), &tracker);
        before_invalid = tracker.epoch;
        ram[0x910] = bad_names[i];
        poolrad_walk_observe(ram, sizeof(ram), &tracker);
        assert(tracker.epoch > before_invalid && tracker.discontinuity);
    }
    fixture(0x8000, 0x2000, 0x4000);
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    before_invalid = tracker.epoch;
    poolrad_walk_observe(ram, 0x920, &tracker);
    assert(tracker.epoch > before_invalid && tracker.discontinuity);
    /* Combat/camp/load/outdoor and script-relocation signals break even when
     * they start and end between Android requests (observe, no probe). */
    const uint32_t guards[] = { POOLRAD_ENGINE_BACK, POOLRAD_MENU_STATE_BACK,
        POOLRAD_STARTUP_BACK, POOLRAD_LOADED_BACK, POOLRAD_RELOCATION_BACK,
        POOLRAD_MODE_BACK };
    for (unsigned i = 0; i < sizeof(guards) / sizeof(guards[0]); i++) {
        fixture(0x8000, 0x2000, 0x4000);
        poolrad_walk_observe(ram, sizeof(ram), &tracker);
        uint32_t previous = tracker.epoch;
        unsigned char old = ram[a5 - guards[i]];
        ram[a5 - guards[i]] = 255;
        poolrad_walk_observe(ram, sizeof(ram), &tracker);
        assert(tracker.epoch > previous);
        uint32_t interrupted = tracker.epoch;
        poolrad_walk_observe(ram, sizeof(ram), &tracker);
        assert(tracker.epoch == interrupted);
        assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && !output[26]);
        ram[a5 - guards[i]] = old;
        assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && output[26]);
        assert(poolrad_u32(output + 28) > previous);
    }
    for (unsigned engine = 0; engine < 256; engine++) {
        ram[a5 - POOLRAD_ENGINE_BACK] = engine;
        assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output));
        assert(output[26] == (engine == 4) && output[27] == engine);
    }
    fixture(0x8000, 0x2000, 0x4000);
    for (unsigned menu = 0; menu < 256; menu++) {
        ram[a5 - POOLRAD_MENU_STATE_BACK + 1] = menu;
        assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output));
        assert(output[26] == (menu == 2));
    }
    fixture(0x8000, 0x2000, 0x4000);
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    uint32_t previous = tracker.epoch;
    ram[0x618b] = 0;
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    ram[0x618b] = 20;
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && output[26]);
    assert(tracker.epoch > previous); // Area round trip entirely between polls.
    previous = tracker.epoch;
    memcpy(ram + 0x7000 - 8, ram + 0x6000 - 8, 2056);
    put32(0x2200, 0x7000);
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    assert(tracker.epoch > previous); // The same ID at a moved state pointer.
    previous = tracker.epoch;
    memcpy(ram + 0x5000 - 8, ram + 0x4000 - 8, 1032);
    put32(0x2000, 0x5000);
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    assert(tracker.epoch > previous);
    previous = tracker.epoch;
    fixture(0x9000, 0x3000, 0x5000);
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    assert(tracker.epoch > previous); // Independent A5 relocation.
    previous = tracker.epoch;
    ram[0x911] = 'X';
    assert(!poolrad_walk_probe(ram, sizeof(ram), &tracker, output));
    assert(tracker.epoch > previous);
    fixture(0x8000, 0x2000, 0x4000);
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && output[26]);
    assert(!poolrad_walk_probe(NULL, sizeof(ram), &tracker, output));
    assert(!poolrad_walk_probe(ram, sizeof(ram), &tracker, NULL));
    assert(poolrad_walk_probe(ram, sizeof(ram), NULL, output));
    assert(!output[26] && poolrad_u32(output + 28) == 0);
    poolrad_walk_observe(NULL, 0, NULL);
    poolrad_walk_reset(NULL);
    fixture(0x24c0, 0x2000, 0x4000); // Valid older map, insufficient A5 guard interval.
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output));
    assert(!output[26] && output[27] == 255);
    fixture(0x8000, 0x2000, 0x4000);
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    previous = tracker.epoch;
    poolrad_walk_reset(&tracker);
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && output[26]);
    assert(tracker.epoch > previous);
    tracker.epoch = UINT32_MAX;
    poolrad_walk_reset(&tracker);
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output));
    assert(!output[26] && poolrad_u32(output + 28) == 0 && tracker.exhausted);
    puts("Movement probe: PRM3 settled-input gate, combat/load rejection and continuity passed.");
}
static void unknown_identity(void) {
    assert(poolrad_probe(ram, sizeof(ram), output));
    assert(!output[POOLRAD_ID_VALID_OUT]);
    assert(output[POOLRAD_ID_OUT] == 255 && output[POOLRAD_ID_OUT + 1] == 255);
    /* Valid geometry is still available to the map and independent party reader. */
    assert(output[130] == 15);
}
static void replay(const char *path) {
    FILE *file = fopen(path, "rb");
    assert(file && fseek(file, 0, SEEK_END) == 0);
    long length = ftell(file);
    assert(length > 0 && length <= 64 * 1024 * 1024 && fseek(file, 0, SEEK_SET) == 0);
    unsigned char *capture = malloc((size_t)length);
    assert(capture && fread(capture, 1, (size_t)length, file) == (size_t)length);
    assert(fclose(file) == 0);
    poolrad_walk_tracker tracker = {0};
    int valid = poolrad_walk_probe(capture, (size_t)length, &tracker, output);
    printf("Capture %s: %s", path, valid ? "map" : "unavailable");
    if (valid) printf(" mode=%u identity-valid=%u GEO=%u x=%u y=%u facing=%u",
        output[32], output[33], ((unsigned)output[34] << 8) | output[35],
        output[130], output[131], output[132] / 2);
    if (valid) printf(" exploration-safe=%u engine=%u epoch=%u", output[26], output[27],
        poolrad_u32(output + 28));
    puts("");
    free(capture);
}
int main(int argc, char **argv) {
    fixture(0x8000, 0x2000, 0x4000);
    assert(poolrad_probe(ram, sizeof(ram), output));
    assert(output[130] == 15 && output[131] == 1 && output[132] == 6);
    assert(memcmp(output + 176, ram + 0x4000, 1024) == 0);
    assert(memcmp(output, "PRM2", 4) == 0);
    assert(output[32] == 1 && output[33] == 1 && output[34] == 0 && output[35] == 20);
    assert(!poolrad_probe(NULL, sizeof(ram), output));
    assert(!poolrad_probe(ram, sizeof(ram), NULL));
    for (size_t size = 0; size < 0x930; size++) assert(!poolrad_probe(ram, size, output));
    fixture(0x9000, 0x3000, 0x5000); // Relocation, not fixed absolute addresses.
    assert(poolrad_probe(ram, sizeof(ram), output));
    ram[0x911] = 'X'; assert(!poolrad_probe(ram, sizeof(ram), output));
    for (int offset = 82; offset <= 84; offset++) {
        fixture(0x8000, 0x2000, 0x4000);
        ram[0x8000 + offset] = 255; assert(!poolrad_probe(ram, sizeof(ram), output));
    }
    fixture(0x8000, 0x2000, 0x4000); ram[0x8054] = 3;
    assert(!poolrad_probe(ram, sizeof(ram), output));
    uint32_t bad[] = {0, 1, 0xfffe, 0xffffff, 0x100000};
    for (unsigned i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) {
        fixture(0x8000, 0x2000, 0x4000); put32(0x904, bad[i]);
        assert(!poolrad_probe(ram, sizeof(ram), output));
        fixture(0x8000, 0x2000, 0x4000); put32(0x8000, bad[i]);
        assert(!poolrad_probe(ram, sizeof(ram), output));
        fixture(0x8000, 0x2000, 0x4000); put32(0x2000, bad[i]);
        assert(!poolrad_probe(ram, sizeof(ram), output));
    }
    fixture(0x8000, 0x2000, 0x4000); put32(0x3ff8, 0);
    assert(!poolrad_probe(ram, sizeof(ram), output));
    fixture(0x8000, 0x2000, 0x4000); memset(ram + 0x4000, 0, 1024);
    assert(!poolrad_probe(ram, sizeof(ram), output));

    const uint32_t a5 = 0x8000 + POOLRAD_GLOBALS_BACK;
    for (unsigned mode = 0; mode < 256; mode++) {
        fixture(0x8000, 0x2000, 0x4000);
        ram[a5 - POOLRAD_MODE_BACK] = mode;
        assert(poolrad_probe(ram, sizeof(ram), output));
        assert(output[32] == mode && output[33] == (mode == 1));
        if (mode != 1) unknown_identity();
    }
    for (unsigned id = 0; id <= 33; id++) {
        fixture(0x8000, 0x2000, 0x4000);
        ram[0x618b] = id;
        assert(poolrad_probe(ram, sizeof(ram), output));
        assert(output[33] == (id <= 32));
        if (id <= 32) assert(output[34] == 0 && output[35] == id);
        else unknown_identity();
    }
    fixture(0x8000, 0x2000, 0x4000); ram[0x618a] = 1; unknown_identity();
    fixture(0x8000, 0x2000, 0x4000); ram[0x618a] = ram[0x618b] = 255; unknown_identity();
    for (unsigned i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) {
        fixture(0x8000, 0x2000, 0x4000); put32(a5 - POOLRAD_STATE_BACK, bad[i]); unknown_identity();
        fixture(0x8000, 0x2000, 0x4000); put32(0x2200, bad[i]); unknown_identity();
    }
    /* Correct logical sizes despite every possible header size correction.
     * Only four-byte-aligned allocations are valid for this Mac II profile. */
    for (unsigned correction = 0; correction < 16; correction++) {
        fixture(0x8000, 0x2000, 0x4000);
        put32(0x3ff8, 0x80000000 | (correction << 24) | (1032 + correction));
        assert(poolrad_probe(ram, sizeof(ram), output) == !(correction & 3));
        fixture(0x8000, 0x2000, 0x4000);
        put32(0x5ff8, 0x80000000 | (correction << 24) | (2056 + correction));
        assert(poolrad_probe(ram, sizeof(ram), output));
        assert(output[33] == !(correction & 3));
    }
    for (unsigned tag = 0; tag < 256; tag++) {
        fixture(0x8000, 0x2000, 0x4000);
        put32(0x3ff8, (tag << 24) | 1032);
        assert(poolrad_probe(ram, sizeof(ram), output) == (tag == 0x80));
        fixture(0x8000, 0x2000, 0x4000);
        put32(0x5ff8, (tag << 24) | 2056);
        assert(poolrad_probe(ram, sizeof(ram), output));
        assert(output[33] == (tag == 0x80));
    }
    fixture(0x8000, 0x2000, 0x4000); put32(0x5ff8, 0x8000080c); unknown_identity();
    fixture(0x8000, 0x2000, 0x4000); put32(0x5ff8, 0x80000804); unknown_identity();
    fixture(0x8000, 0x2000, 0x4000); put32(0x2200, 0xf800); put32(0xf7f8, 0x8c000814); unknown_identity();
    fixture(0x8000, 0x2000, 0x4000); put32(0x2000, 0xfc00); put32(0xfbf8, 0x8c000414);
    assert(!poolrad_probe(ram, sizeof(ram), output));
    /* All pointers can relocate independently, including marked 24-bit handles. */
    fixture(0x8000, 0x2000, 0x4000);
    memcpy(ram + 0x7000 - 8, ram + 0x6000 - 8, 2056);
    put32(a5 - POOLRAD_STATE_BACK, 0xab002400); put32(0x2400, 0xcd007000);
    assert(poolrad_probe(ram, sizeof(ram), output) && output[33] == 1 && output[35] == 20);
    /* The native ID is independent of mutable geometry; Java must authenticate
     * the exact first768-byte catalog prefix before exposing a notebook key. */
    ram[0x4300] ^= 0xff;
    assert(poolrad_probe(ram, sizeof(ram), output) && output[35] == 20);
    assert(output[176 + 768] == ram[0x4300]);
    puts("Map probe: PRM2 identity, modes, logical heap sizes, relocation and bounds passed.");
    walk_tests();
    for (int i = 1; i < argc; i++) replay(argv[i]);
    return 0;
}
