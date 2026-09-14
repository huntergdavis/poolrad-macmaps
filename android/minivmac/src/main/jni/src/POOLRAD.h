/* Read-only profile for the supplied Macintosh Pool of Radiance v1.1.
 * A5-relative offsets discovered from labeled local captures; see docs/LOCAL_TESTING.md.
 * Kept independent of JNI so the actual native bounds checks can be tested on the host.
 */
#ifndef POOLRAD_PROBE_H
#define POOLRAD_PROBE_H
#include <stdint.h>
#include <stddef.h>
#include <string.h>

#define POOLRAD_PROBE_SIZE 1200
#define POOLRAD_GLOBALS_BACK 15168
#define POOLRAD_GLOBALS_SIZE 128
#define POOLRAD_GLOBALS_OUT 48
#define POOLRAD_GEOMETRY_OUT 176

static uint32_t poolrad_u32(const unsigned char *p) {
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | p[3];
}
static int poolrad_range(uint32_t address, size_t count, size_t size) {
    return address < size && count <= size - address;
}
static int poolrad_probe(const unsigned char *ram, size_t size, unsigned char *out) {
    static const char application[] = "Pool of Radiance v1.1";
    uint32_t a5, globals, handle, map;
    if (ram == NULL || out == NULL || size < 0x930) return 0;
    if (ram[0x910] != sizeof(application) - 1 || memcmp(ram + 0x911, application, sizeof(application) - 1) != 0) return 0;
    a5 = poolrad_u32(ram + 0x904) & 0x00ffffff;
    if (a5 < POOLRAD_GLOBALS_BACK || (a5 & 1)) return 0;
    globals = a5 - POOLRAD_GLOBALS_BACK;
    if (!poolrad_range(globals, POOLRAD_GLOBALS_SIZE, size)) return 0;
    handle = poolrad_u32(ram + globals) & 0x00ffffff;
    if (handle < 0x1000 || (handle & 1) || !poolrad_range(handle, 4, size)) return 0;
    map = poolrad_u32(ram + handle) & 0x00ffffff;
    if (map < 0x1000 || (map & 1) || !poolrad_range(map, 1024, size)) return 0;
    /* Observed movable block is exactly 1,024 bytes + the eight-byte Mac heap header. */
    if ((poolrad_u32(ram + map - 8) & 0x00ffffff) != 1032) return 0;
    /* At the code wheel the game has allocated this block but all geometry is zero. */
    int has_geometry = 0;
    for (int i = 0; i < 512; i++) has_geometry |= ram[map + i];
    if (!has_geometry) return 0;
    if (ram[globals + 82] >= 16 || ram[globals + 83] >= 16 || ram[globals + 84] > 6 || (ram[globals + 84] & 1)) return 0;
    memset(out, 0, POOLRAD_PROBE_SIZE);
    memcpy(out, "PRM1", 4);
    memcpy(out + 4, ram + 0x910, 32);
    memcpy(out + 36, ram + 0x904, 4);
    memcpy(out + 40, ram + globals, 4);
    memcpy(out + 44, ram + handle, 4);
    memcpy(out + POOLRAD_GLOBALS_OUT, ram + globals, POOLRAD_GLOBALS_SIZE);
    memcpy(out + POOLRAD_GEOMETRY_OUT, ram + map, 1024);
    return 1;
}
#endif
