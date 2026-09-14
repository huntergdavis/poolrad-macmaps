/* Read-only profile for the supplied Macintosh Pool of Radiance v1.1.
 * A5-relative offsets verified against the supplied Mac executable and labeled captures;
 * see docs/MAP_MEMORY.md. No RAM or save writes, heuristic ID, or DOS offset reuse.
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
#define POOLRAD_STATE_BACK 24242 /* 0x5eb2: handle to the 2,048-byte game state. */
#define POOLRAD_MODE_BACK 24201  /* 0x5e89: local-map versus outdoor presentation. */
#define POOLRAD_MODE_OUT 32
#define POOLRAD_ID_VALID_OUT 33
#define POOLRAD_ID_OUT 34
#define POOLRAD_WALK_VERSION_OUT 25
#define POOLRAD_WALK_SAFE_OUT 26
#define POOLRAD_WALK_ENGINE_OUT 27
#define POOLRAD_WALK_EPOCH_OUT 28
#define POOLRAD_ENGINE_BACK 0x5e90
#define POOLRAD_INPUT_TAG_BACK 0x617e
#define POOLRAD_PENDING_INPUT_BACK 0x60a4
#define POOLRAD_MENU_STATE_BACK 0x5ed4
#define POOLRAD_STARTUP_BACK 0x30e1
#define POOLRAD_LOADED_BACK 0x5e8b
#define POOLRAD_RELOCATION_BACK 0x1921

static uint32_t poolrad_u32(const unsigned char *p) {
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | p[3];
}
static int poolrad_range(uint32_t address, size_t count, size_t size) {
    return address < size && count <= size - address;
}
static inline int poolrad_game_name(const unsigned char *ram, size_t size) {
    static const char application[] = "Pool of Radiance v1.1";
    return ram != NULL && size >= 0x930 && ram[0x910] == sizeof(application) - 1
        && memcmp(ram + 0x911, application, sizeof(application) - 1) == 0;
}
/* Apple 24-bit movable block: physical size includes the eight-byte header and
 * the tag's low-nibble size correction. As with the party reader, allocator
 * padding is not application data. Mac II allocations are four-byte aligned. */
