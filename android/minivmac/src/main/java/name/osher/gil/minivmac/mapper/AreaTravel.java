package name.osher.gil.minivmac.mapper;

/** PRM7 travel metadata, independent of the narrower footprint continuity. */
public final class AreaTravel {
    public final long epoch, serial;
    public final int fromArea, fromTile, toArea;
    public final PoolRadState position;
    private AreaTravel(byte[] p, PoolRadState position) {
        epoch=u32(p,1212); serial=u32(p,1216);
        fromArea=p[1220]==1 ? p[1221]&255 : -1;
        fromTile=p[1222]&255; toArea=p[1224]&255; this.position=position;
    }
    public static AreaTravel parse(byte[] packet) { return parse(packet, null); }
    static AreaTravel parse(byte[] p, AreaIdentity.Catalog catalog) {
        if(p==null || p.length!=1228 || p[0]!='P' || p[1]!='R' || p[2]!='M' || p[3]!='7'
                || u32(p,1212)==0 || p[1220]<0 || p[1220]>1 || (p[1221]&255)>32
                || (p[1223]&255)>3 || (p[1224]&255)>32 || p[1225]!=0 || p[1226]!=0 || p[1227]!=0) return null;
        MapObservation observation=MapObservation.parse(p,catalog);
        if(observation.mode!=MapMode.EXPLORATION && observation.mode!=MapMode.UPDATING) return null;
        PoolRadState state=observation.state;
        if(state!=null && state.map.id!=(p[1224]&255)) return null;
        return new AreaTravel(p, state!=null && state.explorationSafe ? state : null);
    }
    private static long u32(byte[] p,int at) {
        return ((p[at]&255L)<<24)|((p[at+1]&255L)<<16)|((p[at+2]&255L)<<8)|(p[at+3]&255L);
    }
}
