import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import name.osher.gil.minivmac.*;
import name.osher.gil.minivmac.mapper.*;
import java.lang.reflect.*;

/** Real Android dispatch: map corners, zoom separation, Off, cancellation, overlay isolation. */
public final class EnterPlacementRenderCheck {
    static int returns, notes, guestTouches;
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static Object field(Object view, String name) throws Exception {
        Field f = view.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(view);
    }
    static void event(View view, int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
    }
    static void tap(View view, float x, float y) { event(view,0,x,y); event(view,1,x,y); }
    static void render(View view) {
        view.draw(new Canvas(Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888)));
    }
    // Detached Views queue performClick until attached. Deliver those real
    // callbacks here, as the window's Handler does in the live app.
    static void posted(View view) throws Exception {
        Method get = View.class.getDeclaredMethod("getRunQueue"); get.setAccessible(true);
        Object queue = get.invoke(view); Class<?> type = queue.getClass();
        Method size = type.getMethod("size"), runnable = type.getMethod("getRunnable",int.class);
        Method remove = type.getMethod("removeCallbacks",Runnable.class);
        while ((int)size.invoke(queue)>0) {
            Runnable next = (Runnable)runnable.invoke(queue,0);
            remove.invoke(queue,next); next.run();
        }
    }
    public static void main(String[] args) throws Exception {
        if (Looper.getMainLooper()==null) Looper.prepareMainLooper();
        if (Typeface.DEFAULT==null) Typeface.class.getMethod("loadPreinstalledSystemFontMap").invoke(null);
        Class<?> at = Class.forName("android.app.ActivityThread");
        Context c = (Context)at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        LiveMapView map = new LiveMapView(c,null); map.layout(0,0,900,520);
        map.setListener(new LiveMapView.Listener() {
            public void onAreaChanged(AreaIdentity a) { }
            public void onTileTapped(AreaIdentity a,int x,int y) { notes++; }
            public void onReturnPressed() { returns++; }
        });
        byte[] p = new byte[1200]; p[0]='P';p[1]='R';p[2]='M';p[3]='1';p[130]=8;p[131]=8;
        Method show = LiveMapView.class.getDeclaredMethod("showState",PoolRadState.class,MapMode.class);
        show.setAccessible(true); show.invoke(map,PoolRadState.parse(p),MapMode.EXPLORATION);
        render(map);
        RectF original = new RectF((RectF)field(map,"returnTarget"));
        check(original.centerX()>450,"Default Enter is not at map right");
        tap(map,original.centerX(),original.centerY()); check(returns==1,"Default Enter failed");
        map.setEnterPlacement(EnterPlacement.MAP_LEFT); render(map);
        RectF left = new RectF((RectF)field(map,"returnTarget"));
        check(left.centerX()<100,"Left Enter is not at map left");
        for (RectF zoom : (RectF[])field(map,"zoomTargets"))
            check(!RectF.intersects(left,zoom),"Enter overlaps zoom touch target");
        tap(map,left.centerX(),left.centerY()); check(returns==2 && notes==0,"Left Enter sent a note tap");
        event(map,0,left.centerX(),left.centerY());event(map,3,left.centerX(),left.centerY());
        check(returns==2,"Cancelled Enter dispatched a key");
        for (EnterPlacement placement : new EnterPlacement[]{EnterPlacement.OFF,EnterPlacement.SCREEN_LEFT,EnterPlacement.SCREEN_RIGHT}) {
            map.setEnterPlacement(placement); render(map);
            check(((RectF)field(map,"returnTarget")).isEmpty(),"Hidden map Enter kept a target");
        }
        System.out.println("PASS map default/left Enter, separate zoom targets, cancellation and hidden targets");
        FrameLayout host = new FrameLayout(c);
        View guest = new View(c); guest.setOnTouchListener((v,e)->{guestTouches++;return true;});
        host.addView(guest,new FrameLayout.LayoutParams(-1,-1));
        EnterOverlayButton button = new EnterOverlayButton(c,null);
        button.setOnClickListener(v->returns++);
        host.addView(button,new FrameLayout.LayoutParams(60,60,android.view.Gravity.BOTTOM|android.view.Gravity.RIGHT));
        host.measure(View.MeasureSpec.makeMeasureSpec(500,1073741824),View.MeasureSpec.makeMeasureSpec(400,1073741824));
        host.layout(0,0,500,400); render(host);
        check(!button.isFocusable(),"Overlay steals keyboard focus");
        tap(host,470,370); posted(button);
        check(guestTouches==0,"Overlay tap leaked to guest mouse");
        check(returns==3,"Overlay did not dispatch Enter: " + returns);
        event(host,0,470,370);event(host,3,470,370);posted(button);
        check(returns==3,"Cancelled overlay sent Enter");
        tap(host,100,100);check(guestTouches==2,"Overlay intercepted outside taps");
        button.setVisibility(View.GONE);tap(host,470,370);
        check(returns==3 && guestTouches==4,"Off still intercepted guest taps");
        System.out.println("PASS overlay Enter dispatch, no mouse leak, cancellation, outside taps and Off");
    }
}
