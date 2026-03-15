package com.rulerhao.media_protector.data;

import android.os.Environment;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FileConfig {
    public static final String ENCRYPTED_EXTENSION = ".mprot";
    public static final String PROTECTED_EXTENSION = ENCRYPTED_EXTENSION;

    /** Returns the folder where protected files are stored. */
    public static File getProtectedFolder() {
        return new File(Environment.getExternalStorageDirectory(), ".MediaProtector");
    }

    private static final Set<String> SUPPORTED_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".heic", ".heif", ".mp4"
    )));

    private static final Set<String> VIDEO_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ".mp4"
    )));

    private static final Set<String> GIF_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ".gif"
    )));

    private static final Set<String> HEIF_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ".heic", ".heif"
    )));

    public static boolean isSupportedMediaFile(String filename) {
        if (filename == null) return false;
        String lowerName = filename.toLowerCase();
        
        // Check if it's the encrypted format
        if (lowerName.endsWith(ENCRYPTED_EXTENSION)) {
            return true;
        }
        
        // Check regular media extensions
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lowerName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRegularMediaFile(String filename) {
        if (filename == null) return false;
        String lowerName = filename.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lowerName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEncryptedFile(String filename) {
        return filename != null && filename.toLowerCase().endsWith(ENCRYPTED_EXTENSION);
    }

    public static boolean isVideoFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        for (String ext : VIDEO_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    public static boolean isGifFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        for (String ext : GIF_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    public static boolean isHeifFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        for (String ext : HEIF_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }
}
