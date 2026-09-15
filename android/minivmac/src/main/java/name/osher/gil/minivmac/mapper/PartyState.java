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
    /** Eight per-member spell blocks follow the condition pairs. */
    public static final int SPELL_STRIDE = 8, SPELL_LEVELS = 3, SPELL_SLOTS = 21;
    public static final int SPELL_PACKET_SIZE = CONDITION_PACKET_SIZE + MAX_MEMBERS * SPELL_STRIDE;
    /** Eight per-member equipment blocks follow the spell blocks. */
    public static final int NAME_BYTES = 32, EQUIP_STRIDE = 1 + 2 * NAME_BYTES + 3;
    /** The slowest the printed rules describe; see EQUIPMENT_REFERENCE.md. */
    public static final int SLOWEST_MOVEMENT = 3;
    public static final int EQUIP_PACKET_SIZE = SPELL_PACKET_SIZE + MAX_MEMBERS * EQUIP_STRIDE;
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
        /**
         * Memorized spells the game reports ready to cast, and those the player
         * chose that still need rest, counted per spell level 1..3. Null means
         * this member's spell array could not be read; it is never guessed.
         */
        private final int[] ready, awaitingRest;
        /**
         * Readied weapon and armor as the game itself names them. Null means the
         * item blocks could not be read; an empty string means that slot really
         * is empty. The two are never conflated.
         */
        private final String readiedWeapon, readiedArmor;
        /** Movement in combat squares and carried gp-weight, as the game reports them. */
        public final int movementSquares, carriedWeight;
        private Member(String name, int currentHp, int maxHp, Integer armorClass, int characterClass,
                       int condition, int trackedEffects, boolean hasConditionSample,
                       int[] ready, int[] awaitingRest, String readiedWeapon, String readiedArmor,
                       int movementSquares, int carriedWeight) {
            this.readiedWeapon = readiedWeapon; this.readiedArmor = readiedArmor;
            this.movementSquares = movementSquares; this.carriedWeight = carriedWeight;
            this.name = name; this.currentHp = currentHp; this.maxHp = maxHp;
            this.armorClass = armorClass; this.characterClass = characterClass;
            this.condition = condition; this.trackedEffects = trackedEffects;
            this.hasConditionSample = hasConditionSample;
            this.ready = ready; this.awaitingRest = awaitingRest;
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
        public boolean spellsAvailable() { return ready != null; }
        /** Spells of this level the game will let the character cast right now. */
        public int spellsReady(int level) {
            return ready == null || level < 1 || level > SPELL_LEVELS ? 0 : ready[level - 1];
        }
        /** Spells chosen through the game's own Memorize screen that still need rest. */
        public int spellsAwaitingRest(int level) {
            return awaitingRest == null || level < 1 || level > SPELL_LEVELS ? 0 : awaitingRest[level - 1];
        }
        public int spellsReadyTotal() {
            int total = 0; for (int l = 1; l <= SPELL_LEVELS; l++) total += spellsReady(l); return total;
        }
        public int spellsAwaitingRestTotal() {
            int total = 0; for (int l = 1; l <= SPELL_LEVELS; l++) total += spellsAwaitingRest(l); return total;
        }
        /** True only when the game itself is holding spells that rest would finish. */
        public boolean restWouldMemorize() { return spellsAwaitingRestTotal() > 0; }
        private static String byLevel(Member member, boolean waiting) {
            StringBuilder out = new StringBuilder();
            for (int level = 1; level <= SPELL_LEVELS; level++) {
                int count = waiting ? member.spellsAwaitingRest(level) : member.spellsReady(level);
                if (count == 0) continue;
                if (out.length() > 0) out.append(", ");
                out.append("level ").append(level).append(" \u00d7 ").append(count);
            }
            return out.toString();
        }
        public String spellsReadyLabel() {
            if (ready == null) return "Spell readiness unavailable";
            String detail = byLevel(this, false);
            return detail.isEmpty() ? "No spells ready to cast" : "Ready to cast: " + detail;
        }
        public String spellsAwaitingRestLabel() {
            if (awaitingRest == null) return "Spell readiness unavailable";
            String detail = byLevel(this, true);
            return detail.isEmpty() ? "Nothing waiting on rest" : "Awaiting rest: " + detail;
        }

        public boolean equipmentAvailable() { return readiedWeapon != null; }
        /** Empty when that hand is genuinely empty; null only when unreadable. */
        public String readiedWeapon() { return readiedWeapon; }
        public String readiedArmor() { return readiedArmor; }
        public String readiedWeaponLabel() {
            if (readiedWeapon == null) return "Readied equipment unavailable";
            return readiedWeapon.isEmpty() ? "No weapon readied" : "Weapon: " + readiedWeapon;
        }
        public String readiedArmorLabel() {
            if (readiedArmor == null) return "Readied equipment unavailable";
            return readiedArmor.isEmpty() ? "No armor readied" : "Armor: " + readiedArmor;
        }

        public boolean loadAvailable() { return movementSquares >= 0; }
        /** True only when the game's own movement is already at the printed floor. */
        public boolean slowedToMinimum() {
            return movementSquares >= 0 && movementSquares <= SLOWEST_MOVEMENT;
        }
        public String loadLabel() {
            if (movementSquares < 0) return "Movement and carried weight unavailable";
            return "Movement: " + movementSquares + " squares · carrying " + carriedWeight;
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

    /**
     * One NUL-padded reference name. Everything after the terminator must be
     * zero, and only printable ASCII is accepted, so a malformed block is
     * rejected rather than shown as a fragment.
     */
    private static String itemName(byte[] data, int start) {
        int length = 0;
        while (length < NAME_BYTES && data[start + length] != 0) length++;
        if (length == NAME_BYTES) return null;
        for (int offset = length; offset < NAME_BYTES; offset++)
            if (data[start + offset] != 0) return null;
        for (int offset = 0; offset < length; offset++) {
            int letter = data[start + offset] & 255;
            if (letter < 0x20 || letter >= 0x7f) return null;
        }
        return new String(data, start, length, Charset.forName("US-ASCII"));
    }

    /** Null means unavailable. A previous packet must not remain labeled live. */
    public static PartyState parse(byte[] data) {
        if (data == null || data.length < 8 || data[0] != 'P' || data[1] != 'R' || data[2] != 'P'
                || (data[3] != '1' && data[3] != '2' && data[3] != '3' && data[3] != '4'
                    && data[3] != '5')) return null;
        boolean equipment = data[3] == '5';
        boolean spells = equipment || data[3] == '4';
        boolean conditions = spells || data[3] == '3';
        if (data.length != (equipment ? EQUIP_PACKET_SIZE : spells ? SPELL_PACKET_SIZE
                : conditions ? CONDITION_PACKET_SIZE : PACKET_SIZE)) return null;
        boolean details = data[3] != '1';
        int count = data[4] & 255;
        if (count < 1 || count > MAX_MEMBERS || data[5] != 0 || data[6] != 0 || data[7] != 0) return null;
        List<Member> members = new ArrayList<>(count);
        for (int index = 0; index < MAX_MEMBERS; index++) {
            int start = 8 + index * ROW_SIZE;
            if (index >= count) {
                for (int offset = 0; offset < ROW_SIZE; offset++) if (data[start + offset] != 0) return null;
                if (conditions && (data[PACKET_SIZE + index * 2] != 0 || data[PACKET_SIZE + index * 2 + 1] != 0)) return null;
                if (spells) for (int offset = 0; offset < SPELL_STRIDE; offset++)
                    if (data[CONDITION_PACKET_SIZE + index * SPELL_STRIDE + offset] != 0) return null;
                if (equipment) for (int offset = 0; offset < EQUIP_STRIDE; offset++)
                    if (data[SPELL_PACKET_SIZE + index * EQUIP_STRIDE + offset] != 0) return null;
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
            int[] ready = null, awaitingRest = null;
            if (spells) {
                int block = CONDITION_PACKET_SIZE + index * SPELL_STRIDE;
                int status = data[block] & 255;
                if (status != 0 && status != 255) return null;
                if (data[block + 7] != 0) return null;
                if (status == 255) {
                    for (int offset = 1; offset < SPELL_STRIDE; offset++)
                        if (data[block + offset] != 0) return null;
                } else {
                    ready = new int[SPELL_LEVELS]; awaitingRest = new int[SPELL_LEVELS];
                    int total = 0;
                    for (int level = 0; level < SPELL_LEVELS; level++) {
                        ready[level] = data[block + 1 + level] & 255;
                        awaitingRest[level] = data[block + 4 + level] & 255;
                        total += ready[level] + awaitingRest[level];
                    }
                    // The game's own array holds at most 21 slots; more is malformed.
                    if (total > SPELL_SLOTS) return null;
                }
            }
            String readiedWeapon = null, readiedArmor = null;
            int movementSquares = -1, carriedWeight = -1;
            if (equipment) {
                int block = SPELL_PACKET_SIZE + index * EQUIP_STRIDE;
                int status = data[block] & 255;
                if (status != 0 && status != 255) return null;
                if (status == 255) {
                    for (int offset = 1; offset < 1 + 2 * NAME_BYTES; offset++)
                        if (data[block + offset] != 0) return null;
                } else {
                    readiedWeapon = itemName(data, block + 1);
                    readiedArmor = itemName(data, block + 1 + NAME_BYTES);
                    if (readiedWeapon == null || readiedArmor == null) return null;
                }
                // Plain record fields: present whether or not the items resolved.
                movementSquares = data[block + 1 + 2 * NAME_BYTES] & 255;
                carriedWeight = ((data[block + 2 + 2 * NAME_BYTES] & 255) << 8)
                        | (data[block + 3 + 2 * NAME_BYTES] & 255);
            }
            final String name;
            try {
                // ASCII names need no optional charset. Accented Mac names are
                // decoded as Mac Roman, never misrepresented as UTF-8/Latin-1.
                name = new String(data, start, length, Charset.forName(extended ? "x-MacRoman" : "US-ASCII"));
            } catch (IllegalArgumentException unavailableCharset) { return null; }
            members.add(new Member(name, current, maximum, armorClass, characterClass,
                    condition, effects, conditions, ready, awaitingRest, readiedWeapon, readiedArmor,
                    movementSquares, carriedWeight));
        }
        return new PartyState(members, data);
    }
}
