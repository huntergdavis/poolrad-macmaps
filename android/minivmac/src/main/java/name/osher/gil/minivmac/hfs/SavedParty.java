package name.osher.gil.minivmac.hfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Who is in a saved game, read from its resource fork.
 *
 * The saved game keeps one `PoRc` resource per party member, named with the
 * character's own name, and each one is the same 302-byte character record the
 * running game holds in memory, followed by that character's items. So the
 * offsets this project already established by reading the game's own code apply
 * unchanged — see RECORD_ALIGNMENT.md — and a saved game can be read with the
 * knowledge already in hand rather than from nothing.
 *
 * Read-only. This tells you who is in a save; it does not change one.
 */
public final class SavedParty {
    /** The Finder type the game gives a saved character. */
    public static final String CHARACTER_TYPE = "PoRc";
    /** Offsets inside the record, confirmed against both memory and two saves. */
    public static final int NAME = 0x00, NAME_BYTES = 16;
    public static final int ABILITIES = 0x10, ABILITY_COUNT = 6;
    public static final int EXCEPTIONAL_STRENGTH = 0x16;
    public static final int MEMORISED_SPELLS = 0x17, SPELL_SLOTS = 21;
    public static final int CLASS = 0x2f, MAX_HP = 0x32;
    public static final int CURRENT_HP = 0x12b, MOVEMENT = 0x12c;
    public static final int RECORD_SIZE = 302;
    /** Right after the record: a count of the character's items. */
    public static final int ITEM_COUNT = 0x12f;

    public static final class Member {
        public final String name;
        public final int characterClass, maxHp, currentHp, movement, itemCount;
        public final int[] abilities;
        /** The whole resource, record and items, exactly as the save holds it. */
        public final byte[] record;

        Member(String name, byte[] record) {
            this.name = name; this.record = record;
            this.characterClass = record[CLASS] & 255;
            this.maxHp = record[MAX_HP] & 255;
            this.currentHp = record[CURRENT_HP] & 255;
            this.movement = record[MOVEMENT] & 255;
            this.itemCount = record[ITEM_COUNT] & 255;
            this.abilities = new int[ABILITY_COUNT];
            for (int i = 0; i < ABILITY_COUNT; i++) abilities[i] = record[ABILITIES + i] & 255;
        }

        @Override public String toString() {
            return name + " (class " + characterClass + ", " + currentHp + "/" + maxHp + " HP, "
                    + itemCount + " items)";
        }
    }

    public final List<Member> members;

    private SavedParty(List<Member> members) { this.members = members; }

    /** Read the party out of a saved game's resource fork. */
    public static SavedParty parse(byte[] resourceFork) throws IOException {
        ResourceFork fork = ResourceFork.parse(resourceFork);
        List<Member> members = new ArrayList<>();
        for (ResourceFork.Resource resource : fork.ofType(CHARACTER_TYPE)) {
            if (resource.data.length < RECORD_SIZE + 2)
                throw new IOException("A saved character is shorter than a character record");
            String name = resource.name.isEmpty() ? nameIn(resource.data) : resource.name;
            if (name.isEmpty()) throw new IOException("A saved character has no name");
            members.add(new Member(name, resource.data));
        }
        if (members.isEmpty()) throw new IOException("This save holds no characters");
        if (members.size() > 8) throw new IOException("This save holds more characters than a party can");
        return new SavedParty(members);
    }

    /**
     * The name inside the record, which the resource name normally repeats.
     * Sixteen bytes, NUL-padded -- the same shape the Amiga port uses, where
     * DOS spends a count byte and fifteen.
     */
    private static String nameIn(byte[] record) {
        StringBuilder text = new StringBuilder(NAME_BYTES);
        for (int i = 0; i < NAME_BYTES; i++) {
            int value = record[NAME + i] & 255;
            if (value == 0) break;
            text.append(value >= 0x20 && value < 0x7f ? (char) value : '?');
        }
        return text.toString().trim();
    }
}
