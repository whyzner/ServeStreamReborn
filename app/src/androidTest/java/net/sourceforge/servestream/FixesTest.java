package net.sourceforge.servestream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import net.sourceforge.servestream.utils.AppearanceSettings;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.util.Collection;

@RunWith(AndroidJUnit4.class)
public class FixesTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();

    private Activity resumed() {
        final Activity[] out = new Activity[1];
        inst.runOnMainSync(new Runnable() {
            public void run() {
                Collection<Activity> c = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
                out[0] = c.isEmpty() ? null : c.iterator().next();
            }
        });
        return out[0];
    }

    private void shell(String cmd) throws Exception {
        ParcelFileDescriptor pfd = inst.getUiAutomation().executeShellCommand(cmd);
        FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
        byte[] b = new byte[1024];
        while (in.read(b) > 0) { }
        in.close();
        pfd.close();
    }

    private boolean enabled(Context c, String alias) {
        int s = c.getPackageManager().getComponentEnabledSetting(
                new ComponentName(c.getPackageName(), c.getPackageName() + ".Launcher" + alias));
        return s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || (s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && "Default".equals(alias));
    }

    /** Changing the theme while the app was opened from its home screen icon must not close it. */
    @Test
    public void themeChangeDoesNotCloseTheApp() throws Exception {
        final Context c = inst.getTargetContext();
        AppearanceSettings.applyIcon(c, "light");
        // open the app the way the home screen does: through the launcher icon
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(c.getPackageName(), c.getPackageName() + ".LauncherDefault"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        c.startActivity(i);
        SystemClock.sleep(4000);
        Activity before = resumed();
        assertNotNull("app did not open", before);

        inst.runOnMainSync(new Runnable() {
            public void run() {
                AppearanceSettings.requestIcon(c, "dark");
            }
        });
        SystemClock.sleep(4000);
        Activity after = resumed();
        assertNotNull("the app was closed by the theme change", after);
        assertFalse(after.isFinishing());
        assertTrue("icon must wait while the app is on screen", AppearanceSettings.iconSwitchPending());
        assertTrue(enabled(c, "Default"));

        // leaving the app lets the icon change
        shell("input keyevent KEYCODE_HOME");
        SystemClock.sleep(5000);
        assertFalse(AppearanceSettings.iconSwitchPending());
        assertTrue("dark icon not switched on", enabled(c, "Dark"));
        assertFalse(enabled(c, "Default"));

        AppearanceSettings.applyIcon(c, "light");
        assertTrue(enabled(c, "Default"));
    }

    /** Bluetooth options without the "Nearby devices" permission: asks for it instead of crashing. */
    @Test
    public void bluetoothOptionsWithoutPermission() throws Exception {
        Context c = inst.getTargetContext();
        Intent i = new Intent(c, net.sourceforge.servestream.activity.BluetoothOptionsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity a = inst.startActivitySync(i);
        SystemClock.sleep(3000);
        assertFalse("bluetooth options closed", a.isFinishing() || a.isDestroyed());
        final Activity fa = a;
        inst.runOnMainSync(new Runnable() {
            public void run() {
                fa.finish();
            }
        });
    }
}
