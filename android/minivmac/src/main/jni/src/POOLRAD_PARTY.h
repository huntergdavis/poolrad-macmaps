/* Read-only Macintosh Pool of Radiance v1.1 party details.
 * Independent disassembly and capture evidence: docs/PARTY.md.
 * Only party order, names, health and verified AC/class identifiers leave the core.
 */
#ifndef POOLRAD_PARTY_PROBE_H
#define POOLRAD_PARTY_PROBE_H
#include "POOLRAD.h"

#define POOLRAD_PARTY_MAX_MEMBERS 8
/* CODE 5 +0x27ae/+0x2abe caps appended monsters at 63. */
#define POOLRAD_PARTY_MAX_COMBATANTS 63
#define POOLRAD_PARTY_MAX_LINKS (POOLRAD_PARTY_MAX_MEMBERS + POOLRAD_PARTY_MAX_COMBATANTS)
#define POOLRAD_PARTY_ROW_SIZE 20
#define POOLRAD_PARTY_SIZE (8 + POOLRAD_PARTY_MAX_MEMBERS * POOLRAD_PARTY_ROW_SIZE)
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

/* PRP2: byte4=count, bytes5..7=0; eight rows: name[16], current, max, AC, class.
 * AC is signed two's complement -127..60; 0x80 means unavailable. The Mac's
 * formatter displays 60 minus an unsigned byte; values below -127 cannot be
 * represented here and remain unknown, not clamped to a guessed rules range.
 * Class is the verified Mac table index 0..17; 0xff means unavailable.
 * Names retain Macintosh Roman bytes, zero terminated. Unused rows are zero.
 * The caller owns a separate POOLRAD_PARTY_SIZE-byte output buffer; on failure
 * it is zeroed so a discarded/invalid sample cannot masquerade as old health.
 */
static int poolrad_party_probe(const unsigned char *ram, size_t size, unsigned char *out) {
    unsigned char map_sample[POOLRAD_PROBE_SIZE], packet[POOLRAD_PARTY_SIZE] = {0};
    uint32_t handles[POOLRAD_PARTY_MAX_LINKS], records[POOLRAD_PARTY_MAX_LINKS];
    uint32_t a5, head_address, handle;
    unsigned count = 0, links = 0, combatants = 0, occupied_slots = 0;
    if (out == NULL) return 0;
    memset(out, 0, POOLRAD_PARTY_SIZE);
    /* Reuse the shipped profile's exact app/A5/geometry/bootstrap guards.
     * This is not a new combat/exploration detector: health describes the party.
     */
    if (!poolrad_probe(ram, size, map_sample)) return 0;
    a5 = poolrad_u32(ram + 0x904) & 0x00ffffff;
    if (a5 < POOLRAD_PARTY_HEAD_BACK) return 0;
    head_address = a5 - POOLRAD_PARTY_HEAD_BACK;
    if (!poolrad_range(head_address, 4, size)) return 0;
    handle = poolrad_u32(ram + head_address) & 0x00ffffff;
    if (handle == 0) return 0; // No loaded party is unavailable, not an empty live row.

    while (handle != 0) {
        uint32_t record, block_header, physical_size;
        unsigned length, slot, has_visible_name = 0;
        unsigned char *row;
        if (links >= POOLRAD_PARTY_MAX_LINKS || handle < 0x1000 || (handle & 1)
                || !poolrad_range(handle, 4, size)) return 0;
        record = poolrad_u32(ram + handle) & 0x00ffffff;
        if (record < 0x1000 || (record & 1)
                || !poolrad_range(record, POOLRAD_PARTY_RECORD_SIZE, size)) return 0;
        /* 24-bit Mac heap headers encode physical size plus a low-nibble size
         * correction. The same 302-byte record legitimately occupies 312 bytes
         * (tag0x82) or 316 bytes (tag0x86) after allocator padding. Validate the
         * exact logical size, relocatable type and complete physical bounds;
         * never accept an arbitrary oversized record or inspect padding as data.
         * Inside Macintosh: Memory, Memory Manager pp2-22..2-23.
         */
        block_header = poolrad_u32(ram + record - 8);
        physical_size = block_header & 0x00ffffff;
        if ((block_header >> 28) != 8
                || physical_size != POOLRAD_PARTY_RECORD_SIZE + 8 + ((block_header >> 24) & 15)
                || (physical_size & 3)
                || !poolrad_range(record - 8, physical_size, size)) return 0;
        for (unsigned i = 0; i < links; i++) {
            if (handles[i] == handle || records[i] == record) return 0;
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
        if (slot == 0xff) return 0;
        if (slot >= POOLRAD_PARTY_MAX_MEMBERS) {
            if (++combatants > POOLRAD_PARTY_MAX_COMBATANTS) return 0;
            continue;
        }
        if (count >= POOLRAD_PARTY_MAX_MEMBERS || (occupied_slots & (1u << slot))) return 0;
        occupied_slots |= 1u << slot;
        for (length = 0; length < 16 && ram[record + length] != 0; length++) {
            unsigned char letter = ram[record + length];
            if (letter < 0x20 || letter == 0x7f) return 0;
            if (letter != ' ') has_visible_name = 1;
        }
        if (length == 0 || length == 16 || !has_visible_name) return 0;
        if (ram[record + POOLRAD_PARTY_MAX_HP_OFFSET] == 0
                || ram[record + POOLRAD_PARTY_CURRENT_HP_OFFSET] > ram[record + POOLRAD_PARTY_MAX_HP_OFFSET]) return 0;
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
        count++;
    }
    if (count == 0) return 0;
    memcpy(packet, "PRP2", 4); packet[4] = (unsigned char) count;
    memcpy(out, packet, sizeof(packet));
    return 1;
}
#endif
