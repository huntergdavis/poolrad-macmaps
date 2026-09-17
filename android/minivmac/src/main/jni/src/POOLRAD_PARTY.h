/* Read-only Macintosh Pool of Radiance v1.1 party details.
 * Independent disassembly and capture evidence: docs/PARTY.md.
 * Only party order, names, health and verified display identifiers leave the core.
 */
#ifndef POOLRAD_PARTY_PROBE_H
#define POOLRAD_PARTY_PROBE_H
#include "POOLRAD.h"

#define POOLRAD_PARTY_MAX_MEMBERS 8
/* CODE 5 +0x27ae/+0x2abe caps appended monsters at 63. */
#define POOLRAD_PARTY_MAX_COMBATANTS 63
#define POOLRAD_PARTY_MAX_LINKS (POOLRAD_PARTY_MAX_MEMBERS + POOLRAD_PARTY_MAX_COMBATANTS)
#define POOLRAD_PARTY_ROW_SIZE 20
#define POOLRAD_PARTY_BASE_SIZE (8 + POOLRAD_PARTY_MAX_MEMBERS * POOLRAD_PARTY_ROW_SIZE)
#define POOLRAD_PARTY_CONDITION_SIZE (POOLRAD_PARTY_BASE_SIZE + 2 * POOLRAD_PARTY_MAX_MEMBERS)
#define POOLRAD_PARTY_SPELL_STRIDE 8
#define POOLRAD_PARTY_SPELL_SIZE (POOLRAD_PARTY_CONDITION_SIZE \
        + POOLRAD_PARTY_SPELL_STRIDE * POOLRAD_PARTY_MAX_MEMBERS)
#define POOLRAD_PARTY_NAME_BYTES 32
#define POOLRAD_PARTY_EQUIP_STRIDE (1 + 2 * POOLRAD_PARTY_NAME_BYTES + 3)
#define POOLRAD_PARTY_EQUIP_SIZE (POOLRAD_PARTY_SPELL_SIZE \
        + POOLRAD_PARTY_EQUIP_STRIDE * POOLRAD_PARTY_MAX_MEMBERS)
#define POOLRAD_PARTY_CLASS_SLOTS 8
#define POOLRAD_PARTY_TRAIN_CLASSES 3
#define POOLRAD_PARTY_TRAIN_STRIDE (1 + 4 + POOLRAD_PARTY_TRAIN_CLASSES * 6)
#define POOLRAD_PARTY_TRAIN_SIZE (POOLRAD_PARTY_EQUIP_SIZE \
        + POOLRAD_PARTY_TRAIN_STRIDE * POOLRAD_PARTY_MAX_MEMBERS)
/* PRP7 appends one quick byte per member: 0 off, 1 on, 0xff unreadable. */
#define POOLRAD_PARTY_QUICK_UNAVAILABLE 0xff
#define POOLRAD_PARTY_SIZE (POOLRAD_PARTY_TRAIN_SIZE + POOLRAD_PARTY_MAX_MEMBERS)
#define POOLRAD_PARTY_HEAD_BACK 20894
/* CODE7 +0x1ebc allocates 0x12e bytes and +0x1ee6 clears exactly that many. */
#define POOLRAD_PARTY_RECORD_SIZE 302
#define POOLRAD_PARTY_NEXT_OFFSET 0x110
#define POOLRAD_PARTY_CURRENT_HP_OFFSET 0x12b
#define POOLRAD_PARTY_MAX_HP_OFFSET 0x32
#define POOLRAD_PARTY_SLOT_OFFSET 0xc9
#define POOLRAD_PARTY_AC_OFFSET 0x11d
#define POOLRAD_PARTY_CLASS_OFFSET 0x2f
#define POOLRAD_PARTY_UNKNOWN_AC 0x80
#define POOLRAD_PARTY_UNKNOWN_CLASS 0xff
#define POOLRAD_PARTY_LAST_CLASS 17
#define POOLRAD_PARTY_CONDITION_OFFSET 0x118
#define POOLRAD_PARTY_EFFECT_HEAD_OFFSET 0x82
#define POOLRAD_PARTY_UNKNOWN_CONDITION 0xff
#define POOLRAD_PARTY_UNKNOWN_EFFECTS 0xff
#define POOLRAD_PARTY_POISONED 1
#define POOLRAD_PARTY_HELPLESS 2
#define POOLRAD_PARTY_EFFECT_LIMIT 64
/* CODE6 +0x27d8, CODE4 +0x2896 and CODE6 +0x4472 all bound this array at 21. */
#define POOLRAD_PARTY_SPELL_OFFSET 0x17
#define POOLRAD_PARTY_SPELL_SLOTS 21
#define POOLRAD_PARTY_SPELL_TABLE_BACK 0xe84
#define POOLRAD_PARTY_SPELL_ENTRY 16
#define POOLRAD_PARTY_SPELL_LEVELS 3
#define POOLRAD_PARTY_SPELLS_UNAVAILABLE 0xff
/* CODE3 +0x61aa reads *( *(character) + slot*4 + 0xd8 ); the character sheet at
 * +0x3c26 and +0x3ca4 gates its "Weapon:" and "Armor:" lines on slots 0 and 2.
 */
