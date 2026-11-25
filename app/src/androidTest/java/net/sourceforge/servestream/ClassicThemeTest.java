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

    /** Plays a real stream, waits for the player screen and checks its themed parts. */
    private void openPlayerAndCapture(String shot) throws Exception {
        // player screen dressed as a late-90s desktop player: metal body + LCD strip
        // open a real stream the way a link does; the app then shows the player
        android.content.Intent view = new android.content.Intent(android.content.Intent.ACTION_VIEW)
                .setDataAndType(android.net.Uri.parse("https://stream1.rcast.net/73730"), "audio/mpeg")
                .setClass(inst.getTargetContext(), MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        inst.getTargetContext().startActivity(view);
        Activity found = null;
        long end = SystemClock.elapsedRealtime() + 40000;
        while (SystemClock.elapsedRealtime() < end && found == null) {
            SystemClock.sleep(1000);
            final Activity[] r = new Activity[1];
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    for (Activity a : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                        if (a instanceof net.sourceforge.servestream.activity.MediaPlayerActivity) r[0] = a;
                    }
                }
            });
            found = r[0];
        }
        assertTrue("player screen did not open", found != null);
        SystemClock.sleep(6000);
        final Activity player = found;
        final boolean[] dressed = new boolean[2];
        inst.runOnMainSync(new Runnable() {
            public void run() {
                View strip = player.findViewById(R.id.time_strip);
                View info = player.findViewById(R.id.player_info);
                View lcd = player.findViewById(R.id.lcd_panel);
                // platinum dresses the time strip, classic the information pane,
                // real shows its status display (with the time) and labelled fields
                boolean realOk = lcd != null && lcd.getVisibility() == View.VISIBLE
                        && player.findViewById(R.id.stop_button) != null
                        && ((android.widget.TextView) player.findViewById(R.id.lcd_time)).getText().length() > 0
                        && ((android.widget.TextView) player.findViewById(R.id.lcd_state)).getText().length() > 0
                        && player.findViewById(R.id.player_fields) != null
                        && player.findViewById(R.id.player_fields).getVisibility() == View.VISIBLE
                        && ((android.widget.TextView) player.findViewById(R.id.field_title)).getText().length() > 0;
                dressed[0] = (strip != null && strip.getBackground() != null)
                        || (info != null && info.getBackground() != null)
                        || realOk;
                View play = player.findViewById(R.id.play_pause_button);
                dressed[1] = play != null && play.getBackground() instanceof android.graphics.drawable.StateListDrawable;
            }
        });
        screenshot(shot);
        finish(player);
        assertTrue("player look missing (" + shot + ")", dressed[0]);
        assertTrue("player buttons not themed (" + shot + ")", dressed[1]);
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

        openPlayerAndCapture("classic_player");
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

        openPlayerAndCapture("platinum_player");
    }

    /** The home screen shows exactly one icon, the one for the chosen theme. */
    @Test
    public void homeScreenIconFollowsTheTheme() {
        android.content.Context c = inst.getTargetContext();
        android.content.pm.PackageManager pm = c.getPackageManager();
        String[][] cases = {{"dark", "Dark"}, {"classic", "Classic"}, {"platinum", "Platinum"},
                {"light", "Default"}, {"metro_dark", "Metro"}, {"metro_light", "Metro"}, {"real", "Real"},
                {"system", "Default"}};
        for (String[] k : cases) {
            AppearanceSettings.applyIcon(c, k[0]);
            android.content.Intent launcher = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(c.getPackageName());
            java.util.List<android.content.pm.ResolveInfo> found = pm.queryIntentActivities(launcher, 0);
            assertEquals("launcher icons for " + k[0], 1, found.size());
            assertEquals(c.getPackageName() + ".Launcher" + k[1], found.get(0).activityInfo.name);
            assertTrue(found.get(0).loadIcon(pm) != null);
        }
    }

    private void checkMetro(String value, int surfaceColour, String shotPrefix) throws Exception {
        setTheme(value);
        final Activity main = start(MainActivity.class);
        inst.waitForIdleSync();
        final int[] colour = new int[1];
        inst.runOnMainSync(new Runnable() {
            public void run() {
                colour[0] = surface(main);
            }
        });
        screenshot(shotPrefix + "_main");
        finish(main);
        assertEquals(value + " window colour", surfaceColour, colour[0]);
        Activity settings = start(SettingsActivity.class);
        inst.waitForIdleSync();
        screenshot(shotPrefix + "_settings");
        finish(settings);
    }

    @Test
    public void metroDarkThemeIsApplied() throws Exception {
        checkMetro("metro_dark", 0xFF000000, "metro_dark");
    }

    @Test
    public void metroLightThemeIsApplied() throws Exception {
        checkMetro("metro_light", 0xFFFFFFFF, "metro_light");
    }

    @Test
    public void realThemeIsApplied() throws Exception {
        setTheme("real");
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
        screenshot("real_main");
        finish(main);
        assertEquals("dark G2 window colour", 0xFF3A4350, colour[0]);
        assertTrue("title bar is not the gradient", gradientBar[0]);

        Activity settings = start(SettingsActivity.class);
        inst.waitForIdleSync();
        screenshot("real_settings");
        finish(settings);

        openPlayerAndCapture("real_player");
    }

    /** Unfolded foldable / tablet: the real player keeps the picture above the fields. */
    @Test
    public void realPlayerOnUnfoldedScreen() throws Exception {
        setTheme("real");
        inst.getUiAutomation().executeShellCommand("wm size 1840x1840").close();
        inst.getUiAutomation().executeShellCommand("wm density 420").close();
        SystemClock.sleep(3000);
        try {
            android.content.Intent view = new android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(android.net.Uri.parse("https://stream1.rcast.net/73730"), "audio/mpeg")
                    .setClass(inst.getTargetContext(), MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            inst.getTargetContext().startActivity(view);
            Activity found = null;
            long end = SystemClock.elapsedRealtime() + 40000;
            while (SystemClock.elapsedRealtime() < end && found == null) {
                SystemClock.sleep(1000);
                final Activity[] r = new Activity[1];
                inst.runOnMainSync(new Runnable() {
                    public void run() {
                        for (Activity a : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                            if (a instanceof net.sourceforge.servestream.activity.MediaPlayerActivity) r[0] = a;
                        }
                    }
                });
                found = r[0];
            }
            assertTrue("player screen did not open", found != null);
            SystemClock.sleep(5000);
            final Activity player = found;
            final int[] pos = new int[4];
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    int[] xy = new int[2];
                    View art = player.findViewById(R.id.album_art);
                    View field = player.findViewById(R.id.field_title);
                    art.getLocationOnScreen(xy);
                    pos[0] = xy[1] + art.getHeight();   // bottom of the picture
                    field.getLocationOnScreen(xy);
                    pos[1] = xy[1];                     // top of the title field
                    pos[2] = player.findViewById(R.id.station_list_pane) != null ? 1 : 0;
                    pos[3] = surface(player);
                }
            });
            screenshot("real_player_unfolded");
            finish(player);
            assertTrue("no station list beside the player", pos[2] == 1);
            assertEquals("player keeps the grey 9x window", 0xFFC0C0C0, pos[3]);
            assertTrue("fields are not under the picture (" + pos[0] + " / " + pos[1] + ")", pos[1] >= pos[0]);
        } finally {
            inst.getUiAutomation().executeShellCommand("wm size reset").close();
            inst.getUiAutomation().executeShellCommand("wm density reset").close();
            SystemClock.sleep(2000);
        }
    }
}
