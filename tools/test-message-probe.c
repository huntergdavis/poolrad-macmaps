/* Synthetic RAM only: no ROM, game bytes, or copyrighted message text. */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "../android/minivmac/src/main/jni/src/POOLRAD_MESSAGE.h"

static unsigned char ram[65536], out[POOLRAD_MESSAGE_SIZE];
static void put32(size_t at, uint32_t value) {
    ram[at] = value >> 24; ram[at+1] = value >> 16; ram[at+2] = value >> 8; ram[at+3] = value;
}
/* A5 -> TEHandle -> TERec -> hText -> characters, all relocatable. */
static void fixture(uint32_t a5, uint32_t handle, uint32_t record,
                    uint32_t text_handle, uint32_t text, const char *value) {
    memset(ram, 0, sizeof(ram));
    const char name[] = "Pool of Radiance v1.1";
    ram[0x910] = sizeof(name) - 1; memcpy(ram + 0x911, name, sizeof(name) - 1);
    put32(0x904, a5);
    put32(a5 - POOLRAD_MESSAGE_BACK, handle);
    put32(handle, record);
    unsigned length = (unsigned)strlen(value);
    ram[record + POOLRAD_TE_LENGTH] = (unsigned char)(length >> 8);
    ram[record + POOLRAD_TE_LENGTH + 1] = (unsigned char)length;
    put32(record + POOLRAD_TE_HTEXT, text_handle);
    put32(text_handle, text);
    memcpy(ram + text, value, length);
}
static const char *decoded(void) {
    static char buffer[POOLRAD_MESSAGE_MAX + 1];
    unsigned length = ((unsigned)out[POOLRAD_MESSAGE_LENGTH_OUT] << 8)
        | out[POOLRAD_MESSAGE_LENGTH_OUT + 1];
    memcpy(buffer, out + POOLRAD_MESSAGE_TEXT_OUT, length); buffer[length] = 0;
    return buffer;
}
static void unavailable(void) {
    assert(memcmp(out, "PRT1", 4) == 0);
    assert(out[POOLRAD_MESSAGE_STATUS_OUT] == POOLRAD_MESSAGE_UNAVAILABLE);
    assert(out[POOLRAD_MESSAGE_TRUNCATED_OUT] == 0);
    for (int i = POOLRAD_MESSAGE_LENGTH_OUT; i < POOLRAD_MESSAGE_SIZE; i++) assert(out[i] == 0);
}

