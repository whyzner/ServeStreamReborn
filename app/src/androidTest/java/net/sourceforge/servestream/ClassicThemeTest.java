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
        final Activity settings = start(SettingsActivity.class);
        inst.waitForIdleSync();
        SystemClock.sleep(1000);
        final int[] found = new int[2];
        inst.runOnMainSync(new Runnable() {
            public void run() {
                countMetroWidgets(settings.getWindow().getDecorView(), found);
            }
        });
        screenshot(shotPrefix + "_settings");
        finish(settings);
        assertTrue("settings have no on/off switches", found[0] > 0);
        assertTrue("theme / language have no drop-down box", found[1] > 0);
    }

    /** [0] = switches, [1] = drop-down boxes with text. */
    private static void countMetroWidgets(View v, int[] found) {
        if (v instanceof com.google.android.material.materialswitch.MaterialSwitch) found[0]++;
        if (v.getId() == R.id.metro_value && ((android.widget.TextView) v).getText().length() > 0) found[1]++;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) countMetroWidgets(g.getChildAt(i), found);
        }
    }

    @Test
    public void metroDarkThemeIsApplied() throws Exception {
        checkMetro("metro_dark", 0xFF0D1117, "metro_dark");
    }

    @Test
    public void metroLightThemeIsApplied() throws Exception {
        checkMetro("metro_light", 0xFFFAFAFA, "metro_light");
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
        assertEquals("darker grey window colour", 0xFFB4B4B4, colour[0]);
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
            assertEquals("player has the same grey as the other screens", 0xFFB4B4B4, pos[3]);
            assertTrue("fields are not under the picture (" + pos[0] + " / " + pos[1] + ")", pos[1] >= pos[0]);
        } finally {
            inst.getUiAutomation().executeShellCommand("wm size reset").close();
            inst.getUiAutomation().executeShellCommand("wm density reset").close();
            SystemClock.sleep(2000);
        }
    }


    /** Metro station list: All / Favourites / Recent tabs, star, backup keeps the star. */
    @Test
    public void metroStationTabsAndFavorites() throws Exception {
        for (final String theme : new String[] {"metro_dark", "metro_light"}) {
            setTheme(theme);
            final Activity main = start(MainActivity.class);
            inst.waitForIdleSync();
            SystemClock.sleep(1000);
            inst.waitForIdleSync();
            SystemClock.sleep(2500);
            final String[] err = new String[1];
            final long[] starred = new long[] {-1};
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    View tabs = main.findViewById(R.id.metro_tabs);
                    android.widget.ListView list = main.findViewById(android.R.id.list);
                    if (tabs == null || !tabs.isShown()) { err[0] = "tabs not shown"; return; }
                    if (list.getChildCount() == 0) { err[0] = "no stations listed"; return; }
                    View star = list.getChildAt(0).findViewById(R.id.favorite_button);
                    if (star == null || !star.isShown()) { err[0] = "no star on the row"; return; }
                    net.sourceforge.servestream.bean.UriBean first =
                            (net.sourceforge.servestream.bean.UriBean) list.getItemAtPosition(0);
                    if (!first.isFavorite()) star.performClick();
                    starred[0] = first.getId();
                }
            });
            assertTrue(theme + ": " + err[0], err[0] == null);
            SystemClock.sleep(500);
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    ((com.google.android.material.tabs.TabLayout) main.findViewById(R.id.metro_tabs)).getTabAt(1).select();
                }
            });
            inst.waitForIdleSync();
            SystemClock.sleep(800);
            final int[] counts = new int[3];
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    android.widget.ListView list = main.findViewById(android.R.id.list);
                    for (int i = 0; i < list.getCount(); i++) {
                        net.sourceforge.servestream.bean.UriBean u =
                                (net.sourceforge.servestream.bean.UriBean) list.getItemAtPosition(i);
                        counts[0]++;
                        if (!u.isFavorite()) counts[1]++;
                        if (u.getId() == starred[0]) counts[2]++;
                    }
                }
            });
            screenshot(theme + "_favorites");
            assertTrue(theme + ": starred station missing from favourites", counts[2] == 1);
            assertEquals(theme + ": non-favourite in favourites tab", 0, counts[1]);
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    ((com.google.android.material.tabs.TabLayout) main.findViewById(R.id.metro_tabs)).getTabAt(2).select();
                }
            });
            inst.waitForIdleSync();
            SystemClock.sleep(800);
            final boolean[] recentOk = new boolean[] {true};
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    android.widget.ListView list = main.findViewById(android.R.id.list);
                    long last = Long.MAX_VALUE;
                    for (int i = 0; i < list.getCount(); i++) {
                        net.sourceforge.servestream.bean.UriBean u =
                                (net.sourceforge.servestream.bean.UriBean) list.getItemAtPosition(i);
                        if (u.getLastConnect() <= 0 || u.getLastConnect() > last) recentOk[0] = false;
                        last = u.getLastConnect();
                    }
                    ((com.google.android.material.tabs.TabLayout) main.findViewById(R.id.metro_tabs)).getTabAt(0).select();
                }
            });
            assertTrue(theme + ": recent tab not played-only / newest first", recentOk[0]);
            inst.waitForIdleSync();
            SystemClock.sleep(800);
            screenshot(theme + "_list");

            // the star is saved and goes into the backup
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            net.sourceforge.servestream.utils.BackupUtils.writeBackup(inst.getTargetContext(), out);
            String json = out.toString("UTF-8");
            finish(main);
            assertTrue(theme + ": backup has no favourite", json.contains("\"favorite\":true"));

            net.sourceforge.servestream.database.StreamDatabase db =
                    new net.sourceforge.servestream.database.StreamDatabase(inst.getTargetContext());
            try {
                net.sourceforge.servestream.bean.UriBean u = db.findUri((int) starred[0]);
                assertTrue(theme + ": star not saved", u != null && u.isFavorite());
                db.setFavorite(u, false);
            } finally {
                db.close();
            }
        }
    }


    private Activity resumed(final Class<?> cls, long waitMs) {
        long end = SystemClock.elapsedRealtime() + waitMs;
        while (SystemClock.elapsedRealtime() < end) {
            final Activity[] r = new Activity[1];
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    for (Activity a : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                        if (cls.isInstance(a)) r[0] = a;
                    }
                }
            });
            if (r[0] != null) return r[0];
            SystemClock.sleep(500);
        }
        return null;
    }

    private static boolean shown(Activity a, int id) {
        View v = a.findViewById(id);
        return v != null && v.isShown() && v.getWidth() > 0;
    }



    private static int section(final Activity a) {
        return ((MainActivity) a).metroSection();
    }

    private void showSection(final Activity a, final int section) {
        inst.runOnMainSync(new Runnable() {
            public void run() {
                ((MainActivity) a).metroShow(section);
            }
        });
        inst.waitForIdleSync();
        SystemClock.sleep(900);
    }

    /** Metro panorama: big title, section titles, each section, back returns to stations then closes. */
    @Test
    public void metroPanorama() throws Exception {
        for (final String theme : new String[] {"metro_dark", "metro_light"}) {
            setTheme(theme);
            final Activity main = start(MainActivity.class);
            inst.waitForIdleSync();
            SystemClock.sleep(2000);
            final String[] err = new String[1];
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    if (!shown(main, R.id.metro_title)) err[0] = "no big title";
                    else if (((android.view.ViewGroup) main.findViewById(R.id.metro_pivot)).getChildCount() != 5) err[0] = "not 5 section titles";
                    else if (!shown(main, android.R.id.list) && !shown(main, android.R.id.empty)) err[0] = "station list not shown first";
                    else if (!shown(main, R.id.metro_appbar)) err[0] = "no round-icon bar";
                    else if (main.findViewById(R.id.toolbar) != null && main.findViewById(R.id.toolbar).isShown()) err[0] = "old tool bar shown";
                }
            });
            screenshot(theme + "_pano_stations");
            assertTrue(theme + ": " + err[0], err[0] == null);
            assertEquals(theme + ": first section", 0, section(main));

            showSection(main, 1);
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    err[0] = shown(main, R.id.metro_np_play) && shown(main, R.id.metro_np_title) ? null : "now playing not shown";
                }
            });
            screenshot(theme + "_pano_now");
            assertTrue(theme + ": " + err[0], err[0] == null);

            showSection(main, 2);
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    err[0] = shown(main, R.id.metro_single) && shown(main, R.id.metro_row_title) ? null : "explore rows not shown";
                }
            });
            screenshot(theme + "_pano_explore");
            assertTrue(theme + ": " + err[0], err[0] == null);

            showSection(main, 3);
            screenshot(theme + "_pano_alarms");
            showSection(main, 4);
            SystemClock.sleep(800);
            final int[] widgets = new int[2];
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    countMetroWidgets(main.findViewById(R.id.metro_single), widgets);
                }
            });
            screenshot(theme + "_pano_settings");
            assertEquals(theme + ": settings section", 4, section(main));
            // the settings themselves, not a list of groups that open another window
            assertTrue(theme + ": settings not shown in the panorama", widgets[0] > 0 && widgets[1] > 0);
            final boolean[] bar = new boolean[1];
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    bar[0] = shown(main, R.id.metro_appbar);
                }
            });
            assertTrue(theme + ": round-icon bar still shown under settings", !bar[0]);

            // back: to stations, then the screen closes (predictive back dispatcher)
            inst.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            SystemClock.sleep(1000);
            assertEquals(theme + ": back did not return to stations", 0, section(main));
            inst.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            SystemClock.sleep(1500);
            assertTrue(theme + ": back from stations did not close", main.isFinishing() || main.isDestroyed());
        }
    }

    /** Metro: playing a station shows the now playing section (LIVE for radio, pause button). */
    @Test
    public void metroNowPlaying() throws Exception {
        for (final String theme : new String[] {"metro_dark", "metro_light"}) {
            setTheme(theme);
            android.content.Intent view = new android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(android.net.Uri.parse("https://stream1.rcast.net/73730"), "audio/mpeg")
                    .setClass(inst.getTargetContext(), MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            inst.getTargetContext().startActivity(view);
            final Activity main = resumed(MainActivity.class, 15000);
            assertTrue(theme + ": main screen did not open", main != null);
            long end = SystemClock.elapsedRealtime() + 40000;
            while (SystemClock.elapsedRealtime() < end && section(main) != 1) SystemClock.sleep(500);
            assertEquals(theme + ": playing did not show now playing", 1, section(main));
            SystemClock.sleep(6000);
            final String[] err = new String[1];
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    android.widget.TextView title = main.findViewById(R.id.metro_np_title);
                    View live = main.findViewById(R.id.metro_np_live);
                    View play = main.findViewById(R.id.metro_np_play);
                    if (title == null || title.getText().length() == 0) err[0] = "no title";
                    else if (live == null || !live.isShown()) err[0] = "no LIVE for a radio stream";
                    else if (!main.getString(R.string.pause).contentEquals(play.getContentDescription())) err[0] = "button is not pause while playing";
                    else if (!shown(main, R.id.metro_np_queue)) err[0] = "no queue button";
                }
            });
            screenshot(theme + "_pano_playing");
            finish(main);
            assertTrue(theme + ": " + err[0], err[0] == null);
        }
    }

    /** Metro on wide windows: list and now playing side by side, other sections full width. */
    @Test
    public void metroWideTwoPanes() throws Exception {
        setTheme("metro_dark");
        try {
            inst.getUiAutomation().executeShellCommand("wm size 1840x1840").close();
            inst.getUiAutomation().executeShellCommand("wm density 420").close();
            SystemClock.sleep(3000);
            start(MainActivity.class);
            SystemClock.sleep(3000);
            final Activity m = resumed(MainActivity.class, 10000);
            final boolean[] r = new boolean[3];
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    r[0] = shown(m, R.id.metro_left) && shown(m, R.id.metro_right);
                    r[1] = shown(m, R.id.metro_np_play);
                }
            });
            screenshot("metro_dark_wide_main");
            assertTrue("wide: list and now playing not side by side", r[0]);
            assertTrue("wide: now playing controls missing", r[1]);
            showSection(m, 2);
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    r[2] = shown(m, R.id.metro_single) && !shown(m, R.id.metro_left);
                }
            });
            screenshot("metro_dark_wide_explore");
            finish(m);
            assertTrue("wide: explore not full width", r[2]);
        } finally {
            inst.getUiAutomation().executeShellCommand("wm size reset").close();
            inst.getUiAutomation().executeShellCommand("wm density reset").close();
            SystemClock.sleep(2000);
        }
    }

    /** Metro: swipe between sections; section and tab kept when unfolding. */
    @Test
    public void metroSwipeAndFold() throws Exception {
        setTheme("metro_dark");
        start(MainActivity.class);
        SystemClock.sleep(2000);
        Activity main = resumed(MainActivity.class, 10000);
        android.graphics.Point size = new android.graphics.Point();
        main.getWindowManager().getDefaultDisplay().getRealSize(size);
        int y = size.y * 55 / 100;
        String left = "input swipe " + (size.x * 85 / 100) + " " + y + " " + (size.x * 15 / 100) + " " + y + " 150";
        String right = "input swipe " + (size.x * 15 / 100) + " " + y + " " + (size.x * 85 / 100) + " " + y + " 150";
        inst.getUiAutomation().executeShellCommand(left).close();
        SystemClock.sleep(1500);
        assertEquals("swipe left did not go to now playing", 1, section(main));
        inst.getUiAutomation().executeShellCommand(left).close();
        SystemClock.sleep(1500);
        assertEquals("swipe left did not go to explore", 2, section(main));
        inst.getUiAutomation().executeShellCommand(right).close();
        SystemClock.sleep(1500);
        assertEquals("swipe right did not go back to now playing", 1, section(main));
        inst.getUiAutomation().executeShellCommand(right).close();
        SystemClock.sleep(1500);
        assertEquals("swipe right did not go back to stations", 0, section(main));

        final Activity list = main;
        inst.runOnMainSync(new Runnable() {
            public void run() {
                ((com.google.android.material.tabs.TabLayout) list.findViewById(R.id.metro_tabs)).getTabAt(1).select();
            }
        });
        showSection(main, 2);
        try {
            inst.getUiAutomation().executeShellCommand("wm size 1840x1840").close();
            inst.getUiAutomation().executeShellCommand("wm density 420").close();
            SystemClock.sleep(4000);
            final Activity wide = resumed(MainActivity.class, 10000);
            final int[] tab = new int[] {-1};
            showSection(wide, 0);
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    com.google.android.material.tabs.TabLayout t = wide.findViewById(R.id.metro_tabs);
                    if (t != null) tab[0] = t.getSelectedTabPosition();
                }
            });
            assertEquals("tab lost when unfolding", 1, tab[0]);
        } finally {
            inst.getUiAutomation().executeShellCommand("wm size reset").close();
            inst.getUiAutomation().executeShellCommand("wm density reset").close();
            SystemClock.sleep(3000);
        }
        finish(resumed(MainActivity.class, 5000));
    }

    /** The settings switches and drop-down boxes are in every theme, not only Metro. */
    @Test
    public void settingsWidgetsInEveryTheme() throws Exception {
        for (final String theme : new String[] {"light", "dark", "classic", "platinum", "real"}) {
            setTheme(theme);
            final Activity settings = start(SettingsActivity.class);
            inst.waitForIdleSync();
            SystemClock.sleep(1200);
            final int[] found = new int[2];
            inst.runOnMainSync(new Runnable() {
                public void run() {
                    countMetroWidgets(settings.getWindow().getDecorView(), found);
                }
            });
            screenshot("settings_" + theme);
            finish(settings);
            assertTrue(theme + ": no on/off switches", found[0] > 0);
            assertTrue(theme + ": theme / language have no drop-down box", found[1] > 0);
        }
    }
}
