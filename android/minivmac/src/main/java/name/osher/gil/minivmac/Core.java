package name.osher.gil.minivmac;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Objects;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import androidx.annotation.StringRes;
import name.osher.gil.minivmac.desktop.DiskAccessGate;

public class Core {
	private static final String TAG = "minivmac.Core";
	
	private int numInsertedDisks = 0;
	@SuppressWarnings("unused") private String[] diskPath;
	@SuppressWarnings("unused") private RandomAccessFile[] diskFile;

	private static ClipboardManager mClipboardManager;
	@SuppressWarnings("FieldMayBeFinal") private volatile boolean initOk = false;
	private volatile boolean emulationEnded = false;
	private volatile boolean diskCloseFailed = false;

	private OnInitScreenListener mOnInitScreenListener;
	private OnUpdateScreenListener mOnUpdateScreenListener;
	private OnDiskEventListener mOnDiskEventListener;
	private OnAlertListener mOnAlertListener;
	private RamSnapshotListener mRamSnapshotListener;
	private volatile MapSampleListener mMapSampleListener;
	private volatile MapSampleListener mWheelSampleListener;
	private volatile MapSampleListener mPartySampleListener;
	private volatile MapSampleListener mMessageSampleListener;
	private volatile MapSampleListener mCombatSampleListener;

	public void setPartySampleListener(MapSampleListener listener) { mPartySampleListener = listener; }
	public boolean requestPartySample() { return initOk && requestPartySampleNative(); }
	private static native boolean requestPartySampleNative();
	@SuppressWarnings("unused") // Read-only compact sample delivered on emulation thread.
	public void onPartySample(byte[] sample) {
		MapSampleListener listener = mPartySampleListener;
		if (listener != null) listener.onSample(sample);
	}

	public void setMessageSampleListener(MapSampleListener listener) { mMessageSampleListener = listener; }
	public boolean requestMessageSample() { return initOk && requestMessageSampleNative(); }
	private static native boolean requestMessageSampleNative();
	@SuppressWarnings("unused") // Read-only Message-window text delivered on emulation thread.
	public void onMessageSample(byte[] sample) {
		MapSampleListener listener = mMessageSampleListener;
		if (listener != null) listener.onSample(sample);
	}

	public void setCombatSampleListener(MapSampleListener listener) { mCombatSampleListener = listener; }
	public boolean requestCombatSample() { return initOk && requestCombatSampleNative(); }
	private static native boolean requestCombatSampleNative();
	@SuppressWarnings("unused") // Read-only tactical grid delivered on emulation thread.
	public void onCombatSample(byte[] sample) {
		MapSampleListener listener = mCombatSampleListener;
		if (listener != null) listener.onSample(sample);
	}

	public void setWheelSampleListener(MapSampleListener listener) { mWheelSampleListener = listener; }
	public boolean requestWheelSample() { return initOk && requestWheelSampleNative(); }
	private static native boolean requestWheelSampleNative();
	@SuppressWarnings("unused") // Read-only compact sample delivered on emulation thread.
	public void onWheelSample(byte[] sample) {
		MapSampleListener listener = mWheelSampleListener;
		if (listener != null) listener.onSample(sample);
	}

	public interface MapSampleListener { void onSample(byte[] sample); }
	public void setMapSampleListener(MapSampleListener listener) { mMapSampleListener = listener; }
	public boolean requestMapSample() { return initOk && requestMapSampleNative(); }
	private static native boolean requestMapSampleNative();
	@SuppressWarnings("unused") // Called on the emulation thread through JNI.
	public void onMapSample(byte[] sample) {
		// Developer opt-in only: adb shell setprop log.tag.PoolRad.Walk DEBUG.
		// Metadata, never RAM/geometry or disk bytes; INFO disables it again.
		if (BuildConfig.DEBUG && android.util.Log.isLoggable("PoolRad.Walk", android.util.Log.DEBUG)) {
			String state = "unavailable";
			if (sample != null && sample.length == 1200 && (sample[3] == '3' || sample[3] == '4')) {
				long epoch = ((sample[28]&255L)<<24) | ((sample[29]&255L)<<16)
						| ((sample[30]&255L)<<8) | (sample[31]&255L);
				state = "mode="+(sample[3]=='4' ? sample[24]&255 : -1)
						+" safe="+(sample[26]&255)+" engine="+(sample[27]&255)+" epoch="+epoch
						+" area="+(sample[35]&255)+" x="+(sample[130]&255)+" y="+(sample[131]&255);
			}
			android.util.Log.d("PoolRad.Walk", state);
		}
		MapSampleListener listener = mMapSampleListener;
		if (listener != null) listener.onSample(sample);
	}

	public interface RamSnapshotListener { void onSnapshot(byte[] ram); }

