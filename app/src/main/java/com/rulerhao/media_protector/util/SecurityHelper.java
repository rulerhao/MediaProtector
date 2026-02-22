package com.rulerhao.media_protector.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Helper class for managing PIN and fingerprint security settings.
 */
public class SecurityHelper {

    private static final String PREFS_NAME = "security_prefs";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_ENABLED = "pin_enabled";
    private static final String KEY_FINGERPRINT_ENABLED = "fingerprint_enabled";

    private SecurityHelper() {}

    /**
     * Check if PIN lock is enabled.
     */
    public static boolean isPinEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_PIN_ENABLED, false);
    }

    /**
     * Check if fingerprint unlock is enabled.
     */
    public static boolean isFingerprintEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_FINGERPRINT_ENABLED, false);
    }

    /**
     * Check if fingerprint hardware is available and enrolled.
     */
    @SuppressWarnings("deprecation")
    public static boolean isFingerprintAvailable(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        try {
            FingerprintManager fingerprintManager =
                    (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
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
