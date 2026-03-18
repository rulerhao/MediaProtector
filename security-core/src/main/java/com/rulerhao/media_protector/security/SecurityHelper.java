package com.rulerhao.media_protector.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricManager;
import android.os.Build;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Helper class for managing PIN and biometric security settings.
 */
public class SecurityHelper {

    private static final String PREFS_NAME = "security_prefs";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_ENABLED = "pin_enabled";
    private static final String KEY_FINGERPRINT_ENABLED = "fingerprint_enabled";
    private static final String KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout";
    private static final String KEY_LAST_ACTIVITY_TIME = "last_activity_time";

    /** Auto-lock timeout options in minutes. 0 = never. */
    public static final int[] TIMEOUT_OPTIONS = {0, 1, 5, 15, 30};
    public static final int DEFAULT_TIMEOUT = 5; // 5 minutes default

    private SecurityHelper() {}

    /**
     * Check if PIN lock is enabled.
     */
    public static boolean isPinEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_PIN_ENABLED, false);
    }

    /**
     * Check if biometric unlock is enabled (stored as fingerprint for backwards compatibility).
     */
    public static boolean isFingerprintEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_FINGERPRINT_ENABLED, false);
    }

    /**
     * Alias for isFingerprintEnabled for clarity.
     */
    public static boolean isBiometricEnabled(Context context) {
        return isFingerprintEnabled(context);
    }

    /**
     * Check if biometric hardware is available and enrolled.
     * Supports fingerprint, face, and other biometrics on Android 10+.
     */
    public static boolean isBiometricAvailable(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - use BiometricManager
            try {
                BiometricManager biometricManager =
                        (BiometricManager) context.getSystemService(Context.BIOMETRIC_SERVICE);
                if (biometricManager == null) {
                    return false;
                }
                int canAuthenticate = biometricManager.canAuthenticate();
                return canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS;
            } catch (Exception e) {
                return false;
            }
        } else {
            // Android 6-9 - fallback to fingerprint check
            return isFingerprintAvailableLegacy(context);
        }
    }

    /**
     * Legacy fingerprint check for Android 6-9.
     */
    @SuppressWarnings("deprecation")
    private static boolean isFingerprintAvailableLegacy(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        try {
            android.hardware.fingerprint.FingerprintManager fingerprintManager =
                    (android.hardware.fingerprint.FingerprintManager)
                            context.getSystemService(Context.FINGERPRINT_SERVICE);
            if (fingerprintManager == null) {
                return false;
            }
            return fingerprintManager.isHardwareDetected()
                    && fingerprintManager.hasEnrolledFingerprints();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Alias for backwards compatibility.
     */
    public static boolean isFingerprintAvailable(Context context) {
        return isBiometricAvailable(context);
    }

    /**
     * Save a new PIN (hashed).
     */
    public static void setPin(Context context, String pin) {
        String hash = hashPin(pin);
        getPrefs(context).edit()
                .putString(KEY_PIN_HASH, hash)
                .putBoolean(KEY_PIN_ENABLED, true)
                .apply();
    }

    /**
     * Verify the entered PIN against the stored hash.
     */
    public static boolean verifyPin(Context context, String pin) {
        String storedHash = getPrefs(context).getString(KEY_PIN_HASH, null);
        if (storedHash == null) return false;
        String inputHash = hashPin(pin);
        return storedHash.equals(inputHash);
    }

    /**
     * Disable PIN lock and clear stored PIN.
     */
    public static void clearPin(Context context) {
        getPrefs(context).edit()
                .remove(KEY_PIN_HASH)
                .putBoolean(KEY_PIN_ENABLED, false)
                .putBoolean(KEY_FINGERPRINT_ENABLED, false)
                .apply();
    }

    /**
     * Enable or disable fingerprint unlock.
     */
    public static void setFingerprintEnabled(Context context, boolean enabled) {
        getPrefs(context).edit()
                .putBoolean(KEY_FINGERPRINT_ENABLED, enabled)
                .apply();
    }

    /**
     * Check if any lock is enabled (PIN or fingerprint).
     */
    public static boolean isLockEnabled(Context context) {
        return isPinEnabled(context);
    }

    /**
     * Get the auto-lock timeout in minutes. 0 = never.
     */
    public static int getAutoLockTimeout(Context context) {
        return getPrefs(context).getInt(KEY_AUTO_LOCK_TIMEOUT, DEFAULT_TIMEOUT);
    }

    /**
     * Set the auto-lock timeout in minutes.
     */
    public static void setAutoLockTimeout(Context context, int minutes) {
        getPrefs(context).edit()
                .putInt(KEY_AUTO_LOCK_TIMEOUT, minutes)
                .apply();
    }

    /**
     * Update the last activity timestamp.
     */
    public static void updateLastActivityTime(Context context) {
        getPrefs(context).edit()
                .putLong(KEY_LAST_ACTIVITY_TIME, System.currentTimeMillis())
                .apply();
    }

    /**
     * Check if the app should be locked based on timeout.
     */
    public static boolean shouldLockDueToTimeout(Context context) {
        if (!isPinEnabled(context)) return false;

        int timeoutMinutes = getAutoLockTimeout(context);
        if (timeoutMinutes == 0) return false; // Never auto-lock

        long lastActivity = getPrefs(context).getLong(KEY_LAST_ACTIVITY_TIME, 0);
        if (lastActivity == 0) return false;

        long elapsed = System.currentTimeMillis() - lastActivity;
        long timeoutMs = timeoutMinutes * 60 * 1000L;
        return elapsed > timeoutMs;
    }

    /**
     * Get timeout label for display.
     */
    public static String getTimeoutLabel(int minutes) {
        if (minutes == 0) return "Never";
        if (minutes == 1) return "1 minute";
        return minutes + " minutes";
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pin.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple encoding if SHA-256 not available
            return pin;
        }
    }
}
