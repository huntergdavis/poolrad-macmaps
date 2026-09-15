package name.osher.gil.minivmac.journal;

import java.nio.charset.StandardCharsets;

/**
 * The text the running game is showing in its own Message window, as delivered
 * by the native PRT1 reader. Nothing here is written to the guest, and an
 * unreadable sample is reported as unavailable rather than as an empty message.
 */
public final class GameMessage {
    public static final int MAX_TEXT = 512;
    public static final int PACKET_SIZE = 8 + MAX_TEXT;

    /** Null only when the packet is malformed or the reader could not read the window. */
    public final String text;
    /**
     * True when the game's own text was longer than the reader's ceiling. The
     * tail is missing, so a reference number near the end may have lost a
     * digit; citation reading must refuse a truncated sample outright.
     */
    public final boolean truncated;

    private GameMessage(String text, boolean truncated) {
        this.text = text; this.truncated = truncated;
    }

    /** Returns null for anything that is not a well-formed available PRT1 packet. */
    public static GameMessage parse(byte[] packet) {
        if (packet == null || packet.length != PACKET_SIZE) return null;
        if (packet[0] != 'P' || packet[1] != 'R' || packet[2] != 'T' || packet[3] != '1') return null;
        int status = packet[4] & 255, truncated = packet[5] & 255;
        int length = ((packet[6] & 255) << 8) | (packet[7] & 255);
        if (status == 255) {
            // An unavailable packet must not smuggle text along with it.
            if (truncated != 0 || length != 0) return null;
            for (int at = 8; at < packet.length; at++) if (packet[at] != 0) return null;
            return null;
        }
        if (status != 1 || truncated > 1 || length > MAX_TEXT) return null;
        for (int at = 8 + length; at < packet.length; at++) if (packet[at] != 0) return null;
        for (int at = 8; at < 8 + length; at++) {
            int value = packet[at] & 255;
            boolean printable = value >= 0x20 && value <= 0x7e || value == 0x0d || value == 0x09;
            if (!printable) return null;
        }
        return new GameMessage(new String(packet, 8, length, StandardCharsets.US_ASCII), truncated == 1);
    }
}
