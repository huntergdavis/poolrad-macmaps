/* Diagnostic adapter for the exact native probe. Input/output remain private under scratch. */
#include <stdio.h>
#include <stdlib.h>
#include "../android/minivmac/src/main/jni/src/POOLRAD.h"
int main(int argc, char **argv) {
    int display = argc == 3 && strcmp(argv[2], "--display") == 0;
    if (argc != 2 && !display) { fprintf(stderr, "Usage: probe-ram INPUT.ram [--display] > OUTPUT.probe\n"); return 2; }
    FILE *f = fopen(argv[1], "rb");
    if (!f) return 2;
    if (fseek(f, 0, SEEK_END) != 0) return 2;
    long length = ftell(f);
    if (length < 0 || length > 16 * 1024 * 1024 || fseek(f, 0, SEEK_SET) != 0) return 2;
    unsigned char *ram = malloc((size_t)length);
    if (!ram || fread(ram, 1, (size_t)length, f) != (size_t)length) return 2;
    fclose(f);
    unsigned char probe[POOLRAD_PROBE_SIZE];
    poolrad_walk_tracker tracker = {0};
    int valid = display ? poolrad_display_probe(ram, (size_t)length, &tracker, probe)
        : poolrad_probe(ram, (size_t)length, probe);
    free(ram);
    if (!valid) { fprintf(stderr, "No supported loaded area\n"); return 1; }
    fprintf(stderr, "x=%u y=%u facing=%u A5=%06x map=%06x\n", probe[130], probe[131], probe[132] / 2,
        poolrad_u32(probe + 36), poolrad_u32(probe + 44));
    return fwrite(probe, 1, sizeof(probe), stdout) == sizeof(probe) ? 0 : 2;
}