	public void setRamSnapshotListener(RamSnapshotListener listener) {
		mRamSnapshotListener = listener;
	}

	public boolean captureRam() {
		return BuildConfig.DEBUG && initOk && requestRamSnapshotNative();
	}

	public boolean isReady() { return initOk; }

	private static native boolean requestRamSnapshotNative();

	@SuppressWarnings("unused") // Called on the emulation thread through JNI.
	public void onRamSnapshot(byte[] ram) {
		if (mRamSnapshotListener != null) mRamSnapshotListener.onSnapshot(ram);
	}

	private static volatile boolean mIsInitialized = false;

	private static String mModuleName;

	static {
		System.loadLibrary("jni_proxy");
	}

	public Core() {
		if (mModuleName != null) {
			loadVariant(mModuleName);
		} else {
			throw new IllegalStateException("Module name is not set.");
		}
	}

	public void setOnInitScreenListener(OnInitScreenListener listener) {
		mOnInitScreenListener = listener;
	}

	public void setOnUpdateScreenListener(OnUpdateScreenListener listener) {
		mOnUpdateScreenListener = listener;
	}

	public void setOnDiskEventListener(OnDiskEventListener listener) {
		mOnDiskEventListener = listener;
	}

	public void setOnAlertListener(OnAlertListener listener) {
		mOnAlertListener = listener;
	}
	
	// initialization
	public native boolean loadVariant(String libPath);
	private native static boolean init(Core core, ByteBuffer rom);
	
	// emulation
	private native static void _resumeEmulation();
	private native static void _pauseEmulation();
	private native static boolean isPaused();
	private native static void _setSpeed(int value);
	private native static int _getSpeed();
	private native static void setWantMacReset();
	private native static void setWantMacInterrupt();
	private native static void setRequestMacOff();
	private native static void setForceMacOff();

	public static void setModule(String moduleName) {
		if (!Objects.equals(mModuleName, moduleName)) {
			mModuleName = moduleName;
			if (mIsInitialized) {
				setForceMacOff();
				mIsInitialized = false;
			}
		}
	}

	public static Boolean isInitialized() {
		return mIsInitialized;
	}

	public Boolean initEmulation(ByteBuffer rom) {
		mIsInitialized = true;
		try {
			loadVariant(mModuleName);
			return init(this, rom);
		} finally {
			emulationEnded = true;
			mIsInitialized = false;
		}
	}

	public void wantMacReset() {
		if (!initOk) return;
			setWantMacReset();
		}

	public void wantMacInterrupt() {
		if (!initOk) return;
			setWantMacInterrupt();
		}

	public void requestMacOff() {
		if (!initOk) return;
			setRequestMacOff();
		}

	public void forceMacOff() {
		// eject all disks
		for (int i = 0; diskFile != null && i < diskFile.length; i++) {
			if (diskFile[i] != null) {
				sonyEject(i, false);
			}
		}

		// force off
		setForceMacOff();
	}
	
	public void resumeEmulation() {
		if (!initOk) return;
		if (!isPaused()) return;
		_resumeEmulation();
	}
	
	public void pauseEmulation() {
		if (!initOk) return;
		if (isPaused()) return;
		_pauseEmulation();
	}

	public static void setSpeed(int value) {
		if (!mIsInitialized) return;
		_setSpeed(value);
	}

	public static int getSpeed() {
		if (!mIsInitialized) return 0;
		return _getSpeed();
	}

	public boolean initScreen() {
		final int width = getScreenWidth();
		final int height = getScreenHeight();
		if (mOnInitScreenListener != null) {
			mOnInitScreenListener.onInitScreen(width, height);
		}
		return true;
	}

	public void updateScreen(final int top, final int left, final int bottom, final int right) {
		final int [] screenUpdate = getScreenUpdate();
		if (mOnUpdateScreenListener != null && screenUpdate != null) {
			mOnUpdateScreenListener.onUpdateScreen(screenUpdate, top, left, bottom, right);
		}
	}
	
	// mouse
	private native static void moveMouse(int dx, int dy);
	private native static void setMousePos(int x, int y);
	private native static void setMouseButton(boolean down);
	@SuppressWarnings("unused") private native static int getMouseX();
	@SuppressWarnings("unused") private native static int getMouseY();
	@SuppressWarnings("unused") private native static boolean getMouseButton();

	public void setMousePosition(int x, int y) {
		setMousePos(x, y);
	}

	public void setMouseBtn(Boolean down) {
		setMouseButton(down);
	}

	public void setMoveMouse(int dx, int dy) {
		moveMouse(dx, dy);
	}
	
	// keyboard
	private native static void setKeyDown(int scancode);
	private native static void setKeyUp(int scancode);

	public void keyDown(int scancode) {
		setKeyDown(scancode);
	}

