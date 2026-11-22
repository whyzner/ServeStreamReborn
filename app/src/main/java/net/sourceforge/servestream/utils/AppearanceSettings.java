package net.sourceforge.servestream.utils;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** Theme (light/dark/system) and in-app language settings. */
public final class AppearanceSettings {
    public static final String KEY_THEME = "ui_theme";
    public static final String KEY_LANGUAGE = "ui_language";

    private AppearanceSettings() {
    }

    public static void applyTheme(String value) {
        int mode;
        if ("light".equals(value)) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("dark".equals(value)) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
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