#define POOLRAD_PARTY_READIED_OFFSET 0xd8
#define POOLRAD_PARTY_WEAPON_SLOT 0
#define POOLRAD_PARTY_ARMOR_SLOT 2
#define POOLRAD_PARTY_ITEM_PART_OFFSET 0x2f
#define POOLRAD_PARTY_ITEM_SUPPRESS_OFFSET 0x36
#define POOLRAD_PARTY_ITEM_MIN_SIZE 0x37
#define POOLRAD_PARTY_ITEM_NAME_TABLE_BACK 0x5db2
#define POOLRAD_PARTY_ITEM_NAME_ENTRIES 256
#define POOLRAD_PARTY_ITEM_PART_MAX 40
#define POOLRAD_PARTY_EQUIP_UNAVAILABLE 0xff
/* CODE7 +0x4436 reads the per-class level at record+0x9a+slot and treats zero
 * as "not this class"; +0x4644 compares the shared experience at record+0xb4
 * against the game's own threshold table, class*0x50 + (level+1)*4 bytes into
 * A5-0x15b4. A threshold at or below zero means no further level is defined.
 */
#define POOLRAD_PARTY_LEVEL_OFFSET 0x9a
#define POOLRAD_PARTY_EXPERIENCE_OFFSET 0xb4
#define POOLRAD_PARTY_TRAIN_TABLE_BACK 0x15b4
#define POOLRAD_PARTY_TRAIN_CLASS_STRIDE 0x50
#define POOLRAD_PARTY_TRAIN_LEVELS 20
#define POOLRAD_PARTY_TRAIN_UNAVAILABLE 0xff
/* The game's own character sheet prints these two beside Weapon:/Armor:.
 * Unlike the item handles they are plain record fields and never purge.
 */
/* The quick flag, and the one field this project writes. Found by capture, not
 * guessed: see the comment on poolrad_party_set_quick below and docs/PARTY.md. */
#define POOLRAD_PARTY_QUICK_OFFSET 0x11b
#define POOLRAD_PARTY_QUICK_OFF 0
#define POOLRAD_PARTY_QUICK_ON 1
#define POOLRAD_PARTY_ENCUMBRANCE_OFFSET 0x10e
#define POOLRAD_PARTY_MOVEMENT_OFFSET 0x12c

/* CODE3 +0x2406 looks up an effect ID at node+0, following node+6 handles.
 * CODE11 +0x0ea2 allocates 10-byte nodes. As with characters, verify logical
 * size including heap padding. Incomplete lists return unknown, not partial flags.
 * Poison: CODE4 +0x7488 queries ID55 before Neutralize Poison.
 * Helpless: CODE3 +0x0b3e queries IDs51,52,53,31 in the original table.
 */
static unsigned char poolrad_party_effects(const unsigned char *ram, size_t size, uint32_t record) {
    uint32_t handle = poolrad_u32(ram + record + POOLRAD_PARTY_EFFECT_HEAD_OFFSET) & 0x00ffffff;
    uint32_t handles[POOLRAD_PARTY_EFFECT_LIMIT], records[POOLRAD_PARTY_EFFECT_LIMIT];
    unsigned count = 0, flags = 0;
    while (handle != 0) {
        uint32_t node, header, physical;
        if (count == POOLRAD_PARTY_EFFECT_LIMIT || handle < 0x1000 || (handle & 1)
                || !poolrad_range(handle, 4, size)) return POOLRAD_PARTY_UNKNOWN_EFFECTS;
        node = poolrad_u32(ram + handle) & 0x00ffffff;
        if (node < 0x1000 || (node & 1) || !poolrad_range(node, 10, size)) return POOLRAD_PARTY_UNKNOWN_EFFECTS;
        header = poolrad_u32(ram + node - 8); physical = header & 0x00ffffff;
        if ((header & 0xf0000000) != 0x80000000
                || physical != 18 + ((header >> 24) & 15) || (physical & 1)
                || !poolrad_range(node - 8, physical, size)) return POOLRAD_PARTY_UNKNOWN_EFFECTS;
        for (unsigned i = 0; i < count; i++)
            if (handles[i] == handle || records[i] == node) return POOLRAD_PARTY_UNKNOWN_EFFECTS;
        handles[count] = handle; records[count++] = node;
        unsigned id = ram[node];
        if (id == 55) flags |= POOLRAD_PARTY_POISONED;
        if (id == 31 || id == 51 || id == 52 || id == 53) flags |= POOLRAD_PARTY_HELPLESS;
        handle = poolrad_u32(ram + node + 6) & 0x00ffffff;
    }
    return (unsigned char) flags;
}

