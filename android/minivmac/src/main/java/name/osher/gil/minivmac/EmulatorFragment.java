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
import android.widget.TextView;
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
import name.osher.gil.minivmac.mapper.PollingPace;
import name.osher.gil.minivmac.mapper.LoadSequence;
import name.osher.gil.minivmac.mapper.GameSignal;
import name.osher.gil.minivmac.mapper.AutomaticWheel;
import name.osher.gil.minivmac.mapper.WheelPrompt;
import name.osher.gil.minivmac.desktop.DiskAccessGate;

public class EmulatorFragment extends Fragment
        implements IOnIOEventListener, CodeWheelDialog.Host {
    private static final String TAG = "minivmac.EmulatorFrag";
    static final String STATE_COMPANION_TAB = "poolrad_companion_tab";
    private static final String PREF_SHOW_COMPANION = "poolrad_show_companion";

    /*
     * Android keycode to Macintosh key code.
     *
     * The tail from 144 is the numeric keypad, which used to be past the end of
     * this table entirely, so a hardware keypad reached the guest not at all.
     * Pool of Radiance moves a character in combat with the keypad, and the
     * diagonals are the point of it: 1 and 3 step up-left and up-right, which
     * no arrow key can do. These are the same Mac codes the app's own on-screen
     * numpad sends in us_numpad.xml, so both routes agree.
     *
     * The four arrow keys stay unmapped. They were mapped briefly on the
     * strength of an old note saying combat used them; it does not, and a
     * speculative mapping is not worth the behaviour it invents.
     */
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
            -1, -1, -1, 0x37, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57,
            0x58, 0x59, 0x5B, 0x5C, 0x4B, 0x43, 0x4E, 0x45, 0x41, -1,
            0x4C, 0x51};
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
    /**
     * The map's own Return key. It goes through exactly the path the code-wheel
     * answer and the on-screen keyboard use -- a scancode down, then up -- so
     * it is a shortcut for a key the app already sends, not a new way into the
     * game. Nothing is written to guest memory.
     */
    private void pressGuestReturn() {
        Core target = mCore;
        if (target == null || !target.isReady()) return;
        cancelAutomaticWheel();
        mAutomaticKey = translateKeyCode(KeyEvent.KEYCODE_ENTER);
        mAutomaticKeyCore = target;
        target.keyDown(mAutomaticKey);
        mUIHandler.postDelayed(mReleaseAutomaticKey, 100);
    }

    /**
     * The only write this app makes into the running game: one party member's
     * quick flag. Every guard is in the native reader, which refuses unless the
     * whole party validates and the byte already holds a value the field is
     * allowed to have. Authorised by the owner on 2026-09-16; see DESIGN.md.
     */
    private boolean setGuestQuick(int member, boolean on) {
        Core target = mCore;
        if (target == null || !target.isReady()) return false;
        boolean written = target.queuePartyQuick(mLiveMap.partyMember(member), on);
        // Show the game's own answer, not an assumption: ask for a fresh sample
        // rather than repainting what we hoped happened.
        if (written) target.requestPartySample();
        mLiveMap.refreshQuickPending();
        return written;
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
        // The game has reached its own copy-protection prompt, so boot is done:
        // stop nudging Return and leave the wheel to the auto-answer.
        if (prompt != null) stopBootDismiss();
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
    private SaveBackupController mSaveBackup;
    private SaveStateController mSaveState;
    private MapStackLayout mMapStack;
    private boolean mMapPolling;
    private volatile int mMapGeneration;
    /** How fast to read the machine, given how long since its screen moved. */
    private final PollingPace mMapPace = new PollingPace();
    private final Runnable mMapPoll = new Runnable() {
        @Override public void run() {
            /*
             * Two different things used to end this loop the same way. Being
             * told to stop is one; finding the companion momentarily inactive
             * -- mid-restart, or for an instant while the fragment is not
             * resumed -- is the other, and it used to return without posting
             * the next tick. Nothing then noticed. The loop was simply gone,
             * and the only way back was to toggle the companion's tab, which
             * is a thing nobody should have to know to do.
             *
             * So a tick with nothing to read into now waits and comes round
             * again; only being told to stop actually stops it.
             */
            if (!mMapPolling) return;
            if (companionMapActive()) {
                Core target = mCore;
                if (target != null && target.isReady()) {
                    target.requestMapSample(); target.requestPartySample();
                    target.requestMessageSample(); target.requestCombatSample();
                }
                else { mLiveMap.showSample(null); mLiveMap.showPartySample(null); }
            }
            mUIHandler.postDelayed(this, mMapPace.interval(SystemClock.elapsedRealtime()));
        }
    };

    /**
     * The guest's screen moved, so read at the full rate again — and if a slow
     * read was already scheduled, do not sit out the rest of its wait. Without
     * this, stepping through a door after a quiet minute would take three
     * seconds to reach the map.
     */
    private void guestScreenMoved() {
        long now = SystemClock.elapsedRealtime();
        mLastGuestScreenMs = now;
        boolean waiting = mMapPolling && mMapPace.slowed(now);
        mMapPace.sawActivity(now);
        if (waiting && mUIHandler != null) {
            mUIHandler.removeCallbacks(mMapPoll);
            mUIHandler.post(mMapPoll);
        }
    }

    /* --- auto-dismiss the Mac's boot dialog (F30) --- */

    /*
     * After an unclean shutdown the Mac stalls at "This computer may not have
     * been shut down properly", waiting on the Return key, and never reaches the
     * game until someone presses it. So during the boot window -- before any
     * party is in the world -- a screen that has gone still for a few seconds is
     * taken to be that dialog (or a title card), and one Return is sent to move
     * it along. A Return at this point can only advance a splash or dismiss an
     * alert; there is nothing to lose on a machine with no game running yet.
     * It disarms as soon as the game reaches its own copy-protection prompt.
     */
    private volatile long mLastGuestScreenMs;
    /** Last time a large part of the screen changed; small blinks (a cursor, the
     *  menu clock) do not count, so a still dialog reads as still. */
    private volatile long mLastBigScreenMs;
    private boolean mBootDismissArmed;
    private int mBootDismissSent;
    private static final long BOOT_STALL_MS = 3500;
    /** A change at least this many pixels is real drawing, not a blink. */
    private static final int BOOT_BIG_CHANGE_PX = 12000;
    private static final int BOOT_DISMISS_MAX = 6;
    private final Runnable mBootDismissTick = new Runnable() {
        @Override public void run() {
            if (!mBootDismissArmed) return;
            Core core = mCore;
            long now = SystemClock.elapsedRealtime();
            boolean noGameYet = mLiveMap == null || mLiveMap.snapshot() == null;
            long idle = now - mLastBigScreenMs;
            if (core != null && core.isReady() && isResumed() && noGameYet
                    && mBootDismissSent < BOOT_DISMISS_MAX
                    && idle > BOOT_STALL_MS) {
                pressGuestReturn();
                mBootDismissSent++;
                mLastBigScreenMs = now;   // let the screen answer before nudging again
            }
            if (mUIHandler != null && mBootDismissArmed) mUIHandler.postDelayed(this, 1500);
        }
    };

    private void startBootDismiss() {
        stopBootDismiss();
        mBootDismissArmed = true;
        mBootDismissSent = 0;
        mLastBigScreenMs = SystemClock.elapsedRealtime();
        if (mUIHandler != null) mUIHandler.postDelayed(mBootDismissTick, 1500);
    }

    private void stopBootDismiss() {
        mBootDismissArmed = false;
        if (mUIHandler != null) mUIHandler.removeCallbacks(mBootDismissTick);
    }

    private volatile byte[] mLastPartySample;
    private LoadSequence mLoad;
    private View mBusyLayout;
    private TextView mBusyText;
    /** Mac key codes: the modifier and the key the sequence needs most. */
    private static final int MAC_COMMAND = 0x37, MAC_RETURN = 0x24;
    /** Long enough for the guest to notice each key; it is not a fast typist. */
    private static final long KEY_GAP_MS = 70;

    /** What the probe last said the machine was doing. */
    private GameSignal guestSignal() { return GameSignal.of(mLastPartySample); }

    private void tapGuestKey(int macKey) {
        Core target = mCore;
        if (target == null || !target.isReady() || macKey < 0) return;
        target.keyDown(macKey);
        mUIHandler.postDelayed(() -> {
            Core still = mCore;
            if (still == target && still.isReady()) still.keyUp(macKey);
        }, KEY_GAP_MS / 2);
    }

    /** Hold Command, press a letter, let go. */
    private void sendCommandKey(char letter) {
        Core target = mCore;
        int macKey = translateKeyCode(KeyEvent.KEYCODE_A + Character.toUpperCase(letter) - 'A');
        if (target == null || !target.isReady() || macKey < 0) return;
        target.keyDown(MAC_COMMAND);
        mUIHandler.postDelayed(() -> {
            Core still = mCore;
            if (still != target || !still.isReady()) return;
            still.keyDown(macKey);
            mUIHandler.postDelayed(() -> {
                Core again = mCore;
                if (again != target || !again.isReady()) return;
                again.keyUp(macKey);
                mUIHandler.postDelayed(() -> {
                    Core last = mCore;
                    if (last == target && last.isReady()) last.keyUp(MAC_COMMAND);
                }, KEY_GAP_MS);
            }, KEY_GAP_MS);
        }, KEY_GAP_MS);
    }

    /** Type text one key at a time, with Return afterwards only if asked. */
    private void sendGuestLine(String text, boolean thenReturn) {
        long at = 0;
        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            int macKey = macKeyFor(letter);
            if (macKey < 0) continue;
            at += KEY_GAP_MS * 2;
            mUIHandler.postDelayed(() -> tapGuestKey(macKey), at);
        }
        if (thenReturn) mUIHandler.postDelayed(() -> tapGuestKey(MAC_RETURN), at + KEY_GAP_MS * 3);
    }

    /**
     * Only what the game's own dialogs need: letters, digits and a space. A
     * character with no mapping is skipped rather than guessed at, because a
     * wrong key in a Standard File dialog selects the wrong file.
     */
    private int macKeyFor(char letter) {
        char upper = Character.toUpperCase(letter);
        if (upper >= 'A' && upper <= 'Z') return translateKeyCode(KeyEvent.KEYCODE_A + upper - 'A');
        if (upper >= '0' && upper <= '9') return translateKeyCode(KeyEvent.KEYCODE_0 + upper - '0');
        if (upper == ' ') return translateKeyCode(KeyEvent.KEYCODE_SPACE);
        if (upper == '.') return translateKeyCode(KeyEvent.KEYCODE_PERIOD);
        return -1;
    }

    private void showBusy(String what) {
        if (mBusyLayout == null) return;
        mBusyText.setText(what);
        mBusyLayout.setVisibility(View.VISIBLE);
    }

    private void hideBusy() {
        if (mBusyLayout != null) mBusyLayout.setVisibility(View.GONE);
    }

    /**
     * Quit the game, start it again and load a save, with the guest covered so
     * none of it is watched. Every step is decided by LoadSequence from what
     * the probe says the machine is doing; nothing here reads the screen.
     */
    void loadSavedGame(String application, String folder, String save) {
        if (mLoad != null) { toastGuest("Already loading something."); return; }
        Core target = mCore;
        if (target == null || !target.isReady()) { toastGuest("The emulator is not running."); return; }
        mLoad = new LoadSequence(application, folder, save);
        showBusy(mLoad.describe());
        mUIHandler.post(mLoadTick);
    }

    private final Runnable mLoadTick = new Runnable() {
        @Override public void run() {
            LoadSequence load = mLoad;
            if (load == null) return;
            Core target = mCore;
            if (target == null || !target.isReady()) { finishLoad("The emulator stopped."); return; }
            LoadSequence.Instruction step = load.next(guestSignal(), SystemClock.elapsedRealtime());
            showBusy(load.describe());
            switch (step.kind) {
                case RESTART_GUEST: initEmulator(); break;
                case COMMAND_KEY: sendCommandKey(step.key); break;
                case TYPE_LINE: sendGuestLine(step.text, true); break;
                case TYPE_ONLY: sendGuestLine(step.text, false); break;
                case FINISHED: finishLoad(step.message); return;
                case FAILED: finishLoad(step.message); return;
                default: break;
            }
            mUIHandler.postDelayed(this, 400);
        }
    };

    private void finishLoad(String message) {
        mLoad = null;
        hideBusy();
        toastGuest(message);
        // Whatever happened, the companion starts reading again from nothing.
        startMapPolling();
    }

    private void toastGuest(String message) {
        if (isAdded() && message != null)
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    private void startMapPolling() {
        stopMapPolling();
        if (companionMapActive()) {
            mMapPolling = true;
            // A pane that has just opened knows nothing; read at full rate
            // until the machine itself shows it has gone quiet.
            mMapPace.reset();
            mUIHandler.post(mMapPoll);
        }
    }

    private void stopMapPolling() {
        mMapPolling = false;
        mMapGeneration++;
        if (mUIHandler != null) mUIHandler.removeCallbacks(mMapPoll);
        // Not a blink: nothing held may survive the pane going away.
        if (mLiveMap != null) mLiveMap.clearReadings();
    }

    private boolean companionMapActive() {
        // User-owned exploration keeps recording while Info is selected or the
        // companion is hidden. Pausing/destroying the activity still stops it.
        return isResumed() && mLiveMap != null && mCompanionPane != null;
    }

    /* --- automatic save states (F37) --- */

    /** How often an automatic save is taken while a party is in the world. */
    private static final long AUTOSAVE_INTERVAL_MS = 5 * 60 * 1000;
    private boolean mAutoSaving;
    private final Runnable mAutoSaveTick = new Runnable() {
        @Override public void run() {
            if (!mAutoSaving) return;
            maybeAutoSave();
            if (mUIHandler != null) mUIHandler.postDelayed(this, AUTOSAVE_INTERVAL_MS);
        }
    };

    private void startAutoSave() {
        stopAutoSave();
        mAutoSaving = true;
        if (mUIHandler != null) mUIHandler.postDelayed(mAutoSaveTick, AUTOSAVE_INTERVAL_MS);
    }

    private void stopAutoSave() {
        mAutoSaving = false;
        if (mUIHandler != null) mUIHandler.removeCallbacks(mAutoSaveTick);
    }

    /**
     * Take an automatic save only when it is worth taking: the app is in front,
     * the machine is running, the player has turned auto-save on, and a party is
     * actually in the world (so we never rotate the boot screen or the Finder
     * over a real save). The save itself is silent and rotates its own set.
     */
    private void maybeAutoSave() {
        if (!isResumed() || !autoSaveEnabled()) return;
        Core core = mCore;
        if (core == null || !core.isReady()) return;
        if (mLiveMap == null || mLiveMap.snapshot() == null) return;
        saveState().autoSave();
    }

    /** Push the companion view preferences (one-line rows F69, message mirror F64) to the map; applied on resume so returning from Settings takes effect. */
    private void applyOneLinePartyPref() {
        if (mLiveMap == null) return;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            mLiveMap.setOneLineParty(prefs.getBoolean(SettingsFragment.KEY_PREF_ONELINE_PARTY, false));
            mLiveMap.setMirrorMessage(prefs.getBoolean(SettingsFragment.KEY_PREF_MIRROR_MESSAGE, false));
        } catch (RuntimeException ignored) { }
    }

    private boolean autoSaveEnabled() {
        try {
            return PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getBoolean(SettingsFragment.KEY_PREF_AUTOSAVE, true);
        } catch (RuntimeException notAttached) {
            return false;
        }
    }

    String selectedCompanionTab() { return mSelectedCompanionTab; }

    private void onCompanionTabSelected(String tab) {
        mSelectedCompanionTab = tab;
        mScreenView.requestFocus();
    }

    void restoreCompanionTab(String tab) {
        mSelectedCompanionTab = CompanionPane.INFO.equals(tab) ? CompanionPane.INFO
                : CompanionPane.CONNECTIONS.equals(tab) ? CompanionPane.CONNECTIONS : CompanionPane.MAP;
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
                case OPTIONS: mNotebook.showOptions(this::applyOneLinePartyPref); break;
                case SAVES: saveState().showStatus(); break;
                case PARTY_ORDER: PartyOrderDialog.show(requireActivity(),
                        () -> mLiveMap == null ? null : mLiveMap.partySnapshot()); break;
                case MESSAGE_LOG: mNotebook.showMessageLog(); break;
                case EXPLORATION: mNotebook.showExploration(); break;
                case LEVELS: LevelsReferenceDialog.show(requireActivity()); break;
                case SPELLS: SpellReferenceDialog.show(requireActivity()); break;
                case JOURNAL: ((MiniVMac) requireActivity()).journal().show(); break;
                case EQUIPMENT: EquipmentReferenceDialog.show(requireActivity()); break;
                case MONEY: LiveTextReferenceDialog.show(requireActivity(), "Money",
                        "Current holdings · all party members",
                        () -> name.osher.gil.minivmac.mapper.PartyMoney.describe(
                                mLiveMap == null ? null : mLiveMap.partySnapshot()),
                        "Gems and jewelry are counts, not appraised values. Coin value excludes them.",
                        18, "Coin converter", () -> MoneyReferenceDialog.show(requireActivity())); break;
                case WHEEL:
                    if (getChildFragmentManager().findFragmentByTag("code-wheel") == null)
                        new CodeWheelDialog().show(getChildFragmentManager(), "code-wheel");
                    break;
                case LEGEND: MapLegendDialog.show(requireActivity()); break;
                case NOTE_INDEX: mNotebook.showNoteIndex(); break;
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
        mBusyLayout = root.findViewById(R.id.busy_layout);
        mBusyText = root.findViewById(R.id.busy_text);
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
        mNotebook.setConnectionsView(mCompanionPane.connections());
        mNotebook.setCitationNotice(keys -> {
            if (keys.isEmpty()) { mCompanionPane.clearCitationNotice(); return; }
            String label = keys.size() == 1 ? "Read " + keys.get(0).label()
                    : "Read " + keys.size() + " newly noted references";
            mCompanionPane.showCitationNotice(label, () -> openCompanionTool(
                    () -> ((MiniVMac) requireActivity()).journal().showCitations(keys)));
        });
        mNotebook.setReturnKey(this::pressGuestReturn);
        mNotebook.setQuickSetter(this::setGuestQuick);
        mNotebook.setPartySelector(member -> {
            Core core = mCore;
            Runnable refused = () -> {
                if (isAdded()) android.widget.Toast.makeText(requireContext(),
                        "The game cannot select that character right now.", android.widget.Toast.LENGTH_SHORT).show();
            };
            boolean queued = core != null && core.selectPartyMember(member, refused);
            if (!queued) refused.run();
            return queued;
        });
        mNotebook.setPartySheet(member -> {
            Core core = mCore;
            Runnable refused = () -> {
                if (isAdded()) android.widget.Toast.makeText(requireContext(),
                        "The game cannot open that character's sheet right now.", android.widget.Toast.LENGTH_SHORT).show();
            };
            boolean queued = core != null && core.selectPartyMember(member, refused, () -> {
                if (mCore == core && core.isReady() && isResumed()) tapGuestKey(translateKeyCode(KeyEvent.KEYCODE_V));
            });
            if (!queued) refused.run();
            return queued;
        });
        mLiveMap.setQuickPending(member -> mCore == null ? null : mCore.pendingQuick(member));
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
                } else if (menuItem.getItemId() == R.id.action_quick_save) {
                    saveState().quickSave();
                    return true;
                } else if (menuItem.getItemId() == R.id.action_quick_load) {
                    saveState().quickLoad();
                    return true;
                } else if (menuItem.getItemId() == R.id.action_save_states) {
                    openCompanionTool(() -> saveState().chooseSave());
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

    private SaveStateController saveState() {
        if (mSaveState == null) {
            mSaveState = new SaveStateController(requireActivity(), () -> mCore);
            // Pair a save with the notebook open at save time, and bring it back on load.
            mSaveState.setNotebookLink(new SaveStateController.NotebookLink() {
                @Override public String currentNotebookId() {
                    return mNotebook == null ? null : mNotebook.notebookId();
                }
                @Override public boolean readyForLoad() {
                    return mNotebook != null && mNotebook.readyForStateLoad();
                }
                @Override public void prepareAutoLoad(String notebookId,
                        java.util.function.Consumer<SaveStateController.NotebookRestore> ready) {
                    if (mNotebook != null) mNotebook.prepareStateLoad(notebookId, false, ready);
                    else ready.accept(null);
                }
                @Override public void prepareLoad(String notebookId,
                        java.util.function.Consumer<SaveStateController.NotebookRestore> ready) {
                    if (mNotebook != null) mNotebook.prepareStateLoad(notebookId, ready);
                    else ready.accept(null);
                }
            });
        }
        return mSaveState;
    }

    private void showSavedGameBackups() {
        if (mSaveBackup == null) mSaveBackup = new SaveBackupController(
                requireActivity(), FileManager.getInstance(), () -> {
                    Core target = mCore;
                    return target != null && target.hasDisksInserted();
                });
        mSaveBackup.show(); // Backup/restore only; the withdrawn restart-based Load stays absent.
    }

    @Override
    public void onDestroyView() {
        cancelPendingCompanionTool();
        stopMapPolling();
        stopWheelPolling();
        stopAutoSave();
        stopBootDismiss();
        if (mCore != null) mCore.setMapSampleListener(null);
        if (mCore != null) mCore.setPartySampleListener(null);
        if (mCore != null) mCore.setWheelSampleListener(null);
        if (mNotebook != null) mNotebook.dispose();
        if (mSaveBackup != null) { mSaveBackup.dispose(); mSaveBackup = null; }
        if (mSaveState != null) { mSaveState.dispose(); mSaveState = null; }
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
        final boolean restoreOnLaunch = !mEmulatorStarted
                && PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean(SettingsFragment.KEY_PREF_AUTOLOAD, true);
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
            final SaveStateController stateController = saveState();
            mCore.setSaveStateListener(stateController::onState);
            if (restoreOnLaunch) mapCore.setStartupRestore(() ->
                    mUIHandler.post(() -> stateController.autoLoadLatest(mapCore)));
            mCore.setPartySampleListener(sample -> {
                final int generation = mMapGeneration;
                // Kept whatever the companion is doing: this is how the load
                // sequence knows whether a game is running, and it has to be
                // true even with the map hidden.
                mLastPartySample = sample;
                mUIHandler.post(() -> {
                    if (mMapPolling && generation == mMapGeneration && mCore == mapCore && companionMapActive())
                        { mLiveMap.showPartySample(sample); mLiveMap.refreshQuickPending(); }
                });
            });
            mCore.setCombatSampleListener(sample -> {
                final int generation = mMapGeneration;
                mUIHandler.post(() -> {
                    if (mMapPolling && generation == mMapGeneration && mCore == mapCore && companionMapActive())
                        mLiveMap.showCombatSample(sample);
                });
            });
            // References the game cites belong to the notebook, not the map view,
            // so this is delivered whichever companion tab is showing.
            mCore.setMessageSampleListener(sample -> {
                final int generation = mMapGeneration;
                mUIHandler.post(() -> {
                    if (mMapPolling && generation == mMapGeneration && mCore == mapCore
                            && companionMapActive()) {
                        if (mNotebook != null) mNotebook.onGameMessage(sample);
                        if (mLiveMap != null) {   // F64: mirror the text in larger type
                            name.osher.gil.minivmac.journal.GameMessage msg =
                                    name.osher.gil.minivmac.journal.GameMessage.parse(sample);
                            mLiveMap.showGameMessage(msg == null ? "" : msg.text);
                        }
                    }
                });
            });

            mCore.setOnInitScreenListener((screenWidth, screenHeight) -> mUIHandler.post(() -> {
                mScreenView.setTargetScreenSize(screenWidth, screenHeight);
                if (mMapStack != null) mMapStack.setGuestSize(screenWidth, screenHeight);
                /*
                 * A machine has just come up. Whether it is the first one or a
                 * restart, the companion starts again from nothing: every held
                 * reading is dropped, because a new machine is not a blink, and
                 * the polling loop is restarted so it does not matter whether
                 * the old one was still going round.
                 *
                 * Restarting the guest used to leave the map where it was until
                 * the tab was toggled, which is a thing nobody should have to
                 * know to do.
                 */
                if (mCore == mapCore) startMapPolling();
                if (mCore == mapCore) startBootDismiss();
            }));

            ScreenView.OnMouseEventListener mouseInput = createMouseInputListener();
            mScreenView.setOnMouseEventListener(mouseInput);
            mTrackPadView.setOnMouseEventListener(mouseInput);

            mCore.setOnUpdateScreenListener((update, top, left, bottom, right) -> mUIHandler.post(() -> {
                guestScreenMoved();
                if ((long) (bottom - top) * (right - left) >= BOOT_BIG_CHANGE_PX)
                    mLastBigScreenMs = SystemClock.elapsedRealtime();
                mScreenView.updateScreen(update, top, left, bottom, right);
            }));

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
        if (mCore != null) mCore.cancelPartySelection();
        stopMapPolling();
        stopWheelPolling();
        stopAutoSave();
        stopBootDismiss();
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
        startAutoSave();
        applyOneLinePartyPref();

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
        if (mNotebook != null && mNotebook.onEditorKey(event)) return true;
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
        if (mNotebook != null && mNotebook.onEditorKey(event)) return true;
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
            case SettingsFragment.RESULT_COMPANION_OPTIONS:
                openCompanionTool(() -> mNotebook.showOptions(this::applyOneLinePartyPref));
                break;
            case SettingsFragment.RESULT_SAVED_GAME_BACKUPS:
                showSavedGameBackups();
                break;
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
