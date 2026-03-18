package com.rulerhao.media_protector.browse;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility for filtering out system folders during file scanning.
 */
public final class SystemFolderFilter {

    private SystemFolderFilter() {} // Prevent instantiation

    /**
     * Set of folder names to skip during scanning.
     */
    private static final Set<String> SYSTEM_FOLDERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "Android",
            ".thumbnails",
            ".trash",
            ".MediaProtector"  // Our own protected folder
    )));

    /**
     * Checks if a folder should be skipped during scanning.
     *
     * @param folder The folder to check
     * @return true if the folder should be skipped
     */
    public static boolean isSystemFolder(File folder) {
        if (folder == null) return true;
        return SYSTEM_FOLDERS.contains(folder.getName());
    }

    /**
     * Checks if a folder name is a system folder.
     *
     * @param folderName The folder name to check
     * @return true if the folder should be skipped
     */
    public static boolean isSystemFolder(String folderName) {
        if (folderName == null) return true;
        return SYSTEM_FOLDERS.contains(folderName);
    }

    /**
     * Checks if a file is within a system folder path.
     *
     * @param file The file to check
     * @return true if the file is within a system folder
     */
    public static boolean isInSystemFolder(File file) {
        if (file == null) return true;

        File parent = file.getParentFile();
        while (parent != null) {
            if (SYSTEM_FOLDERS.contains(parent.getName())) {
                return true;
            }
            parent = parent.getParentFile();
        }
        return false;
    }

    /**
     * Gets the set of system folder names.
     *
     * @return Unmodifiable set of system folder names
     */
    public static Set<String> getSystemFolderNames() {
        return SYSTEM_FOLDERS;
    }
}
