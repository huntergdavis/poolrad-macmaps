/* Read-only reader for the text the supplied Macintosh Pool of Radiance v1.1
 * is showing in its own Message window. Offsets verified against the supplied
 * executable and every private capture; see docs/MESSAGE_MEMORY.md.
 *
 * CODE2 +0x606c..+0x608c creates the window and stores its WindowPtr at
 * A5-0x6174; CODE2 +0x6120 calls _TENew and stores the TEHandle at A5-0x6178.
 * From there it is an ordinary TERec: teLength at +0x3c, hText at +0x3e.
 *
 * Nothing is written, nothing is interpreted here. The caller decides what the
 * words mean; this only hands over bytes the game itself is displaying.
 */
#ifndef POOLRAD_MESSAGE_PROBE_H
#define POOLRAD_MESSAGE_PROBE_H
#include "POOLRAD.h"

#define POOLRAD_MESSAGE_BACK 0x6178
#define POOLRAD_TE_LENGTH 0x3c
#define POOLRAD_TE_HTEXT 0x3e
#define POOLRAD_MESSAGE_MAX 512
#define POOLRAD_MESSAGE_SIZE (8 + POOLRAD_MESSAGE_MAX)
#define POOLRAD_MESSAGE_STATUS_OUT 4
#define POOLRAD_MESSAGE_TRUNCATED_OUT 5
#define POOLRAD_MESSAGE_LENGTH_OUT 6
#define POOLRAD_MESSAGE_TEXT_OUT 8
#define POOLRAD_MESSAGE_PRESENT 1
#define POOLRAD_MESSAGE_UNAVAILABLE 255

/* The game writes plain upper-case ASCII and carriage returns. Anything else
 * means the handle is not the text this reader thinks it is: the three combat
 * captures that the application-profile guard already rejects also land on
 * non-text bytes here. Rejecting the whole sample is the safe answer, because
 * a half-decoded sentence could carry a half-decoded reference number.
 */
static inline int poolrad_message_char(unsigned char value) {
    return (value >= 0x20 && value <= 0x7e) || value == 0x0d || value == 0x09;
}

/* inline, because this header is now included by the combat reader too, and a
 * plain static is an unused function to whichever of them does not call it.
 *
 * Returns 1 and fills a POOLRAD_MESSAGE_SIZE packet whenever the supported
 * game is frontmost, even if the text itself cannot be read: an unavailable
 * packet is a fact worth reporting, and never an empty message.
 */
static inline int poolrad_message_probe(const unsigned char *ram, size_t size, unsigned char *out) {
    uint32_t a5, handle, record, text_handle, text;
    unsigned length, copied, i;
    if (out == NULL) return 0;
    if (!poolrad_game_name(ram, size)) return 0;
    memset(out, 0, POOLRAD_MESSAGE_SIZE);
    memcpy(out, "PRT1", 4);
    out[POOLRAD_MESSAGE_STATUS_OUT] = POOLRAD_MESSAGE_UNAVAILABLE;
    a5 = poolrad_u32(ram + 0x904) & 0x00ffffff;
    if (a5 < POOLRAD_MESSAGE_BACK || (a5 & 1)) return 1;
    if (!poolrad_range(a5 - POOLRAD_MESSAGE_BACK, 4, size)) return 1;
    handle = poolrad_u32(ram + a5 - POOLRAD_MESSAGE_BACK) & 0x00ffffff;
    if (handle < 0x1000 || (handle & 1) || !poolrad_range(handle, 4, size)) return 1;
    record = poolrad_u32(ram + handle) & 0x00ffffff;
    if (record < 0x1000 || (record & 1)
            || !poolrad_range(record, POOLRAD_TE_HTEXT + 4, size)) return 1;
    length = ((unsigned)ram[record + POOLRAD_TE_LENGTH] << 8)
        | ram[record + POOLRAD_TE_LENGTH + 1];
    text_handle = poolrad_u32(ram + record + POOLRAD_TE_HTEXT) & 0x00ffffff;
    if (text_handle < 0x1000 || (text_handle & 1)
            || !poolrad_range(text_handle, 4, size)) return 1;
    text = poolrad_u32(ram + text_handle) & 0x00ffffff;
    if (text < 0x1000) return 1;
    copied = length > POOLRAD_MESSAGE_MAX ? POOLRAD_MESSAGE_MAX : length;
    if (!poolrad_range(text, length, size)) return 1;
    for (i = 0; i < copied; i++)
        if (!poolrad_message_char(ram[text + i])) return 1;
    memcpy(out + POOLRAD_MESSAGE_TEXT_OUT, ram + text, copied);
    out[POOLRAD_MESSAGE_STATUS_OUT] = POOLRAD_MESSAGE_PRESENT;
    /* A truncated sample may have lost a digit, so the reader must not read a
     * reference number out of it. Say so rather than hiding the text. */
    out[POOLRAD_MESSAGE_TRUNCATED_OUT] = length > POOLRAD_MESSAGE_MAX;
    out[POOLRAD_MESSAGE_LENGTH_OUT] = (unsigned char)(copied >> 8);
    out[POOLRAD_MESSAGE_LENGTH_OUT + 1] = (unsigned char)copied;
    return 1;
}

