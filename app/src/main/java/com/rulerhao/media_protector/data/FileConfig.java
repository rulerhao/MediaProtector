package com.rulerhao.media_protector.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FileConfig {
    public static final String ENCRYPTED_EXTENSION = ".mprot";

    private static final Set<String> SUPPORTED_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".mp4"
    )));

    private static final Set<String> VIDEO_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ".mp4"
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
}
