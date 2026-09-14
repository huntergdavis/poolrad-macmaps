/* Read-only Macintosh Pool of Radiance v1.1 party health.
 * Independent disassembly and capture evidence: docs/PARTY.md.
 * Only party order, names and verified current/maximum HP leave the core.
 */
#ifndef POOLRAD_PARTY_PROBE_H
#define POOLRAD_PARTY_PROBE_H
#include "POOLRAD.h"

#define POOLRAD_PARTY_MAX_MEMBERS 8
#define POOLRAD_PARTY_ROW_SIZE 20
#define POOLRAD_PARTY_SIZE (8 + POOLRAD_PARTY_MAX_MEMBERS * POOLRAD_PARTY_ROW_SIZE)
#define POOLRAD_PARTY_HEAD_BACK 20894
#define POOLRAD_PARTY_RECORD_SIZE 304
#define POOLRAD_PARTY_NEXT_OFFSET 0x110
#define POOLRAD_PARTY_CURRENT_HP_OFFSET 0x12b
#define POOLRAD_PARTY_MAX_HP_OFFSET 0x32

/* PRP1: byte4=count, bytes5..7=0; eight rows: name[16], current, max, 0, 0.
 * Names retain Macintosh Roman bytes, zero terminated. Unused rows are zero.
 * The caller owns a separate POOLRAD_PARTY_SIZE-byte output buffer; on failure
 * it is zeroed so a discarded/invalid sample cannot masquerade as old health.
 */
static int poolrad_party_probe(const unsigned char *ram, size_t size, unsigned char *out) {
    unsigned char map_sample[POOLRAD_PROBE_SIZE], packet[POOLRAD_PARTY_SIZE] = {0};
    uint32_t handles[POOLRAD_PARTY_MAX_MEMBERS], records[POOLRAD_PARTY_MAX_MEMBERS];
    uint32_t a5, head_address, handle;
    unsigned count = 0;
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
        uint32_t record;
        unsigned length, has_visible_name = 0;
        unsigned char *row;
        if (count >= POOLRAD_PARTY_MAX_MEMBERS || handle < 0x1000 || (handle & 1)
                || !poolrad_range(handle, 4, size)) return 0;
        record = poolrad_u32(ram + handle) & 0x00ffffff;
        if (record < 0x1000 || (record & 1)
                || !poolrad_range(record, POOLRAD_PARTY_RECORD_SIZE, size)) return 0;
        if ((poolrad_u32(ram + record - 8) & 0x00ffffff) != POOLRAD_PARTY_RECORD_SIZE + 8) return 0;
        for (unsigned i = 0; i < count; i++) {
            if (handles[i] == handle || records[i] == record) return 0;
        }
        handles[count] = handle; records[count] = record;
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
        count++;
        handle = poolrad_u32(ram + record + POOLRAD_PARTY_NEXT_OFFSET) & 0x00ffffff;
    }
    memcpy(packet, "PRP1", 4); packet[4] = (unsigned char) count;
    memcpy(out, packet, sizeof(packet));
    return 1;
}
#endif
