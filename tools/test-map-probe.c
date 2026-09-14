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
    int valid = poolrad_probe(capture, (size_t)length, output);
    printf("Capture %s: %s", path, valid ? "map" : "unavailable");
    if (valid) printf(" mode=%u identity-valid=%u GEO=%u x=%u y=%u facing=%u",
        output[32], output[33], ((unsigned)output[34] << 8) | output[35],
        output[130], output[131], output[132] / 2);
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
    for (int i = 1; i < argc; i++) replay(argv[i]);
    return 0;
}