/* Memorized-spell readiness, from the character's own 21-slot array at +0x17.
 * CODE4 +0x0ac2..+0x0b08 stores a chosen spell into the first zero slot with
 * bit 7 SET; CODE4 +0x27c6..+0x288a clears that bit while resting and reports
 * "has memorized"; CODE6 +0x2790..+0x27cc offers only slots below 0x80 to Cast;
 * CODE3 +0x1562..+0x15a2 zeroes the matching slot when one is cast. So a slot
 * is empty (0), ready to cast (1..0x7f) or awaiting rest (bit 7 set).
 * The spell's level is byte +1 of its 16-byte entry in the game's own table at
 * A5-0xe84, indexed by the low seven bits (CODE6 +0x23fa..+0x2402).
 * Only per-level counts leave this reader: never a spell list or raw slot bytes.
 * Any unreadable table, out-of-range level or bad bound makes this one member's
 * counts unavailable; names, health, AC, class and condition stay valid.
 */
static int poolrad_party_spells(const unsigned char *ram, size_t size, uint32_t a5,
        uint32_t record, unsigned char *out) {
    uint32_t table;
    unsigned ready[POOLRAD_PARTY_SPELL_LEVELS] = {0}, waiting[POOLRAD_PARTY_SPELL_LEVELS] = {0};
    if (a5 < POOLRAD_PARTY_SPELL_TABLE_BACK) return 0;
    table = a5 - POOLRAD_PARTY_SPELL_TABLE_BACK;
    if (!poolrad_range(table, 128 * POOLRAD_PARTY_SPELL_ENTRY, size)) return 0;
    for (unsigned slot = 0; slot < POOLRAD_PARTY_SPELL_SLOTS; slot++) {
        unsigned value = ram[record + POOLRAD_PARTY_SPELL_OFFSET + slot];
        unsigned id = value & 0x7f, level;
        if (value == 0) continue; /* CODE4 +0x0ade treats zero as a free slot. */
        level = ram[table + id * POOLRAD_PARTY_SPELL_ENTRY + 1];
        if (level < 1 || level > POOLRAD_PARTY_SPELL_LEVELS) return 0;
        if (value & 0x80) waiting[level - 1]++; else ready[level - 1]++;
    }
    out[0] = 0;
    for (unsigned level = 0; level < POOLRAD_PARTY_SPELL_LEVELS; level++) {
        out[1 + level] = (unsigned char) ready[level];
        out[4 + level] = (unsigned char) waiting[level];
    }
    out[7] = 0;
    return 1;
}

/* Readied weapon and armor names, composed exactly the way the game composes
 * them. CODE3 +0x0658..+0x06c2 walks the three name parts at item +0x2f+n from
 * n=3 down to n=1, skips a zero index, skips a part whose bit (3-n) is set in
 * the byte at item +0x36, and appends the string whose 4-byte pointer sits at
 * A5-0x5db2 + index*4. Only the composed base name leaves this reader; the
 * item record's own leading string is the game's scratch render buffer and is
 * deliberately never read. Counts, plurals, magic columns and "+N" suffixes
 * are formatter extras this reader does not reproduce.
 *
 * A purged handle, an unreadable part or an over-long name makes this one
 * character's equipment unavailable; it never reads as "nothing readied".
 */
static int poolrad_party_item_name(const unsigned char *ram, size_t size, uint32_t table,
        uint32_t item, unsigned char *out) {
    unsigned length = 0, suppress;
    if (!poolrad_range(item, POOLRAD_PARTY_ITEM_MIN_SIZE, size)) return 0;
    suppress = ram[item + POOLRAD_PARTY_ITEM_SUPPRESS_OFFSET];
    for (unsigned n = 3; n >= 1; n--) {
        uint32_t pointer;
        unsigned index = ram[item + POOLRAD_PARTY_ITEM_PART_OFFSET + n], part = 0;
        if (index == 0 || ((suppress >> (3 - n)) & 1)) continue;
        pointer = poolrad_u32(ram + table + index * 4) & 0x00ffffff;
        if (pointer < 0x1000 || !poolrad_range(pointer, POOLRAD_PARTY_ITEM_PART_MAX, size)) return 0;
        while (part < POOLRAD_PARTY_ITEM_PART_MAX && ram[pointer + part] != 0) {
            unsigned char letter = ram[pointer + part];
            /* Reference text only: never emit control bytes or raw record data. */
            if (letter < 0x20 || letter == 0x7f) return 0;
            part++;
        }
        if (part == 0 || part >= POOLRAD_PARTY_ITEM_PART_MAX) return 0;
        if (length != 0) {
            if (length + 1 >= POOLRAD_PARTY_NAME_BYTES) return 0;
            out[length++] = ' ';
        }
        if (length + part >= POOLRAD_PARTY_NAME_BYTES) return 0;
        memcpy(out + length, ram + pointer, part);
        length += part;
    }
    return length != 0;
}

