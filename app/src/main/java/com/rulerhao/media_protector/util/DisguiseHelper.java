package com.rulerhao.media_protector.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import com.rulerhao.media_protector.MainActivity;

/**
 * Helper class for managing app disguise settings.
 * Allows the app to appear as a different app (Calculator, Notes, etc.)
 * to provide privacy from casual observers.
 */
public class DisguiseHelper {

    private static final String PREFS_NAME = "disguise_prefs";
    private static final String KEY_DISGUISE_ENABLED = "disguise_enabled";
    private static final String KEY_DISGUISE_TYPE = "disguise_type";
    private static final String KEY_SECRET_CODE = "secret_code";

    /** Default secret code to access the real app from decoy */
    public static final String DEFAULT_SECRET_CODE = "1234";

    /**
     * Available disguise types.
     */
    public enum DisguiseType {
        NONE(0, "MediaProtector", "ic_launcher"),
        CALCULATOR(1, "Calculator", "ic_disguise_calculator"),
        NOTES(2, "My Notes", "ic_disguise_notes"),
        WEATHER(3, "Weather", "ic_disguise_weather");

        public final int id;
        public final String displayName;
        public final String iconName;

        DisguiseType(int id, String displayName, String iconName) {
            this.id = id;
            this.displayName = displayName;
            this.iconName = iconName;
        }

        public static DisguiseType fromId(int id) {
            for (DisguiseType type : values()) {
                if (type.id == id) return type;
            }
            return NONE;
        }
    }

    private DisguiseHelper() {}

    /**
     * Check if disguise mode is enabled.
     */
    public static boolean isDisguiseEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_DISGUISE_ENABLED, false);
    }

    /**
     * Enable or disable disguise mode.
     */
    public static void setDisguiseEnabled(Context context, boolean enabled) {
        getPrefs(context).edit()
                .putBoolean(KEY_DISGUISE_ENABLED, enabled)
                .apply();
    }

    /**
     * Get the current disguise type.
     */
    public static DisguiseType getDisguiseType(Context context) {
        int typeId = getPrefs(context).getInt(KEY_DISGUISE_TYPE, DisguiseType.CALCULATOR.id);
        return DisguiseType.fromId(typeId);
    }

    /**
     * Set the disguise type.
     */
    public static void setDisguiseType(Context context, DisguiseType type) {
        getPrefs(context).edit()
                .putInt(KEY_DISGUISE_TYPE, type.id)
                .apply();
    }

    /**
     * Get the secret code to access the real app.
     */
    public static String getSecretCode(Context context) {
        return getPrefs(context).getString(KEY_SECRET_CODE, DEFAULT_SECRET_CODE);
    }

    /**
     * Set the secret code to access the real app.
     */
    public static void setSecretCode(Context context, String code) {
        getPrefs(context).edit()
                .putString(KEY_SECRET_CODE, code)
                .apply();
    }

    /**
     * Verify if the entered code matches the secret code.
     */
    public static boolean verifySecretCode(Context context, String code) {
        return getSecretCode(context).equals(code);
    }

    /**
     * Get the display name for the current disguise.
     */
    public static String getDisplayName(Context context) {
        if (!isDisguiseEnabled(context)) {
            return "MediaProtector";
        }
        return getDisguiseType(context).displayName;
    }

    /**
     * Get available disguise types for UI display.
     */
    public static DisguiseType[] getAvailableDisguises() {
        return new DisguiseType[] {
            DisguiseType.CALCULATOR,
            DisguiseType.NOTES,
            DisguiseType.WEATHER
        };
    }

    /**
     * Get disguise type names for dialog display.
     */
    public static String[] getDisguiseNames() {
        DisguiseType[] types = getAvailableDisguises();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            names[i] = types[i].displayName;
        }
        return names;
    }

    /**
     * Apply the disguise by enabling/disabling activity aliases.
     * This changes the app icon and name in the launcher.
     *
     * @param context The application context
     * @param type    The disguise type to apply (use NONE to disable disguise)
     */
    public static void applyDisguise(Context context, DisguiseType type) {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();

        // Activity alias names
        String defaultAlias = packageName + ".MainActivityDefault";
        String calculatorAlias = packageName + ".MainActivityCalculator";
        String notesAlias = packageName + ".MainActivityNotes";
        String weatherAlias = packageName + ".MainActivityWeather";

        // Disable all aliases first
        setComponentEnabled(pm, defaultAlias, false);
        setComponentEnabled(pm, calculatorAlias, false);
        setComponentEnabled(pm, notesAlias, false);
        setComponentEnabled(pm, weatherAlias, false);

        // Enable the appropriate alias
        switch (type) {
            case CALCULATOR:
                setComponentEnabled(pm, calculatorAlias, true);
                break;
            case NOTES:
                setComponentEnabled(pm, notesAlias, true);
                break;
            case WEATHER:
                setComponentEnabled(pm, weatherAlias, true);
                break;
            case NONE:
            default:
                setComponentEnabled(pm, defaultAlias, true);
                break;
        }
    }

    private static void setComponentEnabled(PackageManager pm, String className, boolean enabled) {
        ComponentName component = new ComponentName(
                "com.rulerhao.media_protector", className);
        int newState = enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        try {
            pm.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            // Silently ignore - may not have permission or component may not exist
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
