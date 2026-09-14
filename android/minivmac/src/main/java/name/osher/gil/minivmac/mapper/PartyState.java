package name.osher.gil.minivmac.mapper;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Immutable read-only Macintosh party details, in the guest's linked-list order. */
public final class PartyState {
    public static final int MAX_MEMBERS = 8;
    public static final int ROW_SIZE = 20;
    public static final int PACKET_SIZE = 8 + MAX_MEMBERS * ROW_SIZE;
    public static final int CONDITION_PACKET_SIZE = PACKET_SIZE + MAX_MEMBERS * 2;
    public static final int POISONED = 1, HELPLESS = 2;
    private static final String[] CONDITION_LABELS = {
            "Okay", "Animated", "Temporarily gone", "Running", "Unconscious",
            "Dying", "Dead", "Petrified (Stoned)", "Gone"
    };

    // Original Mac v1.1 class-name table, selected by record +0x2f. These are
    // display identities, not a promise that every class can be player-created.
    private static final String[] CLASS_LABELS = {
            "Cleric", "Druid", "Fighter", "Paladin", "Ranger", "Magic-User", "Thief", "Monk",
            "Cleric/Fighter", "Cleric/Fighter/Magic-User", "Cleric/Ranger", "Cleric/Magic-User",
            "Cleric/Thief", "Fighter/Magic-User", "Fighter/Thief", "Fighter/Magic-User/Thief",
            "Magic-User/Thief", "Monster"
    };

    public static final class Member {
        public final String name;
        public final int currentHp, maxHp;
        /** Null means unavailable, including legacy health-only packets. Lower AC is better. */
        public final Integer armorClass;
        /** Original Mac class-table ID; -1 means unavailable, not Cleric (ID 0). */
        public final int characterClass;
        /** Mac status table ID 0..8; -1 is unavailable, never inferred from HP. */
        public final int condition;
        /** Only poison/helplessness flags; -1 means the effect list was unavailable. */
        public final int trackedEffects;
        private final boolean hasConditionSample;
        private Member(String name, int currentHp, int maxHp, Integer armorClass, int characterClass,
                       int condition, int trackedEffects, boolean hasConditionSample) {
            this.name = name; this.currentHp = currentHp; this.maxHp = maxHp;
            this.armorClass = armorClass; this.characterClass = characterClass;
            this.condition = condition; this.trackedEffects = trackedEffects;
            this.hasConditionSample = hasConditionSample;
        }
        public float healthFraction() { return currentHp / (float) maxHp; }
        public String classLabel() { return characterClass < 0 ? "Class unavailable" : CLASS_LABELS[characterClass]; }
        public boolean injured() { return currentHp < maxHp; }
        public boolean hasEffect(int flag) { return trackedEffects >= 0 && (trackedEffects & flag) != 0; }
        public String conditionLabel() { return condition < 0 ? "Condition unavailable" : CONDITION_LABELS[condition]; }
        public String effectsLabel() {
            if (trackedEffects < 0) return "Poison / helplessness unavailable";
            if (hasEffect(POISONED) && hasEffect(HELPLESS)) return "Poisoned; helpless";
            if (hasEffect(POISONED)) return "Poisoned";
            if (hasEffect(HELPLESS)) return "Helpless";
            return "No poison or helplessness detected";
        }
        public String conditionSummary() {
            return conditionLabel() + (injured() ? "; injured" : "") + "; " + effectsLabel();
        }
        /** One quiet monochrome badge; the full combination is available on tap. */
        public String badge() {
            if (condition == 6) return "X";
            if (condition == 5) return "!";
            if (condition == 4) return "Z";
            if (condition == 7) return "S";
            if (condition == 8 || condition == 2) return "–";
            if (hasEffect(POISONED)) return "P";
            if (hasEffect(HELPLESS)) return "H";
            if (condition == 3) return ">";
            if (condition == 1) return "A";
            if (injured()) return "+";
            if (hasConditionSample && (condition < 0 || trackedEffects < 0)) return "?";
            return "";
        }
        public String badgeMeaning() {
            switch (badge()) {
                case "X": return "Dead";
                case "!": return "Dying";
                case "Z": return "Unconscious";
                case "S": return "Petrified";
                case "–": return conditionLabel();
                case "P": return "Poisoned";
                case "H": return "Helpless";
                case ">": return "Running";
                case "A": return "Animated";
                case "+": return "Injured";
                case "?": return "Condition or tracked effects unavailable";
                default: return "No condition badge";
            }
        }
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
        if (data == null || data.length < 8 || data[0] != 'P' || data[1] != 'R'
                || data[2] != 'P' || (data[3] != '1' && data[3] != '2' && data[3] != '3')) return null;
        boolean conditions = data[3] == '3';
        if (data.length != (conditions ? CONDITION_PACKET_SIZE : PACKET_SIZE)) return null;
        boolean details = data[3] != '1';
        int count = data[4] & 255;
        if (count < 1 || count > MAX_MEMBERS || data[5] != 0 || data[6] != 0 || data[7] != 0) return null;
        List<Member> members = new ArrayList<>(count);
        for (int index = 0; index < MAX_MEMBERS; index++) {
            int start = 8 + index * ROW_SIZE;
            if (index >= count) {
                for (int offset = 0; offset < ROW_SIZE; offset++) if (data[start + offset] != 0) return null;
                if (conditions && (data[PACKET_SIZE + index * 2] != 0 || data[PACKET_SIZE + index * 2 + 1] != 0)) return null;
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
            if (maximum == 0 || current > maximum) return null;
            Integer armorClass = null;
            int characterClass = -1;
            if (details) {
                // The Mac displays 60 - its unsigned AC byte. PRP2 carries only
                // the signed-byte-representable -127..60 range; 0x80 is unknown.
                int ac = data[start + 18];
                if (ac != -128) {
                    if (ac > 60) return null;
                    armorClass = ac;
                }
                int id = data[start + 19] & 255;
                if (id != 255) {
                    if (id >= CLASS_LABELS.length) return null;
                    characterClass = id;
                }
            } else if (data[start + 18] != 0 || data[start + 19] != 0) {
                return null;
            }
            int condition = -1, effects = -1;
            if (conditions) {
                condition = data[PACKET_SIZE + index * 2] & 255;
                effects = data[PACKET_SIZE + index * 2 + 1] & 255;
                if (condition == 255) condition = -1;
                else if (condition >= CONDITION_LABELS.length) return null;
                if (effects == 255) effects = -1;
                else if (effects > (POISONED | HELPLESS)) return null;
            }
            final String name;
            try {
                // ASCII names need no optional charset. Accented Mac names are
                // decoded as Mac Roman, never misrepresented as UTF-8/Latin-1.
                name = new String(data, start, length, Charset.forName(extended ? "x-MacRoman" : "US-ASCII"));
            } catch (IllegalArgumentException unavailableCharset) { return null; }
            members.add(new Member(name, current, maximum, armorClass, characterClass, condition, effects, conditions));
        }
        return new PartyState(members, data);
    }
}