static int poolrad_party_equipment(const unsigned char *ram, size_t size, uint32_t a5,
        uint32_t record, unsigned char *out) {
    static const unsigned slots[2] = {POOLRAD_PARTY_WEAPON_SLOT, POOLRAD_PARTY_ARMOR_SLOT};
    uint32_t table;
    if (a5 < POOLRAD_PARTY_ITEM_NAME_TABLE_BACK) return 0;
    table = a5 - POOLRAD_PARTY_ITEM_NAME_TABLE_BACK;
    if (!poolrad_range(table, POOLRAD_PARTY_ITEM_NAME_ENTRIES * 4, size)) return 0;
    out[0] = 0;
    for (unsigned i = 0; i < 2; i++) {
        unsigned char *name = out + 1 + i * POOLRAD_PARTY_NAME_BYTES;
        uint32_t handle = poolrad_u32(ram + record + POOLRAD_PARTY_READIED_OFFSET
                + slots[i] * 4) & 0x00ffffff;
        uint32_t item;
        if (handle == 0) continue; /* Genuinely nothing readied in that slot. */
        if ((handle & 1) || handle < 0x1000 || !poolrad_range(handle, 4, size)) return 0;
        item = poolrad_u32(ram + handle) & 0x00ffffff;
        /* A resident party can still hold purged item blocks; that is unknown,
         * not an empty hand, so the whole character's equipment is withheld.
         */
        if (item == 0 || (item & 1) || item < 0x1000) return 0;
        if (!poolrad_party_item_name(ram, size, table, item, name)) return 0;
    }
    return 1;
}

/* Experience and the game's own next-level thresholds, per class slot.
 * Emits the shared experience once, then up to three (slot, level, threshold)
 * triples for the classes the character actually has. A threshold of zero means
 * the game defines no further level for that class. Nothing here trains, edits
 * experience, or predicts a level-up: it reports the two numbers the game
 * itself compares, so the player can see how close they are.
 */
static int poolrad_party_training(const unsigned char *ram, size_t size, uint32_t a5,
        uint32_t record, unsigned char *out) {
    uint32_t table, experience;
    unsigned written = 0;
    if (a5 < POOLRAD_PARTY_TRAIN_TABLE_BACK) return 0;
    table = a5 - POOLRAD_PARTY_TRAIN_TABLE_BACK;
    if (!poolrad_range(table, POOLRAD_PARTY_CLASS_SLOTS * POOLRAD_PARTY_TRAIN_CLASS_STRIDE, size))
        return 0;
    experience = poolrad_u32(ram + record + POOLRAD_PARTY_EXPERIENCE_OFFSET);
    out[1] = (unsigned char) (experience >> 24); out[2] = (unsigned char) (experience >> 16);
    out[3] = (unsigned char) (experience >> 8);  out[4] = (unsigned char) experience;
    for (unsigned slot = 0; slot < POOLRAD_PARTY_CLASS_SLOTS; slot++) {
        unsigned level = ram[record + POOLRAD_PARTY_LEVEL_OFFSET + slot];
        uint32_t entry, threshold;
        unsigned char *triple;
        if (level == 0) continue; /* CODE7 +0x443a: a zero level is not this class. */
        if (level >= POOLRAD_PARTY_TRAIN_LEVELS - 1) return 0; /* Beyond the table. */
        if (written == POOLRAD_PARTY_TRAIN_CLASSES) return 0;  /* More classes than expected. */
        entry = table + slot * POOLRAD_PARTY_TRAIN_CLASS_STRIDE + (level + 1) * 4;
        threshold = poolrad_u32(ram + entry);
        /* The table stores -1 past a class's last level; report that as zero. */
        if (threshold & 0x80000000u) threshold = 0;
        triple = out + 5 + written * 6;
        triple[0] = (unsigned char) slot; triple[1] = (unsigned char) level;
        triple[2] = (unsigned char) (threshold >> 24); triple[3] = (unsigned char) (threshold >> 16);
        triple[4] = (unsigned char) (threshold >> 8);  triple[5] = (unsigned char) threshold;
        written++;
    }
    if (written == 0) return 0; /* Every character has at least one class. */
    out[0] = (unsigned char) written;
    return 1;
}

/* Why a party reading was refused. Every one of these is a structural fact
 * about the emulated heap, never game content: the probe walks a linked list of
 * character records and gives up the moment anything does not check out, and
 * until now "gives up" looked exactly like "no game running" from the outside.
 * Hunter's tablet has never once shown a party while its map works perfectly,
 * so the refusal has to say which check refused. Reported alongside the number
 * of roster links already accepted.
 */
