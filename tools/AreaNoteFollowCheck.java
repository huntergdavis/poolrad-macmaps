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
public final class AreaNoteFollowCheck extends Instrumentation {
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
            area(0, 1, 2); page(0, 1, 2); shot("arrival");
            android.app.Dialog automatic = (android.app.Dialog) field(session(), "dialog");
            int passThrough = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            check((automatic.getWindow().getAttributes().flags & passThrough) == passThrough,
                    "automatic page blocks game keyboard or outside touches");
            close();
            area(0, 2, 2); SystemClock.sleep(400); check(session() == null, "ordinary movement reopened page");
            ui(() -> {
                Object sample = call(map, "snapshot");
                Object identity = field(sample, "area");
                method(controller.getClass(), "onTileTapped", identity.getClass(), int.class, int.class)
                        .invoke(controller, identity, 7, 8);
            });
            page(0, 7, 8); close();
            area(20, 3, 4); page(20, 3, 4); shot("new-area");
            area(0, 9, 10); page(0, 7, 8); shot("remembered");
            Object pinned = session();
            View sheet = (View) field(pinned, "sheet");
            Rect bounds = new Rect(); ui(() -> sheet.getGlobalVisibleRect(bounds));
            float x = bounds.left + bounds.width() * .8f, y = bounds.top + bounds.height() * .5f;
            long down = SystemClock.uptimeMillis();
            touch(down, MotionEvent.ACTION_DOWN, x, y);
            touch(down, MotionEvent.ACTION_MOVE, x + 35, y + 20);
            check((Boolean) call(sheet, "isDrawing"), "stroke did not start");
            area(20, 4, 4); area(1, 5, 6); SystemClock.sleep(400);
            check(session() == pinned, "area switch interrupted active stroke");
            shot("active-stroke");
            touch(down, MotionEvent.ACTION_UP, x + 70, y + 30);
            await(() -> integer(pinned, "revision") > 0);
            area(20, 4, 4); area(1, 6, 6); SystemClock.sleep(400);
            check(session() == pinned, "area switch replaced edited page");
            shot("protected-ink");
            close(); page(1, 6, 6); shot("deferred-latest");
            area(0, 0, 0); page(0, 7, 8);
            Object ink = call(field(session(), "sheet"), "getNote");
            check(!((java.util.List<?>) call(ink, "strokes")).isEmpty(), "ink was not saved to its original page");
            shot("ink-preserved");
            Object original = session();
            touch(down = SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y + 40);
            touch(down, MotionEvent.ACTION_UP, x + 25, y + 55);
            await(() -> integer(original, "revision") > 0);
            area(20, 2, 4); area(0, 0, 0); waitForIdleSync();
            check(session() == original, "return to open area replaced its editor");
            close(); SystemClock.sleep(400);
            check(session() == null, "return to open area reopened a page just closed");
            area(20, 2, 4); page(20, 3, 4);
            Object beforeBack = session();
            long keyTime = SystemClock.uptimeMillis();
            check(getUiAutomation().injectInputEvent(new android.view.KeyEvent(keyTime, keyTime,
                    android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK, 0), true), "Back down rejected");
            check(getUiAutomation().injectInputEvent(new android.view.KeyEvent(keyTime, SystemClock.uptimeMillis(),
                    android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK, 0), true), "Back up rejected");
            await(() -> { try { return session() != beforeBack; } catch(Exception e) { return false; } });
            results.append("PASS arrival fallback, remembered page, no same-area reopen, read-only follow, active-stroke protection, edited-page protection, latest deferred area, original ink persistence, return to open area, game-input window flags, physical Back\n");
            result.putString("stream", results.toString()); finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", results + "FAIL " + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void area(int id, int x, int y) throws Exception {
        byte[] packet = new byte[1200]; packet[0]='P'; packet[1]='R'; packet[2]='M'; packet[3]='1';
        packet[130]=(byte)x; packet[131]=(byte)y; packet[176]=(byte)(id+1);
        byte[] geometry=java.util.Arrays.copyOfRange(packet,176,1200);
        StringBuilder digest=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(geometry)) digest.append(String.format("%02x", b & 255));
        Class<?> catalog=Class.forName("name.osher.gil.minivmac.mapper.AreaIdentity$Catalog");
        Constructor<?> ctor=catalog.getDeclaredConstructor(String[].class); ctor.setAccessible(true);
        Object identities=ctor.newInstance((Object)new String[]{id+" "+digest});
        Object state=method(stateClass,"parse",byte[].class,catalog).invoke(null,packet,identities);
        check(state != null && field(state,"area") != null,"synthetic identity rejected");
        ui(() -> method(map.getClass(),"showState",stateClass).invoke(map,state));
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
        File dir=new File(activity.getFilesDir(),"f57-check"); dir.mkdirs();
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
