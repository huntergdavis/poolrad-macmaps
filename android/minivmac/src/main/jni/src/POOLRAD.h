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

#define POOLRAD_PROBE_SIZE 1228
#define POOLRAD_TRAVEL_OUT 1212
#define POOLRAD_CLOCK_OUT 1204
#define POOLRAD_GLOBALS_BACK 15168
#define POOLRAD_GLOBALS_SIZE 128
#define POOLRAD_GLOBALS_OUT 48
#define POOLRAD_GEOMETRY_OUT 176
#define POOLRAD_STATE_BACK 24242 /* 0x5eb2: handle to the 2,048-byte game state. */
#define POOLRAD_MODE_BACK 24201  /* 0x5e89: local-map versus outdoor presentation. */
#define POOLRAD_MODE_OUT 32
#define POOLRAD_ID_VALID_OUT 33
#define POOLRAD_ID_OUT 34
/* CODE3 +0x2c52 appends STRS0 +0x13de, " search", to the game's own position
 * line when bit 0 of the 16-bit field at *(A5-0x5eae)+0x594 is set. That bit is
 * the only thing reported here; the rest of the record is not interpreted.
 */
#define POOLRAD_SEARCH_BACK 0x5eae
#define POOLRAD_SEARCH_FIELD 0x594
#define POOLRAD_SEARCH_OUT 1200
#define POOLRAD_SEARCH_UNAVAILABLE 255
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
#define POOLRAD_SCRIPT_OPCODE_BACK 0x2f60
#define POOLRAD_CALL_HIGH_BACK 0x2f16
#define POOLRAD_CALL_LOW_BACK 0x2ed5
#define POOLRAD_SCRIPT_HANDLE_BACK 0x5ea6
#define POOLRAD_SCRIPT_ID_BACK 0x192b
#define POOLRAD_SCRIPT_IP_BACK 0x5e96
#define POOLRAD_DISPLAY_MODE_OUT 24
#define POOLRAD_DISPLAY_UNAVAILABLE 0
#define POOLRAD_DISPLAY_EXPLORATION 1
#define POOLRAD_DISPLAY_COMBAT 2
#define POOLRAD_DISPLAY_CAMP 3
#define POOLRAD_DISPLAY_WILDERNESS 4
#define POOLRAD_DISPLAY_LOADING 5
#define POOLRAD_DISPLAY_UPDATING 6

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
 * padding is not application data. */
/*
 * Even, not four-byte aligned.
 *
 * This said "Mac II allocations are four-byte aligned", which was generalised
 * from one emulator's heap, where these records happened to carry a two-byte
 * size correction. Hunter's tablet reported the block header 0x80000136 for a
 * character record: relocatable, size correction 0, physical 310 = the 302-byte
 * record plus its 8-byte header and no padding at all -- the canonical case.
 * That is a perfectly valid block, and the alignment test threw it away, which
 * is why his party never once appeared while his map worked. The Memory
 * Manager's actual invariant on the 24-bit heap is that a block size is even
 * (Inside Macintosh: Memory, Memory Manager pp2-22..2-23); the exact physical
 * size is already pinned by the logical size and the correction nibble beside
 * this test, so evenness is all the alignment there is to check.
 */
static int poolrad_map_block(const unsigned char *ram, size_t size,
                             uint32_t data, uint32_t logical_size) {
    uint32_t header, physical, correction;
    if (data < 0x1000 || (data & 1) || !poolrad_range(data - 8, 8, size)) return 0;
    header = poolrad_u32(ram + data - 8);
    physical = header & 0x00ffffff;
    correction = (header >> 24) & 15;
    return (header >> 28) == 8 && !(physical & 1)
        && physical == logical_size + 8 + correction
        && poolrad_range(data - 8, physical, size);
}
/* CODE3 +2b6e prints hour +192 and minutes 10*(+190)+(+18e).
 * CODE4 +2126 normalizes the seven counters using A5-373a's unit table;
 * +2202 updates the state at +18c. See GAME_CLOCK.md for day numbering and
 * the deliberately unsupported high-year/overflow cases. */
