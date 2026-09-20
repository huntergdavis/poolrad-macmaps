import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Method;
import java.util.Collections;
import name.osher.gil.minivmac.LiveMapView;
import name.osher.gil.minivmac.mapper.AreaIdentity;
import name.osher.gil.minivmac.mapper.MapViewport;
import name.osher.gil.minivmac.mapper.MapMode;
import name.osher.gil.minivmac.mapper.PoolRadState;
import name.osher.gil.minivmac.notebook.NoteIcon;

/** Detached actual Android View/Canvas checks. Synthetic geometry; never touches the guest. */
public final class OriginalScaleRenderCheck {
    static LiveMapView view;
    static int taps, lastTile = -1;
    public static void main(String[] args) {
        try { checks(); } catch(Throwable failure) { failure.printStackTrace(System.err);System.exit(1); }
    }
    static void checks() throws Exception {
        if (Looper.getMainLooper()==null) Looper.prepareMainLooper();
        if (Typeface.DEFAULT==null) Typeface.class.getMethod("loadPreinstalledSystemFontMap").invoke(null);
        Class<?> at=Class.forName("android.app.ActivityThread");
        Context context=(Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        view=new LiveMapView(context,null);
        new FrameLayout(context).addView(view);
        view.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY));
        view.layout(0,0,400,300);
        state(8,8);
        view.setListener(new LiveMapView.Listener() {
            public void onAreaChanged(AreaIdentity area) {}
            public void onTileTapped(AreaIdentity area,int x,int y) { taps++;lastTile=y*16+x; }
        });
        view.showNotebook("Synthetic notebook",Collections.singletonMap(136,NoteIcon.FLAG));
        Bitmap fit=render();
        view.setOriginalTileScale(true);
        check(grid().cell==32,"Tile pitch is not 32 physical pixels");
        check(!fit.sameAs(render()),"Toggle did not change visible scale");
        view.setOriginalTileScale(false);
        check(fit.sameAs(render()),"Default fitted view did not return pixel-for-pixel");
        System.out.println("PASS opt-in scale and exact fitted-view restoration");
        view.setOriginalTileScale(true);
        MapViewport before=grid();
        Bitmap initial=render();
        event(MotionEvent.ACTION_DOWN,180,160);
        event(MotionEvent.ACTION_MOVE,80,100);
        event(MotionEvent.ACTION_MOVE,180,160); // returning to the start must not turn a drag into a tap
        event(MotionEvent.ACTION_UP,180,160);
        check(taps==0,"Drag became a note tap");
        event(MotionEvent.ACTION_DOWN,180,160);
        event(MotionEvent.ACTION_MOVE,80,100);
        event(MotionEvent.ACTION_UP,80,100);
        MapViewport after=grid();
        check(after.scrollX>before.scrollX && after.scrollY>before.scrollY,"Map did not scroll in both axes");
        Bitmap scrolled=render();
        check(!initial.sameAs(scrolled),"Scrolling did not change map pixels");
        for(int y=0;y<scrolled.getHeight();y++)for(int x=0;x<scrolled.getWidth();x++)
            if(y<24*context.getResources().getDisplayMetrics().density || y>=after.clipBottom)
                check(initial.getPixel(x,y)==scrolled.getPixel(x,y),"Scrolling changed fixed header or caption at "+x+","+y);
        System.out.println("PASS two-axis scrolling, fixed chrome and drag cancellation");
        float x=after.left+8.5f*32,y=after.top+8.5f*32;
        event(MotionEvent.ACTION_DOWN,x,y);event(MotionEvent.ACTION_UP,x,y);
        check(taps==1 && lastTile==136,"Scrolled flag opened the wrong note");
        int prior=taps;
        event(MotionEvent.ACTION_DOWN,after.clipRight+1,100);event(MotionEvent.ACTION_UP,after.clipRight+1,100);
        check(taps==prior,"Clipped map accepted an off-map note tap");
        System.out.println("PASS scrolled flag hit-testing and clipped hit bounds");
        state(8,8);
        check(grid().scrollX==after.scrollX && grid().scrollY==after.scrollY,"Unchanged update reset a manual pan");
        state(0,0);
        check(grid().tileAt(grid().left+16,grid().top+16)==0,"Movement left party off screen");
        state(15,15);
        check(grid().tileAt(grid().left+15.5f*32,grid().top+15.5f*32)==255,"Far corner movement left party off screen");
        System.out.println("PASS unchanged updates preserve pan and movement reveals party");
        event(MotionEvent.ACTION_DOWN,180,160);event(MotionEvent.ACTION_MOVE,500,500);
        event(MotionEvent.ACTION_CANCEL,500,500);
        event(MotionEvent.ACTION_UP,500,500);
        check(taps==prior,"Cancelled pan opened a note");
        render();
        event(MotionEvent.ACTION_DOWN,100,15);event(MotionEvent.ACTION_UP,100,15);
        check(grid().tileAt(grid().left+15.5f*32,grid().top+15.5f*32)==255,"Header did not find party after panning away");
        System.out.println("PASS cancellation and header find-party");
        Method mode=LiveMapView.class.getDeclaredMethod("showState",PoolRadState.class,MapMode.class);
        mode.setAccessible(true);mode.invoke(view,null,MapMode.CAMP);
        float oldScroll=grid().scrollY;
        event(MotionEvent.ACTION_DOWN,180,160);event(MotionEvent.ACTION_MOVE,180,260);
        event(MotionEvent.ACTION_UP,180,260);
        check(grid().scrollY<oldScroll,"Retained area map cannot be scrolled while camping");
        event(MotionEvent.ACTION_DOWN,180,160);event(MotionEvent.ACTION_UP,180,160);
        check(taps==prior,"Reference-only map opened a note");
        System.out.println("PASS retained reference map scrolls without enabling note taps");
        System.out.println("PASS 6 original-scale Android View checks");
    }
    static void state(int x,int y) throws Exception {
        byte[] p=new byte[1200];p[0]='P';p[1]='R';p[2]='M';p[3]='1';p[130]=(byte)x;p[131]=(byte)y;
        for(int i=176;i<688;i++)p[i]=0x11;
        Method m=LiveMapView.class.getDeclaredMethod("showState",PoolRadState.class);
        m.setAccessible(true);m.invoke(view,PoolRadState.parse(p));
    }
    static MapViewport grid() throws Exception {
        Method m=LiveMapView.class.getDeclaredMethod("viewport");m.setAccessible(true);
        return (MapViewport)m.invoke(view);
    }
    static Bitmap render() {
        Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(b));return b;
    }
    static void event(int action,float x,float y) {
        long now=SystemClock.uptimeMillis();
        MotionEvent e=MotionEvent.obtain(now,now,action,x,y,0);
        try {check(view.onTouchEvent(e),"Map input escaped to guest");} finally {e.recycle();}
    }
    static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