/* The Combat Message window, which is a different window from the one above.
 *
 * CODE2 puts the ordinary Message window's TEHandle at A5-0x6178. During a
 * battle that one is empty -- teLength zero in all sixteen captures -- and the
 * text the player is reading lives in a second TERec whose handle sits at
 * A5-0x6230. Found by taking the visible combat text, "Hogarth\rHitpoints 10
 * ...", locating its buffer, walking back through its master pointer to the
 * TERec whose hText names it, and then to the global holding that TERec's
 * handle. Confirmed against every capture: in combat it reads the acting
 * character and matches the screenshot taken beside it (Lara, Shara, Hogarth,
 * Zarram); out of combat there is nothing there.
 *
 * Its first line is the name of whoever is acting, which is the one thing a
 * small screen makes hard to keep track of.
 */
#define POOLRAD_COMBAT_MESSAGE_BACK 0x6230
#define POOLRAD_ACTOR_MAX 16

/* Copies the acting character's name into `out`, NUL terminated, and returns
 * its length. Zero means nobody: not in combat, unreadable, or a first line
 * that is not a plain name.
 */
static inline unsigned poolrad_combat_actor(const unsigned char *ram, size_t size,
                                     unsigned char *out) {
    uint32_t a5, handle, record, text_handle, text;
    unsigned length, i;
    if (out == NULL) return 0;
    memset(out, 0, POOLRAD_ACTOR_MAX);
    if (!poolrad_game_name(ram, size)) return 0;
    a5 = poolrad_u32(ram + 0x904) & 0x00ffffff;
    if (a5 < POOLRAD_COMBAT_MESSAGE_BACK || (a5 & 1)) return 0;
    if (!poolrad_range(a5 - POOLRAD_COMBAT_MESSAGE_BACK, 4, size)) return 0;
    handle = poolrad_u32(ram + a5 - POOLRAD_COMBAT_MESSAGE_BACK) & 0x00ffffff;
    if (handle < 0x1000 || (handle & 1) || !poolrad_range(handle, 4, size)) return 0;
    record = poolrad_u32(ram + handle) & 0x00ffffff;
    if (record < 0x1000 || (record & 1)
            || !poolrad_range(record, POOLRAD_TE_HTEXT + 4, size)) return 0;
    length = ((unsigned)ram[record + POOLRAD_TE_LENGTH] << 8)
        | ram[record + POOLRAD_TE_LENGTH + 1];
    if (length == 0 || length > POOLRAD_MESSAGE_MAX) return 0;
    text_handle = poolrad_u32(ram + record + POOLRAD_TE_HTEXT) & 0x00ffffff;
    if (text_handle < 0x1000 || (text_handle & 1)
            || !poolrad_range(text_handle, 4, size)) return 0;
    text = poolrad_u32(ram + text_handle) & 0x00ffffff;
    if (text < 0x1000 || !poolrad_range(text, length, size)) return 0;
    /* The first line, and only if it looks like one of the game's own names:
     * printable, no leading or trailing space, and short enough to be one. */
    for (i = 0; i < length && ram[text + i] != 0x0d; i++) {
        if (i >= POOLRAD_ACTOR_MAX - 1) return 0;
        if (ram[text + i] < 0x20 || ram[text + i] > 0x7e) return 0;
    }
    if (i == 0 || i >= POOLRAD_ACTOR_MAX) return 0;
    if (ram[text] == ' ' || ram[text + i - 1] == ' ') return 0;
    memcpy(out, ram + text, i);
    return i;
}
#endif
