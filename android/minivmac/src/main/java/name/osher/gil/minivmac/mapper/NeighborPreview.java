package name.osher.gil.minivmac.mapper;

import java.util.*;
import name.osher.gil.minivmac.notebook.*;

/** A known outgoing passage at the party's exact current square, never an inferred reverse. */
public final class NeighborPreview {
    public final AreaConnections.Edge passage;
    public final ExploredMap remembered;
    public final ExplorationTrail trail;
    public final GeoMap map;
    public final String status;
    public NeighborPreview(AreaConnections.Edge edge,ExploredMap remembered,ExplorationTrail trail,String status) {
        passage=edge;this.remembered=remembered;this.trail=trail;this.status=status;
        map=remembered.geometry(edge.toArea,trail);
    }
    public boolean visible(int tile) { return remembered.known(tile) && trail.visited(tile); }
    public int count() { int n=0;for(int tile=0;tile<256;tile++)if(visible(tile))n++;return n; }
    public String label() { return AreaIdentity.labelForId("por-mac-v11-geo-"+passage.toArea); }
    public static List<AreaConnections.Edge> exits(AreaConnections history,PoolRadState position) {
        if(position==null || position.area==null || !position.explorationSafe) return Collections.emptyList();
        List<AreaConnections.Edge> found=new ArrayList<>();
        for(AreaConnections.Edge edge:history.edges)
            if(edge.fromArea==position.map.id && edge.fromTile==position.y*16+position.x) found.add(edge);
        return Collections.unmodifiableList(found);
    }
}
