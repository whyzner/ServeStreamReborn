package net.sourceforge.servestream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.sourceforge.servestream.activity.MainActivity;
import net.sourceforge.servestream.activity.SettingsActivity;
import net.sourceforge.servestream.utils.AppearanceSettings;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;

/** The classic (late-90s desktop) theme really changes the screens, and switching back works. */
@RunWith(AndroidJUnit4.class)
public class ClassicThemeTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();

    private void setTheme(final String value) {
        PreferenceManager.getDefaultSharedPreferences(inst.getTargetContext()).edit()
                .putString(AppearanceSettings.KEY_THEME, value).commit();
        inst.runOnMainSync(new Runnable() {
            public void run() {
                AppearanceSettings.applyTheme(value);
            }
        });
    }

    private static int surface(Activity a) {
        TypedValue tv = new TypedValue();
        a.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true);
        return tv.data;
    }

    private void screenshot(String name) throws Exception {
        SystemClock.sleep(2500);
        ParcelFileDescriptor pfd = inst.getUiAutomation()
                .executeShellCommand("screencap -p /sdcard/Download/" + name + ".png");
        FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
        byte[] buf = new byte[1024];
        while (in.read(buf) > 0) { }
        in.close();
        pfd.close();
    }

    @After
    public void tearDown() {
        setTheme("system");
    }

    private Activity start(Class<?> cls) {
        android.content.Intent i = new android.content.Intent(inst.getTargetContext(), cls)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return inst.startActivitySync(i);
    }

    private void finish(final Activity a) {
        inst.runOnMainSync(new Runnable() {
            public void run() {
                a.finish();
            }
        });
        SystemClock.sleep(800);
    }

    @Test
    public void classicThemeIsApplied() throws Exception {
        setTheme("classic");
        final Activity main = start(MainActivity.class);
        inst.waitForIdleSync();
        final int[] colour = new int[1];
        final boolean[] gradientBar = new boolean[1];
        inst.runOnMainSync(new Runnable() {
            public void run() {
                colour[0] = surface(main);
                View bar = main.findViewById(R.id.toolbar);
                gradientBar[0] = bar != null
                        && bar.getBackground() instanceof android.graphics.drawable.GradientDrawable;
            }
        });
        screenshot("classic_main");
        finish(main);
        assertEquals("grey window colour", 0xFFC0C0C0, colour[0]);
        assertTrue("title bar is not the gradient", gradientBar[0]);

        Activity settings = start(SettingsActivity.class);
        inst.waitForIdleSync();
        screenshot("classic_settings");
        finish(settings);
    }

    @Test
    public void switchingBackRestoresTheNormalLook() {
        setTheme("classic");
        setTheme("light");
        final Activity main = start(MainActivity.class);
        inst.waitForIdleSync();
        final int[] colour = new int[1];
        inst.runOnMainSync(new Runnable() {
            public void run() {
                colour[0] = surface(main);
            }
        });
        finish(main);
        assertNotEquals(0xFFC0C0C0, colour[0]);
    }

    @Test
    public void platinumThemeIsApplied() throws Exception {
        setTheme("platinum");
        final Activity main = start(MainActivity.class);
        inst.waitForIdleSync();
        final int[] colour = new int[1];
        final boolean[] stripedBar = new boolean[1];
        inst.runOnMainSync(new Runnable() {
            public void run() {
                colour[0] = surface(main);
                View bar = main.findViewById(R.id.toolbar);
                stripedBar[0] = bar != null
                        && bar.getBackground() instanceof android.graphics.drawable.LayerDrawable;
            }
        });
        screenshot("platinum_main");
        finish(main);
        assertEquals("light grey window colour", 0xFFDDDDDD, colour[0]);
        assertTrue("title bar is not pinstriped", stripedBar[0]);

        Activity settings = start(SettingsActivity.class);
        inst.waitForIdleSync();
        screenshot("platinum_settings");
        finish(settings);
    }
}
