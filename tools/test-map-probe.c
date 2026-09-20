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
static void status_only(unsigned mode, unsigned engine) {
    assert(memcmp(output, "PRM7", 4) == 0);
    assert(output[24] == mode && output[25] == 1 && output[26] == 0 && output[27] == engine);
    assert(output[33] == 0 && output[34] == 255 && output[35] == 255);
    for (int i = 40; i < 48; i++) assert(output[i] == 0);
    for (int i = 48; i < POOLRAD_CLOCK_OUT; i++) {
        /* A status-only packet shows no position line at all, so the search
         * marker reads unavailable rather than the cleared "not searching". */
        unsigned expected = i >= 130 && i <= 132 ? 255
            : i == POOLRAD_SEARCH_OUT ? POOLRAD_SEARCH_UNAVAILABLE : 0;
        assert(output[i] == expected);
    }
}
static void display_tests(void) {
    const uint32_t a5 = 0x8000 + POOLRAD_GLOBALS_BACK;
    poolrad_walk_tracker tracker = {0};
    fixture(0x8000, 0x2000, 0x4000);
    unsigned char original[sizeof(ram)];
    memcpy(original, ram, sizeof(ram));
    assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    assert(memcmp(output, "PRM7", 4) == 0 && output[24] == POOLRAD_DISPLAY_EXPLORATION);
    assert(output[26] == 1 && output[33] == 1 && poolrad_u32(output + 28) != 0);
    assert(output[130] == 15 && output[131] == 1 && output[132] == 6);
    assert(memcmp(output + 176, ram + 0x4000, 1024) == 0);
    assert(memcmp(original, ram, sizeof(ram)) == 0);
    for (unsigned engine = 0; engine < 256; engine++) {
        fixture(0x8000, 0x2000, 0x4000);
        ram[a5 - POOLRAD_ENGINE_BACK] = engine;
        unsigned mode = engine == 4 ? 1 : engine == 5 ? 2 : engine == 2 ? 3 : engine == 3 ? 4 : 0;
        assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
        assert(output[24] == mode && output[27] == engine);
        if (mode != 1) status_only(mode, engine);
        // Modes work without any valid local map, including out-of-grid positions.
        put32(0x8000, 0);
        assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
        status_only(mode, engine);
        ram[a5 - POOLRAD_STARTUP_BACK] = 1;
        assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
        status_only(engine <= 7 ? 5 : 0, engine);
    }
    for (unsigned presentation = 0; presentation < 256; presentation++) {
        fixture(0x8000, 0x2000, 0x4000);
        ram[a5 - POOLRAD_ENGINE_BACK] = 3;
        ram[a5 - POOLRAD_MODE_BACK] = presentation;
        assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
        status_only(presentation >= 1 && presentation <= 4 ? 4 : 0, 3);
    }
    const uint32_t flags[] = {POOLRAD_STARTUP_BACK, POOLRAD_LOADED_BACK};
    for (unsigned i = 0; i < sizeof(flags) / sizeof(flags[0]); i++) {
        for (unsigned value = 0; value < 256; value++) {
            fixture(0x8000, 0x2000, 0x4000);
            ram[a5 - flags[i]] = value;
            assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
            if (value == 0) assert(output[24] == 1 && output[26] == 1);
            else status_only(value == 1 ? 5 : 0, 4);
        }
    }
    fixture(0x8000, 0x2000, 0x4000);
    memset(ram + 0x4000, 0, 1024);
    assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    status_only(1, 4); // Position unavailable, not invented exploration coordinates.
    ram[a5 - POOLRAD_ENGINE_BACK] = 0;
    assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    status_only(0, 0); // Initial title/code wheel is not guessed to be loading.
    ram[a5 - POOLRAD_STARTUP_BACK] = 1;
    assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    status_only(5, 0); // Explicit party setup is named even before GEO loading.
    fixture(0x8000, 0x2000, 0x4000);
    ram[0x8052] = 200;
    assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    status_only(1, 4);
    fixture(0x8000, 0x2000, 0x4000);
    ram[a5 - POOLRAD_INPUT_TAG_BACK] = 0;
    assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    assert(output[24] == 1 && output[26] == 0 && output[33] == 1); // Valid display, not a footstep sample.
    // Busy text remains visible; uncommitted relocation/menu/other presentation does not.
    ram[a5 - POOLRAD_SCRIPT_OPCODE_BACK] = 0x12;
    assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    assert(output[24] == 1 && output[26] == 0 && output[130] == 15);
    for (unsigned value = 1; value < 256; value++) {
        fixture(0x8000, 0x2000, 0x4000);
        ram[a5 - POOLRAD_RELOCATION_BACK] = value;
        assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
        status_only(POOLRAD_DISPLAY_UPDATING, 4);
    }
    for (unsigned value = 0; value < 256; value++) {
        fixture(0x8000, 0x2000, 0x4000);
        ram[a5 - POOLRAD_MENU_STATE_BACK + 1] = value;
        assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
        if (value == 2) assert(output[24] == 1 && output[26] == 1);
        else status_only(POOLRAD_DISPLAY_UPDATING, 4);
        fixture(0x8000, 0x2000, 0x4000);
        ram[a5 - POOLRAD_MODE_BACK] = value;
        assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
        if (value == 1) assert(output[24] == 1 && output[26] == 1);
        else status_only(value >= 2 && value <= 4 ? POOLRAD_DISPLAY_UPDATING : 0, 4);
    }
    assert(!poolrad_display_probe(NULL, 0, &tracker, output));
    assert(!poolrad_display_probe(ram, sizeof(ram), &tracker, NULL));
    fixture(0x8000, 0x2000, 0x4000);
    assert(poolrad_display_probe(ram, sizeof(ram), NULL, output));
    assert(output[24] == 1 && output[26] == 0 && poolrad_u32(output + 28) == 0);
    for (unsigned tag = 0; tag < 256; tag++) {
        fixture(0x8000, 0x2000, 0x4000);
        put32(0x5ff8, (tag << 24) | 2056);
        assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output) == (tag == 0x80));
    }
    const uint32_t bad[] = {0, 1, 0xfffe, 0xffffff, 0x100000};
    for (unsigned i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) {
        fixture(0x8000, 0x2000, 0x4000); put32(0x904, bad[i]);
        assert(!poolrad_display_probe(ram, sizeof(ram), &tracker, output));
        fixture(0x8000, 0x2000, 0x4000); put32(a5 - POOLRAD_STATE_BACK, bad[i]);
        assert(!poolrad_display_probe(ram, sizeof(ram), &tracker, output));
        fixture(0x8000, 0x2000, 0x4000); put32(0x2200, bad[i]);
        assert(!poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    }
    fixture(0x8000, 0x2000, 0x4000);
    assert(!poolrad_display_probe(ram, a5 + 4687, &tracker, output));
    assert(poolrad_display_probe(ram, a5 + 4688, &tracker, output));
    fixture(0x9000, 0x3000, 0x5000);
    assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    assert(output[24] == 1 && output[26] == 1);
    /* Search marker. CODE3 +0x2c52 appends STRS0 +0x13de, " search", when bit 0
     * of the 16-bit field at *(A5-0x5eae)+0x594 is set; nothing else is read. */
    fixture(0x8000, 0x2000, 0x4000);
    assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    assert(output[POOLRAD_SEARCH_OUT] == POOLRAD_SEARCH_UNAVAILABLE); // No record yet.
    for (unsigned value = 0; value < 256; value++) {
        fixture(0x8000, 0x2000, 0x4000);
        put32(a5 - POOLRAD_SEARCH_BACK, 0x2400); put32(0x2400, 0x7000);
        ram[0x7000 + POOLRAD_SEARCH_FIELD] = 0xff; // The high byte is never read.
        ram[0x7000 + POOLRAD_SEARCH_FIELD + 1] = (unsigned char)value;
        assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
        assert(output[POOLRAD_SEARCH_OUT] == (value & 1));
        assert(output[130] == 15 && output[131] == 1); // Position is untouched.
    }
    const uint32_t bad_search[] = {0, 1, 0xfff, 0x2401, 0xffffff, 0x100000};
    for (unsigned i = 0; i < sizeof(bad_search) / sizeof(bad_search[0]); i++) {
        fixture(0x8000, 0x2000, 0x4000);
        put32(a5 - POOLRAD_SEARCH_BACK, bad_search[i]);
        assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
        assert(output[POOLRAD_SEARCH_OUT] == POOLRAD_SEARCH_UNAVAILABLE);
        fixture(0x8000, 0x2000, 0x4000);
        put32(a5 - POOLRAD_SEARCH_BACK, 0x2400); put32(0x2400, bad_search[i]);
        assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
        assert(output[POOLRAD_SEARCH_OUT] == POOLRAD_SEARCH_UNAVAILABLE);
    }
    /* A record that runs past the end of RAM is unavailable, never a wild read. */
    fixture(0x8000, 0x2000, 0x4000);
    put32(a5 - POOLRAD_SEARCH_BACK, 0x2400);
    put32(0x2400, (uint32_t)(sizeof(ram) - POOLRAD_SEARCH_FIELD));
    assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    assert(output[POOLRAD_SEARCH_OUT] == POOLRAD_SEARCH_UNAVAILABLE);
    /* Searching while the guest is elsewhere must not leak a position. */
    fixture(0x8000, 0x2000, 0x4000);
    put32(a5 - POOLRAD_SEARCH_BACK, 0x2400); put32(0x2400, 0x7000);
    ram[0x7000 + POOLRAD_SEARCH_FIELD + 1] = 1;
    ram[a5 - POOLRAD_ENGINE_BACK] = 5;
    assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    status_only(POOLRAD_DISPLAY_COMBAT, 5);
    fixture(0x8000, 0x2000, 0x4000);
    ram[0x910] = 6; memcpy(ram + 0x911, "Finder", 6);
    assert(!poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    puts("Display probe: PRM7 explicit modes, search marker, status-only clearing and independent profile passed.");
}
static void clock_tests(void) {
    const uint32_t a5 = 0x8000 + POOLRAD_GLOBALS_BACK;
    const unsigned limits[] = {10,10,6,24,30,12,256};
    poolrad_walk_tracker tracker = {0};
    fixture(0x8000, 0x2000, 0x4000);
    for (unsigned i=0;i<7;i++) {
        ram[a5-0x373a+i*2]=limits[i]>>8;
        ram[a5-0x373a+i*2+1]=limits[i];
    }
    unsigned char original[sizeof(ram)];
    memcpy(original,ram,sizeof(ram));
    assert(poolrad_display_probe(ram,sizeof(ram),&tracker,output));
    assert(output[1204]==1 && poolrad_u32(output+1205)==1);
    assert(output[1209]==0 && output[1210]==0 && output[1211]==0);
    assert(memcmp(original,ram,sizeof(ram))==0);
    for(unsigned i=0;i<7;i++) {
        for(unsigned value=0;value<=limits[i];value++) {
            ram[0x618c+i*2]=value>>8; ram[0x618d+i*2]=value;
            assert(poolrad_display_probe(ram,sizeof(ram),&tracker,output));
            assert(output[1204]==(value<limits[i]));
            if(value<limits[i]) {
                unsigned day=1+(i==4?value:i==5?30*value:i==6?360*value:0);
                assert(poolrad_u32(output+1205)==day);
                assert(output[1209]==(i==3?value:0));
                assert(output[1210]==(i==1?value:i==2?10*value:0));
            }
        }
        ram[0x618c+i*2]=ram[0x618d+i*2]=0;
    }
    for(unsigned mode=0;mode<8;mode++) {
        ram[a5-POOLRAD_ENGINE_BACK]=mode;
        assert(poolrad_display_probe(ram,sizeof(ram),&tracker,output));
        assert(output[1204]==(mode>=2 && mode<=5));
    }
    ram[a5-POOLRAD_ENGINE_BACK]=4;
    ram[a5-POOLRAD_STARTUP_BACK]=1;
    assert(poolrad_display_probe(ram,sizeof(ram),&tracker,output) && output[1204]==0);
    ram[a5-POOLRAD_STARTUP_BACK]=0;
    ram[0x619b]=1;
    assert(poolrad_display_probe(ram,sizeof(ram),&tracker,output) && output[1204]==0);
    ram[0x619b]=0;
    ram[a5-0x373a+1]=11;
    assert(poolrad_display_probe(ram,sizeof(ram),&tracker,output) && output[1204]==0);
    ram[a5-0x373a+1]=10;
    put32(0x2200,0xffff);
    assert(!poolrad_display_probe(ram,sizeof(ram),&tracker,output) || output[1204]==0);
    puts("Clock: units, all counter values, modes, invalid blocks and read-only behavior passed.");
}
static void tour_tests(void) {
    assert(poolrad_tour_phase(0xb166, 0x2a, 0) == 1); // Direction setter has committed; x/y not yet.
    assert(poolrad_tour_phase(0xb174, 2, 0) == 1);
    assert(poolrad_tour_phase(0xb1ae, 9, 0) == 1); // Only x committed.
    assert(poolrad_tour_phase(0xb1b5, 9, 0) == 1); // y setter may still be executing.
    assert(poolrad_tour_phase(0xb1b9, 0x2d, 0x2c90) == 2);
    assert(poolrad_tour_phase(0xb1bf, 9, 0) == 2);
    assert(poolrad_tour_phase(0xb1c3, 0x2d, 0xba03) == 2);
    assert(poolrad_tour_phase(0xb1c4, 0x3a, 0) == 2);
    for (unsigned ip = 0; ip <= 65535; ip++) {
        assert((poolrad_tour_phase(ip, 0x2d, 0x2c90) == 2) == (ip == 0xb1b9));
        assert((poolrad_tour_phase(ip, 0x2d, 0xba03) == 2) == (ip == 0xb1c3));
    }
    for (unsigned opcode = 0; opcode <= 255; opcode++) {
        assert((poolrad_tour_phase(0xb1b9, opcode, 0x2c90) == 2) == (opcode == 0x2d));
        assert((poolrad_tour_phase(0xb1ae, opcode, 0) == 1) == (opcode == 9));
    }
    // A matching IP/opcode alone cannot bless arbitrary or malformed script data.
    const uint32_t a5 = 0x8000 + POOLRAD_GLOBALS_BACK;
    fixture(0x8000, 0x2000, 0x4000);
    ram[0x618b] = 0;
    ram[a5 - POOLRAD_SCRIPT_IP_BACK] = 0xb1;
    ram[a5 - POOLRAD_SCRIPT_IP_BACK + 1] = 0xb9;
    ram[a5 - POOLRAD_SCRIPT_OPCODE_BACK] = 0x2d;
    ram[a5 - POOLRAD_CALL_HIGH_BACK] = 0x2c;
    ram[a5 - POOLRAD_CALL_LOW_BACK] = 0x90;
    ram[a5 - POOLRAD_INPUT_TAG_BACK] = 0;
    ram[a5 - POOLRAD_RELOCATION_BACK] = 1;
    const uint32_t bad[] = {0, 1, 0xffff, 0xffffff};
    for (unsigned i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) {
        put32(a5 - POOLRAD_SCRIPT_HANDLE_BACK, bad[i]);
        assert(!poolrad_tour_sample(ram, sizeof(ram), a5, 0x6000, 0));
    }
    put32(a5 - POOLRAD_SCRIPT_HANDLE_BACK, 0x2300);
    put32(0x2300, 0x9000); put32(0x8ff8, 0x80001e08);
    assert(!poolrad_tour_profile(ram, sizeof(ram), a5, 0x6000, 0)); // Synthetic script != verified route.
    poolrad_walk_tracker tracker = {0};
    assert(poolrad_display_probe(ram, sizeof(ram), &tracker, output));
    status_only(POOLRAD_DISPLAY_UPDATING, 4);
    puts("Tour probe: exact committed phases, half-coordinate rejection and missing-profile rejection passed.");
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
    // Exact story-menu / one-tile-forward paths, not arbitrary engine4/tag0.
    ram[a5 - POOLRAD_INPUT_TAG_BACK] = 0;
    ram[a5 - POOLRAD_CALL_HIGH_BACK] = 0xc0;
    ram[a5 - POOLRAD_CALL_LOW_BACK] = 0x1e;
    for (unsigned opcode = 0; opcode < 256; opcode++) {
        ram[a5 - POOLRAD_SCRIPT_OPCODE_BACK] = opcode;
        assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output));
        assert(output[26] == (opcode == 0x2b || opcode == 0x2d));
        assert(tracker.epoch == 1);
    }
    ram[a5 - POOLRAD_SCRIPT_OPCODE_BACK] = 0x2d;
    for (unsigned high = 0; high < 256; high++) {
        ram[a5 - POOLRAD_CALL_HIGH_BACK] = high;
        assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output));
        assert(output[26] == (high == 0xc0));
    }
    ram[a5 - POOLRAD_CALL_HIGH_BACK] = 0xc0;
    for (unsigned low = 0; low < 256; low++) {
        ram[a5 - POOLRAD_CALL_LOW_BACK] = low;
        assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output));
        assert(output[26] == (low == 0x1e));
    }
    ram[a5 - POOLRAD_SCRIPT_OPCODE_BACK] = 0x2b;
    ram[a5 - POOLRAD_PENDING_INPUT_BACK] = 1;
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && !output[26]);
    ram[a5 - POOLRAD_PENDING_INPUT_BACK] = 0;
    // The proven forward call changes one coordinate without a relocation flag.
    ram[a5 - POOLRAD_SCRIPT_OPCODE_BACK] = 0x2d;
    ram[a5 - POOLRAD_CALL_LOW_BACK] = 0x1e;
    ram[0x8052] = 13;
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && output[26]);
    assert(output[130] == 13 && tracker.epoch == 1);
    ram[a5 - POOLRAD_SCRIPT_OPCODE_BACK] = 0x2b;
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && output[26]);
    assert(tracker.epoch == 1);
    // General script setters still break, even if their eventual delta is one.
    ram[a5 - POOLRAD_RELOCATION_BACK] = 1;
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
    assert(tracker.epoch == 2);
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && !output[26]);
    ram[0x8052] = 12;
    ram[a5 - POOLRAD_RELOCATION_BACK] = 0;
    assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output) && output[26]);
    assert(output[130] == 12 && tracker.epoch == 2);
    for (unsigned engine = 0; engine < 8; engine++) {
        ram[a5 - POOLRAD_ENGINE_BACK] = engine;
        assert(poolrad_walk_probe(ram, sizeof(ram), &tracker, output));
        assert(output[26] == (engine == 4));
    }
    fixture(0x8000, 0x2000, 0x4000);
    memset(&tracker, 0, sizeof(tracker));
    poolrad_walk_observe(ram, sizeof(ram), &tracker);
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
/* Optional local evidence: mutate only this test's malloc-owned copy of a RAM
 * capture. Never touch the source file, emulator RAM, disks, or saved games. */
