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
#include "POOLRAD_MESSAGE.h"

#define POOLRAD_COMBAT_COUNT_BACK 0x46e8
#define POOLRAD_COMBAT_TABLE_BACK 0x46e4
#define POOLRAD_COMBAT_STRIDE 4
/* The roster reader already refuses more than this many linked combatants. */
#define POOLRAD_COMBAT_MAX (POOLRAD_PARTY_MAX_LINKS)
/* Both observed battles stay well inside this; anything larger is not a grid. */
#define POOLRAD_COMBAT_MAX_COORDINATE 63
/* PRC2 appended the acting character's name, NUL padded. Whose turn it is was
 * the one thing the overview could not say, and it is the thing a small screen
 * makes hardest to keep track of. Read from the Combat Message window; see
 * POOLRAD_MESSAGE.h. */
#define POOLRAD_COMBAT_ENTRIES_SIZE (8 + POOLRAD_COMBAT_MAX * 4)
#define POOLRAD_COMBAT_ACTOR_OUT POOLRAD_COMBAT_ENTRIES_SIZE
/* PRC3 appends who the party is fighting: a count, then that many
 * {16-byte NUL-padded name, one-byte tally} pairs, deduplicated by name and
 * counting only those still standing. The game names its own monsters in the
 * same character records the party lives in, so this costs no new reading --
 * only the grouping. Eight kinds is more than a battle has ever shown.
 */
#define POOLRAD_COMBAT_FOES_MAX 8
#define POOLRAD_COMBAT_FOE_NAME 16
#define POOLRAD_COMBAT_FOES_OUT (POOLRAD_COMBAT_ACTOR_OUT + POOLRAD_ACTOR_MAX)
#define POOLRAD_COMBAT_SIZE (POOLRAD_COMBAT_FOES_OUT + 1 \
        + POOLRAD_COMBAT_FOES_MAX * (POOLRAD_COMBAT_FOE_NAME + 1))
#define POOLRAD_COMBAT_STATUS_OUT 4
#define POOLRAD_COMBAT_COUNT_OUT 5
#define POOLRAD_COMBAT_ENTRY_OUT 8
#define POOLRAD_COMBAT_PRESENT 1
#define POOLRAD_COMBAT_UNAVAILABLE 255
#define POOLRAD_COMBAT_KIND_PARTY 1
#define POOLRAD_COMBAT_KIND_OTHER 2
/* Still in the chain and the table, no longer on the battlefield. Never sent. */
#define POOLRAD_COMBAT_KIND_DEAD 3
/* One of yours, down where they fell: unconscious, dying, dead or petrified.
 * Sent, because these are the ones a player goes to bandage. Which of the four
 * it is rides along in the entry's fourth byte, so the pane can say the word
 * rather than group them. */
#define POOLRAD_COMBAT_KIND_FALLEN 4
/* The entry's fourth byte: the game's own condition, or 0xff if it did not
 * read as one. Standing combatants send their condition here too. */
#define POOLRAD_COMBAT_CONDITION_UNAVAILABLE 0xff

/* The game's own condition byte, record +0x118: 0 Okay, 1 Animated,
 * 2 Temporarily gone, 3 Running, 4 Unconscious, 5 Dying, 6 Dead,
 * 7 Petrified, 8 Gone. See docs/PARTY_CONDITIONS.md. */
#define POOLRAD_COMBAT_CONDITION_LAST 8
#define POOLRAD_COMBAT_CONDITION_AWAY_A 2
#define POOLRAD_COMBAT_CONDITION_AWAY_B 8
#define POOLRAD_COMBAT_CONDITION_DOWN_FIRST 4
#define POOLRAD_COMBAT_CONDITION_DOWN_LAST 7

/* Walks the roster chain purely to learn how many combatants there are and
 * which of them are party members. Returns the count, or -1 if the chain does
 * not validate exactly as the party reader requires. Kinds are written into
 * `kinds` in chain order. No name, health or statistic is read here.
 */
