package name.osher.gil.minivmac;

import static android.os.Looper.getMainLooper;

import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.ByteBuffer;
import java.util.List;
import name.osher.gil.minivmac.mapper.AutomaticWheel;
import name.osher.gil.minivmac.mapper.WheelPrompt;
import name.osher.gil.minivmac.desktop.DiskAccessGate;

public class EmulatorFragment extends Fragment
        implements IOnIOEventListener, CodeWheelDialog.Host {
    private static final String TAG = "minivmac.EmulatorFrag";
    static final String STATE_COMPANION_TAB = "poolrad_companion_tab";
    private static final String PREF_SHOW_COMPANION = "poolrad_show_companion";

    private final static int[] keycodeTranslationTable = {
            -1, -1, -1, -1, -1, -1, -1, 0x1D, 0x12, 0x13,
            0x14, 0x15, 0x17, 0x16, 0x1A, 0x1C, 0x19, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x00,
            0x0B, 0x08, 0x02, 0x0E, 0x03, 0x05, 0x04, 0x22, 0x26, 0x28,
            0x25, 0x2E, 0x2D, 0x1F, 0x23, 0x0C, 0x0F, 0x01, 0x11, 0x20,
            0x09, 0x0D, 0x07, 0x10, 0x06, 0x2B, 0x2F, 0x37, 0x37, 0x38,
            0x38, 0x30, 0x31, 0x3A, -1, -1, 0x24, 0x33, 0x32, 0x1B,
            0x18, 0x21, 0x1E, 0x2A, 0x29, 0x27, 0x2C, 0x37, 0x3A, -1,
            -1, 0x45, -1, -1, 0x3A, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, 0x37};
    private final static int TRACKBALL_SENSITIVITY = 8;
    private final static int KEYCODE_MAC_SHIFT = 56;
    private final static int KEYCODE_NUMPAD = -20;

    private String mRomFileName;
    private long mRomChecksum;
    private ScreenView mScreenView;
    private TrackPadView mTrackPadView;
    private ImageButton mFullScreenButton;
    private View mRestartLayout;
    private String mLang;
    private boolean mIsTrackpad;
    private Boolean onActivity = false;
    private Boolean mEmulatorStarted = false;

    private KeyboardView mKeyboardView;
    private Keyboard mQwertyKeyboard;
    private Keyboard mSymbolsKeyboard;
    private Keyboard mSymbolsShiftedKeyboard;
    private Keyboard mNumpadKeyboard;
    private ClipboardManager mClipboardManager;
    private MenuProvider mMenuProvider;

    private volatile Core mCore;
    private Handler mUIHandler;
    private final AtomicBoolean mSnapshotBusy = new AtomicBoolean();
    private File mSnapshotDirectory;
    private Runnable mCodeEntry;
    private Core mCodeEntryCore;
    private int mCodeEntryKey = -1;
    private final AutomaticWheel mAutomaticWheel = new AutomaticWheel();
    private boolean mWheelPolling;
    private volatile int mWheelGeneration;
    private int mAutomaticKey = -1;
    private Core mAutomaticKeyCore;
    private final Runnable mReleaseAutomaticKey = () -> releaseAutomaticKey();
    private final Runnable mWheelPoll = new Runnable() {
        @Override public void run() {
            if (!mWheelPolling) return;
            Core target = mCore;
            if (target != null && target.isReady()) target.requestWheelSample();
            else mAutomaticWheel.reset();
            mUIHandler.postDelayed(this, 250);
        }
    };

    private void startWheelPolling() {
        stopWheelPolling();
        if (isResumed() && mScreenView != null) { mWheelPolling = true; mUIHandler.post(mWheelPoll); }
    }
    private void stopWheelPolling() {
        mWheelPolling = false; mWheelGeneration++;
        if (mUIHandler != null) mUIHandler.removeCallbacks(mWheelPoll);
        cancelAutomaticWheel();
    }
    private void cancelAutomaticWheel() {
        mAutomaticWheel.suspend();
        if (mUIHandler != null) mUIHandler.removeCallbacks(mReleaseAutomaticKey);
        releaseAutomaticKey();
    }
    private void releaseAutomaticKey() {
        Core target = mAutomaticKeyCore;
        if (mAutomaticKey >= 0 && target != null && target == mCore && target.isReady())
            target.keyUp(mAutomaticKey);
        mAutomaticKey = -1; mAutomaticKeyCore = null;
    }
    private void receiveWheelSample(Core target, byte[] sample) {
        if (target != mCore || !target.isReady()) return;
        if (!isResumed() || mCodeEntry != null || mScreenView == null || !mScreenView.hasWindowFocus()
                || getChildFragmentManager().findFragmentByTag("code-wheel") != null) {
            cancelAutomaticWheel(); return;
        }
        WheelPrompt prompt = WheelPrompt.parse(sample);
        int key = mAutomaticWheel.observeIfReleased(prompt, SystemClock.uptimeMillis(), mAutomaticKey >= 0);
        if (key == 0) return;
        mAutomaticKey = translateKeyCode(key == '\n' ? KeyEvent.KEYCODE_ENTER : KeyEvent.KEYCODE_A + key - 'A');
        mAutomaticKeyCore = target; target.keyDown(mAutomaticKey);
        mUIHandler.postDelayed(mReleaseAutomaticKey, 100);
        if (BuildConfig.DEBUG) Log.d(TAG, "Automatic wheel prompt " + prompt.index
                + " attempt " + prompt.attempt + " dispatched key at prefix length "
                + prompt.typed.length() + (key == '\n' ? " (Return)" : ""));
        if (key == '\n') Log.i(TAG, "Verified code-wheel answer entered; waiting for the original game.");
    }
    private LiveMapView mLiveMap;
    private CompanionPane mCompanionPane;
    private String mSelectedCompanionTab = CompanionPane.MAP;
    private ViewTreeObserver.OnPreDrawListener mPendingCompanionTool;
    private NotebookController mNotebook;
    private MapStackLayout mMapStack;
    private boolean mMapPolling;
    private volatile int mMapGeneration;
    private final Runnable mMapPoll = new Runnable() {
        @Override public void run() {
            if (!mMapPolling || !companionMapActive()) return;
            Core target = mCore;
            if (target != null && target.isReady()) {
                target.requestMapSample(); target.requestPartySample(); target.requestMessageSample();
            }
            else { mLiveMap.showSample(null); mLiveMap.showPartySample(null); }
            mUIHandler.postDelayed(this, 250);
        }
    };

    private void startMapPolling() {
        stopMapPolling();
        if (companionMapActive()) {
            mMapPolling = true;
            mUIHandler.post(mMapPoll);
        }
    }

    private void stopMapPolling() {
        mMapPolling = false;
        mMapGeneration++;
        if (mUIHandler != null) mUIHandler.removeCallbacks(mMapPoll);
        if (mLiveMap != null) {
            mLiveMap.showSample(null);
            mLiveMap.showPartySample(null);
        }
    }

    private boolean companionMapActive() {
        // User-owned exploration keeps recording while Info is selected or the
        // companion is hidden. Pausing/destroying the activity still stops it.
        return isResumed() && mLiveMap != null && mCompanionPane != null;
    }

    String selectedCompanionTab() { return mSelectedCompanionTab; }

    private void onCompanionTabSelected(String tab) {
        mSelectedCompanionTab = tab;
        mScreenView.requestFocus();
    }

    void restoreCompanionTab(String tab) {
        mSelectedCompanionTab = CompanionPane.INFO.equals(tab) ? CompanionPane.INFO : CompanionPane.MAP;
        if (mCompanionPane != null) mCompanionPane.setTab(mSelectedCompanionTab);
    }

    private boolean companionInitiallyVisible() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        if (!prefs.contains(PREF_SHOW_COMPANION)) {
            // Upgrade once, keeping an existing user's hidden-map choice.
            prefs.edit().putBoolean(PREF_SHOW_COMPANION, prefs.getBoolean("poolrad_show_map", true)).apply();
        }
        return prefs.getBoolean(PREF_SHOW_COMPANION, true);
    }

    private void setCompanionVisible(boolean show) {
        mCompanionPane.setVisibility(show ? View.VISIBLE : View.GONE);
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putBoolean(PREF_SHOW_COMPANION, show).apply();
        requireActivity().invalidateOptionsMenu();
    }

    private void cancelPendingCompanionTool() {
        if (mPendingCompanionTool != null && mCompanionPane != null
                && mCompanionPane.getViewTreeObserver().isAlive())
            mCompanionPane.getViewTreeObserver().removeOnPreDrawListener(mPendingCompanionTool);
        mPendingCompanionTool = null;
    }

    private void openCompanionTool(Runnable open) {
        cancelPendingCompanionTool();
        if (mCompanionPane.getVisibility() == View.VISIBLE && mCompanionPane.isLaidOut()
                && !mCompanionPane.isLayoutRequested()) {
            open.run();
            return;
        }
        // Menu tools may be invoked with the companion hidden. Reveal it, then wait
        // for its real bounds so no dialog is ever sized over the guest display.
        setCompanionVisible(true);
        mPendingCompanionTool = () -> {
            cancelPendingCompanionTool();
            if (isAdded() && isResumed() && mCompanionPane != null
                    && mCompanionPane.getVisibility() == View.VISIBLE
                    && mCompanionPane.getWidth() > 0 && mCompanionPane.getHeight() > 0) open.run();
            return true;
        };
        mCompanionPane.getViewTreeObserver().addOnPreDrawListener(mPendingCompanionTool);
    }

    private void showCompanionTool(CompanionPane.Tool tool) {
        openCompanionTool(() -> {
            switch (tool) {
                case EXPLORATION: mNotebook.showExploration(); break;
                case LEVELS: LevelsReferenceDialog.show(requireActivity()); break;
                case SPELLS: SpellReferenceDialog.show(requireActivity()); break;
                case JOURNAL: ((MiniVMac) requireActivity()).journal().show(); break;
                case EQUIPMENT: EquipmentReferenceDialog.show(requireActivity()); break;
                case MONEY: MoneyReferenceDialog.show(requireActivity()); break;
                case WHEEL:
                    if (getChildFragmentManager().findFragmentByTag("code-wheel") == null)
                        new CodeWheelDialog().show(getChildFragmentManager(), "code-wheel");
                    break;
            }
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) restoreCompanionTab(savedInstanceState.getString(STATE_COMPANION_TAB));
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_COMPANION_TAB, selectedCompanionTab());
        super.onSaveInstanceState(outState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.screen, container, false);

        onActivity = false;
        mScreenView = root.findViewById(R.id.screen);
        mTrackPadView = root.findViewById(R.id.trackpad);
        mRestartLayout = root.findViewById(R.id.restart_layout);
        Button restartButton = root.findViewById(R.id.restart_button);
        restartButton.setOnClickListener(v -> initEmulator());
        mKeyboardView = root.findViewById(R.id.keyboard);
        mUIHandler = new Handler(getMainLooper());
        mMapStack = (MapStackLayout) root;
        mCompanionPane = root.findViewById(R.id.companion_pane);
        mLiveMap = root.findViewById(R.id.live_map);
        mCompanionPane.setVisibility(companionInitiallyVisible() ? View.VISIBLE : View.GONE);
        mCompanionPane.setTab(mSelectedCompanionTab);
        mCompanionPane.setOnTabSelectedListener(this::onCompanionTabSelected);
        mCompanionPane.setOnToolSelectedListener(this::showCompanionTool);
        mNotebook = new NotebookController(requireActivity(), mLiveMap);
        mSnapshotDirectory = new File(requireContext().getFilesDir(), "snapshots");

        mClipboardManager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);

        mFullScreenButton = root.findViewById(R.id.toggle_ui_button);
        mFullScreenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MiniVMac)requireActivity()).toggleSystemUI();
                if (((MiniVMac)requireActivity()).isUIVisible()) {
                    mFullScreenButton.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_fullscreen, null));
                } else {
                    mFullScreenButton.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_fullscreen_exit, null));
                }
            }
        });

        mScreenView.requestFocus();

        mMenuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.minivmac_actions, menu);
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                menu.findItem(R.id.action_live_map).setChecked(mCompanionPane.getVisibility() == View.VISIBLE);
                // Populate disk group
                SubMenu dm = menu.findItem(R.id.action_insert_disk).getSubMenu();
                if (dm != null) {
                    MenuCompat.setGroupDividerEnabled(dm, true);
                    dm.removeGroup(R.id.disks_group);
                    // add disks
                    File[] disks = FileManager.getInstance().getAvailableDisks();
                    for (int i = 0; disks != null && i < disks.length; i++) {
                        String diskName = disks[i].getName();
                        MenuItem m = dm.add(R.id.disks_group, diskName.hashCode(), i + 2, diskName.substring(0, diskName.lastIndexOf(".")));
                        m.setEnabled(mCore != null && !mCore.isDiskInserted(disks[i]));
                        m.setIcon(R.drawable.ic_disk_floppy);
                    }
                }

                // Populate speed group
                if (getActivity() != null) {
                    SubMenu speedMenu = menu.findItem(R.id.action_speed).getSubMenu();
                    if (speedMenu != null) {
                        speedMenu.removeGroup(R.id.speed_group);

                        String[] speedEntries = getResources().getStringArray(R.array.speed_entries);
                        String[] speedValues = getResources().getStringArray(R.array.speed_values);

                        for (int i = 0; i < speedEntries.length; i++) {
                            MenuItem item = speedMenu.add(R.id.speed_group, Integer.parseInt(speedValues[i]), i, speedEntries[i]);
                            item.setChecked(Core.getSpeed() == Integer.parseInt(speedValues[i]));
                        }

                        speedMenu.setGroupCheckable(R.id.speed_group, true, true);
                    }
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                // Disk group
                if (menuItem.getGroupId() == R.id.disks_group) {
                    File[] disks = FileManager.getInstance().getAvailableDisks();
                    if (disks != null) {
                        for (File disk : disks) {
                            if (disk.getName().hashCode() == menuItem.getItemId()) {
                                if (mCore != null) {
                                    mCore.insertDisk(disk);
                                }
                                return true;
                            }
                        }
                    }
                    // disk not found
                    return true;
                }

                // Speed group
                if (menuItem.getGroupId() == R.id.speed_group) {
                    Core.setSpeed(menuItem.getItemId());
                    menuItem.setChecked(true);
                    return true;
                }

                // Other actions
                if (menuItem.getItemId() == R.id.action_live_map) {
                    boolean show = mCompanionPane.getVisibility() != View.VISIBLE;
                    setCompanionVisible(show);
                    menuItem.setChecked(show);
                    return true;
                } else if (menuItem.getItemId() == R.id.action_notebooks) {
                    openCompanionTool(() -> mNotebook.chooseNotebook());
                    return true;
                } else if (menuItem.getItemId() == R.id.action_screenshot) {
                    ((MiniVMac) requireActivity()).captureScreenshot();
                    return true;
                } else if (menuItem.getItemId() == R.id.action_keyboard) {
                    toggleKeyboard();
                    return true;
                } else if (menuItem.getItemId() == R.id.action_manage_disks) {
                    showDiskManager();
                    return true;
                } else if (menuItem.getItemId() == R.id.action_import_file) {
                    showSelectDisk();
                    return true;
                } else if (menuItem.getItemId() == R.id.action_settings) {
                    showSettings();
                    return true;
                }

                return false;
            }
        };

        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(mMenuProvider);

        updateByPrefs();

        return root;
    }

    @Override
    public void onDestroyView() {
        cancelPendingCompanionTool();
        stopMapPolling();
        stopWheelPolling();
        if (mCore != null) mCore.setMapSampleListener(null);
        if (mCore != null) mCore.setPartySampleListener(null);
        if (mCore != null) mCore.setWheelSampleListener(null);
        if (mNotebook != null) mNotebook.dispose();
        mNotebook = null;
        if (mCompanionPane != null) {
            mCompanionPane.setOnTabSelectedListener(null);
            mCompanionPane.setOnToolSelectedListener(null);
        }
        mCompanionPane = null;
        mLiveMap = null;
        mMapStack = null;
        cancelCodeEntry();
        super.onDestroyView();
        MenuHost menuHost = requireActivity();
        menuHost.removeMenuProvider(mMenuProvider);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (!mEmulatorStarted) {
            initEmulator();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mEmulatorStarted) {
            // Release multicast lock
            WifiManager wifi = (WifiManager) requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiManager.MulticastLock multicastLock = wifi.createMulticastLock("LToUDPMulticastLock");
            multicastLock.setReferenceCounted(true);
            if (multicastLock.isHeld())
            {
                Log.i(TAG, "Releasing multicast lock");
                multicastLock.release();
            }
        }
    }

    private void initEmulator() {
        // Acquire multicast lock
        WifiManager wifi = (WifiManager) requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock multicastLock = wifi.createMulticastLock("LToUDPMulticastLock");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();
        if (!multicastLock.isHeld())
        {
            Log.e(TAG, "Failed to acquire multicast lock");
        } else {
            Log.i(TAG, "Acquired multicast lock");
        }

        // load ROM
        File romFile = FileManager.getInstance().getRomFile(mRomFileName);
        ByteBuffer rom;
        try {
            rom = ByteBuffer.allocateDirect((int)romFile.length());
            FileInputStream romReader = new FileInputStream(romFile);
            romReader.getChannel().read(rom);
            romReader.close();
        } catch (Exception x) {
            Log.w(TAG, "Unable to load ROM file.", x);
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(requireContext());
            SharedPreferences.Editor edit = sharedPref.edit();
            edit.remove(SettingsFragment.KEY_PREF_ROM);
            edit.remove(SettingsFragment.KEY_PREF_ROM_FILE);
            edit.apply();
            Utils.showAlert(getContext(), null, getString(R.string.errNoROM), false,
                    (dialog, which) -> showSettings());
            return;
        }

        final DiskAccessGate.Lease session = DiskAccessGate.GLOBAL.tryBeginEmulation();
        if (session == null) {
            Toast.makeText(requireContext(), "Emulator or disk operation already active. Finish it before restarting.", Toast.LENGTH_LONG).show();
            mRestartLayout.setVisibility(View.VISIBLE);
            return;
        }
        Thread emulation = new Thread(() -> {
            Core sessionCore = null;
            try {
            sessionCore = new Core();
            mCore = sessionCore;
            mCore.setRamSnapshotListener(this::saveRamSnapshot);
            final Core mapCore = mCore;
            mUIHandler.post(() -> { if (mCore == mapCore) mAutomaticWheel.reset(); });
            mCore.setWheelSampleListener(sample -> {
                final int generation = mWheelGeneration;
                mUIHandler.post(() -> {
                    if (mWheelPolling && generation == mWheelGeneration && mCore == mapCore)
                        receiveWheelSample(mapCore, sample);
                });
            });
            mCore.setMapSampleListener(sample -> {
                final int generation = mMapGeneration;
                mUIHandler.post(() -> {
                    if (mMapPolling && generation == mMapGeneration && mCore == mapCore && companionMapActive())
                        mLiveMap.showSample(sample);
                });
            });
            mCore.setPartySampleListener(sample -> {
                final int generation = mMapGeneration;
                mUIHandler.post(() -> {
                    if (mMapPolling && generation == mMapGeneration && mCore == mapCore && companionMapActive())
                        mLiveMap.showPartySample(sample);
                });
            });
            // References the game cites belong to the notebook, not the map view,
            // so this is delivered whichever companion tab is showing.
            mCore.setMessageSampleListener(sample -> {
                final int generation = mMapGeneration;
                mUIHandler.post(() -> {
                    if (mMapPolling && generation == mMapGeneration && mCore == mapCore
                            && companionMapActive() && mNotebook != null)
                        mNotebook.onGameMessage(sample);
                });
            });

            mCore.setOnInitScreenListener((screenWidth, screenHeight) -> mUIHandler.post(() -> {
                mScreenView.setTargetScreenSize(screenWidth, screenHeight);
                if (mMapStack != null) mMapStack.setGuestSize(screenWidth, screenHeight);
            }));

            ScreenView.OnMouseEventListener mouseInput = createMouseInputListener();
            mScreenView.setOnMouseEventListener(mouseInput);
            mTrackPadView.setOnMouseEventListener(mouseInput);

            mCore.setOnUpdateScreenListener((update, top, left, bottom, right) -> mUIHandler.post(() -> mScreenView.updateScreen(update, top, left, bottom, right)));

            mCore.setOnDiskEventListener(new Core.OnDiskEventListener() {

                @Override
                public void onDiskInserted(String path) {
                    FragmentActivity activity = getActivity();
                    if (activity != null) {
                        activity.invalidateOptionsMenu();
                    }
                }

                @Override
                public void onDiskEjected(String path) {
                    if (FileManager.getInstance().isInDownloads(path)) {
                        File f = new File(path);
                        Utils.showShareDialog(getContext(), f, f.getName());
                    }

                    FragmentActivity activity = getActivity();
                    if (activity != null) {
                        activity.invalidateOptionsMenu();
                    }
                }

                @Override
                public void onCreateDisk(int size, String filename) {
                    mCore.makeNewDisk(size, FileManager.getInstance().getDownloadDir().getAbsolutePath(), filename);
                    Core.notifyDiskCreated();
                }
            });

            mCore.setOnAlertListener(new Core.OnAlertListener() {
                @Override
                public void onAlert(String title, String msg, boolean end, DialogInterface.OnClickListener listener) {
                    Context context = getContext();
                    if (context != null) {
                        Utils.showAlert(context, title, msg, end, listener);
                    } else {
                        Log.w(TAG, "Unable to show alert because there is no context attached.");
                    }
                }

                @Override
                public void onAlert(int msgResId, boolean end) {
                    Context context = getContext();
                    if (context != null) {
                        Utils.showAlert(context, context.getString(msgResId), end);
                    } else {
                        Log.w(TAG, "Unable to show alert because there is no context attached.");
                    }
                }
            });

            mCore.initClipboardManager(mClipboardManager);

            mUIHandler.post(() -> {
                mRestartLayout.setVisibility(View.GONE);
                requireActivity().invalidateOptionsMenu();
            });

            // Start the emulation
            mCore.initEmulation(rom);

            } finally {
                // initEmulation is blocking. Only its return guarantees native teardown
                // has finished; isReady()/pause are not safe disk-maintenance gates.
                if (sessionCore != null) sessionCore.closeDisksAfterEmulation();
                if (mCore == sessionCore) mCore = null;
                mSnapshotBusy.set(false);
                session.close();
                mUIHandler.post(() -> {
                    if (getView() != null && mRestartLayout != null)
                        mRestartLayout.setVisibility(View.VISIBLE);
                });
            }
        });
        mEmulatorStarted = true;
        emulation.setName("EmulationThread");
        try {
            emulation.start();
        } catch (RuntimeException | Error failure) {
            session.close();
            mEmulatorStarted = false;
            throw failure;
        }
    }

    /** Views can deliver a final move/up after the emulation thread clears mCore. */
    private ScreenView.OnMouseEventListener createMouseInputListener() {
        return new ScreenView.OnMouseEventListener() {
            @Override public void onMousePosition(int x, int y) {
                Core target = mCore;
                if (target != null && target.isReady()) target.setMousePosition(x, y);
            }
            @Override public void onMouseMove(int dx, int dy) {
                Core target = mCore;
                if (target != null && target.isReady()) target.setMoveMouse(dx, dy);
            }
            @Override public void onMouseClick(boolean down) {
                if (down) cancelAutomaticWheel();
                Core target = mCore;
                if (target != null && target.isReady()) target.setMouseBtn(down);
            }
        };
    }

    private void captureRam() {
        if (!mSnapshotBusy.compareAndSet(false, true)) return;
        if (mCore == null || !mCore.captureRam()) {
            mSnapshotBusy.set(false);
            Toast.makeText(requireContext(), R.string.capture_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(requireContext(), R.string.capture_pending, Toast.LENGTH_SHORT).show();
    }

    private void saveRamSnapshot(byte[] ram) {
        final File directory = mSnapshotDirectory;
        new Thread(() -> {
            String name = null;
            try {
                if (ram == null) throw new IOException("RAM allocation failed");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Snapshot directory unavailable");
                File target = File.createTempFile("ram-" + System.currentTimeMillis() + "-", ".ram", directory);
                try (FileOutputStream out = new FileOutputStream(target)) { out.write(ram); }
                name = target.getName();
                Log.i(TAG, "Saved RAM snapshot: " + name + " (" + ram.length + " bytes)");
            } catch (IOException e) {
                Log.e(TAG, "RAM snapshot failed", e);
            } finally {
                mSnapshotBusy.set(false);
            }
            final String savedName = name;
            mUIHandler.post(() -> {
                if (getContext() == null) return;
                String message = savedName == null ? getString(R.string.capture_failed)
                        : getString(R.string.capture_saved, savedName);
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            });
        }, "RamSnapshotWriter").start();
    }

    private void initKeyboard(String langCode) {
        // Create the Keyboard
        final String QWERTY = "_qwerty";
        final String SYMBOLS = "_symbols";
        final String SYMBOLS_SHIFT = "_symbols_shift";
        final String NUMPAD = "_numpad";

        int qwerty = getResources().getIdentifier(langCode + QWERTY, "xml", requireContext().getApplicationInfo().packageName);
        int symbols = getResources().getIdentifier(langCode + SYMBOLS, "xml", requireContext().getApplicationInfo().packageName);
        int symbols_shift = getResources().getIdentifier(langCode + SYMBOLS_SHIFT, "xml", requireContext().getApplicationInfo().packageName);
        int numpad = getResources().getIdentifier(langCode + NUMPAD, "xml", requireContext().getApplicationInfo().packageName);

        mQwertyKeyboard = new Keyboard(getContext(), qwerty);
        if (symbols != 0) {
            mSymbolsKeyboard = new Keyboard(getContext(), symbols);
        }
        if (symbols_shift != 0) {
            mSymbolsShiftedKeyboard = new Keyboard(getContext(), symbols_shift);
        }
        if (numpad != 0) {
            mNumpadKeyboard = new Keyboard(getContext(), numpad);
        }

        // Attach the keyboard to the view
        mKeyboardView.setKeyboard(mQwertyKeyboard);
        // Do not show the preview balloons
        mKeyboardView.setPreviewEnabled(false);
        // Install the key handler
        mKeyboardView.setOnKeyboardActionListener(mOnKeyboardActionListener);
    }

    private final KeyboardView.OnKeyboardActionListener mOnKeyboardActionListener = new KeyboardView.OnKeyboardActionListener() {

        @Override public void onKey(int primaryCode, int[] keyCodes) {
            if (mKeyboardView != null) {
                if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
                    Keyboard current = mKeyboardView.getKeyboard();
                    if (current == mSymbolsKeyboard) {
                        mKeyboardView.setKeyboard(mQwertyKeyboard);
                        setShifted(false);
                        mKeyboardView.setShifted(false);
                    } else if (current == mSymbolsShiftedKeyboard) {
                        mKeyboardView.setKeyboard(mQwertyKeyboard);
                        setShifted(true);
                        mKeyboardView.setShifted(true);
                    } else if (current == mNumpadKeyboard) {
                        mKeyboardView.setKeyboard(mSymbolsKeyboard);
                        setShifted(false);
                        mKeyboardView.setShifted(false);
                    } else {
                        if (getKey(KEYCODE_MAC_SHIFT).on) {
                            mKeyboardView.setKeyboard(mSymbolsShiftedKeyboard);
                            setShifted(true);
                        } else {
                            mKeyboardView.setKeyboard(mSymbolsKeyboard);
                            setShifted(false);
                        }
                    }
                } else if (primaryCode == KEYCODE_NUMPAD) {
                    resetShift();
                    mKeyboardView.setKeyboard(mNumpadKeyboard);
                } else if (primaryCode == KEYCODE_MAC_SHIFT) {
                    Keyboard currentKeyboard = mKeyboardView.getKeyboard();
                    if (mQwertyKeyboard == currentKeyboard) {
                        mKeyboardView.setShifted(!mKeyboardView.isShifted());
                    } else if (currentKeyboard == mSymbolsKeyboard) {
                        setShifted(true);
                        mKeyboardView.setKeyboard(mSymbolsShiftedKeyboard);
                        setShifted(true);
                    } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
                        setShifted(false);
                        mKeyboardView.setKeyboard(mSymbolsKeyboard);
                        setShifted(false);
                    }
                }
            }
        }

        @Override public void onPress(int primaryCode) {
            cancelAutomaticWheel();
            Core target = mCore;
            if (target == null || !target.isReady()) return;
            if (primaryCode >= 0) {
                Keyboard.Key key = getKey(primaryCode);

                if (key != null && (!key.sticky || !key.on)) {
                    target.keyDown(primaryCode);
                }
            }
        }

        @Override public void onRelease(int primaryCode) {
            Core target = mCore;
            if (target == null || !target.isReady()) return;
            if (primaryCode >= 0) {
                Keyboard.Key key = getKey(primaryCode);

                if (key != null && (!key.sticky || !key.on)) {
                    target.keyUp(primaryCode);
                }
            }
        }

        @Override public void onText(CharSequence text) {
        }

        @Override public void swipeDown() {
        }

        @Override public void swipeLeft() {
        }

        @Override public void swipeRight() {
        }

        @Override public void swipeUp() {
        }

        private Keyboard.Key getKey(int primaryCode) {
            List<Keyboard.Key> keys = mKeyboardView.getKeyboard().getKeys();
            for (Keyboard.Key key : keys) {
                if (key.codes.length > 0 && key.codes[0] == primaryCode) {
                    return key;
                }
            }
            return null;
        }

        public void setShifted(boolean shiftState) {
            Keyboard.Key shiftKey = getKey(KEYCODE_MAC_SHIFT);
            if (shiftKey != null) {
                shiftKey.on = shiftState;
            }
        }

        private void resetShift() {
            Keyboard.Key shiftKey = getKey(KEYCODE_MAC_SHIFT);
            if (shiftKey != null && shiftKey.on) {
                Core target = mCore;
                if (target != null && target.isReady()) {
                    target.keyUp(KEYCODE_MAC_SHIFT);
                }
                shiftKey.on = false;
            }
        }
    };

    private void setTrackpad(boolean isTrackpad) {
        if (isTrackpad) {
            mTrackPadView.setVisibility(View.VISIBLE);
            mFullScreenButton.setVisibility(View.VISIBLE);
        } else {
            mTrackPadView.setVisibility(View.GONE);
            mFullScreenButton.setVisibility(View.GONE);
        }
    }

    private void updateByPrefs() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
        String moduleName = sharedPref.getString(SettingsFragment.KEY_PREF_MACHINE, getDefaultModuleName());
        mRomFileName = sharedPref.getString(SettingsFragment.KEY_PREF_ROM_FILE, getDefaultRomFile());
        boolean scalePref = sharedPref.getBoolean(SettingsFragment.KEY_PREF_SCALE, true);
        boolean scrollPref = sharedPref.getBoolean(SettingsFragment.KEY_PREF_SCROLL, false);
        Core.setModule(moduleName);
        mScreenView.setScaled(scalePref);
        mScreenView.setScroll(scrollPref);

        long romChecksum = mRomChecksum;
        mRomChecksum = sharedPref.getLong(SettingsFragment.KEY_PREF_ROM_CHECKSUM, RomManager.INVALID_CHECKSUM);
        if (romChecksum != mRomChecksum) {
            if (mCore != null) {
                mCore.forceMacOff();
            }
        }

        String newLang = sharedPref.getString(SettingsFragment.KEY_PREF_KEYBOARDS, "us");
        if (!newLang.equals(mLang)) {
            mLang = newLang;
            initKeyboard(newLang);
        }

        String mouseType = sharedPref.getString(SettingsFragment.KEY_PREF_MOUSE, "touchscreen");
        Boolean isTrackpad = mouseType.equals("trackpad");
        if (!isTrackpad.equals(mIsTrackpad)) {
            mIsTrackpad = isTrackpad;
            setTrackpad(mIsTrackpad);
        }
    }

    private String getDefaultRomFile() {
        return getResources().getString(R.string.defaultRomFileName);
    }

    private String getDefaultModuleName() {
        return getResources().getString(R.string.defaultModuleName);
    }

    @Override
    public void onPause () {
        stopMapPolling();
        stopWheelPolling();
        cancelCodeEntry();
        if (mCore != null) {
            mCore.pauseEmulation();
        }

        super.onPause();

        if (mCore != null && !mCore.hasDisksInserted() && !onActivity) {
            mCore.requestMacOff();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startMapPolling();
        startWheelPolling();

        if (mCore != null) {
            mCore.resumeEmulation();
        }
    }

    @Override public boolean typeWheelCode(String code) {
        cancelAutomaticWheel();
        if (mCore == null || !mCore.isReady() || !isResumed() || mCodeEntry != null || !code.matches("[A-Z0-9]{5,6}")) {
            Toast.makeText(requireContext(), "Emulator is not ready for code entry.", Toast.LENGTH_SHORT).show();
            return false;
        }
        int[] keys = new int[code.length() + 1];
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            keys[i] = translateKeyCode(Character.isDigit(ch) ? KeyEvent.KEYCODE_0 + ch - '0' : KeyEvent.KEYCODE_A + ch - 'A');
        }
        keys[code.length()] = translateKeyCode(KeyEvent.KEYCODE_ENTER);
        final Core target = mCore;
        mCodeEntryCore = target;
        mCodeEntry = new Runnable() {
            int index;
            public void run() {
                if (mCore != target || !target.isReady() || !isResumed()) { cancelCodeEntry(); return; }
                if (mCodeEntryKey >= 0) {
                    target.keyUp(mCodeEntryKey); mCodeEntryKey = -1; index++;
                    if (index == keys.length) { mCodeEntry = null; mCodeEntryCore = null; return; }
                } else {
                    mCodeEntryKey = keys[index]; target.keyDown(mCodeEntryKey);
                }
                mUIHandler.postDelayed(this, 100);
            }
        };
        // Let the native dialog close before starting guest input; leave a CPU tick between key edges.
        mUIHandler.postDelayed(mCodeEntry, 250);
        return true;
    }

    private void cancelCodeEntry() {
        if (mCodeEntry != null && mUIHandler != null) mUIHandler.removeCallbacks(mCodeEntry);
        Core target = mCodeEntryCore;
        if (mCodeEntryKey >= 0 && target != null && target == mCore && target.isReady())
            target.keyUp(mCodeEntryKey);
        mCodeEntryKey = -1; mCodeEntry = null; mCodeEntryCore = null;
    }

    @Override
    public boolean onKeyDown (int keyCode, @NonNull KeyEvent event) {
        cancelAutomaticWheel();
        if (mScreenView != null && mScreenView.isScroll()) {
            switch(keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    mScreenView.scrollScreen(keyCode, 8);
                    return true;
            }
        }

        int macKey = translateKeyCode(keyCode);
        Core target = mCore;
        if (macKey >= 0) {
            if (target != null && target.isReady()) target.keyDown(macKey);
            return true;
        }

        if(keyCode == KeyEvent.KEYCODE_BACK) {
            // letting this through will break on next launch
            // since it will create a new instance instead of resuming
            // this one. I thought singleInstance was for that.

            // Close the keyboard, if it is open
            toggleKeyboard();

            return true;
        }

        return false;
    }

    @Override
    public boolean onKeyUp (int keyCode, @NonNull KeyEvent event) {
        int macKey = translateKeyCode(keyCode);
        Core target = mCore;
        if (macKey >= 0) {
            if (target != null && target.isReady()) target.keyUp(macKey);
            return true;
        }
        return false;
    }

    @Override
    public boolean onTrackballEvent (MotionEvent event) {
        if (event.getX() > 0) mScreenView.scrollScreen(KeyEvent.KEYCODE_DPAD_RIGHT, (int)(TRACKBALL_SENSITIVITY*event.getX()));
        else if (event.getX() < 0) mScreenView.scrollScreen(KeyEvent.KEYCODE_DPAD_LEFT, (int)-(TRACKBALL_SENSITIVITY*event.getX()));
        if (event.getY() > 0) mScreenView.scrollScreen(KeyEvent.KEYCODE_DPAD_DOWN, (int)(TRACKBALL_SENSITIVITY*event.getY()));
        else if (event.getY() < 0) mScreenView.scrollScreen(KeyEvent.KEYCODE_DPAD_UP, (int)-(TRACKBALL_SENSITIVITY*event.getY()));

        return true;
    }

    public int translateKeyCode (int keyCode) {
        if (keyCode < 0 || keyCode >= keycodeTranslationTable.length) return -1;
        return keycodeTranslationTable[keyCode];
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        initKeyboard(mLang);
    }

    private void showAbout() {
        Dialog dialog = new AboutDialog(getContext());
        dialog.show();
    }

    private final ActivityResultLauncher<Intent> _diskManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        onActivity = false;
        requireActivity().invalidateOptionsMenu();
    });

    public void showDiskManager() {
        if (!FileManager.getInstance().isInitialized()) return;

        onActivity = true;
        Intent i = new Intent(getActivity(), DiskManagerActivity.class);
        _diskManager.launch(i);
    }

    private final ActivityResultLauncher<String> _importFile = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        onActivity = false;
        Utils.loadFileWithProgressBar(requireContext(), uri, new IAsyncCopyCallback() {
            @Override
            public void onSuccessfulCopy(File file) {
                if (mCore != null) {
                    mCore.insertDisk(file);
                }
            }
        });
    });

    public void showSelectDisk() {
        if (!FileManager.getInstance().isInitialized()) return;

        onActivity = true;
        _importFile.launch("*/*");
    }

    private final ActivityResultLauncher<Intent> _settings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        int resultCode = result.getResultCode();

        onActivity = false;

        if (!mEmulatorStarted) {
            initEmulator();
            return;
        }

        switch (resultCode) {
            case SettingsFragment.RESULT_RESET:
                reset();
                break;
            case SettingsFragment.RESULT_INTERRUPT:
                interrupt();
                break;
            case SettingsFragment.RESULT_POWEROFF:
                powerOff();
                break;
            case SettingsFragment.RESULT_ABOUT:
                showAbout();
                break;
        }
        requireActivity().invalidateOptionsMenu();
        updateByPrefs();
    });

    public void showSettings() {
        onActivity = true;
        Intent i = new Intent(getActivity(), SettingsActivity.class);
        _settings.launch(i);
        //((MiniVMac)getActivity()).showSettings();
    }

    public void toggleKeyboard() {
        if (mKeyboardView.getVisibility() == View.VISIBLE) {
            mKeyboardView.setVisibility(View.GONE);
            mKeyboardView.setEnabled(false);
        } else {
            mKeyboardView.setVisibility(View.VISIBLE);
            mKeyboardView.setEnabled(true);
        }
    }

    public void reset() {
        if (mCore != null) {
            mCore.wantMacReset();
        }
    }

    public void interrupt() {
        if (mCore != null) {
            mCore.wantMacInterrupt();
        }
    }

    public void powerOff() {
        if (mCore != null) {
            mCore.forceMacOff();
        }
    }
}
