import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

/**
 * Focused JVM checks of actual companion lifecycle method bodies, extracted unchanged.
 * Reuses StoppedCoreInputCheck's compiler/recording-fixture pattern. Android Views,
 * preferences, Handler and Core are stand-ins: no app window, native emulator, ROM,
 * device input, dialog-bound measurement or physical hardware acceptance.
 *
 * Run from the repository root with JDK 17:
 *   java tools/CompanionLifecycleCheck.java
 */
public final class CompanionLifecycleCheck {
    private static final Path SOURCE = Path.of("android/minivmac/src/main/java/name/osher/gil/minivmac");

    public static void main(String[] args) throws Exception {
        String fragment = Files.readString(SOURCE.resolve("EmulatorFragment.java"));
        String activity = Files.readString(SOURCE.resolve("MiniVMac.java"));
        require(fragment.contains("mCompanionPane.setOnTabSelectedListener(this::onCompanionTabSelected);"),
                "The actual companion must use the checked tab callback");
        require(fragment.contains("mCompanionPane.setVisibility(companionInitiallyVisible() ? View.VISIBLE : View.GONE);"),
                "Initial visibility must use the checked migration helper");
        require(fragment.contains("mCompanionPane.setTab(mSelectedCompanionTab);"),
                "The new pane must receive the restored stable tab ID");

        StringBuilder body = new StringBuilder();
        for (String signature : new String[]{
                "private boolean companionMapActive()", "String selectedCompanionTab()",
                "void restoreCompanionTab(String tab)", "private void onCompanionTabSelected(String tab)",
                "private boolean companionInitiallyVisible()", "private void setCompanionVisible(boolean show)",
                "private void startMapPolling()", "private void stopMapPolling()",
                "public void onCreate(@Nullable Bundle savedInstanceState)",
                "public void onSaveInstanceState(@NonNull Bundle outState)",
                "public void onPause ()", "public void onResume()"})
            body.append(block(fragment, signature)).append('\n');
        body.append(block(fragment, "private final Runnable mMapPoll = new Runnable()")).append(';');
        String listeners = block(fragment, "mCore.setMapSampleListener(sample ->") + ");\n"
                + block(fragment, "mCore.setPartySampleListener(sample ->") + ");\n";
        String activityRestore = statement(block(activity, "protected void onCreate(Bundle savedInstanceState)"),
                "if (savedInstanceState != null)");
        String source = "import java.util.*; import java.util.function.Consumer;\n"
                + "public final class CompanionLifecycleProbe {\n" + FIXTURES
                + "static final class EmulatorFragment extends BaseFragment {\n" + FRAGMENT_FIELDS
                + statement(fragment, "static final String STATE_COMPANION_TAB")
                + statement(fragment, "private static final String PREF_SHOW_COMPANION")
                + statement(fragment, "private String mSelectedCompanionTab")
                + body + "\nvoid bindSamples() { final Core mapCore = mCore;\n" + listeners + "}\n}\n"
                + "static final class MiniVMac extends BaseActivity {\n" + ACTIVITY_FIELDS
                + statement(activity, "private String mRestoredCompanionTab")
                + "void restoreState(Bundle savedInstanceState) {\n" + activityRestore + "}\n"
                + block(activity, "protected void onSaveInstanceState(@NonNull Bundle outState)")
                + block(activity, "public void showEmulator()") + "\n}\n" + CHECKS + "\n}";
        Path temporary = Files.createTempDirectory("poolrad-companion-lifecycle-");
        Path java = temporary.resolve("CompanionLifecycleProbe.java");
        Files.writeString(java, source);
        require(ToolProvider.getSystemJavaCompiler() != null, "A full JDK is required");
        require(ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", temporary.toString(), java.toString()) == 0, "Actual lifecycle fixture did not compile");
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()})) {
            try { loader.loadClass("CompanionLifecycleProbe").getMethod("run").invoke(null); }
            catch (InvocationTargetException failure) {
                throw new AssertionError("Actual companion lifecycle behavior failed", failure.getCause());
            }
        }
    }

    private static String block(String source, String signature) {
        int start = unique(source, signature), opening = source.indexOf('{', start), depth = 0;
        require(opening >= 0, "Missing body for " + signature);
        for (int at = opening; at < source.length(); at++) {
            char c = source.charAt(at);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(start, at + 1);
        }
        throw new AssertionError("Unterminated actual body: " + signature);
    }

    private static String statement(String source, String signature) {
        int start = unique(source, signature), end = source.indexOf(';', start);
        require(end >= 0, "Missing actual statement: " + signature);
        return source.substring(start, end + 1) + "\n";
    }

    private static int unique(String source, String signature) {
        int start = source.indexOf(signature);
        require(start >= 0 && source.indexOf(signature, start + 1) < 0,
                "Expected exactly one actual source element: " + signature);
        return start;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final String FIXTURES = """
        @interface NonNull {} @interface Nullable {}
        static class Bundle {
            final Map<String,String> data = new HashMap<>();
            void putString(String key, String value) { data.put(key, value); }
            String getString(String key) { return data.get(key); }
            String getString(String key, String fallback) { return data.getOrDefault(key, fallback); }
        }
        static class View {
            static final int VISIBLE = 0, GONE = 8;
            int visibility = VISIBLE;
            int getVisibility() { return visibility; }
            void setVisibility(int value) { visibility = value; }
        }
        static final class CompanionPane extends View {
            static final String MAP = "map", INFO = "info";
            String tab = MAP;
            Consumer<String> listener;
            boolean isMapSelected() { return MAP.equals(tab); }
            void setTab(String value) {
                String next = INFO.equals(value) ? INFO : MAP;
                if (next.equals(tab)) return;
                tab = next;
                if (listener != null) listener.accept(next);
            }
        }
        static final class LiveMapView extends View {
            int mapClears, partyClears, mapUpdates, partyUpdates;
            void showSample(byte[] value) { if (value == null) mapClears++; else mapUpdates++; }
            void showPartySample(byte[] value) { if (value == null) partyClears++; else partyUpdates++; }
        }
        static final class ScreenView { int focus; void requestFocus() { focus++; } }
        static final class SharedPreferences {
            final Map<String,Boolean> values = new HashMap<>(); int writes;
            boolean contains(String key) { return values.containsKey(key); }
            boolean getBoolean(String key, boolean fallback) { return values.getOrDefault(key, fallback); }
            Editor edit() { return new Editor(); }
            final class Editor {
                final Map<String,Boolean> changes = new HashMap<>();
                Editor putBoolean(String key, boolean value) { changes.put(key,value); return this; }
                void apply() { values.putAll(changes); writes++; }
            }
        }
        static final class Context { final SharedPreferences prefs = new SharedPreferences(); }
        static final class PreferenceManager {
            static SharedPreferences getDefaultSharedPreferences(Context context) { return context.prefs; }
        }
        static final class Handler {
            final List<Runnable> pending = new ArrayList<>(); long lastDelay;
            void post(Runnable job) { pending.add(job); }
            void postDelayed(Runnable job, long delay) { lastDelay=delay; pending.add(job); }
            void removeCallbacks(Runnable job) { pending.removeIf(queued -> queued == job); }
            void runQueued() {
                List<Runnable> current = new ArrayList<>(pending); pending.clear();
                for (Runnable job : current) job.run();
            }
        }
        static final class Core {
            boolean ready = true; int mapRequests, partyRequests, pauses, resumes, off;
            Consumer<byte[]> mapListener, partyListener;
            boolean isReady() { return ready; }
            void requestMapSample() { mapRequests++; }
            void requestPartySample() { partyRequests++; }
            void setMapSampleListener(Consumer<byte[]> listener) { mapListener=listener; }
            void setPartySampleListener(Consumer<byte[]> listener) { partyListener=listener; }
            void pauseEmulation() { pauses++; }
            void resumeEmulation() { resumes++; }
            boolean hasDisksInserted() { return true; }
            void requestMacOff() { off++; }
        }
        static class BaseFragment {
            boolean resumed = true;
            void onCreate(Bundle state) { }
            void onSaveInstanceState(Bundle state) { }
            void onPause() { resumed = false; }
            void onResume() { resumed = true; }
            boolean isResumed() { return resumed; }
        }
        static class BaseActivity {
            int invalidations;
            final Manager manager = new Manager();
            void invalidateOptionsMenu() { invalidations++; }
            protected void onSaveInstanceState(Bundle state) { }
            Manager getSupportFragmentManager() { return manager; }
        }
        static final class Manager {
            Object placed;
            Manager beginTransaction() { return this; }
            Manager replace(int id,Object fragment) { placed=fragment; return this; }
            void commitAllowingStateLoss() { }
        }
        static final class Controller { void onSaveInstanceState(Bundle state) { } }
        static final class android { static final class R { static final class id { static final int content=1; } } }
        """;

    private static final String FRAGMENT_FIELDS = """
        private LiveMapView mLiveMap = new LiveMapView();
        private CompanionPane mCompanionPane = new CompanionPane();
        private ScreenView mScreenView = new ScreenView();
        private Handler mUIHandler = new Handler();
        private Core mCore = new Core();
        private boolean mMapPolling;
        private volatile int mMapGeneration;
        private boolean onActivity;
        final Context context = new Context();
        final BaseActivity activity = new BaseActivity();
        int wheelStarts, wheelStops, restarts, cancelledCodes;
        Context requireContext() { return context; }
        BaseActivity requireActivity() { return activity; }
        void startWheelPolling() { wheelStarts++; }
        void stopWheelPolling() { wheelStops++; }
        void initEmulator() { restarts++; }
        void cancelCodeEntry() { cancelledCodes++; }
        void mountView() {
            mCompanionPane.setTab(mSelectedCompanionTab);
            mCompanionPane.listener = this::onCompanionTabSelected;
        }
        """;

    private static final String ACTIVITY_FIELDS = """
        Object _currentFragment;
        final Controller screenshotController = new Controller(), notebookTransfers = new Controller();
        """;

    private static final String CHECKS = """
        private static int passed;
        private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
        private static void run(String name,Runnable test) { test.run(); passed++; System.out.println("PASS " + name); }
        private static EmulatorFragment fixture() { EmulatorFragment f=new EmulatorFragment(); f.mountView(); return f; }
        public static void run() {
            run("legacy hidden preference migrates once; newer explicit preference wins", () -> {
                EmulatorFragment f=fixture(); SharedPreferences prefs=f.context.prefs;
                prefs.values.put("poolrad_show_map", false);
                check(!f.companionInitiallyVisible(), "Upgrade lost hidden-map choice");
                check(prefs.writes==1 && !prefs.getBoolean("poolrad_show_companion",true), "Migration was not persisted");
                prefs.values.put("poolrad_show_map",true);
                check(!f.companionInitiallyVisible() && prefs.writes==1, "Legacy value overrode migrated preference");
                prefs.values.put("poolrad_show_companion",true);
                check(f.companionInitiallyVisible() && prefs.writes==1, "Explicit new value did not win");
                check(fixture().companionInitiallyVisible(), "Fresh installation should show companion");
            });
            run("stable tab IDs restore before view creation and unknown IDs fall back to Map", () -> {
                EmulatorFragment f=fixture(); f.mCompanionPane=null;
                f.restoreCompanionTab("info"); check("info".equals(f.selectedCompanionTab()), "Viewless Info restore lost");
                f.mCompanionPane=new CompanionPane(); f.mountView();
                check(!f.mCompanionPane.isMapSelected(), "New view did not receive saved Info tab");
                f.restoreCompanionTab("future-tab"); check("map".equals(f.selectedCompanionTab()), "Unknown tab did not fall back");
                f.restoreCompanionTab(null); check("map".equals(f.selectedCompanionTab()), "Null tab did not fall back");
            });
            run("fragment and activity Bundle carry Info through replacement without persisting a fresh default", () -> {
                EmulatorFragment original=fixture(); original.restoreCompanionTab("info");
                Bundle state=new Bundle(); original.onSaveInstanceState(state);
                EmulatorFragment restored=fixture(); restored.onCreate(state);
                check("info".equals(restored.selectedCompanionTab()), "Fragment Bundle lost Info");
                MiniVMac first=new MiniVMac(); first._currentFragment=original;
                Bundle activityState=new Bundle(); first.onSaveInstanceState(activityState);
                MiniVMac recreated=new MiniVMac(); recreated.restoreState(activityState); recreated.showEmulator();
                check("info".equals(((EmulatorFragment)recreated._currentFragment).selectedCompanionTab()), "Activity replacement lost Info");
                check(recreated.manager.placed==recreated._currentFragment, "Restored fragment was not installed");
                recreated._currentFragment=null; Bundle waiting=new Bundle(); recreated.onSaveInstanceState(waiting);
                check("info".equals(waiting.getString(EmulatorFragment.STATE_COMPANION_TAB)), "Pending startup lost restored ID");
                MiniVMac fresh=new MiniVMac(); fresh.restoreState(null); fresh.showEmulator();
                check("map".equals(((EmulatorFragment)fresh._currentFragment).selectedCompanionTab()), "Fresh session did not default Map");
            });
            run("polling requires resumed, shown companion and Map, not nested map visibility alone", () -> {
                EmulatorFragment f=fixture(); check(f.companionMapActive(), "Visible resumed Map should be active");
                f.resumed=false; check(!f.companionMapActive(), "Paused fragment polled"); f.resumed=true;
                f.mCompanionPane.setVisibility(View.GONE);
                check(f.mLiveMap.getVisibility()==View.VISIBLE && !f.companionMapActive(), "Hidden ancestor still polled");
                f.mCompanionPane.setVisibility(View.VISIBLE); f.mCompanionPane.setTab("info");
                check(!f.companionMapActive(), "Info tab still polled");
                f.mCompanionPane.setTab("map"); f.mLiveMap=null; check(!f.companionMapActive(), "Destroyed map polled");
                f.mLiveMap=new LiveMapView(); f.mCompanionPane=null; check(!f.companionMapActive(), "Destroyed pane polled");
            });
            run("start replaces pending poll, clears both stale samples and advances generation", () -> {
                EmulatorFragment f=fixture(); int before=f.mMapGeneration;
                f.startMapPolling(); f.startMapPolling();
                check(f.mMapPolling && f.mUIHandler.pending.size()==1, "Restart duplicated or lost immediate poll");
                check(f.mMapGeneration==before+2 && f.mLiveMap.mapClears==2 && f.mLiveMap.partyClears==2,
                        "Restart did not invalidate stale arrow and HP");
                f.stopMapPolling(); check(!f.mMapPolling && f.mUIHandler.pending.isEmpty(), "Stop left scheduled poll");
                f.mLiveMap=null; f.mCompanionPane=null; f.mUIHandler=null; f.stopMapPolling();
            });
            run("actual poll requests map and party once, reschedules at 250 ms and clears unavailable Core", () -> {
                EmulatorFragment f=fixture(); f.startMapPolling(); f.mUIHandler.runQueued();
                check(f.mCore.mapRequests==1 && f.mCore.partyRequests==1, "Poll did not request both samples once");
                check(f.mUIHandler.lastDelay==250 && f.mUIHandler.pending.size()==1, "Poll cadence or queue changed");
                f.mCore.ready=false; int map=f.mLiveMap.mapClears, hp=f.mLiveMap.partyClears;
                f.mUIHandler.runQueued();
                check(f.mCore.mapRequests==1 && f.mLiveMap.mapClears==map+1 && f.mLiveMap.partyClears==hp+1,
                        "Unready Core retained stale information or was queried");
                f.mCore=null; f.mUIHandler.runQueued();
                check(f.mLiveMap.mapClears==map+2 && f.mLiveMap.partyClears==hp+2, "Missing Core was not cleared");
            });
            run("tab and visibility changes retain selection, focus guest and do not pause or restart it", () -> {
                EmulatorFragment f=fixture(); f.startMapPolling(); Core core=f.mCore; LiveMapView map=f.mLiveMap;
                f.mCompanionPane.setTab("info");
                check(!f.mMapPolling && f.mUIHandler.pending.isEmpty(), "Info left map polling active");
                f.setCompanionVisible(false); f.setCompanionVisible(true);
                check("info".equals(f.selectedCompanionTab()) && !f.mMapPolling, "Show/hide changed Info or restarted Map polling");
                f.mCompanionPane.setTab("map");
                check(f.mMapPolling && f.mUIHandler.pending.size()==1 && f.mScreenView.focus==2, "Map return lacked one fresh poll/guest focus");
                check(f.mLiveMap==map && f.mCore==core, "Tab change replaced map or native Core");
                check(f.wheelStarts==0 && f.wheelStops==0 && f.restarts==0 && core.pauses==0 && core.resumes==0,
                        "Tab/visibility changed wheel or emulator lifecycle");
                check(f.context.prefs.writes==2 && f.context.prefs.getBoolean("poolrad_show_companion",false), "Visibility not saved to new key");
                check(!f.context.prefs.contains("poolrad_show_map"), "New toggle still wrote obsolete preference");
            });
            run("actual callbacks reject old generations and old Core but accept fresh returned samples", () -> {
                EmulatorFragment f=fixture(); f.startMapPolling(); f.bindSamples(); f.mUIHandler.pending.clear();
                Core old=f.mCore; old.mapListener.accept(new byte[]{1}); old.partyListener.accept(new byte[]{1});
                f.mCompanionPane.setTab("info"); f.mCompanionPane.setTab("map"); f.mUIHandler.runQueued();
                check(f.mLiveMap.mapUpdates==0 && f.mLiveMap.partyUpdates==0, "Stale generation revived arrow or HP");
                f.mUIHandler.pending.clear(); old.mapListener.accept(new byte[]{1}); old.partyListener.accept(new byte[]{1});
                f.mCore=new Core(); f.bindSamples(); f.mUIHandler.runQueued();
                check(f.mLiveMap.mapUpdates==0 && f.mLiveMap.partyUpdates==0, "Old Core updated replacement session");
                f.mCore.mapListener.accept(new byte[]{1}); f.mCore.partyListener.accept(new byte[]{1}); f.mUIHandler.runQueued();
                check(f.mLiveMap.mapUpdates==1 && f.mLiveMap.partyUpdates==1, "Fresh returned samples were not displayed");
            });
            run("callbacks independently refuse hidden or paused pane even before explicit generation invalidation", () -> {
                EmulatorFragment f=fixture(); f.startMapPolling(); f.bindSamples(); f.mUIHandler.pending.clear();
                f.mCore.mapListener.accept(new byte[]{1}); f.mCore.partyListener.accept(new byte[]{1});
                f.mCompanionPane.visibility=View.GONE; f.mUIHandler.runQueued();
                check(f.mLiveMap.mapUpdates==0 && f.mLiveMap.partyUpdates==0, "Hidden pane accepted queued samples");
                f.mCompanionPane.visibility=View.VISIBLE; f.resumed=false;
                f.mCore.mapListener.accept(new byte[]{1}); f.mCore.partyListener.accept(new byte[]{1}); f.mUIHandler.runQueued();
                check(f.mLiveMap.mapUpdates==0 && f.mLiveMap.partyUpdates==0, "Paused pane accepted queued samples");
            });
            run("actual pause/resume stops and resumes Map; Info stays quiet while wheel resumes independently", () -> {
                EmulatorFragment f=fixture(); f.startMapPolling(); f.onPause();
                check(!f.mMapPolling && f.mUIHandler.pending.isEmpty() && f.mCore.pauses==1, "Pause left polling or failed normal guest pause");
                f.onResume(); check(f.mMapPolling && f.mCore.resumes==1, "Visible Map failed to resume normally");
                f.mCompanionPane.setTab("info"); f.onPause(); f.onResume();
                check(!f.mMapPolling && f.mUIHandler.pending.isEmpty(), "Info resumed map polling");
                check(f.wheelStarts==2 && f.wheelStops==2 && f.restarts==0, "Wheel incorrectly followed selected tab or guest restarted");
            });
            System.out.println("PASS " + passed + " actual companion lifecycle checks; recording fixtures only, no Android/device acceptance");
        }
        """;
}