static int poolrad_combat_roster(const unsigned char *ram, size_t size, uint32_t a5,
                                 unsigned char *kinds, unsigned char *conditions,
                                 uint32_t *records_out) {
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
                || (physical & 1) || !poolrad_range(record - 8, physical, size)) return -1;
        for (int i = 0; i < links; i++) if (records[i] == record) return -1;
        records[links] = record;
        if (records_out != NULL) records_out[links] = record;
        slot = ram[record + POOLRAD_PARTY_SLOT_OFFSET];
        if (slot == 0xff) return -1;
        /* The dead stay in this list. A killed orc keeps its place in the
         * chain, keeps its entry in the position table and keeps its last
         * square, while the game's own Combat View stops drawing it -- so the
         * overview went on showing a marker where an enemy used to be, and
         * when the party advanced onto that square it looked like a square
         * sitting on one of your own people. Which is exactly what Hunter
         * reported. Verified in a live battle: an orc at 0 hit points was
         * still combatant 7 of 16 at (31,15). They are counted for the index
         * pairing, which is by position in the chain, and then left out.
         */
        {
            /* Ask the game what state they are in rather than inferring it from
             * a hit-point byte. Off the field entirely -- gone, or temporarily
             * gone -- is nobody. Down but present is a body on a square: a
             * monster's is not drawn by the game's own Combat View and is not
             * drawn here, but one of your own is exactly what you are looking
             * for when you go to bandage them, so it is sent as fallen.
             */
            unsigned char condition = ram[record + POOLRAD_PARTY_CONDITION_OFFSET];
            int known = condition <= POOLRAD_COMBAT_CONDITION_LAST;
            int party = slot < POOLRAD_PARTY_MAX_MEMBERS;
            int away = known && (condition == POOLRAD_COMBAT_CONDITION_AWAY_A
                                 || condition == POOLRAD_COMBAT_CONDITION_AWAY_B);
            int down = known
                ? (condition >= POOLRAD_COMBAT_CONDITION_DOWN_FIRST
                   && condition <= POOLRAD_COMBAT_CONDITION_DOWN_LAST)
                /* No readable condition: fall back on hit points, which is all
                 * there is to go on and is what this used before. */
                : ram[record + POOLRAD_PARTY_CURRENT_HP_OFFSET] == 0;
            conditions[links] = known ? condition : POOLRAD_COMBAT_CONDITION_UNAVAILABLE;
            kinds[links] = away ? POOLRAD_COMBAT_KIND_DEAD
                : down ? (party ? POOLRAD_COMBAT_KIND_FALLEN : POOLRAD_COMBAT_KIND_DEAD)
                : party ? POOLRAD_COMBAT_KIND_PARTY : POOLRAD_COMBAT_KIND_OTHER;
        }
        links++;
        handle = poolrad_u32(ram + record + POOLRAD_PARTY_NEXT_OFFSET) & 0x00ffffff;
    }
    return links;
}

/* Group the standing opposition by name.
 *
 * The game gives every combatant a character record, monsters included, and
 * names them there -- ten orcs are ten records all reading "ORC". So naming
 * what the party is fighting needs no new reading, only the grouping, and
 * counting by name is what turns "twelve others" into something a player can
 * act on.
 *
 * Only those still standing are counted. A monster the game has stopped
 * drawing is not on the field, and including it would have the count disagree
 * with the squares beside it.
 */