#define POOLRAD_PARTY_WHY_OK 0
#define POOLRAD_PARTY_WHY_NO_GAME 1        /* shared app/A5/geometry guard */
#define POOLRAD_PARTY_WHY_NO_A5 2          /* A5 world too small for the head */
#define POOLRAD_PARTY_WHY_HEAD_RANGE 3     /* head address outside RAM */
#define POOLRAD_PARTY_WHY_NO_ROSTER 4      /* head handle zero: no party loaded */
#define POOLRAD_PARTY_WHY_HANDLE 5         /* master pointer not a valid handle */
#define POOLRAD_PARTY_WHY_RECORD 6         /* character record out of bounds */
#define POOLRAD_PARTY_WHY_BLOCK 7          /* Mac heap block header rejected */
#define POOLRAD_PARTY_WHY_LOOP 8           /* the roster chain revisits itself */
#define POOLRAD_PARTY_WHY_SLOT_UNSET 9     /* slot still 0xff, mid-allocation */
#define POOLRAD_PARTY_WHY_COMBATANTS 10    /* more appended monsters than CODE5 allows */
#define POOLRAD_PARTY_WHY_SLOT_CLASH 11    /* two members claim one slot */
#define POOLRAD_PARTY_WHY_NAME 12          /* name bytes not printable/terminated */
#define POOLRAD_PARTY_WHY_HEALTH 13        /* hit points impossible */
#define POOLRAD_PARTY_WHY_EMPTY 14         /* chain held no party members */

/* PRP5: byte4=count, bytes5..7=0; eight rows: name[16], current, max, AC, class.
 * After the unchanged 168-byte base: eight (condition, tracked effects) pairs,
 * then eight 8-byte spell blocks (status, three ready counts, three awaiting
 * counts, zero), then eight 65-byte equipment blocks: status (0 read, 0xff
 * unavailable) and two 32-byte NUL-padded names, readied weapon then armor,
 * then eight 24-byte training blocks: class count (FF unavailable), the shared
 * experience as a big-endian 32-bit value, and three (slot, level, next
 * threshold) triples with the threshold big-endian 32-bit and zero meaning the
 * game defines no further level. Unused triples are zero.
 * Condition is the Mac table ID0..8 or 0xff. Effects: poison=1, helpless=2,
 * or 0xff=unavailable; other effects are not interpreted. Unused pairs are zero.
 * AC is signed two's complement -127..60; 0x80 means unavailable. The Mac's
 * formatter displays 60 minus an unsigned byte; values below -127 cannot be
 * represented here and remain unknown, not clamped to a guessed rules range.
 * Class is the verified Mac table index 0..17; 0xff means unavailable.
 * Names retain Macintosh Roman bytes, zero terminated. Unused rows are zero.
 * The caller owns a separate POOLRAD_PARTY_SIZE-byte output buffer; on failure
 * it is zeroed so a discarded/invalid sample cannot masquerade as old health.
 */
/* Record which check refused, how far the roster walk had got, and the value
 * that failed. The detail is a heap address or a Memory Manager block header --
 * structure, never game content -- and it is what turns "the heap block was
 * rejected" into a one-round-trip fix instead of a guess.
 */
#define POOLRAD_PARTY_GIVE_UP_AT(code, value) do { \
        if (why != NULL) { \
            uint32_t detail = (uint32_t)(value); \
            why[0] = (unsigned char)(code); why[1] = (unsigned char) links; \
            why[2] = (unsigned char)(detail >> 24); why[3] = (unsigned char)(detail >> 16); \
            why[4] = (unsigned char)(detail >> 8); why[5] = (unsigned char) detail; \
        } \
        return 0; \
    } while (0)
#define POOLRAD_PARTY_GIVE_UP(code) POOLRAD_PARTY_GIVE_UP_AT(code, 0)