int main(void) {
    const uint32_t a5 = 0x9000, handle = 0x2000, record = 0x3000;
    const uint32_t text_handle = 0x4000, text = 0x5000;

    /* A sentence shaped like the one the game printed in the tavern. */
    fixture(a5, handle, record, text_handle, text, "YOU OVERHEAR TAVERN TALE 15");
    unsigned char original[sizeof(ram)]; memcpy(original, ram, sizeof(ram));
    assert(poolrad_message_probe(ram, sizeof(ram), out));
    assert(memcmp(out, "PRT1", 4) == 0);
    assert(out[POOLRAD_MESSAGE_STATUS_OUT] == POOLRAD_MESSAGE_PRESENT);
    assert(out[POOLRAD_MESSAGE_TRUNCATED_OUT] == 0);
    assert(strcmp(decoded(), "YOU OVERHEAR TAVERN TALE 15") == 0);
    assert(memcmp(original, ram, sizeof(ram)) == 0); // Never writes to guest RAM.

    /* Relocated everywhere: no absolute address is assumed. */
    fixture(0x8100, 0x2200, 0x3400, 0x4600, 0x5800, "A DRUNKEN BRAWL BREAKS OUT.");
    assert(poolrad_message_probe(ram, sizeof(ram), out));
    assert(strcmp(decoded(), "A DRUNKEN BRAWL BREAKS OUT.") == 0);

    /* Carriage returns and tabs are the game's own line breaks. */
    fixture(a5, handle, record, text_handle, text, "FIRST LINE\rSECOND\tLINE");
    assert(poolrad_message_probe(ram, sizeof(ram), out));
    assert(strcmp(decoded(), "FIRST LINE\rSECOND\tLINE") == 0);

    /* An empty Message window is empty, not unavailable. */
    fixture(a5, handle, record, text_handle, text, "");
    assert(poolrad_message_probe(ram, sizeof(ram), out));
    assert(out[POOLRAD_MESSAGE_STATUS_OUT] == POOLRAD_MESSAGE_PRESENT);
    assert(decoded()[0] == 0);

    /* One byte that is not the game's own text rejects the whole sample. */
    for (int byte = 0; byte < 256; byte++) {
        fixture(a5, handle, record, text_handle, text, "ABCDE");
        ram[text + 2] = (unsigned char)byte;
        assert(poolrad_message_probe(ram, sizeof(ram), out));
        int printable = (byte >= 0x20 && byte <= 0x7e) || byte == 0x0d || byte == 0x09;
        assert((out[POOLRAD_MESSAGE_STATUS_OUT] == POOLRAD_MESSAGE_PRESENT) == printable);
        if (!printable) unavailable();
    }

    /* Exactly the ceiling fits; one more is reported as truncated, never silently cut. */
    char full[POOLRAD_MESSAGE_MAX + 2];
    memset(full, 'A', sizeof(full) - 1); full[sizeof(full) - 1] = 0;
    full[POOLRAD_MESSAGE_MAX] = 0;
    fixture(a5, handle, record, text_handle, text, full);
    assert(poolrad_message_probe(ram, sizeof(ram), out));
    assert(out[POOLRAD_MESSAGE_TRUNCATED_OUT] == 0);
    assert(strlen(decoded()) == POOLRAD_MESSAGE_MAX);
    full[POOLRAD_MESSAGE_MAX] = 'A'; full[POOLRAD_MESSAGE_MAX + 1] = 0;
    fixture(a5, handle, record, text_handle, text, full);
    assert(poolrad_message_probe(ram, sizeof(ram), out));
    assert(out[POOLRAD_MESSAGE_STATUS_OUT] == POOLRAD_MESSAGE_PRESENT);
    assert(out[POOLRAD_MESSAGE_TRUNCATED_OUT] == 1);
    assert(strlen(decoded()) == POOLRAD_MESSAGE_MAX);

    /* Bad pointers at each indirection are unavailable, never a wild read. */
    const uint32_t bad[] = {0, 1, 0xfff, 0x3001, 0xffffff, 0x100000};
    for (unsigned i = 0; i < sizeof(bad)/sizeof(bad[0]); i++) {
        fixture(a5, handle, record, text_handle, text, "ABCDE");
        put32(a5 - POOLRAD_MESSAGE_BACK, bad[i]);
        assert(poolrad_message_probe(ram, sizeof(ram), out)); unavailable();
        fixture(a5, handle, record, text_handle, text, "ABCDE");
        put32(handle, bad[i]);
        assert(poolrad_message_probe(ram, sizeof(ram), out)); unavailable();
        fixture(a5, handle, record, text_handle, text, "ABCDE");
        put32(record + POOLRAD_TE_HTEXT, bad[i]);
        assert(poolrad_message_probe(ram, sizeof(ram), out)); unavailable();
        fixture(a5, handle, record, text_handle, text, "ABCDE");
        put32(text_handle, bad[i]);
        assert(poolrad_message_probe(ram, sizeof(ram), out)); unavailable();
        fixture(a5, handle, record, text_handle, text, "ABCDE");
        put32(0x904, bad[i]);
        int ok = poolrad_message_probe(ram, sizeof(ram), out);
        assert(ok); unavailable();
    }

    /* A length that runs off the end of RAM is unavailable. */
    fixture(a5, handle, record, text_handle, (uint32_t)(sizeof(ram) - 4), "ABC");
    ram[record + POOLRAD_TE_LENGTH] = 0xff; ram[record + POOLRAD_TE_LENGTH + 1] = 0xff;
    assert(poolrad_message_probe(ram, sizeof(ram), out)); unavailable();

    /* Another application frontmost is not this game's message at all. */
    fixture(a5, handle, record, text_handle, text, "ABCDE");
    ram[0x910] = 6; memcpy(ram + 0x911, "Finder", 6);
    assert(!poolrad_message_probe(ram, sizeof(ram), out));
    assert(!poolrad_message_probe(NULL, 0, out));
    fixture(a5, handle, record, text_handle, text, "ABCDE");
    assert(!poolrad_message_probe(ram, sizeof(ram), NULL));
    assert(!poolrad_message_probe(ram, 0x92f, out));

    puts("Message probe: TERec decoding, printable-only guard, truncation and bounds passed.");
    return 0;
}
