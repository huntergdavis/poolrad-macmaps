import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import name.osher.gil.minivmac.hfs.Blocks;
import name.osher.gil.minivmac.hfs.HfsFile;
import name.osher.gil.minivmac.hfs.HfsVolume;
import name.osher.gil.minivmac.hfs.SavedParty;

/**
 * Read the parties out of the saved games on a real disk image, with the same
 * code the app uses. Unit tests cover this against a resource fork built from
 * the specification; this covers it against saves the game itself wrote.
 *
 *   java -cp <app classes> SaveReadCheck /path/to/disk.dsk
 */
public final class SaveReadCheck {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) { System.err.println("usage: SaveReadCheck <image>"); System.exit(2); }
        final RandomAccessFile file = new RandomAccessFile(new File(args[0]), "r");
        Blocks blocks = new Blocks.ReadOnly(new Blocks() {
            @Override public long size() throws IOException { return file.length(); }
            @Override public void read(long offset, byte[] into, int at, int length) throws IOException {
                file.seek(offset); file.readFully(into, at, length);
            }
            @Override public void write(long offset, byte[] from, int at, int length) throws IOException {
                throw new IOException("read only");
            }
            @Override public void close() throws IOException { file.close(); }
        });
        try (Blocks open = blocks) {
            HfsVolume volume = HfsVolume.open(open);
            int folder = volume.folderAt("Pool Of Radiance:PoolRadSave");
            if (folder < 0) { System.err.println("No save folder on this disk"); System.exit(1); }
            int read = 0;
            for (HfsFile save : volume.files(folder)) {
                byte[] resource = volume.readResourceFork(save);
                System.out.println(save.name + " (" + save.dataLength + " + " + resource.length + " bytes)");
                try {
                    SavedParty party = SavedParty.parse(resource);
                    for (SavedParty.Member member : party.members)
                        System.out.printf("    %-18s class %2d  %2d/%-2d HP  move %d  %d items  abilities %s%n",
                                member.name, member.characterClass, member.currentHp, member.maxHp,
                                member.movement, member.itemCount, java.util.Arrays.toString(member.abilities));
                    read++;
                } catch (IOException notAParty) {
                    System.out.println("    not a saved party: " + notAParty.getMessage());
                }
            }
            System.out.println(read + " saved parties read");
        }
    }
}
