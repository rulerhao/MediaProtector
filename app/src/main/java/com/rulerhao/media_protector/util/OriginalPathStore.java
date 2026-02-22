package com.rulerhao.media_protector.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores the original paths of files before encryption.
 * This allows restoring files to their original locations when decrypting.
 *
 * Key: encrypted file name (without path)
 * Value: original file absolute path
 */
public class OriginalPathStore {

    private static final String PREFS_NAME = "original_paths";
    private static final String SETTINGS_PREFS = "app_settings";
    private static final String KEY_RESTORE_ORIGINAL = "restore_to_original";

    private OriginalPathStore() {}

    /**
     * Store the original path for an encrypted file.
     * @param context application context
     * @param encryptedFileName the name of the encrypted file (e.g., "photo.jpg.mprot")
     * @param originalPath the original absolute path before encryption
     */
    public static void storePath(Context context, String encryptedFileName, String originalPath) {
        getPrefs(context).edit()
                .putString(encryptedFileName, originalPath)
                .apply();
    }

    /**
     * Get the original path for an encrypted file.
     * @param context application context
     * @param encryptedFileName the name of the encrypted file
     * @return the original path, or null if not stored
     */
    public static String getOriginalPath(Context context, String encryptedFileName) {
        return getPrefs(context).getString(encryptedFileName, null);
    }

    /**
     * Remove the stored path after successful decryption.
     * @param context application context
     * @param encryptedFileName the name of the encrypted file
     */
    public static void removePath(Context context, String encryptedFileName) {
        getPrefs(context).edit()
                .remove(encryptedFileName)
                .apply();
    }

    /**
     * Check if "restore to original location" setting is enabled.
     * @param context application context
     * @return true if files should be restored to original location on decrypt
     */
    public static boolean isRestoreToOriginalEnabled(Context context) {
        return context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_RESTORE_ORIGINAL, false);
    }

    /**
     * Set the "restore to original location" setting.
     * @param context application context
     * @param enabled true to restore files to original location, false to keep in app
     */
    public static void setRestoreToOriginalEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RESTORE_ORIGINAL, enabled)
                .apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