static int poolrad_map_block(const unsigned char *ram, size_t size,
                             uint32_t data, uint32_t logical_size) {
    uint32_t header, physical, correction;
    if (data < 0x1000 || (data & 1) || !poolrad_range(data - 8, 8, size)) return 0;
    header = poolrad_u32(ram + data - 8);
    physical = header & 0x00ffffff;
    correction = (header >> 24) & 15;
    return (header >> 28) == 8 && !(physical & 3)
        && physical == logical_size + 8 + correction
        && poolrad_range(data - 8, physical, size);
}
static int poolrad_probe(const unsigned char *ram, size_t size, unsigned char *out) {
    uint32_t a5, globals, handle, map;
    if (ram == NULL || out == NULL || size < 0x930) return 0;
    if (!poolrad_game_name(ram, size)) return 0;
    a5 = poolrad_u32(ram + 0x904) & 0x00ffffff;
    if (a5 < POOLRAD_GLOBALS_BACK || (a5 & 1)) return 0;
    globals = a5 - POOLRAD_GLOBALS_BACK;
    if (!poolrad_range(globals, POOLRAD_GLOBALS_SIZE, size)) return 0;
    handle = poolrad_u32(ram + globals) & 0x00ffffff;
    if (handle < 0x1000 || (handle & 1) || !poolrad_range(handle, 4, size)) return 0;
    map = poolrad_u32(ram + handle) & 0x00ffffff;
    if (map < 0x1000 || (map & 1) || !poolrad_range(map, 1024, size)) return 0;
    /* CODE 12 +0x4fa explicitly requests 0x400 logical bytes. */
    if (!poolrad_map_block(ram, size, map, 1024)) return 0;
    /* At the code wheel the game has allocated this block but all geometry is zero. */
    int has_geometry = 0;
    for (int i = 0; i < 512; i++) has_geometry |= ram[map + i];
    if (!has_geometry) return 0;
    if (ram[globals + 82] >= 16 || ram[globals + 83] >= 16 || ram[globals + 84] > 6 || (ram[globals + 84] & 1)) return 0;
    memset(out, 0, POOLRAD_PROBE_SIZE);
    memcpy(out, "PRM2", 4);
    memcpy(out + 4, ram + 0x910, 32);
    /* PRM2 replaces only the unused tail of the diagnostic application-name
     * field. Invalid metadata never becomes a guessed ID or legacy fallback.
     * Its failure does not hide otherwise useful map geometry or party HP. */
    out[POOLRAD_MODE_OUT] = 255;
    out[POOLRAD_ID_VALID_OUT] = 0;
    out[POOLRAD_ID_OUT] = out[POOLRAD_ID_OUT + 1] = 255;
    if (a5 >= POOLRAD_STATE_BACK && poolrad_range(a5 - POOLRAD_MODE_BACK, 1, size)
            && poolrad_range(a5 - POOLRAD_STATE_BACK, 4, size)) {
        uint32_t state_handle, state;
        out[POOLRAD_MODE_OUT] = ram[a5 - POOLRAD_MODE_BACK];
        state_handle = poolrad_u32(ram + a5 - POOLRAD_STATE_BACK) & 0x00ffffff;
        if (state_handle >= 0x1000 && !(state_handle & 1)
                && poolrad_range(state_handle, 4, size)) {
            state = poolrad_u32(ram + state_handle) & 0x00ffffff;
            if (poolrad_map_block(ram, size, state, 2048)) {
                unsigned id = ((unsigned)ram[state + 0x18a] << 8) | ram[state + 0x18b];
                if (out[POOLRAD_MODE_OUT] == 1 && id <= 32) {
                    out[POOLRAD_ID_VALID_OUT] = 1;
                    out[POOLRAD_ID_OUT] = id >> 8;
                    out[POOLRAD_ID_OUT + 1] = id;
                }
            }
        }
    }
    memcpy(out + 36, ram + 0x904, 4);
    memcpy(out + 40, ram + globals, 4);
    memcpy(out + 44, ram + handle, 4);
    memcpy(out + POOLRAD_GLOBALS_OUT, ram + globals, POOLRAD_GLOBALS_SIZE);
    memcpy(out + POOLRAD_GEOMETRY_OUT, ram + map, 1024);
    return 1;
}

/* Host state only. Never persisted as notebook identity or written to the Mac.
 * Observe on the emulator thread at tick boundaries, including between Java
 * requests. A transient input-tag clear during a normal step is NOT an epoch
 * change; entering camp/combat/load/outdoors or changing context is.
 * See MAP_MEMORY.md for the separately verified input/menu/loader guards. */
typedef struct {
    uint32_t epoch, a5, map, state, id;
    unsigned char initialized, discontinuity, exhausted;
} poolrad_walk_tracker;

static inline void poolrad_walk_advance(poolrad_walk_tracker *tracker) {
    if (tracker->epoch == UINT32_MAX) tracker->exhausted = 1;
    else tracker->epoch++;
}

/* The caller starts with a zero-initialized tracker. Keep the sequence across
 * native restarts, rather than accidentally reusing an earlier session token. */
static inline void poolrad_walk_reset(poolrad_walk_tracker *tracker) {
    if (tracker == NULL) return;
    poolrad_walk_advance(tracker);
    tracker->initialized = 0;
    tracker->discontinuity = 1;
}

