package name.osher.gil.minivmac.mapper;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Immutable read-only Macintosh party health, in the guest's linked-list order. */
public final class PartyState {
    public static final int MAX_MEMBERS = 8;
    public static final int ROW_SIZE = 20;
    public static final int PACKET_SIZE = 8 + MAX_MEMBERS * ROW_SIZE;

    public static final class Member {
        public final String name;
        public final int currentHp, maxHp;
        private Member(String name, int currentHp, int maxHp) {
            this.name = name; this.currentHp = currentHp; this.maxHp = maxHp;
        }
        public float healthFraction() { return currentHp / (float) maxHp; }
    }

    public final List<Member> members;
    private final byte[] packet;

    private PartyState(List<Member> members, byte[] packet) {
        this.members = Collections.unmodifiableList(members);
        this.packet = packet.clone();
    }

    public List<Member> members() { return members; }

    public boolean sameDisplay(PartyState other) {
        return other != null && Arrays.equals(packet, other.packet);
    }

    /** Null means unavailable. A previous packet must not remain labeled live. */
    public static PartyState parse(byte[] data) {
        if (data == null || data.length != PACKET_SIZE || data[0] != 'P' || data[1] != 'R'
                || data[2] != 'P' || data[3] != '1') return null;
        int count = data[4] & 255;
        if (count < 1 || count > MAX_MEMBERS || data[5] != 0 || data[6] != 0 || data[7] != 0) return null;
        List<Member> members = new ArrayList<>(count);
        for (int index = 0; index < MAX_MEMBERS; index++) {
            int start = 8 + index * ROW_SIZE;
            if (index >= count) {
                for (int offset = 0; offset < ROW_SIZE; offset++) if (data[start + offset] != 0) return null;
                continue;
            }
            int length = 0;
            boolean visible = false, extended = false;
            while (length < 16 && data[start + length] != 0) {
                int letter = data[start + length] & 255;
                if (letter < 32 || letter == 127) return null;
                visible |= letter != 32;
                extended |= letter >= 128;
                length++;
            }
            if (length == 0 || length == 16 || !visible) return null;
            for (int offset = length; offset < 16; offset++) if (data[start + offset] != 0) return null;
            int current = data[start + 16] & 255, maximum = data[start + 17] & 255;
            if (maximum == 0 || current > maximum || data[start + 18] != 0 || data[start + 19] != 0) return null;
            final String name;
            try {
                // ASCII names need no optional charset. Accented Mac names are
                // decoded as Mac Roman, never misrepresented as UTF-8/Latin-1.
                name = new String(data, start, length, Charset.forName(extended ? "x-MacRoman" : "US-ASCII"));
            } catch (IllegalArgumentException unavailableCharset) { return null; }
            members.add(new Member(name, current, maximum));
        }
        return new PartyState(members, data);
    }
}
