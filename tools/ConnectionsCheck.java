package name.osher.gil.minivmac.check;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.function.BooleanSupplier;

/** Visible, synthetic area transitions through the production controller/editor.
 * Run only on a disposable emulator with no disks mounted. Not a live-game test.
 */
public final class ConnectionsCheck extends Instrumentation {
    private Activity activity;
    private Object map, controller;
    private Class<?> stateClass;
    private final StringBuilder results = new StringBuilder();
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent launch = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(launch);
            await(() -> {
                try {
                    Object fragment = field(activity, "_currentFragment");
                    map = field(fragment, "mLiveMap");
                    controller = field(fragment, "mNotebook");
                    return map != null && controller != null && field(controller, "notebook") != null;
                } catch (Exception e) { return false; }
            });
            ui(() -> call(field(activity, "_currentFragment"), "stopMapPolling"));
            stateClass = Class.forName("name.osher.gil.minivmac.mapper.PoolRadState");
            Class<?> paneClass=Class.forName("name.osher.gil.minivmac.CompanionPane");
            Object pane=field(field(activity,"_currentFragment"),"mCompanionPane");
            ui(()->method(paneClass,"setTab",String.class).invoke(pane,"connections"));
            travel(0,64,1,0,0,0);page(0,0,4);close();count(0);shot("empty");
            travel(20,79,1,1,0,64);page(20,15,4);close();count(1);shot("one-way");
            Object view=field(controller,"connectionsView");
            check(((android.view.View)view).isShown(),"connections view hidden");
            Object graph=field(view,"graph");
            android.graphics.RectF neighbor=(android.graphics.RectF)((java.util.Map<?,?>)field(graph,"targets")).get(0);
            int[] location=new int[2];ui(()->((View)graph).getLocationOnScreen(location));
            long down=SystemClock.uptimeMillis();
            touch(down,MotionEvent.ACTION_DOWN,location[0]+neighbor.centerX(),location[1]+neighbor.centerY());
            touch(down,MotionEvent.ACTION_UP,location[0]+neighbor.centerX(),location[1]+neighbor.centerY());
            waitForIdleSync();check(integer(view,"selected")==0,"neighbor touch navigation failed");
            ui(()->((View)graph).performAccessibilityAction(0x04000000+20,null));
            check(integer(view,"selected")==20,"accessible neighbor navigation failed");
            ui(()->((View)field(view,"current")).performClick());
            check(integer(view,"selected")==20,"current-area navigation failed");
            // A save reload into an otherwise new area must not draw an edge.
            travel(1,17,2,0,0,0);page(1,1,1);close();count(1);shot("reload-no-link");
            travel(20,79,3,0,0,0);page(20,15,4);close();count(1);
            travel(0,64,3,1,20,79);page(0,0,4);close();count(2);shot("both-directions");
            travel(20,79,3,2,0,64);page(20,15,4);close();count(2);
            // Skipping a native transition cannot invent a shortcut to area 2.
            travel(2,34,3,4,1,17);page(2,2,2);close();count(2);shot("missed-area-no-link");
            results.append("PASS empty graph, one-way discovery, graph navigation, observed return, duplicate suppression, reload rejection, missed-area rejection, durable history\n");
            result.putString("stream", results.toString()); finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", results + "FAIL " + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private byte[] previousPacket;
    private Object previousCatalog;
    private void travel(int id,int tile,int epoch,int serial,int from,int fromTile) throws Exception {
        byte[] p=new byte[1228];p[0]='P';p[1]='R';p[2]='M';p[3]='7';
        p[24]=1;p[25]=1;p[26]=1;p[27]=4;p[31]=1;p[32]=1;p[33]=1;p[35]=(byte)id;
        p[130]=(byte)(tile%16);p[131]=(byte)(tile/16);p[176]=(byte)(id+1);
        p[1215]=(byte)epoch;p[1219]=(byte)serial;p[1220]=1;p[1221]=(byte)from;p[1222]=(byte)fromTile;p[1224]=(byte)id;
        Class<?> catalog=Class.forName("name.osher.gil.minivmac.mapper.AreaIdentity$Catalog");
        Constructor<?> ctor=catalog.getDeclaredConstructor(String[].class,String[].class);ctor.setAccessible(true);
        Object identities=ctor.newInstance((Object)new String[]{id+" "+hash(p,176,1200)},(Object)new String[]{id+" "+hash(p,176,944)});
        if(previousPacket!=null)deliver(previousPacket,previousCatalog,false);
        SystemClock.sleep(80);
        deliver(p,identities,true);previousPacket=p;previousCatalog=identities;
    }
    private String hash(byte[] p,int from,int to) throws Exception {
        StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(java.util.Arrays.copyOfRange(p,from,to)))out.append(String.format("%02x",b&255));return out.toString();
    }
    private void deliver(byte[] p,Object identities,boolean display) throws Exception {
        Class<?> travel=Class.forName("name.osher.gil.minivmac.mapper.AreaTravel");
        Object observation=method(travel,"parse",byte[].class,identities.getClass()).invoke(null,p,identities);
        check(observation!=null,"travel fixture rejected");
        Object state=field(observation,"position");check(state!=null,"position rejected");
        ui(()->{method(controller.getClass(),"onConnectionSample",travel).invoke(controller,observation);
            if(display)method(map.getClass(),"showState",stateClass).invoke(map,state);});
    }
    private void count(int expected) throws Exception {
        await(()->{try{return ((java.util.List<?>)field(field(controller,"connections"),"edges")).size()==expected;}catch(Exception e){return false;}});
        String id=(String)call(field(controller,"notebook"),"id");
        Object loaded=method(field(controller,"store").getClass(),"loadConnections",String.class).invoke(field(controller,"store"),id);
        check(((java.util.List<?>)field(loaded,"edges")).size()==expected,"saved connection count differs");
    }
    private void page(int id,int x,int y) throws Exception {
        long started=SystemClock.elapsedRealtime();
        await(() -> { try {
            Object s=session(); return s!=null && field(s,"sheet") != null
                    && field(s,"dialog") != null && ((android.app.Dialog)field(s,"dialog")).isShowing()
                    && integer(s,"x")==x && integer(s,"y")==y
                    && ("por-mac-v11-geo-"+id).equals(call(field(s,"area"),"id"));
        } catch(Exception e){ return false; } });
        waitForIdleSync();
        results.append("Page ").append(id).append(' ').append(x).append(',').append(y)
                .append(" visible within ").append(SystemClock.elapsedRealtime()-started).append(" ms\n");
    }
    private Object session() throws Exception { return field(controller,"session"); }
    private void close() throws Exception {
        Object s=session(); ui(() -> ((View)field(s,"close")).performClick());
        await(() -> { try { return session()!=s; } catch(Exception e){ return false; } });
    }
    private void shot(String name) throws Exception {
        waitForIdleSync(); SystemClock.sleep(200);
        Bitmap bitmap=getUiAutomation().takeScreenshot();
        File dir=new File(activity.getFilesDir(),"f58-check"); dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); }
        bitmap.recycle();
    }
    private void touch(long down,int action,float x,float y) {
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        check(getUiAutomation().injectInputEvent(event,true),"input injection rejected"); event.recycle();
    }
    private interface Work { void run() throws Exception; }
    private void ui(Work work) throws Exception {
        Throwable[] error={null}; runOnMainSync(() -> { try { work.run(); } catch(Throwable e){error[0]=e;} });
        if(error[0]!=null) throw new Exception(error[0]);
    }
    private static void await(BooleanSupplier ready) {
        long deadline=SystemClock.elapsedRealtime()+15000;
        while(!ready.getAsBoolean()) { if(SystemClock.elapsedRealtime()>deadline) throw new AssertionError("UI condition timed out"); SystemClock.sleep(50); }
    }
    private static int integer(Object owner,String name) { try{return (Integer)field(owner,name);}catch(Exception e){throw new RuntimeException(e);} }
    private static Object field(Object owner,String name) throws Exception {
        Class<?> type=owner.getClass();
        while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException e){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    private static Method method(Class<?> type,String name,Class<?>... args) throws Exception { Method m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m; }
    private static Object call(Object owner,String name) throws Exception {return method(owner.getClass(),name).invoke(owner);}
    private static void check(boolean yes,String message) {if(!yes)throw new AssertionError(message);}
}
