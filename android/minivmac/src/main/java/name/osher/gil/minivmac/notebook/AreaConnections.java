package name.osher.gil.minivmac.notebook;

import java.io.*;
import java.util.*;

/** Immutable, directed passages actually observed in one notebook. */
public final class AreaConnections {
    public static final int MAX_EDGES = 8192, MAX_BYTES = 4 + MAX_EDGES * 4 + 64;
    public static final class Edge {
        public final int fromArea, fromTile, toArea, toTile;
        public Edge(int fromArea, int fromTile, int toArea, int toTile) {
            if (fromArea < 0 || fromArea > 32 || toArea < 0 || toArea > 32 || fromArea == toArea
                    || fromTile < 0 || fromTile > 255 || toTile < 0 || toTile > 255)
                throw new IllegalArgumentException("Invalid area connection");
            this.fromArea=fromArea; this.fromTile=fromTile; this.toArea=toArea; this.toTile=toTile;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Edge)) return false;
            Edge e=(Edge)other;
            return fromArea==e.fromArea && fromTile==e.fromTile && toArea==e.toArea && toTile==e.toTile;
        }
        @Override public int hashCode() { return ((fromArea*256+fromTile)*33+toArea)*256+toTile; }
    }
    public final List<Edge> edges;
    public AreaConnections() { this(Collections.emptyList()); }
    private AreaConnections(List<Edge> edges) { this.edges=Collections.unmodifiableList(edges); }
    public AreaConnections add(Edge edge) throws IOException {
        if (edges.contains(edge)) return this;
        if (edges.size() == MAX_EDGES) throw new IOException("Connection history is full");
        List<Edge> next=new ArrayList<>(edges); next.add(edge); return new AreaConnections(next);
    }
    public void write(DataOutputStream out) throws IOException {
        out.writeInt(edges.size());
        for (Edge edge:edges) {
            out.writeByte(edge.fromArea); out.writeByte(edge.fromTile);
            out.writeByte(edge.toArea); out.writeByte(edge.toTile);
        }
    }
    public static AreaConnections read(DataInputStream in) throws IOException {
        int size=in.readInt();
        if(size<0 || size>MAX_EDGES) throw new IOException("Invalid connection count");
        List<Edge> edges=new ArrayList<>(); Set<Edge> unique=new HashSet<>();
        for(int i=0;i<size;i++) {
            try {
                Edge edge=new Edge(in.readUnsignedByte(),in.readUnsignedByte(),in.readUnsignedByte(),in.readUnsignedByte());
                if(!unique.add(edge)) throw new IOException("Duplicate connection");
                edges.add(edge);
            } catch(IllegalArgumentException invalid) { throw new IOException("Invalid connection",invalid); }
        }
        if(in.read()!=-1) throw new IOException("Trailing connection data");
        return new AreaConnections(edges);
    }
}