static void poolrad_combat_foes(const unsigned char *ram, const unsigned char *kinds,
                                const uint32_t *records, unsigned count, unsigned char *out) {
    unsigned kinds_found = 0, i, k;
    out[0] = 0;
    for (i = 0; i < count; i++) {
        const unsigned char *name;
        unsigned char letter;
        unsigned length = 0;
        if (kinds[i] != POOLRAD_COMBAT_KIND_OTHER) continue;
        name = ram + records[i];
        /* A name has to be printable and NUL terminated inside its field, or
         * it is not a name and the whole grouping is abandoned rather than
         * half reported. */
        while (length < POOLRAD_COMBAT_FOE_NAME && (letter = name[length]) != 0) {
            if (letter < 0x20 || letter >= 0x7f) { out[0] = 0; return; }
            length++;
        }
        if (length == 0 || length == POOLRAD_COMBAT_FOE_NAME) { out[0] = 0; return; }
        for (k = 0; k < kinds_found; k++) {
            unsigned char *entry = out + 1 + k * (POOLRAD_COMBAT_FOE_NAME + 1);
            unsigned same = 1, c;
            for (c = 0; c < POOLRAD_COMBAT_FOE_NAME; c++)
                if (entry[c] != (c < length ? name[c] : 0)) { same = 0; break; }
            if (same) {
                if (entry[POOLRAD_COMBAT_FOE_NAME] < 255) entry[POOLRAD_COMBAT_FOE_NAME]++;
                break;
            }
        }
        if (k < kinds_found) continue;
        /* More kinds than the packet holds: report none rather than some, so a
         * partial list is never mistaken for the whole opposition. */
        if (kinds_found >= POOLRAD_COMBAT_FOES_MAX) { out[0] = 0; return; }
        {
            unsigned char *entry = out + 1 + kinds_found * (POOLRAD_COMBAT_FOE_NAME + 1);
            unsigned c;
            for (c = 0; c < POOLRAD_COMBAT_FOE_NAME; c++) entry[c] = c < length ? name[c] : 0;
            entry[POOLRAD_COMBAT_FOE_NAME] = 1;
            kinds_found++;
        }
    }
    out[0] = (unsigned char) kinds_found;
}

/* Returns 1 and fills a POOLRAD_COMBAT_SIZE packet whenever the supported game
 * is frontmost. Outside a battle, and whenever anything fails to validate, the
 * packet says unavailable and carries no squares at all: a stale grid must
 * never be presented as the current one.
 */
static int poolrad_combat_probe(const unsigned char *ram, size_t size, unsigned char *out) {
    unsigned char kinds[POOLRAD_COMBAT_MAX], conditions[POOLRAD_COMBAT_MAX];
    uint32_t a5, table;
    int roster;
    unsigned count, i;
    if (out == NULL) return 0;
    if (!poolrad_game_name(ram, size)) return 0;
    memset(out, 0, POOLRAD_COMBAT_SIZE);
    memcpy(out, "PRC3", 4);
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
    uint32_t records[POOLRAD_COMBAT_MAX];
    roster = poolrad_combat_roster(ram, size, a5, kinds, conditions, records);
    if (roster < 0 || (unsigned)roster != count) return 1;
    /* Entry i belongs to combatant i, which is how the sides are known, so the
     * dead are skipped here rather than earlier: the pairing is by position in
     * the chain and must not be disturbed by leaving anyone out of it. */
    unsigned drawn = 0;
    for (i = 0; i < count; i++) {
        unsigned char *row;
        if (kinds[i] == POOLRAD_COMBAT_KIND_DEAD) continue;
        row = out + POOLRAD_COMBAT_ENTRY_OUT + drawn * 4;
        row[0] = kinds[i];
        row[1] = ram[table + i * POOLRAD_COMBAT_STRIDE + 2];
        row[2] = ram[table + i * POOLRAD_COMBAT_STRIDE + 3];
        row[3] = conditions[i];
        drawn++;
    }
    if (drawn == 0) return 1;   // a battle of nothing but the dead is no battle
    out[POOLRAD_COMBAT_STATUS_OUT] = POOLRAD_COMBAT_PRESENT;
    out[POOLRAD_COMBAT_COUNT_OUT] = (unsigned char) drawn;
    poolrad_combat_actor(ram, size, out + POOLRAD_COMBAT_ACTOR_OUT);
    poolrad_combat_foes(ram, kinds, records, count, out + POOLRAD_COMBAT_FOES_OUT);
    return 1;
}
#endif
