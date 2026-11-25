package net.sourceforge.servestream.utils;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** Theme (light/dark/system) and in-app language settings. */
public final class AppearanceSettings {
    public static final String KEY_THEME = "ui_theme";
    public static final String KEY_LANGUAGE = "ui_language";

    private AppearanceSettings() {
    }

    /** Overlay style of the selected retro look, or 0 for the normal Material look. */
    private static volatile int sRetroStyle;

    /** True while a retro look ("classic" 90s PC or "platinum" 90s Mac) is selected. */
    public static boolean isRetro() {
        return sRetroStyle != 0;
    }

    /** Metro Dark or Metro Light. */
    public static boolean isMetro() {
        return sRetroStyle == net.sourceforge.servestream.R.style.ThemeOverlay_ServeStream_MetroDark
                || sRetroStyle == net.sourceforge.servestream.R.style.ThemeOverlay_ServeStream_MetroLight;
    }

    public static int retroStyle() {
        return sRetroStyle;
    }

    /** The "real" theme has its own player screen layout. */
    public static boolean isRealTheme() {
        return sRetroStyle == net.sourceforge.servestream.R.style.ThemeOverlay_ServeStream_Real;
    }

    private static int retroStyleFor(String value) {
        if ("classic".equals(value)) {
            return net.sourceforge.servestream.R.style.ThemeOverlay_ServeStream_Classic;
        }
        if ("platinum".equals(value)) {
            return net.sourceforge.servestream.R.style.ThemeOverlay_ServeStream_Platinum;
        }
        if ("metro_dark".equals(value)) {
            return net.sourceforge.servestream.R.style.ThemeOverlay_ServeStream_MetroDark;
        }
        if ("metro_light".equals(value)) {
            return net.sourceforge.servestream.R.style.ThemeOverlay_ServeStream_MetroLight;
        }
        if ("real".equals(value)) {
            return net.sourceforge.servestream.R.style.ThemeOverlay_ServeStream_Real;
        }
        return 0;
    }

