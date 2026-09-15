/* Read-only reader for where the supplied Macintosh Pool of Radiance v1.1 has
 * placed each combatant on its own tactical grid. Offsets verified against
 * live captures of two different battles; see docs/COMBAT_MEMORY.md.
 *
 * The game keeps one four-byte entry per combatant, in the same order as the
 * combat roster the party reader already walks:
 *
 *   A5-0x46e8            count, 0 whenever no battle is running
 *   A5-0x46e4 + i*4      { i, flag, x, y } for i in 0..count-1
 *   A5-0x46e4 + count*4  a sentinel entry whose x and y are both zero
 *
 * Nothing is written and nothing is predicted: this reports the squares the
 * game has already drawn on its own Combat View, and no more.
 */
#ifndef POOLRAD_COMBAT_PROBE_H
#define POOLRAD_COMBAT_PROBE_H
#include "POOLRAD_PARTY.h"

#define POOLRAD_COMBAT_COUNT_BACK 0x46e8
#define POOLRAD_COMBAT_TABLE_BACK 0x46e4
#define POOLRAD_COMBAT_STRIDE 4
/* The roster reader already refuses more than this many linked combatants. */
#define POOLRAD_COMBAT_MAX (POOLRAD_PARTY_MAX_LINKS)
/* Both observed battles stay well inside this; anything larger is not a grid. */
#define POOLRAD_COMBAT_MAX_COORDINATE 63
#define POOLRAD_COMBAT_SIZE (8 + POOLRAD_COMBAT_MAX * 4)
#define POOLRAD_COMBAT_STATUS_OUT 4
#define POOLRAD_COMBAT_COUNT_OUT 5
#define POOLRAD_COMBAT_ENTRY_OUT 8
#define POOLRAD_COMBAT_PRESENT 1
#define POOLRAD_COMBAT_UNAVAILABLE 255
#define POOLRAD_COMBAT_KIND_PARTY 1
#define POOLRAD_COMBAT_KIND_OTHER 2

/* Walks the roster chain purely to learn how many combatants there are and
 * which of them are party members. Returns the count, or -1 if the chain does
 * not validate exactly as the party reader requires. Kinds are written into
 * `kinds` in chain order. No name, health or statistic is read here.
 */
static int poolrad_combat_roster(const unsigned char *ram, size_t size, uint32_t a5,
                                 unsigned char *kinds) {
    uint32_t handle, records[POOLRAD_COMBAT_MAX];
    int links = 0;
    if (a5 < POOLRAD_PARTY_HEAD_BACK || !poolrad_range(a5 - POOLRAD_PARTY_HEAD_BACK, 4, size)) return -1;
    handle = poolrad_u32(ram + a5 - POOLRAD_PARTY_HEAD_BACK) & 0x00ffffff;
    if (handle == 0) return -1;
    while (handle != 0) {
        uint32_t record, header, physical;
        unsigned slot;
        if (links >= POOLRAD_COMBAT_MAX || handle < 0x1000 || (handle & 1)
                || !poolrad_range(handle, 4, size)) return -1;
        record = poolrad_u32(ram + handle) & 0x00ffffff;
        if (record < 0x1000 || (record & 1)
                || !poolrad_range(record, POOLRAD_PARTY_RECORD_SIZE, size)) return -1;
        header = poolrad_u32(ram + record - 8);
        physical = header & 0x00ffffff;
        if ((header >> 28) != 8
                || physical != POOLRAD_PARTY_RECORD_SIZE + 8 + ((header >> 24) & 15)
                || (physical & 3) || !poolrad_range(record - 8, physical, size)) return -1;
        for (int i = 0; i < links; i++) if (records[i] == record) return -1;
        records[links] = record;
        slot = ram[record + POOLRAD_PARTY_SLOT_OFFSET];
        if (slot == 0xff) return -1;
        kinds[links] = slot < POOLRAD_PARTY_MAX_MEMBERS
            ? POOLRAD_COMBAT_KIND_PARTY : POOLRAD_COMBAT_KIND_OTHER;
        links++;
        handle = poolrad_u32(ram + record + POOLRAD_PARTY_NEXT_OFFSET) & 0x00ffffff;
    }
    return links;
}

/* Returns 1 and fills a POOLRAD_COMBAT_SIZE packet whenever the supported game
 * is frontmost. Outside a battle, and whenever anything fails to validate, the
 * packet says unavailable and carries no squares at all: a stale grid must
 * never be presented as the current one.
 */
static int poolrad_combat_probe(const unsigned char *ram, size_t size, unsigned char *out) {
    unsigned char kinds[POOLRAD_COMBAT_MAX];
    uint32_t a5, table;
    int roster;
    unsigned count, i;
    if (out == NULL) return 0;
    if (!poolrad_game_name(ram, size)) return 0;
    memset(out, 0, POOLRAD_COMBAT_SIZE);
    memcpy(out, "PRC1", 4);
    out[POOLRAD_COMBAT_STATUS_OUT] = POOLRAD_COMBAT_UNAVAILABLE;
    a5 = poolrad_u32(ram + 0x904) & 0x00ffffff;
    if (a5 < POOLRAD_COMBAT_COUNT_BACK || (a5 & 1)) return 1;
    if (!poolrad_range(a5 - POOLRAD_COMBAT_COUNT_BACK, 1, size)) return 1;
    count = ram[a5 - POOLRAD_COMBAT_COUNT_BACK];
    if (count == 0 || count > POOLRAD_COMBAT_MAX) return 1; // Zero means no battle.
    table = a5 - POOLRAD_COMBAT_TABLE_BACK;
    /* The sentinel one past the last combatant is read too, so the whole
     * structure is bounded before a single square is believed. */
    if (!poolrad_range(table, (count + 1) * POOLRAD_COMBAT_STRIDE, size)) return 1;
    for (i = 0; i < count; i++) {
        const unsigned char *entry = ram + table + i * POOLRAD_COMBAT_STRIDE;
        /* Each entry states its own index; a table that disagrees with itself
         * is not this table. */
        if (entry[0] != i || entry[1] > 1) return 1;
        if (entry[2] > POOLRAD_COMBAT_MAX_COORDINATE
                || entry[3] > POOLRAD_COMBAT_MAX_COORDINATE) return 1;
    }
    if (ram[table + count * POOLRAD_COMBAT_STRIDE + 2] != 0
            || ram[table + count * POOLRAD_COMBAT_STRIDE + 3] != 0) return 1;
    /* The roster says how many combatants there are and which are the party.
     * A count that disagrees with the chain means one of the two was read at
     * the wrong moment, so neither is trusted. */
    roster = poolrad_combat_roster(ram, size, a5, kinds);
    if (roster < 0 || (unsigned)roster != count) return 1;
    for (i = 0; i < count; i++) {
        unsigned char *row = out + POOLRAD_COMBAT_ENTRY_OUT + i * 4;
        row[0] = kinds[i];
        row[1] = ram[table + i * POOLRAD_COMBAT_STRIDE + 2];
        row[2] = ram[table + i * POOLRAD_COMBAT_STRIDE + 3];
    }
    out[POOLRAD_COMBAT_STATUS_OUT] = POOLRAD_COMBAT_PRESENT;
    out[POOLRAD_COMBAT_COUNT_OUT] = (unsigned char)count;
    return 1;
}
#endif
