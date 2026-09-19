/* Reuse the existing synthetic party fixture; no original game data. */
#define main party_probe_suite
#include "test-party-probe.c"
#undef main
#include "../android/minivmac/src/main/jni/src/POOLRAD_SELECTION.h"

static void word(uint32_t at, unsigned v) { ram[at] = v >> 8; ram[at + 1] = v; }
static void window_fixture(void) {
    fixture(0xe000, 0x2000, 0x3000, 6);
    ram[0xe000 - 0x6168] = 1;
    word(0xe000 - 0x5eb6, 16);
    put32(0xe000 - 0x6118, 0x5000);
    put32(0x9d6, 0x5000);
    word(0x5000 + 108, 8); ram[0x5000 + 110] = 1;
    put32(0x5000 + 134, 0x5100); put32(0x5100, 0x5200);
    ram[0x5200] = 11; memcpy(ram + 0x5201, "Information", 11);
    put32(0x5000 + 118, 0x5300); put32(0x5300, 0x5400);
    word(0x5400, 10); word(0x5402, 40); word(0x5404, 271);
    word(0x5406, 216); word(0x5408, 552);
}

int main(int argc, char **argv) {
    int x = -1, y = -1;
    window_fixture();
    unsigned char before[sizeof(ram)]; memcpy(before, ram, sizeof(ram));
    for (unsigned i = 0; i < 6; i++) {
        assert(poolrad_party_target(ram, sizeof(ram), i, 640, 480, &x, &y));
        assert(x == 283 && y == 72 + (int)i * 16);
    }
    assert(!memcmp(before, ram, sizeof(ram)));
    assert(!poolrad_party_target(ram, sizeof(ram), 6, 640, 480, &x, &y));
    word(0x5402, 80); word(0x5404, 300); word(0x5406, 256); word(0x5408, 581);
    assert(poolrad_party_target(ram, sizeof(ram), 3, 640, 480, &x, &y));
    assert(x == 312 && y == 160);
    word(0xe000 - 0x5eb6, 0);
    assert(!poolrad_party_target(ram, sizeof(ram), 0, 640, 480, &x, &y));
    window_fixture(); ram[0xe000 - 0x6168] = 0;
    assert(!poolrad_party_target(ram, sizeof(ram), 0, 640, 480, &x, &y));
    window_fixture(); ram[0x5000 + 110] = 0;
    assert(!poolrad_party_target(ram, sizeof(ram), 0, 640, 480, &x, &y));
    window_fixture(); ram[0x5201] = 'X';
    assert(!poolrad_party_target(ram, sizeof(ram), 0, 640, 480, &x, &y));
    window_fixture(); put32(0x5300, sizeof(ram) - 2);
    assert(!poolrad_party_target(ram, sizeof(ram), 0, 640, 480, &x, &y));
    window_fixture(); put32(0x9d6, 0x5600); ram[0x5600 + 110] = 1;
    put32(0x5600 + 144, 0x5000); put32(0x5600 + 114, 0x5300);
    assert(!poolrad_party_target(ram, sizeof(ram), 0, 640, 480, &x, &y));
    ram[0x5600 + 110] = 0;
    assert(poolrad_party_target(ram, sizeof(ram), 0, 640, 480, &x, &y));
    put32(0x5600 + 144, 0x5600);
    assert(!poolrad_party_target(ram, sizeof(ram), 0, 640, 480, &x, &y));
    window_fixture();
    assert(!poolrad_party_target(ram, sizeof(ram), 0, 200, 50, &x, &y));
    window_fixture(); ram[member_record(0) + POOLRAD_PARTY_SLOT_OFFSET] = 8;
    assert(!poolrad_party_target(ram, sizeof(ram), 0, 640, 480, &x, &y));
    if (argc == 2) {
        FILE *f = fopen(argv[1], "rb"); assert(f);
        fseek(f, 0, SEEK_END); long n = ftell(f); rewind(f);
        unsigned char *capture = malloc(n); assert(capture);
        assert(fread(capture, 1, n, f) == (size_t)n); fclose(f);
        for (unsigned i = 0; i < 6; i++) {
            int ok = poolrad_party_target(capture, n, i, 640, 480, &x, &y);
            printf("capture row %u: %s (%d,%d)\n", i, ok ? "available" : "refused", x, y);
        }
        free(capture);
    }
    puts("PASS party selection bounds, movement, gates, occlusion and read-only checks");
    return 0;
}
