import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import name.osher.gil.minivmac.LiveMapView;
import name.osher.gil.minivmac.mapper.*;
import java.lang.reflect.*;

/** Synthetic Android View checks; tools/render-check.sh MapZoomRenderCheck. */
public final class MapZoomRenderCheck {
    static LiveMapView view;
    static int taps, returns, tile;
    static Object call(String name) throws Exception {
        Method m=LiveMapView.class.getDeclaredMethod(name);m.setAccessible(true);return m.invoke(view);
    }
    static void event(int action,float x,float y) {
        long time=SystemClock.uptimeMillis();
        MotionEvent e=MotionEvent.obtain(time,time,action,x,y,0);
        try {check(view.onTouchEvent(e),"Input escaped map");} finally {e.recycle();}
    }
    static void tap(float x,float y) {event(0,x,y);event(1,x,y);}
    static void render() {view.draw(new Canvas(Bitmap.createBitmap(900,520,Bitmap.Config.ARGB_8888)));}
    static void control(int n) throws Exception {
        render();
        Field f=LiveMapView.class.getDeclaredField("zoomTargets");f.setAccessible(true);
        RectF r=((RectF[])f.get(view))[n];check(!r.isEmpty(),"Missing zoom control");
        tap(r.centerX(),r.centerY());
    }
    static void state(PoolRadState state,MapMode mode) throws Exception {
        Method m=LiveMapView.class.getDeclaredMethod("showState",PoolRadState.class,MapMode.class);
        m.setAccessible(true);m.invoke(view,state,mode);
    }
    static byte[] battle(int x) {
        byte[] b=new byte[CombatSnapshot.PACKET_SIZE];
        b[0]='P';b[1]='R';b[2]='C';b[3]='3';b[4]=1;b[5]=2;
        b[8]=1;b[9]=(byte)x;b[10]=12;
        b[12]=2;b[13]=28;b[14]=13;return b;
    }
    static void check(boolean ok,String msg) {if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args) throws Exception {
        if(Looper.getMainLooper()==null)Looper.prepareMainLooper();
        if(Typeface.DEFAULT==null)Typeface.class.getMethod("loadPreinstalledSystemFontMap").invoke(null);
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context c=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        view=new LiveMapView(c,null);view.layout(0,0,900,520);
        view.setListener(new LiveMapView.Listener(){
            public void onAreaChanged(AreaIdentity a){}
            public void onTileTapped(AreaIdentity a,int x,int y){taps++;tile=y*16+x;}
            public void onReturnPressed(){returns++;}
        });
        byte[] p=new byte[1200];p[0]='P';p[1]='R';p[2]='M';p[3]='1';p[130]=8;p[131]=8;
        for(int i=176;i<688;i++)p[i]=0x11;
        PoolRadState area=PoolRadState.parse(p);check(area!=null,"Bad fixture");
        state(area,MapMode.EXPLORATION);render();
        MapViewport fit=(MapViewport)call("viewport");
        control(1);control(1);
        MapViewport detail=(MapViewport)call("viewport");
        check(detail.cell>fit.cell*2,"Plus did not zoom");
        event(0,450,220);event(2,350,160);event(1,350,160);
        MapViewport pan=(MapViewport)call("viewport");
        check(pan.scrollY>detail.scrollY,"Zoomed map did not pan");
        check(taps==0 && returns==0,"Zoom or pan dispatched game/note action");
        float x=(pan.clipLeft+pan.clipRight)/2,y=(pan.clipTop+pan.clipBottom)/2;
        int wanted=pan.tileAt(x,y);tap(x,y);
        check(taps==1 && tile==wanted,"Zoomed note coordinates drifted");
        control(0);check(((MapViewport)call("viewport")).cell<detail.cell,"Minus did not zoom out");
        control(2);check(((MapViewport)call("viewport")).cell==fit.cell,"Fit did not restore default");
        view.setOriginalTileScale(true);control(1);control(2);
        check(((MapViewport)call("viewport")).cell==32,"1:1 reset lost original scale");
        System.out.println("PASS exploration controls, panning, note targets, no guest input, original reset");
        state(null,MapMode.COMBAT);view.showCombatSample(battle(25));render();
        CombatViewport action=(CombatViewport)call("battleViewport");
        check(action.zoom>1,"Combat did not open on action");
        control(0);
        CombatViewport chosen=(CombatViewport)call("battleViewport");
        view.showCombatSample(battle(26));render();
        CombatViewport updated=(CombatViewport)call("battleViewport");
        check(chosen.cell==updated.cell && chosen.left==updated.left && chosen.top==updated.top,
                "Combat update reset chosen camera");
        control(2);check(((CombatViewport)call("battleViewport")).zoom==1,"Fit hid full arena");
        tap(100,15);check(((CombatViewport)call("battleViewport")).zoom>1,"Header did not reframe action");
        state(area,MapMode.EXPLORATION);
        check(((MapViewport)call("viewport")).cell==32,"Combat overwrote exploration zoom");
        state(null,MapMode.COMBAT);view.showCombatSample(battle(25));render();
        check(((CombatViewport)call("battleViewport")).zoom>1,"Next fight did not reset to action");
        check(taps==1 && returns==0,"Combat controls dispatched game/note action");
        System.out.println("PASS combat entry, stable updates, whole arena, refocus and next fight");
    }
}
