import com.android.uiautomator.core.Configurator;
import com.android.uiautomator.core.UiDevice;
import com.android.uiautomator.core.UiObject;
import com.android.uiautomator.core.UiSelector;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;

/** Run only after a verified disposable snapshot/crash audit with auto-load on. */
public final class LaunchCancelCheck extends UiAutomatorTestCase {
    private static String command(String... args) throws Exception {
        Process process = Runtime.getRuntime().exec(args);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024]; int count;
        while ((count = process.getInputStream().read(buffer)) != -1) out.write(buffer, 0, count);
        process.waitFor();
        return out.toString("UTF-8");
    }
    public void testCancelBeforeNativeApplication() throws Exception {
        String pkg = "com.hunterdavis.poolradmacmaps.ii";
        UiDevice device = getUiDevice();
        assertTrue("App must be stopped before this explicit launch test",
                command("pidof", pkg).trim().isEmpty());
        Configurator.getInstance().setWaitForIdleTimeout(0).setWaitForSelectorTimeout(0);
        command("am", "start", "-n", pkg + "/name.osher.gil.minivmac.MiniVMac");
        UiObject cancel = new UiObject(new UiSelector().packageName(pkg).text("START NORMALLY"));
        assertTrue("Startup cancellation control did not appear", cancel.waitForExists(15000));
        System.out.println("Found real startup button at " + cancel.getBounds());
        boolean clickReported = cancel.click();
        assertTrue("Startup dialog did not close", cancel.waitUntilGone(5000));
        String pid = command("pidof", pkg).trim();
        String logs = command("logcat", "-d", "--pid=" + pid, "-v", "time", "-s", "PoolRad.SaveState");
        assertTrue("The app did not acknowledge cancellation", logs.contains("Automatic load skipped."));
        assertFalse("A cancelled launch applied a snapshot", logs.contains("Launch restore completed:"));
        System.out.println("PASS: app acknowledged Start normally; no restore applied (click event result "
                + clickReported + ")");
    }
}
