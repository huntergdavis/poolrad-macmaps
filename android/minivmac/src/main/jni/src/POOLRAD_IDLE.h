/* Automatic foreground sleep: positively identify the supported game's
 * untimed menu-input wait. A still screen alone is never sufficient.
 * Read-only; see docs/AUTOMATIC_IDLE.md for CODE/stack evidence. */
#ifndef POOLRAD_IDLE_H
#define POOLRAD_IDLE_H
#include "POOLRAD.h"

#define POOLRAD_IDLE_DELAY_MS 5000

/* Resolve only the loaded form of a Macintosh CODE jump-table entry. */
static uint32_t poolrad_idle_entry(const unsigned char *ram, size_t size,
        uint32_t a5, unsigned offset, unsigned segment, uint32_t prologue) {
    uint32_t entry;
    if (!poolrad_range(a5 + offset, 8, size)) return 0;
    const unsigned char *jump = ram + a5 + offset;
    if (jump[0] != 0 || jump[1] != segment || jump[2] != 0x4e || jump[3] != 0xf9) return 0;
    entry = poolrad_u32(jump + 4) & 0xffffff;
    if (entry < 0x1000 || (entry & 1) || !poolrad_range(entry, 0x540, size)
            || poolrad_u32(ram + entry) != prologue) return 0;
    return entry;
}

/* Returns the menu frame's identity only while its event reader is inside
 * GetNextEvent/WaitNextEvent. Neither action handlers nor timed waits match. */
static uint32_t poolrad_idle_wait(const unsigned char *ram, size_t size, uint32_t frame) {
    uint32_t a5, menu, events;
    if (!poolrad_game_name(ram, size)) return 0;
    a5 = poolrad_u32(ram + 0x904) & 0xffffff;
    if (a5 < 0x617e || (a5 & 1) || !poolrad_range(a5 - 0x617e, 0x617e, size)) return 0;
    unsigned engine = ram[a5 - POOLRAD_ENGINE_BACK];
    if (engine < 2 || engine > 5
            || ram[a5 - POOLRAD_STARTUP_BACK] || ram[a5 - POOLRAD_LOADED_BACK]
            || ram[a5 - POOLRAD_PENDING_INPUT_BACK]
            || ram[a5 - POOLRAD_MENU_STATE_BACK] != 0
            || ram[a5 - POOLRAD_MENU_STATE_BACK + 1] != 2) return 0;
    menu = poolrad_idle_entry(ram, size, a5, 0x4c0, 6, 0x4e56ff98);
    events = poolrad_idle_entry(ram, size, a5, 0x2d0, 2, 0x4e56ffb4);
    if (!menu || !events
            || poolrad_u32(ram + menu + 0x52c) != 0x4ead02d2
            || poolrad_u32(ram + menu + 0x530) != 0x548f1d40) return 0;
    frame &= 0xffffff;
    for (unsigned depth = 0; depth < 24; depth++) {
        if (frame < 0x1000 || (frame & 1) || !poolrad_range(frame, 8, size)) return 0;
        uint32_t parent = poolrad_u32(ram + frame) & 0xffffff;
        uint32_t returning = poolrad_u32(ram + frame + 4) & 0xffffff;
        if (parent <= frame || (parent & 1) || !poolrad_range(parent, 10, size)) return 0;
        if (returning == events + 0xdc || returning == events + 0xf2) {
            /* The event reader must return to the menu's null-input loop,
             * not a rest timer, automatic fighter, or another caller. */
            if ((poolrad_u32(ram + parent + 4) & 0xffffff) != menu + 0x530) return 0;
            uint32_t caller = poolrad_u32(ram + parent) & 0xffffff;
            if (caller <= parent || (caller & 1) || !poolrad_range(caller - 4, 34, size)) return 0;
            if (ram[caller - 1] || ram[caller - 4] || ram[parent + 9] > 1) return 0;
            return caller;
        }
        frame = parent;
    }
    return 0;
}

typedef struct {
    uint64_t quiet_since;
    uint32_t waiting_frame;
} poolrad_idle_tracker;

static void poolrad_idle_activity(poolrad_idle_tracker *t, uint64_t now) {
    t->quiet_since = now;
    t->waiting_frame = 0;
}

/* Wait-frame gaps are normal while System 7 services an event. We require a
 * fresh positive match to sleep, plus five quiet seconds in the same wait. */
static int poolrad_idle_observe(poolrad_idle_tracker *t, uint64_t now,
        uint32_t frame, int busy) {
    if (busy || now < t->quiet_since) {
        poolrad_idle_activity(t, now);
        return 0;
    }
    if (!frame) return 0;
    if (frame != t->waiting_frame) {
        t->waiting_frame = frame;
        t->quiet_since = now;
        return 0;
    }
    return now - t->quiet_since >= POOLRAD_IDLE_DELAY_MS;
}
#endif