	public void keyUp(int scancode) {
		setKeyUp(scancode);
	}
	
	// screen
	private native static int screenWidth();
	private native static int screenHeight();
	private native static int[] getScreenUpdate();

	public int getScreenWidth() {
		return screenWidth();
	}

	public int getScreenHeight() {
		return screenHeight();
	}
	
	// sound
	private native static void MySound_Start0();
	
	private static AudioTrack mAudioTrack;

    private static final int SOUND_SAMPLERATE = 22255;
	private static final int kLn2SoundBuffers = 4;
	private static final int kLnOneBuffLen = 9;
	private static final int kLnAllBuffLen = (kLn2SoundBuffers + kLnOneBuffLen);
	private static final int kAllBuffLen = (1 << kLnAllBuffLen);

	public boolean MySound_Init() {
        try {
			mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SOUND_SAMPLERATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_8BIT, kAllBuffLen, AudioTrack.MODE_STREAM);

			mAudioTrack.pause();
			return true;
	    } catch (Throwable tr) {
	    	Log.e(TAG, "MySound_Init() can't init sound.", tr);
	    	return false;
	    }
	}
	
	public int playSound(byte[] buf) {
		if (mAudioTrack == null) return -1;
		return mAudioTrack.write(buf, 0, buf.length);
	}

	public void MySound_Start () {
		if (mAudioTrack != null) {
			MySound_Start0();
			mAudioTrack.play();
		}
	}

	public void MySound_Stop () {
		if (mAudioTrack != null) {
			try {
				mAudioTrack.stop();
			} catch (Throwable tr) {
				Log.e(TAG, "MySound_Stop() can't stop sound.", tr);
			}
		}
	}

	public void MySound_UnInit () {
		if (mAudioTrack != null) {
			AudioTrack deletedTrack = mAudioTrack;
			mAudioTrack = null;
			try {
				deletedTrack.pause();
			} catch (IllegalStateException e) {
				Log.w(TAG, "MySound_UnInit() can't pause sound.", e);
			}
			deletedTrack.flush();
			deletedTrack.release();
		}
	}
	
	// disks
	private native static void notifyDiskInserted(int driveNum, boolean locked);
	private native static void notifyDiskEjected(int driveNum);
	public native static void notifyDiskCreated();
	private native static int getFirstFreeDisk();
	@SuppressWarnings("unused") private native static int getNumDrives();
	
	// disk driver callbacks
	public int sonyTransfer(boolean isWrite, ByteBuffer buf, int driveNum, int start, int length) {
		if (diskFile[driveNum] == null) return -1;
		try {
			byte[] bytes = new byte[length];
			if (isWrite)
			{
				buf.rewind();
				buf.get(bytes);
				diskFile[driveNum].seek(start);
				diskFile[driveNum].write(bytes);
				return length;
			}
			else
			{
				diskFile[driveNum].seek(start);
				int actualLength = diskFile[driveNum].read(bytes);
				buf.rewind();
				buf.put(bytes);
				//Log.v(TAG, "Read " + actualLength + " bytes from drive number " + driveNum + ".");
				return actualLength;
			}
		} catch (Exception x) {
			Log.e(TAG, "Failed to read " + length + " bytes from drive number " + driveNum + ".");
			return -1;
		}
	}
	
	public int sonyGetSize(int driveNum) {
		if (diskFile[driveNum] == null) return 0;
		try {
			return (int)diskFile[driveNum].length();
		} catch (Exception x) {
			Log.e(TAG, "Failed to get disk size from drive number " + driveNum + ".");
			return -1;
		}
	}

	public int sonyEject(int driveNum, boolean deleteit) {
		if (diskFile[driveNum] == null) return -1;
		int ret;
		try {
			diskFile[driveNum].close();
			ret = 0;
		} catch (Exception x) {
			diskCloseFailed = true;
			DiskAccessGate.GLOBAL.poison();
			Log.e(TAG, "Disk handle could not be closed safely", x);
			ret = -1;
		}

		String path = diskPath[driveNum];
		if (deleteit) {
			File file = new File(path);
			file.delete();
		}

		mOnDiskEventListener.onDiskEjected(diskPath[driveNum]);
		// Keep a failed handle for the final post-native cleanup attempt.
		if (ret == 0) diskFile[driveNum] = null;
		diskPath[driveNum] = null;
		numInsertedDisks--;
		
		notifyDiskEjected(driveNum);
		return ret;
	}

	/**
	 * Call only after initEmulation has returned, before releasing its process-wide
	 * lease. Native callbacks are finished; this final cleanup must not call JNI.
	 */
	public synchronized boolean closeDisksAfterEmulation() {
		emulationEnded = true;
		if (diskFile != null) {
			for (int i = 0; i < diskFile.length; i++) {
				RandomAccessFile file = diskFile[i];
				if (file != null) {
					try { file.close(); }
					catch (Exception failure) {
						diskCloseFailed = true;
						Log.e(TAG, "Final disk handle cleanup failed", failure);
					}
					diskFile[i] = null;
				}
			}
		}
		if (diskPath != null) java.util.Arrays.fill(diskPath, null);
		numInsertedDisks = 0;
		if (diskCloseFailed) DiskAccessGate.GLOBAL.poison();
		return !diskCloseFailed;
	}

	public String sonyGetName(int driveNum) {
		if (diskPath[driveNum] == null) return null;

		File file = new File(diskPath[driveNum]);
		return file.getName();
	}

	public int sonyMakeNewDisk(int size, String drivepath) {
		mOnDiskEventListener.onCreateDisk(size, drivepath);
		return 0;
	}

	public int makeNewDisk(int size, String path, String filename) {
		int ret = 0;

		if (!FileManager.getInstance().makeNewDisk(size, filename, path, null)) {
			ret = -1;
		} else {
			File disk = new File(path, filename);
			boolean isOk = insertDisk(disk);

			if (!isOk) {
				disk.delete();
			}
		}

		return ret;
	}
	
	public boolean isDiskInserted(File f) {
		if (numInsertedDisks == 0) return false;
		String path = f.getAbsolutePath();
		for(int i=0; i < diskFile.length; i++) {
			if (diskPath[i] == null) continue;
			if (path.equals(diskPath[i])) return true;
		}
		return false;
	}
	
	public synchronized boolean insertDisk(File f) {
		if (emulationEnded || diskFile == null || !DiskAccessGate.GLOBAL.isEmulationActive()) return false;
		try (DiskAccessGate.Lease ignored = DiskAccessGate.GLOBAL.beginHostIo()) {
			return insertDiskWithAccess(f);
		} catch (IOException busy) {
			Log.w(TAG, "Disk insertion is unavailable", busy);
			return false;
		}
	}

	private boolean insertDiskWithAccess(File f) {
		int driveNum = getFirstFreeDisk();
		// check for free drive
		if (driveNum == -1) {
			mOnAlertListener.onAlert(R.string.errTooManyDisks, false);
			return false;
		}
		
		// check for file
		if (!f.isFile()) return false;
		
		// check permissions
		String mode = "r";
		if (!f.canRead()) return false;
		if (f.canWrite()) mode = "rw";
		
		// open file
		try {
			diskFile[driveNum] = new RandomAccessFile(f, mode);
		} catch (Exception x) {
			diskFile[driveNum] = null;
			return false;
		}
		
		// insert disk
		notifyDiskInserted(driveNum, !f.canWrite());
		diskPath[driveNum] = f.getAbsolutePath();
		numInsertedDisks++;
		mOnDiskEventListener.onDiskInserted(f.getAbsolutePath());
		return true;
	}
	
	public boolean insertDisk(String path) {
		return insertDisk(new File(path));
	}

	public boolean sonyInsert2(String filename) {
        File f = FileManager.getInstance().getDisksFile(filename);
        return insertDisk(f);
    }
	
	public boolean hasDisksInserted() {
		return numInsertedDisks > 0;
	}

	public void initClipboardManager(ClipboardManager clipboardManager) {
		mClipboardManager = clipboardManager;
	}

	public void setClipboardText(String text) {
		if (mClipboardManager != null) {
			ClipData clip = ClipData.newPlainText("Mini vMac Clipboard", text);
			mClipboardManager.setPrimaryClip(clip);
		}
	}

	public String getClipboardText() {
		if (mClipboardManager != null && mClipboardManager.hasPrimaryClip()) {
			ClipData clipData = mClipboardManager.getPrimaryClip();
			if (clipData != null && clipData.getItemCount() > 0) {
				return clipData.getItemAt(0).coerceToText(null).toString();
			}
		}
		return null;
	}

	// warnings
	public void warnMsg(final String shortMsg, final String longMsg) {
		pauseEmulation();

		mOnAlertListener.onAlert(shortMsg, longMsg, false, (di, i) -> resumeEmulation());
	}

	public interface OnInitScreenListener {
		void onInitScreen(int width, int height);
	}

	public interface OnUpdateScreenListener {
		void onUpdateScreen(int[] update, int top, int left, int bottom, int right);
	}

	public interface OnDiskEventListener {
		void onDiskInserted(String path);
		void onDiskEjected(String path);
		void onCreateDisk(int size, String filename);
	}

	public interface OnAlertListener {
		void onAlert(String title, String msg, boolean end, DialogInterface.OnClickListener listener);
		void onAlert(@StringRes int msgResId, boolean end);
	}
}