static inline int poolrad_walk_update(const unsigned char *ram, size_t size,
        const unsigned char *packet, poolrad_walk_tracker *tracker,
        unsigned char *engine_out) {
    uint32_t a5 = 0, map = 0, state = 0, id = 0;
    int hard_break = 1, safe = 0;
    *engine_out = 255;
    if (packet != NULL && packet[POOLRAD_ID_VALID_OUT] == 1) {
        a5 = poolrad_u32(packet + 36) & 0x00ffffff;
        map = poolrad_u32(packet + 44) & 0x00ffffff;
        id = ((uint32_t)packet[POOLRAD_ID_OUT] << 8) | packet[POOLRAD_ID_OUT + 1];
        /* This bounded global interval contains every field below. The PRM2
         * probe has already checked the game-state handle and logical block. */
        if (a5 >= POOLRAD_INPUT_TAG_BACK
                && poolrad_range(a5 - POOLRAD_INPUT_TAG_BACK, POOLRAD_INPUT_TAG_BACK, size)) {
            uint32_t handle = poolrad_u32(ram + a5 - POOLRAD_STATE_BACK) & 0x00ffffff;
            state = poolrad_u32(ram + handle) & 0x00ffffff;
            *engine_out = ram[a5 - POOLRAD_ENGINE_BACK];
            unsigned menu = ((unsigned)ram[a5 - POOLRAD_MENU_STATE_BACK] << 8)
                | ram[a5 - POOLRAD_MENU_STATE_BACK + 1];
            hard_break = *engine_out != 4 || menu != 2
                || ram[a5 - POOLRAD_STARTUP_BACK] != 0
                || ram[a5 - POOLRAD_LOADED_BACK] != 0
                || ram[a5 - POOLRAD_RELOCATION_BACK] != 0;
            safe = !hard_break && ram[a5 - POOLRAD_INPUT_TAG_BACK] == 0x56
                && ram[a5 - POOLRAD_PENDING_INPUT_BACK] == 0;
        }
    }
    if (tracker == NULL) return 0;
    if (!tracker->initialized || (hard_break && !tracker->discontinuity)
            || tracker->a5 != a5 || tracker->map != map
            || tracker->state != state || tracker->id != id) {
        poolrad_walk_advance(tracker);
    }
    tracker->initialized = 1;
    tracker->discontinuity = (unsigned char)hard_break;
    tracker->a5 = a5; tracker->map = map; tracker->state = state; tracker->id = id;
    return safe && !tracker->exhausted;
}

static inline void poolrad_walk_observe(const unsigned char *ram, size_t size,
                                       poolrad_walk_tracker *tracker) {
    unsigned char packet[POOLRAD_PROBE_SIZE], engine;
    /* System 7 minor switches replace CurApName/CurrentA5 even while the game
     * remains frontmost. A bounded non-game application name is an observation
     * gap, not evidence that the game moved or loaded. Do not read cached game
     * pointers or clear a previously observed hard break. Malformed RAM/name,
     * or the actual game name with bad A5/state, still follows the hard-break
     * path. A Java-requested non-game sample remains unavailable below. */
    if (ram != NULL && size >= 0x930 && ram[0x910] > 0 && ram[0x910] <= 31
            && !poolrad_game_name(ram, size)) return;
    int valid = poolrad_probe(ram, size, packet);
    (void)poolrad_walk_update(ram, size, valid ? packet : NULL, tracker, &engine);
}

/* PRM3 is explicit: PRM1/2 copied an unvalidated diagnostic-name tail into
 * bytes25..31, so those older packets must NEVER enable movement recording.
 * Java still authenticates the GEO number + complete immutable map prefix. */
static inline int poolrad_walk_probe(const unsigned char *ram, size_t size,
        poolrad_walk_tracker *tracker, unsigned char *out) {
    unsigned char engine;
    int valid = poolrad_probe(ram, size, out);
    int safe = poolrad_walk_update(ram, size, valid ? out : NULL, tracker, &engine);
    if (!valid) return 0;
    memcpy(out, "PRM3", 4);
    out[POOLRAD_WALK_VERSION_OUT] = 1;
    out[POOLRAD_WALK_SAFE_OUT] = (unsigned char)safe;
    out[POOLRAD_WALK_ENGINE_OUT] = engine;
    uint32_t epoch = tracker != NULL && !tracker->exhausted ? tracker->epoch : 0;
    out[POOLRAD_WALK_EPOCH_OUT] = epoch >> 24;
    out[POOLRAD_WALK_EPOCH_OUT + 1] = epoch >> 16;
    out[POOLRAD_WALK_EPOCH_OUT + 2] = epoch >> 8;
    out[POOLRAD_WALK_EPOCH_OUT + 3] = epoch;
    return 1;
}
#endif
