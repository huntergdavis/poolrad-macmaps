import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import name.osher.gil.minivmac.hfs.Blocks;
import name.osher.gil.minivmac.hfs.HfsFile;
import name.osher.gil.minivmac.hfs.HfsVolume;

/**
 * Run the real HFS reader against a real guest disk image and print what it
 * finds. Not a unit test: it needs somebody's actual disk, so it is a harness
 * the owner or the agent runs by hand, and nothing it reads is checked in.
 *
 *   java -cp <app classes> HfsReadCheck /path/to/disk2.dsk "Pool Of Radiance:PoolRadSave"
 */
public final class HfsReadCheck {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) { System.err.println("usage: HfsReadCheck <image> [folder path]"); System.exit(2); }
        String path = args.length > 1 ? args[1] : "Pool Of Radiance:PoolRadSave";
        try (RandomAccessFile file = new RandomAccessFile(new File(args[0]), "r")) {
            Blocks blocks = new Blocks.ReadOnly(new Blocks() {
                @Override public long size() throws IOException { return file.length(); }
                @Override public void read(long offset, byte[] into, int at, int length) throws IOException {
                    file.seek(offset); file.readFully(into, at, length);
                }
                @Override public void write(long offset, byte[] from, int at, int length) throws IOException {
                    throw new IOException("read only");
                }
                @Override public void close() { }
            });
            HfsVolume volume = HfsVolume.open(blocks);
            System.out.println("volume " + volume.volumeName()
                    + " (allocation block " + volume.allocationBlockSize() + " bytes)");
            int folder = volume.folderAt(path);
            if (folder < 0) { System.err.println("No such folder: " + path); System.exit(1); }
            List<HfsFile> files = volume.files(folder);
            System.out.println(path + " -> id " + folder + ", " + files.size() + " files");
            int checked = 0;
            for (HfsFile entry : files) {
                byte[] data = volume.readDataFork(entry);
                byte[] resource = volume.readResourceFork(entry);
                System.out.printf("  %-16s %s/%s  data %6d  rsrc %6d  extents %s | %s%n",
                        entry.name, entry.type, entry.creator, data.length, resource.length,
                        java.util.Arrays.toString(entry.dataExtents()),
                        java.util.Arrays.toString(entry.resourceExtents()));
                if (data.length != entry.dataLength || resource.length != entry.resourceLength)
                    throw new IOException("Fork length disagreed with the catalog for " + entry.name);
                checked++;
            }
            System.out.println(checked + " files read whole, lengths agreeing with the catalog");
        }
    }
}
