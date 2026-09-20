package name.osher.gil.minivmac.mapper;

import java.io.*;
import java.util.Arrays;
import name.osher.gil.minivmac.notebook.ExplorationTrail;

/** Only explored cells' neutral wall/door symbols. No original GEO payload or events. */
public final class ExploredMap {
    private final byte[] known, edges;
    public ExploredMap() { this(new byte[32], new byte[256]); }
    private ExploredMap(byte[] known, byte[] edges) { this.known=known; this.edges=edges; }
    public boolean known(int tile) { return tile>=0 && tile<256 && (known[tile/8] & (1<<(tile%8)))!=0; }
    public ExploredMap observe(GeoMap map, ExplorationTrail trail) {
        byte[] mask=new byte[32], cells=new byte[256];
        for(int tile=0;tile<256;tile++) if(trail.visited(tile)) {
            mask[tile/8]|=1<<(tile%8);
            for(int d=0;d<4;d++) cells[tile]|=map.edgeKind(tile%16,tile/16,d).ordinal()<<(d*2);
        }
        return Arrays.equals(mask,known) && Arrays.equals(cells,edges) ? this : new ExploredMap(mask,cells);
    }
    /** Coverage is checked again at read time, so Reset walked map also hides old previews. */
    public GeoMap geometry(int area, ExplorationTrail trail) {
        byte[] geo=new byte[1026];
        for(int tile=0;tile<256;tile++) if(known(tile) && trail.visited(tile)) {
            for(int d=0;d<4;d++) {
                int kind=(edges[tile]&255) >>> (d*2) & 3;
                if(kind!=0) geo[2+tile+(d>=2?256:0)]|=1<<(d%2==0?4:0);
                if(kind==2) geo[770+tile]|=1<<(d*2);
            }
        }
        return new GeoMap(area,geo);
    }
    public void write(DataOutputStream out) throws IOException { out.write(known); out.write(edges); }
    public static ExploredMap read(DataInputStream in) throws IOException {
        byte[] mask=new byte[32],cells=new byte[256];in.readFully(mask);in.readFully(cells);
        if(in.read()!=-1) throw new IOException("Trailing explored-map data");
        ExploredMap result=new ExploredMap(mask,cells);
        for(int tile=0;tile<256;tile++) {
            if(!result.known(tile) && cells[tile]!=0) throw new IOException("Unexplored map cell contains geometry");
            for(int d=0;d<4;d++) if(((cells[tile]&255) >>> (d*2) & 3)==3) throw new IOException("Invalid map edge");
        }
        return result;
    }
}
