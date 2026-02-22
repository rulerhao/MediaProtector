package com.rulerhao.media_protector.data;

import android.content.Context;
import android.util.Log;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;
import com.rulerhao.media_protector.util.OriginalPathStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaRepository {

    private static final String TAG = "MediaRepository";
    private final Context context;

    // System folders to skip during recursive search
    private static final String[] SYSTEM_FOLDERS = {
        "proc", "sys", "dev", "system", "data/data", "cache",
        "obb", "apex", "vendor", "product", "odm"
    };

    /** Operation types for unified file processing. */
    public enum Operation {
        ENCRYPT,
        DECRYPT,
        EXPORT
    }

    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService cryptoExecutor = Executors.newSingleThreadExecutor();
    private final HeaderObfuscator obfuscator = new HeaderObfuscator();

    public MediaRepository(Context context) {
        this.context = context.getApplicationContext();
    }

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
    // Encrypt / Decrypt / Export (unified processing)
    // -------------------------------------------------------------------------

    /**
     * Encrypt files: converts original files to .mprot format, deletes originals.
     */
    public void encryptFiles(List<File> files, OperationCallback callback) {
        processFiles(Operation.ENCRYPT, files, null, callback);
    }

    /**
     * Decrypt files: converts .mprot files back to original format, deletes encrypted.
     */
    public void decryptFiles(List<File> files, OperationCallback callback) {
        processFiles(Operation.DECRYPT, files, null, callback);
    }

    /**
     * Export encrypted files to a destination folder as decrypted copies.
     * Original encrypted files are NOT deleted.
     */
    public void exportFiles(List<File> files, File destFolder, OperationCallback callback) {
        processFiles(Operation.EXPORT, files, destFolder, callback);
    }

    /**
     * Unified file processing method that handles encrypt, decrypt, and export operations.
     * Runs on a managed ExecutorService for proper lifecycle management.
     *
     * @param op         the operation to perform
     * @param files      the files to process
     * @param destFolder destination folder (only used for EXPORT operation)
     * @param callback   progress and completion callback
     */
    private void processFiles(Operation op, List<File> files, File destFolder,
                              OperationCallback callback) {
        cryptoExecutor.execute(() -> {
            int succeeded = 0;
            int failed = 0;
            int total = files.size();

            // For export: ensure destination folder exists
            if (op == Operation.EXPORT && destFolder != null && !destFolder.exists()) {
                destFolder.mkdirs();
            }

            for (int i = 0; i < total; i++) {
                File file = files.get(i);
                try {
                    switch (op) {
                        case ENCRYPT:
                            processEncrypt(file);
                            break;
                        case DECRYPT:
                            processDecrypt(file);
                            break;
                        case EXPORT:
                            processExport(file, destFolder);
                            break;
                    }
                    succeeded++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to " + op.name().toLowerCase() + ": " + file, e);
                    failed++;
                }
                callback.onProgress(i + 1, total);
            }
            callback.onComplete(succeeded, failed);
        });
    }

    private void processEncrypt(File file) throws Exception {
        File outFile = HeaderObfuscator.getObfuscatedFile(file);

        // Store original path before encrypting (for potential restore later)
        OriginalPathStore.storePath(context, outFile.getName(), file.getAbsolutePath());

        obfuscator.encrypt(file, outFile);
        if (!file.delete()) {
            Log.w(TAG, "Could not delete original after encrypt: " + file);
        }
    }

    private void processDecrypt(File file) throws Exception {
        String originalName = HeaderObfuscator.getOriginalName(file);
        File outFile;

        // Check if we should restore to original location
        if (OriginalPathStore.isRestoreToOriginalEnabled(context)) {
            String originalPath = OriginalPathStore.getOriginalPath(context, file.getName());
            if (originalPath != null) {
                outFile = new File(originalPath);
                // Ensure parent directory exists
                File parentDir = outFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
            } else {
                // Fallback to same directory if original path not found
                outFile = new File(file.getParent(), originalName);
            }
        } else {
            // Keep in same directory (current behavior)
            outFile = new File(file.getParent(), originalName);
        }

        obfuscator.decrypt(file, outFile);

        // Remove stored path after successful decrypt
        OriginalPathStore.removePath(context, file.getName());

        if (!file.delete()) {
            Log.w(TAG, "Could not delete encrypted file after decrypt: " + file);
        }
    }

    private void processExport(File file, File destFolder) throws Exception {
        String originalName = HeaderObfuscator.getOriginalName(file);
        File outFile = new File(destFolder, originalName);
        outFile = getUniqueFile(outFile);
        obfuscator.decrypt(file, outFile);
    }

    /**
     * Returns a unique file by appending (1), (2), etc. if the file already exists.
     */
    private File getUniqueFile(File file) {
        if (!file.exists()) return file;

        String name = file.getName();
        String baseName;
        String extension;
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = name.substring(0, dotIndex);
            extension = name.substring(dotIndex);
        } else {
            baseName = name;
            extension = "";
        }

        int counter = 1;
        File parent = file.getParentFile();
        while (file.exists()) {
            file = new File(parent, baseName + "(" + counter + ")" + extension);
            counter++;
        }
        return file;
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
        cryptoExecutor.shutdownNow();
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