static int poolrad_party_probe_why(const unsigned char *ram, size_t size,
        unsigned char *out, unsigned char *why) {
    unsigned char map_sample[POOLRAD_PROBE_SIZE], packet[POOLRAD_PARTY_SIZE] = {0};
    uint32_t handles[POOLRAD_PARTY_MAX_LINKS], records[POOLRAD_PARTY_MAX_LINKS];
    uint32_t a5, head_address, handle;
    unsigned count = 0, links = 0, combatants = 0, occupied_slots = 0;
    if (out == NULL) return 0;
    if (why != NULL) memset(why, 0, 6);
    memset(out, 0, POOLRAD_PARTY_SIZE);
    /* Reuse the shipped profile's exact app/A5/geometry/bootstrap guards.
     * This is not a new combat/exploration detector: health describes the party.
     */
    if (!poolrad_probe(ram, size, map_sample)) POOLRAD_PARTY_GIVE_UP(POOLRAD_PARTY_WHY_NO_GAME);
    a5 = poolrad_u32(ram + 0x904) & 0x00ffffff;
    if (a5 < POOLRAD_PARTY_HEAD_BACK) POOLRAD_PARTY_GIVE_UP_AT(POOLRAD_PARTY_WHY_NO_A5, a5);
    head_address = a5 - POOLRAD_PARTY_HEAD_BACK;
    if (!poolrad_range(head_address, 4, size)) POOLRAD_PARTY_GIVE_UP_AT(POOLRAD_PARTY_WHY_HEAD_RANGE, head_address);
    handle = poolrad_u32(ram + head_address) & 0x00ffffff;
    // No loaded party is unavailable, not an empty live row.
    if (handle == 0) POOLRAD_PARTY_GIVE_UP(POOLRAD_PARTY_WHY_NO_ROSTER);

    while (handle != 0) {
        uint32_t record, block_header, physical_size;
        unsigned length, slot, has_visible_name = 0;
        unsigned char *row;
        if (links >= POOLRAD_PARTY_MAX_LINKS || handle < 0x1000 || (handle & 1)
                || !poolrad_range(handle, 4, size)) POOLRAD_PARTY_GIVE_UP_AT(POOLRAD_PARTY_WHY_HANDLE, handle);
        record = poolrad_u32(ram + handle) & 0x00ffffff;
        if (record < 0x1000 || (record & 1)
                || !poolrad_range(record, POOLRAD_PARTY_RECORD_SIZE, size)) POOLRAD_PARTY_GIVE_UP_AT(POOLRAD_PARTY_WHY_RECORD, record);
        /* 24-bit Mac heap headers encode physical size plus a low-nibble size
         * correction. The same 302-byte record legitimately occupies 310 bytes
         * with no padding at all (tag0x80, which is what Hunter's tablet
         * reports), 312 (tag0x82) or 316 (tag0x86). Validate the exact logical
         * size, relocatable type and complete physical bounds; never accept an
         * arbitrary oversized record or inspect padding as data. Block sizes
         * are even, not four-byte aligned -- see POOLRAD.h, where assuming
         * otherwise hid this party for the life of the project.
         * Inside Macintosh: Memory, Memory Manager pp2-22..2-23.
         */
        block_header = poolrad_u32(ram + record - 8);
        physical_size = block_header & 0x00ffffff;
        if ((block_header >> 28) != 8
                || physical_size != POOLRAD_PARTY_RECORD_SIZE + 8 + ((block_header >> 24) & 15)
                || (physical_size & 1)
                || !poolrad_range(record - 8, physical_size, size)) POOLRAD_PARTY_GIVE_UP_AT(POOLRAD_PARTY_WHY_BLOCK, block_header);
        for (unsigned i = 0; i < links; i++) {
            if (handles[i] == handle || records[i] == record) POOLRAD_PARTY_GIVE_UP(POOLRAD_PARTY_WHY_LOOP);
        }
        handles[links] = handle; records[links] = record; links++;
        handle = poolrad_u32(ram + record + POOLRAD_PARTY_NEXT_OFFSET) & 0x00ffffff;
        /* The combat list is the same list as the party, with monsters appended.
         * CODE 2 +0x2f52..0x305c allocates distinct member slots 0..7; 0xff is
         * its temporary unassigned marker. CODE 5 +0x2d56 starts monster groups
         * at slot 8; +0x28b2/+0x290e write that slot and +0x2ac6 increments it.
         * Validate all links, but never emit monster names or health as party.
         */
        slot = ram[record + POOLRAD_PARTY_SLOT_OFFSET];
        if (slot == 0xff) POOLRAD_PARTY_GIVE_UP_AT(POOLRAD_PARTY_WHY_SLOT_UNSET, record);
        if (slot >= POOLRAD_PARTY_MAX_MEMBERS) {
            if (++combatants > POOLRAD_PARTY_MAX_COMBATANTS) POOLRAD_PARTY_GIVE_UP_AT(POOLRAD_PARTY_WHY_COMBATANTS, combatants);
            continue;
        }
        if (count >= POOLRAD_PARTY_MAX_MEMBERS || (occupied_slots & (1u << slot)))
            POOLRAD_PARTY_GIVE_UP_AT(POOLRAD_PARTY_WHY_SLOT_CLASH, (occupied_slots << 8) | slot);
        occupied_slots |= 1u << slot;
        for (length = 0; length < 16 && ram[record + length] != 0; length++) {
            unsigned char letter = ram[record + length];
            if (letter < 0x20 || letter == 0x7f) POOLRAD_PARTY_GIVE_UP_AT(POOLRAD_PARTY_WHY_NAME, (length << 8) | letter);
            if (letter != ' ') has_visible_name = 1;
        }
        if (length == 0 || length == 16 || !has_visible_name) POOLRAD_PARTY_GIVE_UP_AT(POOLRAD_PARTY_WHY_NAME, length << 8);
        if (ram[record + POOLRAD_PARTY_MAX_HP_OFFSET] == 0
                || ram[record + POOLRAD_PARTY_CURRENT_HP_OFFSET] > ram[record + POOLRAD_PARTY_MAX_HP_OFFSET])
            POOLRAD_PARTY_GIVE_UP_AT(POOLRAD_PARTY_WHY_HEALTH,
                    (ram[record + POOLRAD_PARTY_CURRENT_HP_OFFSET] << 8)
                        | ram[record + POOLRAD_PARTY_MAX_HP_OFFSET]);
        row = packet + 8 + count * POOLRAD_PARTY_ROW_SIZE;
        memcpy(row, ram + record, length);
        row[16] = ram[record + POOLRAD_PARTY_CURRENT_HP_OFFSET];
        row[17] = ram[record + POOLRAD_PARTY_MAX_HP_OFFSET];
        /* AC: A5+0x49a formatter reads +0x11d, selects the sign, and formats
         * abs(value-60). Class: CODE3 +0x3a72..0x3ad0 indexes the 18-entry Mac
         * label table at A5-0x5e82 directly using record+0x2f. Neither detail
         * is a party-membership test; unknown values do not hide valid health.
         */
        row[18] = ram[record + POOLRAD_PARTY_AC_OFFSET] <= 187
                ? (unsigned char) (60 - ram[record + POOLRAD_PARTY_AC_OFFSET])
                : POOLRAD_PARTY_UNKNOWN_AC;
        row[19] = ram[record + POOLRAD_PARTY_CLASS_OFFSET] <= POOLRAD_PARTY_LAST_CLASS
                ? ram[record + POOLRAD_PARTY_CLASS_OFFSET] : POOLRAD_PARTY_UNKNOWN_CLASS;
        packet[POOLRAD_PARTY_BASE_SIZE + count * 2] = ram[record + POOLRAD_PARTY_CONDITION_OFFSET] <= 8
                ? ram[record + POOLRAD_PARTY_CONDITION_OFFSET] : POOLRAD_PARTY_UNKNOWN_CONDITION;
        packet[POOLRAD_PARTY_BASE_SIZE + count * 2 + 1] = poolrad_party_effects(ram, size, record);
        {
            unsigned char *spells = packet + POOLRAD_PARTY_CONDITION_SIZE
                    + count * POOLRAD_PARTY_SPELL_STRIDE;
            if (!poolrad_party_spells(ram, size, a5, record, spells)) {
                spells[0] = POOLRAD_PARTY_SPELLS_UNAVAILABLE;
                for (unsigned i = 1; i < POOLRAD_PARTY_SPELL_STRIDE; i++) spells[i] = 0;
            }
        }
        {
            unsigned char *equipment = packet + POOLRAD_PARTY_SPELL_SIZE
                    + count * POOLRAD_PARTY_EQUIP_STRIDE;
            if (!poolrad_party_equipment(ram, size, a5, record, equipment)) {
                equipment[0] = POOLRAD_PARTY_EQUIP_UNAVAILABLE;
                for (unsigned i = 1; i < POOLRAD_PARTY_EQUIP_STRIDE; i++) equipment[i] = 0;
            }
            /* Carried weight and movement are ordinary record fields, readable
             * even when the item blocks are not resident, so they are filled in
             * either way and never depend on the item status byte.
             */
            equipment[1 + 2 * POOLRAD_PARTY_NAME_BYTES] = ram[record + POOLRAD_PARTY_MOVEMENT_OFFSET];
            equipment[2 + 2 * POOLRAD_PARTY_NAME_BYTES] = ram[record + POOLRAD_PARTY_ENCUMBRANCE_OFFSET];
            equipment[3 + 2 * POOLRAD_PARTY_NAME_BYTES] = ram[record + POOLRAD_PARTY_ENCUMBRANCE_OFFSET + 1];
        }
        {
            unsigned char *training = packet + POOLRAD_PARTY_EQUIP_SIZE
                    + count * POOLRAD_PARTY_TRAIN_STRIDE;
            if (!poolrad_party_training(ram, size, a5, record, training)) {
                training[0] = POOLRAD_PARTY_TRAIN_UNAVAILABLE;
                for (unsigned i = 1; i < POOLRAD_PARTY_TRAIN_STRIDE; i++) training[i] = 0;
            }
        }
        {
            /* The quick flag, reported as it reads. Values the field is not
             * allowed to hold are surfaced as unavailable rather than as "off",
             * so a Q is never drawn confidently over a byte nobody understands.
             */
            unsigned char quick = ram[record + POOLRAD_PARTY_QUICK_OFFSET];
            packet[POOLRAD_PARTY_TRAIN_SIZE + count] =
                    (quick == POOLRAD_PARTY_QUICK_OFF || quick == POOLRAD_PARTY_QUICK_ON)
                        ? quick : POOLRAD_PARTY_QUICK_UNAVAILABLE;
        }
        count++;
    }
    if (count == 0) POOLRAD_PARTY_GIVE_UP(POOLRAD_PARTY_WHY_EMPTY);
    memcpy(packet, "PRP7", 4); packet[4] = (unsigned char) count;
    memcpy(out, packet, sizeof(packet));
    return 1;
}