static void poolrad_clock(const unsigned char *ram, size_t size, uint32_t a5,
                          unsigned mode, unsigned char *out) {
    static const unsigned limits[] = {10, 10, 6, 24, 30, 12, 256};
    unsigned values[7];
    memset(out + POOLRAD_CLOCK_OUT, 0, 8);
    if (mode < 1 || mode > 4 || a5 < POOLRAD_STATE_BACK
            || !poolrad_range(a5 - POOLRAD_STATE_BACK, 4, size)
            || !poolrad_range(a5 - 0x373a, 14, size)) return;
    uint32_t handle = poolrad_u32(ram + a5 - POOLRAD_STATE_BACK) & 0xffffff;
    if (handle < 0x1000 || (handle & 1) || !poolrad_range(handle, 4, size)) return;
    uint32_t state = poolrad_u32(ram + handle) & 0xffffff;
    if (!poolrad_map_block(ram, size, state, 2048)) return;
    for (unsigned i = 0; i < 7; i++) {
        unsigned at = state + 0x18c + i * 2;
        unsigned unit = a5 - 0x373a + i * 2;
        values[i] = ((unsigned)ram[at] << 8) | ram[at + 1];
        if (values[i] >= limits[i]
                || (((unsigned)ram[unit] << 8) | ram[unit + 1]) != limits[i]) return;
    }
    if (ram[state + 0x19a] || ram[state + 0x19b]) return;
    uint32_t day = 1 + values[4] + 30 * values[5] + 360 * values[6];
    out[POOLRAD_CLOCK_OUT] = 1;
    out[POOLRAD_CLOCK_OUT + 1] = day >> 24;
    out[POOLRAD_CLOCK_OUT + 2] = day >> 16;
    out[POOLRAD_CLOCK_OUT + 3] = day >> 8;
    out[POOLRAD_CLOCK_OUT + 4] = day;
    out[POOLRAD_CLOCK_OUT + 5] = values[3];
    out[POOLRAD_CLOCK_OUT + 6] = values[2] * 10 + values[1];
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
    /* Search mode is a separate record from the 2,048-byte game state above. */
    out[POOLRAD_SEARCH_OUT] = POOLRAD_SEARCH_UNAVAILABLE;
    if (a5 >= POOLRAD_SEARCH_BACK && poolrad_range(a5 - POOLRAD_SEARCH_BACK, 4, size)) {
        uint32_t search_handle = poolrad_u32(ram + a5 - POOLRAD_SEARCH_BACK) & 0x00ffffff;
        if (search_handle >= 0x1000 && !(search_handle & 1)
                && poolrad_range(search_handle, 4, size)) {
            uint32_t record = poolrad_u32(ram + search_handle) & 0x00ffffff;
            if (record >= 0x1000 && !(record & 1)
                    && poolrad_range(record, POOLRAD_SEARCH_FIELD + 2, size)) {
                out[POOLRAD_SEARCH_OUT] = ram[record + POOLRAD_SEARCH_FIELD + 1] & 1;
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
    uint32_t travel_epoch, travel_serial, travel_a5, travel_state, travel_id;
    unsigned char travel_initialized, travel_broken, travel_exhausted;
    unsigned char last_valid, last_tile, last_facing, from_valid, from_id, from_tile, from_facing;
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
    if (tracker->travel_epoch == UINT32_MAX) tracker->travel_exhausted = 1;
    else tracker->travel_epoch++;
    tracker->travel_initialized = 0;
    tracker->travel_broken = 1;
    tracker->last_valid = tracker->from_valid = 0;
}

/* New Phlan's original 34-entry Rolf route uses table-driven SAVE x/y,
 * not CALL c01e. Authenticate only its immutable 128-byte instruction loop
 * and 136-byte route tables, never the mutable character variables. FNV-1a is
 * a narrow version-profile checksum, not a security/authentication primitive.
 * No original script payload is distributed. All offsets are VM addresses.
 * The caller has already bounded A5 and the 2,048-byte state allocation. */
static inline int poolrad_tour_profile(const unsigned char *ram, size_t size,
                                      uint32_t a5, uint32_t state, uint32_t id) {
    if (id != 0 || ram[a5 - POOLRAD_SCRIPT_ID_BACK] != 0 || ram[state + 0x1e5] != 0) return 0;
    uint32_t handle = poolrad_u32(ram + a5 - POOLRAD_SCRIPT_HANDLE_BACK) & 0x00ffffff;
    if (handle < 0x1000 || (handle & 1) || !poolrad_range(handle, 4, size)) return 0;
    uint32_t program = poolrad_u32(ram + handle) & 0x00ffffff;
    if (!poolrad_map_block(ram, size, program, 7680)) return 0;
    const unsigned starts[] = {0xb145 - 0x9900, 0xb592 - 0x9900};
    const unsigned lengths[] = {128, 136};
    uint64_t hash = UINT64_C(0xcbf29ce484222325);
    for (unsigned section = 0; section < 2; section++) {
        for (unsigned i = 0; i < lengths[section]; i++) {
            hash ^= ram[program + starts[section] + i];
            hash *= UINT64_C(0x100000001b3);
        }
    }
    return hash == UINT64_C(0x5b12181c49eae5fa);
}

/* 1 preserves continuity while the verified tour commits direction/x/y but
 * cannot expose a partly written position. 2 means both coordinates have
 * committed and the redraw / following sound-delay is displaying that tile.
 * The operand loader advances IP before the native setter/redraw executes. */
static inline int poolrad_tour_phase(unsigned ip, unsigned opcode, unsigned call) {
    if ((ip == 0xb1b9 && opcode == 0x2d && call == 0x2c90)
            || (ip == 0xb1bf && opcode == 9)
            || (ip == 0xb1c3 && opcode == 0x2d && call == 0xba03)
            || (ip == 0xb1c4 && opcode == 0x3a)) return 2;
    if ((ip >= 0xb166 && ip <= 0xb170 && opcode == 0x2a)
            || (ip >= 0xb170 && ip <= 0xb174 && opcode == 2)
            || (ip == 0xb1a7 && opcode == 2)
            || (ip >= 0xb1a7 && ip <= 0xb1b5 && opcode == 9)
            || (ip >= 0xb1b5 && ip <= 0xb1b9 && opcode == 0x2d)) return 1;
    return 0;
}

static inline int poolrad_tour_sample(const unsigned char *ram, size_t size,
                                     uint32_t a5, uint32_t state, uint32_t id) {
    unsigned ip = ((unsigned)ram[a5 - POOLRAD_SCRIPT_IP_BACK] << 8)
        | ram[a5 - POOLRAD_SCRIPT_IP_BACK + 1];
    unsigned call = ((unsigned)ram[a5 - POOLRAD_CALL_HIGH_BACK] << 8)
        | ram[a5 - POOLRAD_CALL_LOW_BACK];
    int phase = poolrad_tour_phase(ip, ram[a5 - POOLRAD_SCRIPT_OPCODE_BACK], call);
    return phase && poolrad_tour_profile(ram, size, a5, state, id) ? phase : 0;
}

/* Unlike footprints, actual area travel permits script relocation and GEO changes.
 * The existing verified load/menu/engine guards still break this independent
 * epoch. Count every native GEO change so Java cannot bridge an unseen area.
 * Source square is the last settled native observation, never a guessed edge. */
static inline int poolrad_mode_profile(const unsigned char *, size_t, uint32_t *);
static inline void poolrad_travel_update(const unsigned char *ram, size_t size,
        const unsigned char *packet, int safe, poolrad_walk_tracker *t) {
    uint32_t a5 = 0, state = 0, id = 0;
    int valid = poolrad_mode_profile(ram, size, &a5);
    if (valid) {
        uint32_t handle = poolrad_u32(ram + a5 - POOLRAD_STATE_BACK) & 0xffffff;
        state = poolrad_u32(ram + handle) & 0xffffff;
        id = ((unsigned)ram[state + 0x18a] << 8) | ram[state + 0x18b];
        valid = id <= 32 && ram[a5 - POOLRAD_ENGINE_BACK] == 4
            && ram[a5 - POOLRAD_MODE_BACK] == 1
            && ram[a5 - POOLRAD_MENU_STATE_BACK] == 0
            && ram[a5 - POOLRAD_MENU_STATE_BACK + 1] == 2
            && ram[a5 - POOLRAD_STARTUP_BACK] == 0
            && ram[a5 - POOLRAD_LOADED_BACK] == 0;
    }
    if (!t->travel_initialized || (!valid && !t->travel_broken)
            || (valid && (t->travel_a5 != a5 || t->travel_state != state))) {
        if (t->travel_epoch == UINT32_MAX) t->travel_exhausted = 1;
        else t->travel_epoch++;
        t->travel_serial = 0;
        t->last_valid = t->from_valid = 0;
        t->travel_id = id;
    }
    if (valid && t->travel_broken) {
        t->travel_id = id; t->last_valid = t->from_valid = 0;
    }
    t->travel_initialized = 1;
    t->travel_broken = !valid;
    if (!valid) return;
    t->travel_a5 = a5; t->travel_state = state;
    if (id != t->travel_id) {
        if (t->travel_serial == UINT32_MAX) t->travel_exhausted = 1;
        else t->travel_serial++;
        t->from_valid = t->last_valid;
        t->from_id = t->travel_id; t->from_tile = t->last_tile; t->from_facing = t->last_facing;
        t->travel_id = id; t->last_valid = 0;
    }
    if (safe && packet != NULL && packet[POOLRAD_ID_VALID_OUT] == 1
            && packet[POOLRAD_ID_OUT + 1] == id) {
        t->last_valid = 1;
        t->last_tile = packet[131] * 16 + packet[130];
        t->last_facing = packet[132] / 2;
    }
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
            int tour = poolrad_tour_sample(ram, size, a5, state, id);
            unsigned relocation = ram[a5 - POOLRAD_RELOCATION_BACK];
            hard_break = *engine_out != 4 || menu != 2
                || ram[a5 - POOLRAD_STARTUP_BACK] != 0
                || ram[a5 - POOLRAD_LOADED_BACK] != 0
                || (relocation != 0 && !(relocation == 1 && tour));
            unsigned tag = ram[a5 - POOLRAD_INPUT_TAG_BACK];
            unsigned opcode = ram[a5 - POOLRAD_SCRIPT_OPCODE_BACK];
            /* Rolf's Continue menu deliberately uses input tag zero. Original
             * CODE5 3cdc->19de->1a44 proves opcode2b is that input path. The
             * exact CALL c01e (CODE5 2e8e->1d8e) advances one tile; other CALLs
             * or arbitrary busy script opcodes are not movement samples. */
            int story_position = tag == 0 && (opcode == 0x2b
                || (opcode == 0x2d && ram[a5 - POOLRAD_CALL_HIGH_BACK] == 0xc0
                    && ram[a5 - POOLRAD_CALL_LOW_BACK] == 0x1e));
            safe = !hard_break && (relocation == 0 || tour == 2)
                && (tag == 0x56 || story_position || (tag == 0 && tour == 2))
                && ram[a5 - POOLRAD_PENDING_INPUT_BACK] == 0;
        }
    }
    if (tracker == NULL) return 0;
    poolrad_travel_update(ram, size, packet, safe, tracker);
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

/* M2 can identify a guest mode without a valid local map. Authenticate the
 * separate 2,048-byte state allocation, not a stale or zero-filled GEO block.
 * CODE 0 declares 25,572 bytes below A5 and 4,688 above it. Checking that whole
 * interval bounds every mode field even during map replacement/startup. */
static inline int poolrad_mode_profile(const unsigned char *ram, size_t size,
                                      uint32_t *a5_out) {
    uint32_t a5, handle, state;
    if (!poolrad_game_name(ram, size)) return 0;
    a5 = poolrad_u32(ram + 0x904) & 0x00ffffff;
    if (a5 < 25572 || (a5 & 1) || !poolrad_range(a5 - 25572, 25572 + 4688, size)) return 0;
    handle = poolrad_u32(ram + a5 - POOLRAD_STATE_BACK) & 0x00ffffff;
    if (handle < 0x1000 || (handle & 1) || !poolrad_range(handle, 4, size)) return 0;
    state = poolrad_u32(ram + handle) & 0x00ffffff;
    if (!poolrad_map_block(ram, size, state, 2048)) return 0;
    *a5_out = a5;
    return 1;
}

static inline int poolrad_display_probe(const unsigned char *ram, size_t size,
        poolrad_walk_tracker *tracker, unsigned char *out) {
    uint32_t a5;
    if (out == NULL) return 0;
    int has_map = poolrad_walk_probe(ram, size, tracker, out);
    if (!poolrad_mode_profile(ram, size, &a5)) return 0;
    unsigned engine = ram[a5 - POOLRAD_ENGINE_BACK];
    unsigned presentation = ram[a5 - POOLRAD_MODE_BACK];
    unsigned startup = ram[a5 - POOLRAD_STARTUP_BACK];
    unsigned loaded = ram[a5 - POOLRAD_LOADED_BACK];
    unsigned mode = POOLRAD_DISPLAY_UNAVAILABLE;
    /* Only proven engine values are named. The setup flag spans initial party
     * selection as well as loading; the UI must say Loading / setup. */
    if (engine <= 7 && startup <= 1 && loaded <= 1 && presentation >= 1 && presentation <= 4) {
        if (startup || loaded) mode = POOLRAD_DISPLAY_LOADING;
        else if (engine == 4) {
            unsigned menu = ((unsigned)ram[a5 - POOLRAD_MENU_STATE_BACK] << 8)
                | ram[a5 - POOLRAD_MENU_STATE_BACK + 1];
            unsigned relocation = ram[a5 - POOLRAD_RELOCATION_BACK];
            int committed_tour = 0;
            if (has_map && out[POOLRAD_ID_VALID_OUT] == 1 && relocation == 1) {
                uint32_t handle = poolrad_u32(ram + a5 - POOLRAD_STATE_BACK) & 0x00ffffff;
                uint32_t state = poolrad_u32(ram + handle) & 0x00ffffff;
                unsigned id = ((unsigned)out[POOLRAD_ID_OUT] << 8) | out[POOLRAD_ID_OUT + 1];
                committed_tour = poolrad_tour_sample(ram, size, a5, state, id) == 2;
            }
            mode = presentation == 1 && menu == 2 && (relocation == 0 || committed_tour)
                ? POOLRAD_DISPLAY_EXPLORATION : POOLRAD_DISPLAY_UPDATING;
        }
        else if (engine == 5) mode = POOLRAD_DISPLAY_COMBAT;
        else if (engine == 2) mode = POOLRAD_DISPLAY_CAMP;
        else if (engine == 3) mode = POOLRAD_DISPLAY_WILDERNESS;
    }
    if (!has_map) {
        memset(out, 0, POOLRAD_PROBE_SIZE);
        memcpy(out + 4, ram + 0x910, 20);
        memcpy(out + 36, ram + 0x904, 4);
        /* The clear above would read as "not searching"; without a map sample
         * the search record was never validated, so say unavailable instead. */
        out[POOLRAD_SEARCH_OUT] = POOLRAD_SEARCH_UNAVAILABLE;
    }
    memcpy(out, "PRM7", 4);
    out[POOLRAD_DISPLAY_MODE_OUT] = (unsigned char)mode;
    out[POOLRAD_WALK_VERSION_OUT] = 1;
    out[POOLRAD_WALK_ENGINE_OUT] = (unsigned char)engine;
    out[POOLRAD_MODE_OUT] = (unsigned char)presentation;
    uint32_t epoch = tracker != NULL && !tracker->exhausted ? tracker->epoch : 0;
    out[POOLRAD_WALK_EPOCH_OUT] = epoch >> 24;
    out[POOLRAD_WALK_EPOCH_OUT + 1] = epoch >> 16;
    out[POOLRAD_WALK_EPOCH_OUT + 2] = epoch >> 8;
    out[POOLRAD_WALK_EPOCH_OUT + 3] = epoch;
    if (mode != POOLRAD_DISPLAY_EXPLORATION || !has_map || out[POOLRAD_ID_VALID_OUT] != 1) {
        /* Non-exploration modes are always status-only, even when an old local
         * map happens to remain in RAM. No consumer can mistake its position
         * for a tactical or wilderness location. */
        memset(out + 40, 0, 8);
        memset(out + POOLRAD_GLOBALS_OUT, 0, POOLRAD_PROBE_SIZE - POOLRAD_GLOBALS_OUT);
        out[POOLRAD_ID_VALID_OUT] = 0;
        out[POOLRAD_ID_OUT] = out[POOLRAD_ID_OUT + 1] = 255;
        out[POOLRAD_GLOBALS_OUT + 82] = out[POOLRAD_GLOBALS_OUT + 83]
            = out[POOLRAD_GLOBALS_OUT + 84] = 255;
        /* The clear above lands on the search byte too. A status-only packet
         * shows no position line, so the marker is unavailable, not "off". */
        out[POOLRAD_SEARCH_OUT] = POOLRAD_SEARCH_UNAVAILABLE;
        out[POOLRAD_WALK_SAFE_OUT] = 0;
    }
    if (epoch == 0) out[POOLRAD_WALK_SAFE_OUT] = 0;
    poolrad_clock(ram, size, a5, mode, out);
    if (tracker != NULL && !tracker->travel_broken && !tracker->travel_exhausted) {
        unsigned char *travel = out + POOLRAD_TRAVEL_OUT;
        uint32_t epoch = tracker->travel_epoch, serial = tracker->travel_serial;
        travel[0] = epoch >> 24; travel[1] = epoch >> 16; travel[2] = epoch >> 8; travel[3] = epoch;
        travel[4] = serial >> 24; travel[5] = serial >> 16; travel[6] = serial >> 8; travel[7] = serial;
        travel[8] = tracker->from_valid;
        travel[9] = tracker->from_id; travel[10] = tracker->from_tile; travel[11] = tracker->from_facing;
        travel[12] = tracker->travel_id;
    }
    return 1;
}
#endif
