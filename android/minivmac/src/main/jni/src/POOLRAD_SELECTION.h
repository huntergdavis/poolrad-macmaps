/* Read-only Information-window hit testing. Evidence: docs/PARTY_SELECTION.md. */
#ifndef POOLRAD_SELECTION_H
#define POOLRAD_SELECTION_H
#include "POOLRAD_PARTY.h"

static unsigned poolrad_selection_u16(const unsigned char *p) {
    return ((unsigned)p[0] << 8) | p[1];
}

static int poolrad_selection_region(const unsigned char *ram, size_t size,
        uint32_t handle, int *top, int *left, int *bottom, int *right) {
    uint32_t p;
    if (handle < 0x1000 || (handle & 1) || !poolrad_range(handle, 4, size)) return 0;
    p = poolrad_u32(ram + handle) & 0xffffff;
    if (p < 0x1000 || (p & 1) || !poolrad_range(p, 10, size)) return 0;
    unsigned length = poolrad_selection_u16(ram + p);
    if (length < 10 || (length & 1) || !poolrad_range(p, length, size)) return 0;
    *top = (int16_t)poolrad_selection_u16(ram + p + 2);
    *left = (int16_t)poolrad_selection_u16(ram + p + 4);
    *bottom = (int16_t)poolrad_selection_u16(ram + p + 6);
    *right = (int16_t)poolrad_selection_u16(ram + p + 8);
    return *top <= *bottom && *left <= *right;
}

/* Called only at the emulation-thread sample boundary, immediately before input.
 * Return guest coordinates, never a guest pointer. No guest memory is written. */
static int poolrad_party_target(const unsigned char *ram, size_t size,
        unsigned member, int width, int height, int *x, int *y) {
    unsigned char sample[POOLRAD_PARTY_SIZE];
    uint32_t a5, window, title, p, seen[64];
    int top, left, bottom, right, row_height, tx, ty;
    if (!poolrad_party_probe(ram, size, sample) || member >= sample[4]) return 0;
    a5 = poolrad_u32(ram + 0x904) & 0xffffff;
    if (a5 < 0x6168 || !poolrad_range(a5 - 0x6168, 0x2b4, size)
            || ram[a5 - 0x6168] != 1) return 0;
    /* The game's click handler indexes the unfiltered chain. The party probe
     * validates every link, but omits monsters. Refuse an interleaved roster
     * rather than letting a companion row become a different guest row. */
    p = poolrad_u32(ram + a5 - POOLRAD_PARTY_HEAD_BACK) & 0xffffff;
    for (unsigned i = 0; i <= member; i++) {
        if (!p) return 0;
        uint32_t record = poolrad_u32(ram + p) & 0xffffff;
        if (ram[record + POOLRAD_PARTY_SLOT_OFFSET] >= POOLRAD_PARTY_MAX_MEMBERS) return 0;
        p = poolrad_u32(ram + record + POOLRAD_PARTY_NEXT_OFFSET) & 0xffffff;
    }
    window = poolrad_u32(ram + a5 - 0x6118) & 0xffffff;
    if (window < 0x1000 || (window & 1) || !poolrad_range(window, 156, size)
            || poolrad_selection_u16(ram + window + 108) != 8 || !ram[window + 110]) return 0;
    title = poolrad_u32(ram + window + 134) & 0xffffff;
    if (title < 0x1000 || (title & 1) || !poolrad_range(title, 4, size)) return 0;
    title = poolrad_u32(ram + title) & 0xffffff;
    if (title < 0x1000 || !poolrad_range(title, 12, size) || ram[title] != 11
            || memcmp(ram + title + 1, "Information", 11)) return 0;
    if (!poolrad_selection_region(ram, size, poolrad_u32(ram + window + 118) & 0xffffff,
            &top, &left, &bottom, &right)) return 0;
    row_height = (int16_t)poolrad_selection_u16(ram + a5 - 0x5eb6);
    if (row_height < 8 || row_height > 64) return 0;
    /* CODE2 +0x565a..0x566e: index=(localY+height/2)/height-2.
     * portRect.top is the local origin; contentRgn is in global coordinates. */
    tx = left + 12;
    ty = top + ((int)member + 2) * row_height - (int16_t)poolrad_selection_u16(ram + window + 16);
    if (tx < 0 || ty < 0 || tx >= width || ty >= height
            || tx >= right || ty < top || ty >= bottom) return 0;
    /* Reject occlusion using conservative structure bounding rectangles. This
     * also excludes modal dialogs; a hidden/missing/cyclic chain is refused. */
    if (!poolrad_range(0x9d6, 4, size)) return 0;
    p = poolrad_u32(ram + 0x9d6) & 0xffffff;
    for (unsigned n = 0; n < 64; n++) {
        if (p < 0x1000 || (p & 1) || !poolrad_range(p, 156, size)) return 0;
        for (unsigned i = 0; i < n; i++) if (seen[i] == p) return 0;
        seen[n] = p;
        if (p == window) { *x = tx; *y = ty; return 1; }
        if (ram[p + 110]) {
            if (!poolrad_selection_region(ram, size, poolrad_u32(ram + p + 114) & 0xffffff,
                    &top, &left, &bottom, &right)) return 0;
            if (tx >= left && tx < right && ty >= top && ty < bottom) return 0;
        }
        p = poolrad_u32(ram + p + 144) & 0xffffff;
    }
    return 0;
}
#endif
