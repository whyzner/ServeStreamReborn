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

    public static int retroStyle() {
        return sRetroStyle;
    }

    private static int retroStyleFor(String value) {
        if ("classic".equals(value)) {
            return net.sourceforge.servestream.R.style.ThemeOverlay_ServeStream_Classic;
        }
        if ("platinum".equals(value)) {
            return net.sourceforge.servestream.R.style.ThemeOverlay_ServeStream_Platinum;
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
        if ("light".equals(value) || sRetroStyle != 0) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("dark".equals(value)) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode);
            return true;
        }
        return false;
    }

    /**
     * Puts the classic look on every screen as it is created, and recreates a
     * screen that comes back to the front after the look was switched.
     */
    public static void install(android.app.Application app) {
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

            @Override public void onActivityStarted(android.app.Activity a) { }
            @Override public void onActivityPaused(android.app.Activity a) { }
            @Override public void onActivityStopped(android.app.Activity a) { }
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