static void captured_tour_tests(unsigned char *capture, size_t length) {
    uint32_t a5;
    if (!poolrad_mode_profile(capture, length, &a5)) return;
    if (capture[a5 - POOLRAD_ENGINE_BACK] != 4
            || capture[a5 - POOLRAD_STARTUP_BACK] || capture[a5 - POOLRAD_LOADED_BACK]
            || capture[a5 - POOLRAD_MENU_STATE_BACK] != 0
            || capture[a5 - POOLRAD_MENU_STATE_BACK + 1] != 2) return;
    uint32_t handle = poolrad_u32(capture + a5 - POOLRAD_STATE_BACK) & 0x00ffffff;
    uint32_t state = poolrad_u32(capture + handle) & 0x00ffffff;
    unsigned id = ((unsigned)capture[state + 0x18a] << 8) | capture[state + 0x18b];
    if (!poolrad_tour_profile(capture, length, a5, state, id)) return;
    unsigned char *copy = malloc(length);
    assert(copy); memcpy(copy, capture, length);
    poolrad_walk_tracker tracker = {0};
    copy[a5 - POOLRAD_RELOCATION_BACK] = 0;
    copy[a5 - POOLRAD_INPUT_TAG_BACK] = 0x56;
    assert(poolrad_display_probe(copy, length, &tracker, output) && output[26]);
    uint32_t epoch = tracker.epoch;
    copy[a5 - POOLRAD_INPUT_TAG_BACK] = 0;
    copy[a5 - POOLRAD_RELOCATION_BACK] = 1;
    for (unsigned ip = 0xb166; ip <= 0xb1b9; ip++) {
        for (unsigned opcode = 0; opcode < 256; opcode++) {
            if (poolrad_tour_phase(ip, opcode, 0) != 1) continue;
            copy[a5 - POOLRAD_SCRIPT_IP_BACK] = ip >> 8;
            copy[a5 - POOLRAD_SCRIPT_IP_BACK + 1] = ip;
            copy[a5 - POOLRAD_SCRIPT_OPCODE_BACK] = opcode;
            copy[a5 - POOLRAD_CALL_HIGH_BACK] = 0;
            copy[a5 - POOLRAD_CALL_LOW_BACK] = 0;
            poolrad_walk_observe(copy, length, &tracker);
            assert(tracker.epoch == epoch && !tracker.discontinuity);
            assert(poolrad_display_probe(copy, length, &tracker, output));
            status_only(POOLRAD_DISPLAY_UPDATING, 4);
        }
    }
    copy[a5 - POOLRAD_SCRIPT_IP_BACK] = 0xb1;
    copy[a5 - POOLRAD_SCRIPT_IP_BACK + 1] = 0xb9;
    copy[a5 - POOLRAD_SCRIPT_OPCODE_BACK] = 0x2d;
    copy[a5 - POOLRAD_CALL_HIGH_BACK] = 0x2c;
    copy[a5 - POOLRAD_CALL_LOW_BACK] = 0x90;
    assert(poolrad_display_probe(copy, length, &tracker, output));
    assert(output[24] == 1 && output[26] == 1 && tracker.epoch == epoch);
    // One changed instruction or route-table byte removes the exception.
    handle = poolrad_u32(copy + a5 - POOLRAD_SCRIPT_HANDLE_BACK) & 0x00ffffff;
    uint32_t program = poolrad_u32(copy + handle) & 0x00ffffff;
    const unsigned offsets[] = {0xb145 - 0x9900, 0xb1b5 - 0x9900, 0xb5b4 - 0x9900};
    for (unsigned i = 0; i < sizeof(offsets) / sizeof(offsets[0]); i++) {
        epoch = tracker.epoch;
        copy[program + offsets[i]] ^= 1;
        assert(poolrad_display_probe(copy, length, &tracker, output));
        status_only(POOLRAD_DISPLAY_UPDATING, 4);
        assert(tracker.epoch > epoch);
        copy[program + offsets[i]] ^= 1;
        assert(poolrad_display_probe(copy, length, &tracker, output) && output[26]);
    }
    epoch = tracker.epoch;
    copy[a5 - POOLRAD_SCRIPT_ID_BACK] = 1;
    assert(poolrad_display_probe(copy, length, &tracker, output));
    status_only(POOLRAD_DISPLAY_UPDATING, 4);
    assert(tracker.epoch > epoch);
    free(copy);
    puts("Private tour replay: proven profile, half-coordinate withholding, preserved epoch and mutation rejection passed.");
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
    int valid = poolrad_display_probe(capture, (size_t)length, &tracker, output);
    printf("Capture %s: %s", path, valid ? (output[33] ? "map" : "status-only") : "unavailable");
    if (valid) printf(" mode=%u identity-valid=%u GEO=%u x=%u y=%u facing=%u",
        output[32], output[33], ((unsigned)output[34] << 8) | output[35],
        output[130], output[131], output[132] == 255 ? 255 : output[132] / 2);
    if (valid) printf(" display-mode=%u exploration-safe=%u engine=%u epoch=%u", output[24], output[26], output[27],
        poolrad_u32(output + 28));
    puts("");
    captured_tour_tests(capture, (size_t)length);
    free(capture);
}
static void travel_tests(void) {
    const uint32_t a5=0x8000+POOLRAD_GLOBALS_BACK;
    poolrad_walk_tracker t={0};
    fixture(0x8000,0x2000,0x4000);
    assert(poolrad_display_probe(ram,sizeof(ram),&t,output));
    uint32_t epoch=poolrad_u32(output+1212);
    assert(epoch && poolrad_u32(output+1216)==0);
    ram[a5-POOLRAD_RELOCATION_BACK]=1; ram[0x618b]=0;
    poolrad_walk_observe(ram,sizeof(ram),&t);
    ram[a5-POOLRAD_RELOCATION_BACK]=0;ram[0x8052]=0;ram[0x8053]=4;
    assert(poolrad_display_probe(ram,sizeof(ram),&t,output));
    assert(poolrad_u32(output+1212)==epoch && poolrad_u32(output+1216)==1);
    assert(output[1220]==1 && output[1221]==20 && output[1222]==31 && output[1224]==0);
    // An unseen middle area cannot become a direct A-to-C connection.
    ram[0x618b]=1;poolrad_walk_observe(ram,sizeof(ram),&t);
    ram[0x618b]=2;poolrad_walk_observe(ram,sizeof(ram),&t);
    assert(t.travel_serial==3);
    const unsigned guards[]={POOLRAD_STARTUP_BACK,POOLRAD_LOADED_BACK,POOLRAD_MENU_STATE_BACK-1,POOLRAD_ENGINE_BACK};
    for(unsigned i=0;i<sizeof(guards)/sizeof(guards[0]);i++) {
        fixture(0x8000,0x2000,0x4000);poolrad_walk_observe(ram,sizeof(ram),&t);
        epoch=t.travel_epoch;
        ram[a5-guards[i]]=1;poolrad_walk_observe(ram,sizeof(ram),&t);
        fixture(0x8000,0x2000,0x4000);ram[0x618b]=0;
        assert(poolrad_display_probe(ram,sizeof(ram),&t,output));
        assert(poolrad_u32(output+1212)!=epoch && output[1220]==0 && t.travel_serial==0);
    }
    epoch=t.travel_epoch;poolrad_walk_reset(&t);
    assert(poolrad_display_probe(ram,sizeof(ram),&t,output));
    assert(poolrad_u32(output+1212)!=epoch && output[1220]==0);
    unsigned char before[sizeof(ram)];memcpy(before,ram,sizeof(ram));
    poolrad_walk_observe(ram,sizeof(ram),&t);assert(!memcmp(before,ram,sizeof(ram)));
    t.travel_epoch=UINT32_MAX;poolrad_walk_reset(&t);
    assert(poolrad_display_probe(ram,sizeof(ram),&t,output));assert(poolrad_u32(output+1212)==0);
    puts("Travel: relocation, native transition count, load guards, reset, overflow and read-only checks passed.");
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
     * A Mac heap block's size is even, not a multiple of four -- these two
     * blocks are 1024 and 2048 bytes, so both rules happened to agree on them,
     * which is how the four-byte assumption survived here while it was quietly
     * discarding every 302-byte character record on Hunter's tablet. */
    for (unsigned correction = 0; correction < 16; correction++) {
        fixture(0x8000, 0x2000, 0x4000);
        put32(0x3ff8, 0x80000000 | (correction << 24) | (1032 + correction));
        assert(poolrad_probe(ram, sizeof(ram), output) == !(correction & 1));
        fixture(0x8000, 0x2000, 0x4000);
        put32(0x5ff8, 0x80000000 | (correction << 24) | (2056 + correction));
        assert(poolrad_probe(ram, sizeof(ram), output));
        assert(output[33] == !(correction & 1));
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
    travel_tests();
    walk_tests();
    display_tests();
    clock_tests();
    tour_tests();
    for (int i = 1; i < argc; i++) replay(argv[i]);
    return 0;
}
