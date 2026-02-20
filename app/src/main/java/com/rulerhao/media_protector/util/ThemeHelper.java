package com.rulerhao.media_protector.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.rulerhao.media_protector.R;

public final class ThemeHelper {

    private static final String PREFS_NAME  = "theme_prefs";
    private static final String KEY_DARK    = "dark_mode";

    private ThemeHelper() {}

    /** Apply the stored theme before {@code setContentView()}. */
    public static void applyTheme(Activity activity) {
        activity.setTheme(isDarkMode(activity)
                ? R.style.Theme_Minimal_Dark
                : R.style.Theme_Minimal_Light);
    }

    public static boolean isDarkMode(Context context) {
        return prefs(context).getBoolean(KEY_DARK, true); // default: dark
    }

    /** Flip the stored preference; caller must call {@code recreate()} to apply. */
    public static void toggleTheme(Context context) {
        boolean dark = prefs(context).getBoolean(KEY_DARK, true);
        prefs(context).edit().putBoolean(KEY_DARK, !dark).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
