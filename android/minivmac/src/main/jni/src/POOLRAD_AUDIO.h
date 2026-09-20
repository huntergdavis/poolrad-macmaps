/* Read-only host audio policy for the verified Pool of Radiance v1.1 layout. */
#ifndef POOLRAD_AUDIO_H
#define POOLRAD_AUDIO_H
#include "POOLRAD.h"

/* CODE2 +40a0/+40ae toggle bits 0/1; +41c6/+41ea draw the checkmarks.
 * CODE2 +58bc checks bit 0 before every sound, and bit 1 additionally for
 * footsteps (sound 10). Set means OFF. Unknown applications keep their audio.
 */
static int poolrad_audio_options(const unsigned char *ram, size_t size) {
    if (!poolrad_game_name(ram, size)) return -1;
    uint32_t a5 = poolrad_u32(ram + 0x904) & 0xffffff;
    if ((a5 & 1) || a5 < 0x6228 || !poolrad_range(a5 - 0x6228, 4, size)) return -1;
    uint32_t options = poolrad_u32(ram + a5 - 0x6228);
    return options <= 15 ? (int)options : -1;
}

/* System 7 minor switches replace both CurApName and CurrentA5 with Finder's
 * world for a few ticks. Unknown state is not a player request to unmute.
 * Keep only the last verified value, never dereference a cached guest pointer.
 * Caller initializes *last_options to -1 on a new emulator session. */
static int poolrad_audio_observe(const unsigned char *ram, size_t size, int *last_options) {
    int options = poolrad_audio_options(ram, size);
    if (options >= 0) *last_options = options;
    return *last_options;
}
#endif
