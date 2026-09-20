package name.osher.gil.minivmac.notebook;

import name.osher.gil.minivmac.mapper.AreaTravel;
import name.osher.gil.minivmac.mapper.PoolRadState;

/** UI-thread observation chain. Persistence receives only immutable edges. */
public final class ConnectionRecorder {
    private long epoch, serial, lastTime;
    private int area=-1;
    public void interrupt() { area=-1; epoch=0; }
    public AreaConnections.Edge observe(AreaTravel travel,long now) {
        if(travel==null) { interrupt(); return null; }
        if(now<=lastTime || now-lastTime>1250 || epoch!=travel.epoch) interrupt();
        lastTime=now;
        epoch=travel.epoch;
        PoolRadState position=travel.position;
        if(position==null) return null;
        int next=position.map.id;
        AreaConnections.Edge edge=null;
        if(area>=0 && area!=next && travel.serial==serial+1 && travel.fromArea==area
                && travel.toArea==next)
            edge=new AreaConnections.Edge(area,travel.fromTile,next,position.y*16+position.x);
        area=next; serial=travel.serial;
        return edge;
    }
}
