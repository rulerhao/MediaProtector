package com.rulerhao.media_protector.disguise;

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

    /**
     * Available disguise types.
     */
    public enum DisguiseType {
        NONE(0, "MediaProtector", "ic_launcher"),
        CALCULATOR(1, "Calculator", "ic_disguise_calculator");

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
     * Apply the disguise by enabling/disabling activity aliases.
     * This changes the app icon and name in the launcher.
     *
     * @param context The application context
     * @param type    The disguise type to apply (use NONE to disable disguise)
     */
    public static void applyDisguise(Context context, DisguiseType type) {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();

        String defaultAlias = packageName + ".MainActivityDefault";
        String calculatorAlias = packageName + ".MainActivityCalculator";

        setComponentEnabled(pm, defaultAlias, false);
        setComponentEnabled(pm, calculatorAlias, false);

        if (type == DisguiseType.CALCULATOR) {
            setComponentEnabled(pm, calculatorAlias, true);
        } else {
            setComponentEnabled(pm, defaultAlias, true);
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
