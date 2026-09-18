import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import name.osher.gil.minivmac.hfs.Blocks;
import name.osher.gil.minivmac.hfs.HfsFile;
import name.osher.gil.minivmac.hfs.HfsVolume;
import name.osher.gil.minivmac.hfs.SaveArchive;

/**
 * Prove the backup and restore path against a real guest disk image — on a
 * COPY, which this program insists on: it refuses to open anything but a file
 * whose name says it is a scratch copy.
 *
 * Unit tests cover the same code against a synthetic volume. This covers what
 * a synthetic volume cannot: a 64 MB image, 1 KB allocation blocks, forks split
 * across two extents, and a catalog large enough to span several nodes.
 *
 *   java -cp <app classes> HfsRoundTripCheck /tmp/....copy.dsk
 */
public final class HfsRoundTripCheck {
    private static int passed;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) { System.err.println("usage: HfsRoundTripCheck <image copy>"); System.exit(2); }
        File image = new File(args[0]);
        if (!image.getName().contains("copy"))
            throw new IOException("Refusing to write to " + image + "; name a copy, with \"copy\" in it");

        byte[] before = read(image);
        List<Saved> saves = saves(image);
        check(!saves.isEmpty(), "No saved games found to exercise");
        System.out.println("found " + saves.size() + " saved games");

        for (Saved save : saves) {
            if (save.archive.data.length == 0) continue;

            byte[] scrambled = save.archive.data.clone();
            for (int i = 0; i < scrambled.length; i++) scrambled[i] ^= 0x5a;

            write(image, save.name, scrambled, save.archive.resource);
            check(!Arrays.equals(save.archive.data, currentData(image, save.name)),
                    save.name + " did not actually change");
            pass(save.name + ": a different fork was written and read back");

            write(image, save.name, save.archive.data, save.archive.resource);
            check(Arrays.equals(save.archive.data, currentData(image, save.name)),
                    save.name + " did not come back byte for byte");
            pass(save.name + ": restored byte for byte");
        }

        byte[] after = read(image);
        int differing = 0;
        for (int i = 0; i < before.length; i++) if (before[i] != after[i]) differing++;
        check(differing == 0, differing + " bytes of the image differ after a full round trip");
        pass("the whole 64 MB image is byte-identical after backing up and restoring everything");

        // And the archive format survives a real saved game.
        for (Saved save : saves) {
            SaveArchive round = SaveArchive.unpack(save.archive.pack());
            check(Arrays.equals(round.data, save.archive.data)
                    && Arrays.equals(round.resource, save.archive.resource)
                    && round.name.equals(save.archive.name), save.name + " did not survive packing");
        }
        pass("every saved game packs and unpacks unchanged");
        System.out.println(passed + " round-trip checks passed");
    }

    private static final class Saved {
        final String name; final SaveArchive archive;
        Saved(String name, SaveArchive archive) { this.name = name; this.archive = archive; }
    }

    private static List<Saved> saves(File image) throws IOException {
        java.util.List<Saved> out = new java.util.ArrayList<>();
        try (Blocks blocks = open(image, false)) {
            HfsVolume volume = HfsVolume.open(blocks);
            for (HfsFile file : volume.files(volume.folderAt("Pool Of Radiance:PoolRadSave")))
                out.add(new Saved(file.name, new SaveArchive(file.name, file.type, file.creator,
                        volume.readDataFork(file), volume.readResourceFork(file))));
        }
        return out;
    }

    private static byte[] currentData(File image, String name) throws IOException {
        try (Blocks blocks = open(image, false)) {
            HfsVolume volume = HfsVolume.open(blocks);
            for (HfsFile file : volume.files(volume.folderAt("Pool Of Radiance:PoolRadSave")))
                if (file.name.equals(name)) return volume.readDataFork(file);
        }
        throw new IOException("Lost " + name);
    }

    private static void write(File image, String name, byte[] data, byte[] resource) throws IOException {
        try (Blocks blocks = open(image, true)) {
            HfsVolume volume = HfsVolume.open(blocks);
            for (HfsFile file : volume.files(volume.folderAt("Pool Of Radiance:PoolRadSave")))
                if (file.name.equals(name)) {
                    check(volume.fits(file, data, resource), name + " does not fit its own forks");
                    volume.writeDataForkInPlace(file, data);
                    volume.writeResourceForkInPlace(file, resource);
                    return;
                }
        }
        throw new IOException("No such saved game: " + name);
    }

    private static Blocks open(File image, boolean writable) throws IOException {
        final java.io.RandomAccessFile file = new java.io.RandomAccessFile(image, writable ? "rw" : "r");
        Blocks blocks = new Blocks() {
            @Override public long size() throws IOException { return file.length(); }
            @Override public void read(long offset, byte[] into, int at, int length) throws IOException {
                file.seek(offset); file.readFully(into, at, length);
            }
            @Override public void write(long offset, byte[] from, int at, int length) throws IOException {
                file.seek(offset); file.write(from, at, length);
            }
            @Override public void close() throws IOException { file.close(); }
        };
        return writable ? blocks : new Blocks.ReadOnly(blocks);
    }

    private static byte[] read(File image) throws IOException {
        return java.nio.file.Files.readAllBytes(image.toPath());
    }

    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void pass(String what) { passed++; System.out.println("PASS " + what); }
}
