package com.rulerhao.media_protector.core;

import android.content.Context;
import android.util.Log;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;
import com.rulerhao.media_protector.security.OriginalPathStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for scanning and processing media files.
 *
 * <h3>Folder Depth Limitation</h3>
 * <p>File scanning is limited to <strong>3 levels deep</strong> from the root directory.
 * This limitation exists for the following reasons:</p>
 * <ul>
 *   <li><b>Performance:</b> Unlimited recursion on external storage can cause ANRs
 *       when scanning directories with thousands of nested folders (e.g., app caches,
 *       downloaded archives, project folders).</li>
 *   <li><b>Memory:</b> Deep recursion can exhaust stack space and create large file
 *       lists that exceed available heap memory.</li>
 *   <li><b>User Experience:</b> Most user media resides within 3 levels of the storage
 *       root (DCIM/Camera, Pictures, Downloads, etc.).</li>
 *   <li><b>Battery:</b> Reducing file system traversal saves CPU cycles and battery.</li>
 * </ul>
 *
 * <p>If you need to scan deeper, increase {@code MAX_DEPTH} in {@link #traverse},
 * but consider adding a timeout or progress callback for cancellation support.</p>
 *
 * @see #traverse(File, FileVisitor, int)
 */
public class MediaRepository {

    private static final String TAG = "MediaRepository";
    /**
     * Maximum folder depth for recursive file scanning.
     * Depth 0 = root directory, Depth 3 = 3 levels below root.
     *
     * @see #traverse(File, FileVisitor, int)
     */
    private static final int MAX_DEPTH = 3;
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
        void onProgress(int done, int total, String currentFileName, long bytesProcessed, long bytesTotal);
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
     * Files are saved to the default protected folder.
     */
    public void encryptFiles(List<File> files, OperationCallback callback) {
        processFiles(Operation.ENCRYPT, files, null, callback);
    }

    /**
     * Encrypt files to a specific album/folder.
     * Files are saved to the specified target folder instead of the default protected folder.
     *
     * @param files       the files to encrypt
     * @param targetAlbum the target album folder to save encrypted files to
     * @param callback    progress and completion callback
     */
    public void encryptFilesToAlbum(List<File> files, File targetAlbum, OperationCallback callback) {
        processFilesToAlbum(files, targetAlbum, callback);
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

            // Calculate total bytes for progress reporting
            long bytesTotal = 0;
            for (File file : files) {
                bytesTotal += file.length();
            }
            long bytesProcessed = 0;

            // For export: ensure destination folder exists
            if (op == Operation.EXPORT && destFolder != null && !destFolder.exists()) {
                destFolder.mkdirs();
            }

            for (int i = 0; i < total; i++) {
                File file = files.get(i);
                long fileSize = file.length();
                String fileName = file.getName();
                // For encrypted files, show the original name
                if (op == Operation.DECRYPT || op == Operation.EXPORT) {
                    fileName = HeaderObfuscator.getOriginalName(file);
                }
                callback.onProgress(i + 1, total, fileName, bytesProcessed, bytesTotal);
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
                    bytesProcessed += fileSize;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to " + op.name().toLowerCase() + ": " + file, e);
                    failed++;
                    bytesProcessed += fileSize; // Count failed file size too for accurate progress
                }
            }
            callback.onComplete(succeeded, failed);
        });
    }

    /**
     * Encrypts files directly to a specific album folder.
     */
    private void processFilesToAlbum(List<File> files, File targetAlbum, OperationCallback callback) {
        cryptoExecutor.execute(() -> {
            int succeeded = 0;
            int failed = 0;
            int total = files.size();

            // Calculate total bytes for progress reporting
            long bytesTotal = 0;
            for (File file : files) {
                bytesTotal += file.length();
            }
            long bytesProcessed = 0;

            // Ensure target album folder exists
            if (!targetAlbum.exists()) {
                targetAlbum.mkdirs();
            }

            for (int i = 0; i < total; i++) {
                File file = files.get(i);
                long fileSize = file.length();
                String fileName = file.getName();
                callback.onProgress(i + 1, total, fileName, bytesProcessed, bytesTotal);
                try {
                    processEncryptToAlbum(file, targetAlbum);
                    succeeded++;
                    bytesProcessed += fileSize;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to encrypt to album: " + file, e);
                    failed++;
                    bytesProcessed += fileSize;
                }
            }
            callback.onComplete(succeeded, failed);
        });
    }

    private void processEncryptToAlbum(File file, File targetAlbum) throws Exception {
        // Create encrypted file in target album instead of default location
        String encryptedName = file.getName() + FileConfig.ENCRYPTED_EXTENSION;
        File outFile = getUniqueFile(new File(targetAlbum, encryptedName));

        // Store original path before encrypting
        OriginalPathStore.storePath(context, outFile.getName(), file.getAbsolutePath());

        obfuscator.encrypt(file, outFile);
        if (!file.delete()) {
            Log.w(TAG, "Could not delete original after encrypt: " + file);
        }
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

    /**
     * Moves files to the target directory using filesystem rename (fast, no re-encryption needed).
     */
    public void moveFiles(List<File> files, File targetDir, OperationCallback callback) {
        cryptoExecutor.execute(() -> {
            int succeeded = 0, failed = 0;
            targetDir.mkdirs();
            for (File file : files) {
                try {
                    File dest = getUniqueFile(new File(targetDir, file.getName()));
                    if (file.renameTo(dest)) {
                        succeeded++;
                    } else {
                        Log.w(TAG, "Failed to move: " + file);
                        failed++;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Move failed: " + file, e);
                    failed++;
                }
            }
            final int s = succeeded, f = failed;
            callback.onComplete(s, f);
        });
    }

    // -------------------------------------------------------------------------
    // Unified recursive traversal (replaces scanRecursiveInternal + hasMediaFilesInternal)
    // -------------------------------------------------------------------------

    /**
     * Traverses {@code dir} recursively up to {@link #MAX_DEPTH} levels deep.
     *
     * <p>Uses {@link File#listFiles()} for directory enumeration. Calls
     * {@code visitor.visit(file)} for every regular file found. If the visitor
     * returns {@code false}, traversal stops immediately (early exit).</p>
     *
     * <p><b>Depth limit rationale:</b> See class-level Javadoc for details on why
     * recursion is limited to 3 levels.</p>
     *
     * @param dir     the directory to traverse
     * @param visitor callback invoked for each file; return false to stop
     * @param depth   current recursion depth (0 = root)
     */
    private void traverse(File dir, FileVisitor visitor, int depth) {
        if (depth > MAX_DEPTH) return;
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