/** The reasonless form the sanitizer suites and every other caller already use. */
static int poolrad_party_probe(const unsigned char *ram, size_t size, unsigned char *out) {
    return poolrad_party_probe_why(ram, size, out, NULL);
}

/* The one field this project writes.
 *
 * Every reader here is read-only and stays that way. On 2026-09-16 the owner
 * lifted that for a single purpose -- a per-character Quick toggle -- after
 * being shown that Pool of Radiance has no menu for one: its only Quick is the
 * combat button, for whoever's turn it is. See docs/DESIGN.md.
 *
 * Found, not guessed. Three RAM captures around the game's own Quick button in
 * a live battle: pressing Quick on Lara Spellsword changed exactly two bytes in
 * her record and nothing at all in the other fifteen combatants'. One of the
 * two, +0x120, reads 2 for Hogarth and Shara and 1 for the rest and fell to 0
 * when she attacked, so it is attacks remaining. The other, +0x11b, went 0 to 1
 * for her alone and stayed there while five other characters took their turns.
 * Evidence: docs/PARTY.md.
 */
/* Sets one party member's quick flag, and refuses everything else.
 *
 * The roster is walked with exactly the reader's checks -- the shared app and
 * A5 guards, the master pointer, the record bounds, the Mac heap block header,
 * chain loops, slot assignment -- and the write only happens if all of them
 * pass and the byte already holds a value this field is allowed to have. One
 * byte, in one record, belonging to one party member. The member is named by
 * its position in the chain among party members -- the same numbering the
 * reader's rows use, so row i on screen is character i here -- and monsters,
 * which share this list, are skipped exactly as the reader skips them.
 *
 * Returns 1 when the byte was written or already held the wanted value.
 */
