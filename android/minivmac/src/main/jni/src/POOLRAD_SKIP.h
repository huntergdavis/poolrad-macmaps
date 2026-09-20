/* Read-only identification of informational acknowledgements in Macintosh
 * Pool of Radiance v1.1. A single button is NOT sufficient: story Continue
 * prompts can move the party. See docs/AUTO_SKIP_MESSAGES.md. */
#ifndef POOLRAD_SKIP_H
#define POOLRAD_SKIP_H
#include "POOLRAD_IDLE.h"

typedef struct { uint32_t frame, caller; } poolrad_notice;

static uint32_t poolrad_skip_segment(const unsigned char *ram, size_t size,
        uint32_t a5, unsigned jump, unsigned segment, unsigned offset,
        uint32_t prologue, unsigned length) {
    uint32_t entry = poolrad_idle_entry(ram, size, a5, jump, segment, prologue);
    if (entry < offset + 0x1000 || !poolrad_range(entry - offset, length, size)) return 0;
    return entry - offset;
}

static int poolrad_skip_alert_caller(const unsigned char *ram, size_t size,
        uint32_t a5, uint32_t returning) {
    /* Numeric/name validation and notices after an explicitly requested
     * character departure. Disk/load errors deliberately remain manual. */
    static const struct { unsigned jump, segment, offset, length; uint32_t prologue;
        unsigned calls[3]; } sources[] = {
        {0xa0, 2, 0x1112, 0x1a18, 0x4e56ffec, {0x1a12,0,0}},
        {0x300,3, 0x35b4, 0x35b8, 0x4e56fff2, {0x3444,0x345a,0}},
        {0x7c8,4, 0x315e, 0x3340, 0x4e56fed6, {0x333a,0,0}},
        {0x898,7, 0x1eb2, 0x4284, 0x4e56ffe4, {0x4248,0x425a,0x427e}}
    };
    for (unsigned i=0; i<sizeof sources/sizeof *sources; i++) {
        uint32_t base=poolrad_skip_segment(ram,size,a5,sources[i].jump,sources[i].segment,
                sources[i].offset,sources[i].prologue,sources[i].length);
        if (!base) continue;
        for (unsigned j=0;j<3;j++) {
            unsigned call=sources[i].calls[j];
            if (call && returning==base+call+4 && poolrad_u32(ram+base+call)==0x4ead107a) return 1;
        }
    }
    return 0;
}

static poolrad_notice poolrad_skip_notice(const unsigned char *ram, size_t size, uint32_t frame) {
    poolrad_notice none={0,0};
    if (!poolrad_game_name(ram,size)) return none;
    uint32_t a5=poolrad_u32(ram+0x904)&0xffffff;
    if (a5<0x617e || (a5&1) || !poolrad_range(a5-0x617e,0x617e,size)) return none;
    /* Character creation legitimately retains the startup flag. The exact
     * Alert caller chain establishes eligibility independently of world state. */
    uint32_t initial_frame=frame;
    uint32_t alert=poolrad_skip_segment(ram,size,a5,0x1078,14,0x1c20,0x4e56fffc,0x1fe2);
    /* Follow only a bounded, ascending chain. Match the actual Alert(10000)
     * wrapper and its formatter, then the exact informational game caller. */
    frame &= 0xffffff;
    for (unsigned depth=0;alert && depth<32;depth++) {
        if (frame<0x1000 || (frame&1) || !poolrad_range(frame,14,size)) break;
        uint32_t parent=poolrad_u32(ram+frame)&0xffffff;
        uint32_t returning=poolrad_u32(ram+frame+4)&0xffffff;
        if (parent<=frame || (parent&1) || !poolrad_range(parent,8,size)) break;
        if (returning==alert+0x1fde && ram[frame+8]==0x27 && ram[frame+9]==0x10
                && poolrad_u32(ram+frame+10)==0
                && poolrad_u32(ram+alert+0x1aca)==0x4e560000
                && poolrad_u32(ram+alert+0x1c9c)==0x4e56ffcc
                && poolrad_u32(ram+alert+0x1ae2)==0xa985301f
                && poolrad_u32(ram+alert+0x1fda)==0x4ebafaee
                && (poolrad_u32(ram+parent+4)&0xffffff)==alert+0x1c3c) {
            uint32_t formatter=poolrad_u32(ram+parent)&0xffffff;
            if (formatter<=parent || (formatter&1) || !poolrad_range(formatter,8,size)) return none;
            uint32_t caller=poolrad_u32(ram+formatter+4)&0xffffff;
            if (poolrad_skip_alert_caller(ram,size,a5,caller)) return (poolrad_notice){frame,caller};
            return none;
        }
        frame=parent;
    }
    /* The two post-fight reports only acknowledge already awarded experience
     * and NPC shares. Loot choices and all story/interpreter calls are excluded. */
    uint32_t menu=poolrad_menu_wait(ram,size,initial_frame,6);
    uint32_t reports=poolrad_skip_segment(ram,size,a5,0x990,9,0x662e,0x4e56ffee,0x6632);
    if (menu && reports && poolrad_range(menu-7,23,size) && ram[menu-7]==1) {
        uint32_t caller=poolrad_u32(ram+menu+4)&0xffffff;
        uint32_t choices=poolrad_u32(ram+menu+12)&0xffffff;
        if (poolrad_range(choices,9,size) && !memcmp(ram+choices,"Continue",9)
                && ((caller==reports+0x5d46 && poolrad_u32(ram+reports+0x5d42)==0x4ead04c2)
                 || (caller==reports+0x661c && poolrad_u32(ram+reports+0x6618)==0x4ead04c2)))
            return (poolrad_notice){menu,caller};
    }
    return none;
}

/* A positive match must recur after settling. Brief OS-event stack gaps do
 * not restart the timer; neither gaps nor a still image authorize input. */
typedef struct {
    poolrad_notice candidate, sent;
    uint64_t since, last_match, release_at;
    int held;
} poolrad_skip_tracker;

static int poolrad_skip_observe(poolrad_skip_tracker *t, uint64_t now,
        poolrad_notice notice, int enabled, int busy) {
    if (!enabled || busy) {
        int release=t->held;
        memset(t,0,sizeof *t);
        return release ? -1 : 0;
    }
    if (t->held) {
        if (now>=t->release_at) { t->held=0; return -1; }
        return 0;
    }
    if (!notice.frame) {
        if (now-t->last_match>=350) { t->candidate=(poolrad_notice){0,0};t->sent=t->candidate; }
        return 0;
    }
    t->last_match=now;
    if (notice.frame==t->sent.frame && notice.caller==t->sent.caller) return 0;
    if (notice.frame!=t->candidate.frame || notice.caller!=t->candidate.caller || now<t->since) {
        t->candidate=notice;t->since=now;return 0;
    }
    if (now-t->since<350) return 0;
    t->sent=notice;t->held=1;t->release_at=now+80;
    return 1;
}
#endif
