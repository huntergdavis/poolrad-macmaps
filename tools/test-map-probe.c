/* Synthetic RAM only: no ROM, game bytes, or copyrighted map fixtures. */
#include <assert.h>
#include <stdio.h>
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
}
int main(void) {
    fixture(0x8000, 0x2000, 0x4000);
    assert(poolrad_probe(ram, sizeof(ram), output));
    assert(output[130] == 15 && output[131] == 1 && output[132] == 6);
    assert(memcmp(output + 176, ram + 0x4000, 1024) == 0);
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
    puts("Map probe: bounds, identity, relocation, geometry and facing tests passed.");
    return 0;
}