/* inline, because this header is included by readers that never write, and a
 * plain static would be an unused function to them under -Werror. */
static inline int poolrad_party_set_quick(unsigned char *ram, size_t size,
                                   unsigned member, int on) {
    unsigned char sample[POOLRAD_PARTY_SIZE];
    uint32_t a5, head_address, handle;
    unsigned links = 0, seen = 0;
    uint32_t handles[POOLRAD_PARTY_MAX_LINKS], records[POOLRAD_PARTY_MAX_LINKS];
    if (ram == NULL || member >= POOLRAD_PARTY_MAX_MEMBERS) return 0;
    /* The party must read cleanly right now. A record that the reader would
     * refuse to show is not one to write into. */
    if (!poolrad_party_probe(ram, size, sample)) return 0;
    a5 = poolrad_u32(ram + 0x904) & 0x00ffffff;
    if (a5 < POOLRAD_PARTY_HEAD_BACK || !poolrad_range(a5 - POOLRAD_PARTY_HEAD_BACK, 4, size))
        return 0;
    head_address = a5 - POOLRAD_PARTY_HEAD_BACK;
    handle = poolrad_u32(ram + head_address) & 0x00ffffff;
    while (handle != 0) {
        uint32_t record, block_header, physical_size;
        if (links >= POOLRAD_PARTY_MAX_LINKS || handle < 0x1000 || (handle & 1)
                || !poolrad_range(handle, 4, size)) return 0;
        record = poolrad_u32(ram + handle) & 0x00ffffff;
        if (record < 0x1000 || (record & 1)
                || !poolrad_range(record, POOLRAD_PARTY_RECORD_SIZE, size)) return 0;
        block_header = poolrad_u32(ram + record - 8);
        physical_size = block_header & 0x00ffffff;
        if ((block_header >> 28) != 8
                || physical_size != POOLRAD_PARTY_RECORD_SIZE + 8 + ((block_header >> 24) & 15)
                || (physical_size & 1)
                || !poolrad_range(record - 8, physical_size, size)) return 0;
        for (unsigned i = 0; i < links; i++)
            if (handles[i] == handle || records[i] == record) return 0;
        handles[links] = handle; records[links] = record; links++;
        /* Count party members in chain order, exactly as the reader emits its
         * rows, so row i on screen is character i here. Monsters are appended
         * to this same list and are skipped by both. */
        if (ram[record + POOLRAD_PARTY_SLOT_OFFSET] >= POOLRAD_PARTY_MAX_MEMBERS) {
            handle = poolrad_u32(ram + record + POOLRAD_PARTY_NEXT_OFFSET) & 0x00ffffff;
            continue;
        }
        if (seen++ == member) {
            unsigned char *field = ram + record + POOLRAD_PARTY_QUICK_OFFSET;
            /* Never overwrite something that is not this field. If the byte
             * holds anything but the two values the game puts there, the
             * offset is not what it was believed to be and nothing is written. */
            if (*field != POOLRAD_PARTY_QUICK_OFF && *field != POOLRAD_PARTY_QUICK_ON) return 0;
            *field = on ? POOLRAD_PARTY_QUICK_ON : POOLRAD_PARTY_QUICK_OFF;
            return 1;
        }
        handle = poolrad_u32(ram + record + POOLRAD_PARTY_NEXT_OFFSET) & 0x00ffffff;
    }
    return 0;
}

#endif