    /**
     * Applies the theme choice. Returns true when the day/night mode changed,
     * in which case AppCompat recreates the open screens by itself.
     */
    public static boolean applyTheme(String value) {
        sRetroStyle = retroStyleFor(value);
        int mode;
        if ("dark".equals(value) || "metro_dark".equals(value)) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        } else if ("light".equals(value) || sRetroStyle != 0) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode);
            return true;
        }
        return false;
    }

    private static final String[] LAUNCHERS = {"Default", "Dark", "Classic", "Platinum", "Metro", "Real"};

    private static volatile String sPendingIcon;
    private static int sStartedScreens;
    private static final android.os.Handler sMain = new android.os.Handler(android.os.Looper.getMainLooper());

    /**
     * Asks for the icon of this theme. Android closes screens that were opened
     * from an icon when that icon is switched off, so the switch waits until
     * none of the app's screens is on display.
     */
    public static void requestIcon(final android.content.Context c, String value) {
        sPendingIcon = value;
        final android.content.Context app = c.getApplicationContext();
        sMain.post(new Runnable() {
            public void run() {
                maybeApplyPendingIcon(app);
            }
        });
    }

    private static void maybeApplyPendingIcon(final android.content.Context app) {
        if (sPendingIcon == null || sStartedScreens > 0) {
            return;
        }
        // let a screen that is just being replaced (rotation, theme redraw) come back first
        sMain.postDelayed(new Runnable() {
            public void run() {
                final String value = sPendingIcon;
                if (value == null || sStartedScreens > 0) {
                    return;
                }
                sPendingIcon = null;
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            applyIcon(app, value);
                        } catch (RuntimeException e) {
                            android.util.Log.w("AppearanceSettings", "icon switch failed", e);
                        }
                    }
                }, "IconSwitch").start();
            }
        }, 1500);
    }

    /** For tests: is an icon switch still waiting for the app to leave the screen? */
    public static boolean iconSwitchPending() {
        return sPendingIcon != null;
    }

    /**
     * Shows the home screen icon that goes with the theme: dark, classic (90s PC),
     * platinum (90s Mac), or the normal one for light and system.
     */
    public static void applyIcon(android.content.Context c, String value) {
        String wanted = "Default";
        if ("dark".equals(value)) wanted = "Dark";
        else if ("classic".equals(value)) wanted = "Classic";
        else if ("platinum".equals(value)) wanted = "Platinum";
        else if ("metro_dark".equals(value) || "metro_light".equals(value)) wanted = "Metro";
        else if ("real".equals(value)) wanted = "Real";
        android.content.pm.PackageManager pm = c.getPackageManager();
        // enable the new icon first so the app never has no launcher entry
        for (int pass = 0; pass < 2; pass++) {
            for (String name : LAUNCHERS) {
                boolean on = name.equals(wanted);
                if ((pass == 0) != on) continue;
                android.content.ComponentName cn = new android.content.ComponentName(
                        c.getPackageName(), c.getPackageName() + ".Launcher" + name);
                int want = on ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                int now = pm.getComponentEnabledSetting(cn);
                boolean isOn = now == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        || (now == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && "Default".equals(name));
                if (isOn != on) {
                    pm.setComponentEnabledSetting(cn, want, android.content.pm.PackageManager.DONT_KILL_APP);
                }
            }
        }
    }

    /**
     * Puts the classic look on every screen as it is created, and recreates a
     * screen that comes back to the front after the look was switched.
     */
    public static void install(final android.app.Application app) {
        app.registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            private final java.util.WeakHashMap<android.app.Activity, Integer> applied =
                    new java.util.WeakHashMap<android.app.Activity, Integer>();

            private void style(android.app.Activity a) {
                if (!(a instanceof androidx.appcompat.app.AppCompatActivity)) {
                    return;
                }
                int style = sRetroStyle;
                if (style != 0) {
                    a.getTheme().applyStyle(style, true);
                }
                applied.put(a, style);
            }

            @Override
            public void onActivityPreCreated(android.app.Activity a, android.os.Bundle b) {
                style(a);
            }

            @Override
            public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {
                if (android.os.Build.VERSION.SDK_INT < 29) {
                    style(a);
                }
            }

            @Override
            public void onActivityResumed(android.app.Activity a) {
                Integer was = applied.get(a);
                if (was != null && was != sRetroStyle) {
                    a.recreate();
                }
            }

            @Override public void onActivityStarted(android.app.Activity a) {
                sStartedScreens++;
            }
            @Override public void onActivityPaused(android.app.Activity a) { }
            @Override public void onActivityStopped(android.app.Activity a) {
                if (sStartedScreens > 0) sStartedScreens--;
                maybeApplyPendingIcon(app);
            }
            @Override public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) { }
            @Override public void onActivityDestroyed(android.app.Activity a) {
                applied.remove(a);
            }
        });
    }

    public static void applyLanguage(String value) {
        LocaleListCompat locales = (value == null || "system".equals(value))
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(value);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    private static final String[] SUPPORTED = {
            "en", "ko", "ko-KP", "ja", "zh-CN", "zh-TW", "ru", "fr", "de", "es", "pl"};

    /** Value for the language list: "system" or one of the supported tags. */
    public static String currentLanguage() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty() || locales.get(0) == null) {
            return "system";
        }
        java.util.Locale l = locales.get(0);
        String tag = l.toLanguageTag();
        for (String s : SUPPORTED) {
            if (s.equalsIgnoreCase(tag)) return s;
        }
        if ("zh".equals(l.getLanguage())) {
            String script = l.getScript();
            String country = l.getCountry();
            boolean trad = "Hant".equals(script) || "TW".equals(country) || "HK".equals(country) || "MO".equals(country);
            return trad ? "zh-TW" : "zh-CN";
        }
        for (String s : SUPPORTED) {
            if (s.equals(l.getLanguage())) return s;
        }
        return "system";
    }
}
