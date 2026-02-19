package com.rulerhao.media_protector.data;

import android.util.Log;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaRepository {

    private static final String TAG = "MediaRepository";

    // System folders to skip during recursive search
    private static final String[] SYSTEM_FOLDERS = {
        "proc", "sys", "dev", "system", "data/data", "cache",
        "obb", "apex", "vendor", "product", "odm"
    };

    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();
    private final HeaderObfuscator obfuscator = new HeaderObfuscator();

    // -------------------------------------------------------------------------
    // Callbacks
    // -------------------------------------------------------------------------

    public interface ScanCallback {
        void onScanComplete(List<File> files);
        void onScanError(Exception e);
    }

    public interface OperationCallback {
        void onProgress(int done, int total);
        void onComplete(int succeeded, int failed);
    }

    // -------------------------------------------------------------------------
    // Scan
    // -------------------------------------------------------------------------

    public void scanFiles(File rootDir, ScanCallback callback) {
        scanFilesInternal(rootDir, true, callback);
    }

    public void scanUnencryptedFiles(File rootDir, ScanCallback callback) {
        scanFilesInternal(rootDir, false, callback);
    }

    private void scanFilesInternal(File rootDir, boolean encrypted, ScanCallback callback) {
        scanExecutor.execute(() -> {
            try {
                List<File> result = new ArrayList<>();
                traverse(rootDir, file -> {
                    boolean match = encrypted
                            ? FileConfig.isEncryptedFile(file.getName())
                            : FileConfig.isRegularMediaFile(file.getName());
                    if (match) result.add(file);
                    return true; // always continue
                }, 0);
                callback.onScanComplete(result);
            } catch (Exception e) {
                Log.e(TAG, "Scan failed", e);
                callback.onScanError(e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Encrypt / Decrypt
    // -------------------------------------------------------------------------

    public void encryptFiles(List<File> files, OperationCallback callback) {
        new Thread(() -> {
            int succeeded = 0;
            int failed = 0;
            int total = files.size();
            for (int i = 0; i < total; i++) {
                File file = files.get(i);
                try {
                    File outFile = HeaderObfuscator.getObfuscatedFile(file);
                    obfuscator.encrypt(file, outFile);
                    if (!file.delete()) {
                        Log.w(TAG, "Could not delete original after encrypt: " + file);
                    }
                    succeeded++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to encrypt: " + file, e);
                    failed++;
                }
                callback.onProgress(i + 1, total);
            }
            callback.onComplete(succeeded, failed);
        }).start();
    }

    public void decryptFiles(List<File> files, OperationCallback callback) {
        new Thread(() -> {
            int succeeded = 0;
            int failed = 0;
            int total = files.size();
            for (int i = 0; i < total; i++) {
                File file = files.get(i);
                try {
                    String originalName = HeaderObfuscator.getOriginalName(file);
                    File outFile = new File(file.getParent(), originalName);
                    obfuscator.decrypt(file, outFile);
                    if (!file.delete()) {
                        Log.w(TAG, "Could not delete encrypted file after decrypt: " + file);
                    }
                    succeeded++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to decrypt: " + file, e);
                    failed++;
                }
                callback.onProgress(i + 1, total);
            }
            callback.onComplete(succeeded, failed);
        }).start();
    }

    // -------------------------------------------------------------------------
    // Folder filter (used by FolderBrowserActivity)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code dir} contains media files matching the given mode
     * within 3 levels of depth. Runs synchronously — must be called from a background thread.
     *
     * @param encrypted {@code true} to look for encrypted (.mprot) files,
     *                  {@code false} to look for regular unencrypted media files.
     */
    public boolean hasMediaFiles(File dir, boolean encrypted) {
        boolean[] found = {false};
        traverse(dir, file -> {
            boolean match = encrypted
                    ? FileConfig.isEncryptedFile(file.getName())
                    : FileConfig.isRegularMediaFile(file.getName());
            if (match) {
                found[0] = true;
                return false; // stop early
            }
            return true;
        }, 0);
        return found[0];
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void destroy() {
        scanExecutor.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Unified recursive traversal (replaces scanRecursiveInternal + hasMediaFilesInternal)
    // -------------------------------------------------------------------------

    /**
     * Traverses {@code dir} recursively up to 3 levels deep using {@link File#listFiles()}.
     * Calls {@code visitor.visit(file)} for every regular file found.
     * If the visitor returns {@code false}, traversal stops immediately.
     */
    private void traverse(File dir, FileVisitor visitor, int depth) {
        if (depth > 3) return;
        if (dir == null || !dir.isDirectory() || !dir.canRead()) return;
        if (isSystemFolder(dir)) return;

        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (child.isDirectory()) {
                if (!child.isHidden()) {
                    traverse(child, visitor, depth + 1);
                }
            } else {
                boolean continueTraversal = visitor.visit(child);
                if (!continueTraversal) return;
            }
        }
    }

    private interface FileVisitor {
        /** @return {@code true} to continue traversal, {@code false} to stop. */
        boolean visit(File file);
    }

    private static boolean isSystemFolder(File dir) {
        String path = dir.getAbsolutePath().toLowerCase();
        for (String sysFolder : SYSTEM_FOLDERS) {
            if (path.contains("/" + sysFolder + "/") || path.endsWith("/" + sysFolder)) {
                return true;
            }
        }
        return false;
    }
}
