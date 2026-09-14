package name.osher.gil.minivmac.mapper;

import java.nio.charset.StandardCharsets;

/** A verified CURRENT wheel input frame, not a string found by scanning stale RAM. */
public final class WheelPrompt {
    private static final String[] ANSWERS = {"BEWARE", "ZOMBIE", "NOTNOW", "COPPER",
            "DRAGON", "EFREET", "FRIEND", "JUNGLE", "KNIGHT", "SAVIOR", "TEMPLE", "VULCAN", "WYVERN"};
    public final String answer, typed;
    public final int index, attempt;
    public final long a5, frame, returnAddress;

    private WheelPrompt(byte[] data) {
        a5 = u32(data, 4); frame = u32(data, 8); returnAddress = u32(data, 12);
        index = data[16] & 255; attempt = data[17] & 255;
        answer = new String(data, 20, 6, StandardCharsets.US_ASCII);
        typed = new String(data, 28, data[18] & 255, StandardCharsets.US_ASCII);
    }

    /** Null means unverified/unavailable. It must never trigger automatic typing. */
    public static WheelPrompt parse(byte[] data) {
        if (data == null || data.length != 80 || data[0] != 'P' || data[1] != 'R'
                || data[2] != 'W' || data[3] != '1') return null;
        int index = data[16] & 255, attempt = data[17] & 255, length = data[18] & 255;
        long a5 = u32(data, 4), frame = u32(data, 8), ret = u32(data, 12);
        if (index >= ANSWERS.length || attempt < 1 || attempt > 3 || length > 40 || data[19] != 6
                || a5 >= 0x1000000 || frame >= a5 || a5-frame > 0x10000
                || frame < 0x1000 || ret < 0x1000 || ret >= 0x1000000
                || ((a5 | frame | ret) & 1) != 0 || data[26] != 0 || data[27] != 0) return null;
        for (int i = 0; i < 6; i++) if (data[20+i] != ANSWERS[index].charAt(i)) return null;
        for (int i = 0; i < length; i++) if (data[28+i] < 32 || data[28+i] > 126) return null;
        for (int i = 28+length; i < data.length; i++) if (data[i] != 0) return null;
        return new WheelPrompt(data);
    }

    /** Stable across typed prefixes; a changed attempt/area allocation is a new challenge. */
    public String token() {
        return Long.toHexString(a5) + ":" + Long.toHexString(frame) + ":"
                + Long.toHexString(returnAddress) + ":" + index + ":" + attempt;
    }

    private static long u32(byte[] data, int at) {
        return ((data[at] & 255L) << 24) | ((data[at+1] & 255L) << 16)
                | ((data[at+2] & 255L) << 8) | (data[at+3] & 255L);
    }
}
