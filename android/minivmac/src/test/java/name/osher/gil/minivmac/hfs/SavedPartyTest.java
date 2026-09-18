package name.osher.gil.minivmac.hfs;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.*;

/**
 * Reading who is in a saved game. The resource fork here is built from the
 * Macintosh resource-fork specification, not copied from anybody's save: no
 * real character, name or item appears.
 */
public class SavedPartyTest {
    /** A character record with the fields this reads set to known values. */
    private static byte[] record(String name, int characterClass, int maxHp, int currentHp,
                                 int movement, int items, int[] abilities) {
        byte[] record = new byte[SavedParty.RECORD_SIZE + 4 + items * 66];
        for (int i = 0; i < name.length() && i < SavedParty.NAME_BYTES; i++)
            record[SavedParty.NAME + i] = (byte) name.charAt(i);
        for (int i = 0; i < abilities.length; i++) record[SavedParty.ABILITIES + i] = (byte) abilities[i];
        record[SavedParty.CLASS] = (byte) characterClass;
        record[SavedParty.MAX_HP] = (byte) maxHp;
        record[SavedParty.CURRENT_HP] = (byte) currentHp;
        record[SavedParty.MOVEMENT] = (byte) movement;
        record[SavedParty.ITEM_COUNT] = (byte) items;
        return record;
    }

    /** A resource fork holding the given resources, named, of one type. */
    static byte[] fork(String type, String[] names, byte[][] bodies) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] offsets = new int[bodies.length];
        for (int i = 0; i < bodies.length; i++) {
            offsets[i] = data.size();
            put32(data, bodies[i].length);
            data.write(bodies[i]);
        }
        ByteArrayOutputStream names8 = new ByteArrayOutputStream();
        int[] nameOffsets = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            nameOffsets[i] = names8.size();
            names8.write(names[i].length());
            names8.write(names[i].getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        }

        int typeListLength = 2 + 8;                       // one type
        int referenceLength = bodies.length * 12;
        ByteArrayOutputStream map = new ByteArrayOutputStream();
        map.write(new byte[16 + 4 + 2 + 2]);              // copy of the header, handle, attributes
        put16(map, 28);                                   // type list offset, from the map
        put16(map, 28 + typeListLength + referenceLength);// name list offset
        put16(map, 0);                                    // one type, minus one
        for (int i = 0; i < 4; i++) map.write(type.charAt(i));
        put16(map, bodies.length - 1);
        put16(map, typeListLength);                       // references follow the type list
        for (int i = 0; i < bodies.length; i++) {
            put16(map, 128 + i);                          // resource id
            put16(map, nameOffsets[i]);
            map.write(0);                                 // attributes
            map.write((offsets[i] >>> 16) & 255);
            map.write((offsets[i] >>> 8) & 255);
            map.write(offsets[i] & 255);
            put32(map, 0);                                // handle
        }
        map.write(names8.toByteArray());

        byte[] body = data.toByteArray(), mapBytes = map.toByteArray();
        ByteArrayOutputStream fork = new ByteArrayOutputStream();
        put32(fork, 256); put32(fork, 256 + body.length);
        put32(fork, body.length); put32(fork, mapBytes.length);
        fork.write(new byte[256 - 16]);
        fork.write(body); fork.write(mapBytes);
        return fork.toByteArray();
    }

    private static void put16(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 255); out.write(value & 255);
    }
    private static void put32(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 255); out.write((value >>> 16) & 255);
        out.write((value >>> 8) & 255); out.write(value & 255);
    }

    private static byte[] party() throws IOException {
        return fork(SavedParty.CHARACTER_TYPE,
                new String[]{"Ironhand", "Greylock"},
                new byte[][]{
                        record("Ironhand", 2, 12, 7, 9, 6, new int[]{18, 15, 14, 17, 16, 16}),
                        record("Greylock", 13, 8, 8, 9, 5, new int[]{9, 17, 12, 14, 11, 10})});
    }

    @Test public void everyCharacterInTheSaveIsFoundInOrder() throws IOException {
        SavedParty saved = SavedParty.parse(party());
        assertEquals(2, saved.members.size());
        assertEquals("Ironhand", saved.members.get(0).name);
        assertEquals("Greylock", saved.members.get(1).name);
    }

    @Test public void theRecordInASaveIsTheRecordInMemory() throws IOException {
        SavedParty.Member first = SavedParty.parse(party()).members.get(0);
        assertEquals("class where the running game keeps it", 2, first.characterClass);
        assertEquals(12, first.maxHp);
        assertEquals("current hit points at 0x12b, as in memory", 7, first.currentHp);
        assertEquals(9, first.movement);
        assertEquals(6, first.itemCount);
        assertArrayEquals(new int[]{18, 15, 14, 17, 16, 16}, first.abilities);
    }

    @Test public void theNameInTheRecordStandsInWhenTheResourceIsUnnamed() throws IOException {
        byte[] fork = fork(SavedParty.CHARACTER_TYPE, new String[]{""},
                new byte[][]{record("Ironhand", 2, 12, 12, 9, 0, new int[]{12, 12, 12, 12, 12, 12})});
        assertEquals("Ironhand", SavedParty.parse(fork).members.get(0).name);
    }

    @Test public void resourcesOfAnotherTypeAreNotCharacters() throws IOException {
        byte[] fork = fork("ChrL", new String[]{"CharacterList"}, new byte[][]{new byte[4]});
        try { SavedParty.parse(fork); fail("a character list was read as a party"); }
        catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("no characters"));
        }
    }

    @Test public void aCharacterShorterThanARecordIsRefused() throws IOException {
        byte[] fork = fork(SavedParty.CHARACTER_TYPE, new String[]{"Stub"},
                new byte[][]{new byte[SavedParty.RECORD_SIZE - 1]});
        try { SavedParty.parse(fork); fail("a truncated character was accepted"); }
        catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("shorter than"));
        }
    }

    @Test public void somethingThatIsNotAResourceForkIsRefused() {
        try { ResourceFork.parse(null); fail("null accepted"); } catch (IOException expected) { }
        try { ResourceFork.parse(new byte[8]); fail("a stub accepted"); } catch (IOException expected) { }
        try { ResourceFork.parse(new byte[512]); fail("zeroes accepted"); } catch (IOException expected) { }
    }

    @Test public void aResourceClaimingMoreBytesThanItHasIsRefused() throws IOException {
        byte[] fork = party();
        // The first resource's own length word, made absurd.
        fork[256] = 0x7f; fork[257] = (byte) 0xff;
        try { ResourceFork.parse(fork); fail("an impossible resource length was accepted"); }
        catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("bytes it does not have"));
        }
    }

    @Test public void theForkListsWhatItHoldsByType() throws IOException {
        ResourceFork fork = ResourceFork.parse(party());
        assertEquals(2, fork.all().size());
        assertEquals(2, fork.ofType(SavedParty.CHARACTER_TYPE).size());
        assertEquals(0, fork.ofType("ChrL").size());
        assertEquals("Ironhand", fork.ofType(SavedParty.CHARACTER_TYPE).get(0).name);
    }
}
