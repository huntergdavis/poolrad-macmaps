/* Read-only Macintosh v1.1 code-wheel profile, derived from the actual input
 * function and labeled private RAM captures. No OCR, RAM mutation, or guessing.
 * A frame must belong to the CURRENT CPU A6 chain: stale stack bytes do not count.
 */
#ifndef POOLRAD_WHEEL_H
#define POOLRAD_WHEEL_H
#include <stdint.h>
#include <stddef.h>
#include <string.h>

#define POOLRAD_WHEEL_SIZE 80
static uint32_t prw_u32(const unsigned char *p) {
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | p[3];
}
static unsigned prw_u16(const unsigned char *p) { return ((unsigned)p[0] << 8) | p[1]; }
static void prw_put32(unsigned char *p, uint32_t value) {
    p[0] = value >> 24; p[1] = value >> 16; p[2] = value >> 8; p[3] = value;
}
static int prw_range(uint32_t at, size_t count, size_t size) {
    return at >= 0x1000 && at < size && count <= size - at;
}
static int prw_text(const unsigned char *ram, size_t size, uint32_t at, const char *text) {
    size_t length = strlen(text) + 1;
    return prw_range(at, length, size) && memcmp(ram + at, text, length) == 0;
}

static int poolrad_wheel_probe(const unsigned char *ram, size_t size,
        uint32_t a6, uint32_t a7, unsigned char *out) {
    static const char app[] = "Pool of Radiance v1.1";
    static const char prompt[] = "Input the code word: ";
    static const char *const answers[13] = {"BEWARE", "ZOMBIE", "NOTNOW", "COPPER",
        "DRAGON", "EFREET", "FRIEND", "JUNGLE", "KNIGHT", "SAVIOR", "TEMPLE", "VULCAN", "WYVERN"};
    static const unsigned char before[] = {0x48,0x6e,0xff,0xd6,0x3f,0x3c,0x00,0x28,
        0x3f,0x3c,0x00,0x0a,0x48,0x79};
    static const unsigned char after[] = {0x4f,0xef,0x00,0x0c,0x1b,0x6e,0xff,0xd3,0xa1,0x7d,
        0x70,0x00,0x10,0x2e,0xff,0xd5,0xc0,0xfc,0x00,0x0c,0x41,0xed,0xfd,0xb8,
        0xd1,0xc0,0x2f,0x10,0x48,0x6e,0xff,0xd6,0x4e,0xad,0x01,0x32,0x50,0x8f,0x4a,0x40,0x67,0x3e};
    uint32_t a5, frame;
    if (!ram || !out || size < 0x930 || ram[0x910] != sizeof(app)-1
            || memcmp(ram + 0x911, app, sizeof(app)-1)) return 0;
    a5 = prw_u32(ram + 0x904) & 0x00ffffff;
    a6 &= 0x00ffffff; a7 &= 0x00ffffff;
    if (a5 < 0x6000 || (a5 & 1) || !prw_range(a5, 4, size)
            || !prw_range(a5-0x248, 13*12, size)
            || (a6 & 1) || (a7 & 1) || a7 > a6 || a6 >= a5
            || !prw_range(a7, 8, size) || a5-a7 > 0x10000) return 0;
    frame = a6;
    for (int depth = 0; depth < 64; depth++) {
        uint32_t parent, ret;
        if ((frame & 1) || frame < a7 || frame >= a5 || !prw_range(frame, 20, size)) return 0;
        parent = prw_u32(ram + frame) & 0x00ffffff;
        ret = prw_u32(ram + frame + 4) & 0x00ffffff;
        if ((parent & 1) || parent <= frame || parent > a5 || parent-frame > 0x10000) return 0;
        if (ret >= 22 && prw_range(ret-22, 22+sizeof(after), size)
                && memcmp(ram+ret-22, before, sizeof(before)) == 0
                && memcmp(ram+ret-4, "\x4e\xad\x00\xaa", 4) == 0
                && memcmp(ram+ret, after, sizeof(after)) == 0) {
            uint32_t literal = prw_u32(ram+ret-8) & 0x00ffffff;
            uint32_t destination = prw_u32(ram+frame+16) & 0x00ffffff;
            unsigned index, attempt, length;
            uint32_t word, dialog, text_handle, text_record, item_handle, text;
            if (parent < 46 || !prw_range(parent-46, 46, size)
                    || destination != parent-42 || parent-46 < frame+20
                    || !prw_text(ram,size,literal,prompt)
                    || (prw_u32(ram+frame+8)&0x00ffffff) != literal
                    || memcmp(ram+frame+12,"\x00\x0a\x00\x28",4)) return 0;
            index = ram[parent-43]; attempt = ram[parent-44];
            if (index >= 13 || attempt < 1 || attempt > 3) return 0;
            word = prw_u32(ram+a5-0x248+index*12) & 0x00ffffff;
            if (!prw_text(ram,size,word,answers[index])) return 0;
            /* The parent output buffer is filled ONLY after Return. Read the live
             * dialog's TextEdit handle instead, and require its matching item handle.
             * Mac captures verify TE length at +60, text handle +62, selection +32/+34.
             */
            if (frame < 0x126 || frame-0x126 < a7 || !prw_range(frame-0x126,0x126,size)
                    || ram[frame-0x119] != 1 || prw_u16(ram+frame-0x118) != 16
                    || !prw_range(a5-0x5f4e,5,size) || ram[a5-0x5f4a] != 40) return 0;
            dialog = prw_u32(ram+frame-0x108) & 0x00ffffff;
            if ((dialog&1) || !prw_range(dialog,170,size)) return 0;
            text_handle = prw_u32(ram+dialog+160) & 0x00ffffff;
            if ((text_handle&1) || !prw_range(text_handle,4,size)) return 0;
            text_record = prw_u32(ram+text_handle) & 0x00ffffff;
            if ((text_record&1) || !prw_range(text_record,66,size)) return 0;
            item_handle = prw_u32(ram+a5-0x5f4e) & 0x00ffffff;
            if ((item_handle&1) || !prw_range(item_handle,4,size)
                    || (prw_u32(ram+text_record+62)&0x00ffffff) != item_handle) return 0;
            length = prw_u16(ram+text_record+60);
            if (length > 40 || prw_u16(ram+text_record+32) != length
                    || prw_u16(ram+text_record+34) != length) return 0;
            text = prw_u32(ram+item_handle) & 0x00ffffff;
            if (!prw_range(text,length,size)) return 0;
            for (unsigned i=0;i<length;i++) {
                unsigned c = ram[text+i];
                if (c < 32 || c > 126) return 0;
            }
            memset(out,0,POOLRAD_WHEEL_SIZE); memcpy(out,"PRW1",4);
            prw_put32(out+4,a5); prw_put32(out+8,parent); prw_put32(out+12,ret);
            out[16] = index; out[17] = attempt; out[18] = length; out[19] = 6;
            memcpy(out+20,answers[index],6); memcpy(out+28,ram+text,length);
            return 1;
        }
        frame = parent;
    }
    return 0;
}
#endif
