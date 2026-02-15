package com.rulerhao.media_protector.data;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MediaRepository {

    // System folders to skip during recursive search
    private static final String[] SYSTEM_FOLDERS = {
        "proc", "sys", "dev", "system", "data/data", "cache", 
        "obb", "apex", "vendor", "product", "odm"
    };

    public interface ScanCallback {
        void onScanComplete(List<File> files);
    }

    public void scanFiles(File rootDir, ScanCallback callback) {
        scanFilesInternal(rootDir, true, callback);
    }

    public void scanUnencryptedFiles(File rootDir, ScanCallback callback) {
        scanFilesInternal(rootDir, false, callback);
    }

    private void scanFilesInternal(File rootDir, boolean isEncrypted, ScanCallback callback) {
        new Thread(() -> {
            List<File> result = new ArrayList<>();
            scanRecursiveInternal(rootDir, result, isEncrypted);
            if (callback != null) {
                callback.onScanComplete(result);
            }
        }).start();
    }

    private void scanRecursiveInternal(File dir, List<File> result, boolean isEncrypted) {
        try {
            Process process = new ProcessBuilder().command("ls", "-a", dir.getAbsolutePath()).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals(".") || line.equals("..")) {
                    continue;
                }
                File file = new File(dir, line);
                if (file.isDirectory()) {
                    scanRecursiveInternal(file, result, isEncrypted);
                } else {
                    if (isEncrypted) {
                        if (FileConfig.isEncryptedFile(line)) {
                            result.add(file);
                        }
                    } else {
                        if (FileConfig.isRegularMediaFile(line)) {
                            result.add(file);
                        }
                    }
                }
            }
            reader.close();
            process.waitFor();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void encryptFiles(List<File> files, Runnable onComplete) {
        new Thread(() -> {
            HeaderObfuscator obfuscator = new HeaderObfuscator();
            for (File file : files) {
                try {
                    File outFile = HeaderObfuscator.getObfuscatedFile(file);
                    obfuscator.processInPlace(file); // Encrypt content
                    file.renameTo(outFile); // Rename
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (onComplete != null) {
                onComplete.run();
            }
        }).start();
    }

    public void decryptFiles(List<File> files, Runnable onComplete) {
        new Thread(() -> {
            HeaderObfuscator obfuscator = new HeaderObfuscator();
            for (File file : files) {
                try {
                    // Decrypt in-place (XOR is symmetric)
                    obfuscator.processInPlace(file);
                    
                    // Rename file to remove .mprot extension
                    String originalName = HeaderObfuscator.getOriginalName(file);
                    File renamedFile = new File(file.getParent(), originalName);
                    file.renameTo(renamedFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (onComplete != null) {
                onComplete.run();
            }
        }).start();
    }

    /**
     * Checks if a folder contains media files (recursively).
     * Used for UI filtering in folder browser.
     */
    public boolean hasMediaFiles(File dir) {
        return hasMediaFilesInternal(dir, 0);
    }

    private boolean hasMediaFilesInternal(File dir, int depth) {
        // Limit depth for performance in UI context
        if (depth > 3) return false;
        if (dir == null || !dir.isDirectory() || !dir.canRead()) return false;

        // Skip known system folders
        String folderPath = dir.getAbsolutePath().toLowerCase();
        for (String sysFolder : SYSTEM_FOLDERS) {
            if (folderPath.contains("/" + sysFolder + "/") || folderPath.endsWith("/" + sysFolder)) {
                return false;
            }
        }

        try {
            Process process = new ProcessBuilder().command("ls", "-a", dir.getAbsolutePath()).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    if (line.equals(".") || line.equals("..")) continue;

                    if (FileConfig.isSupportedMediaFile(line)) {
                        reader.close();
                        process.destroy();
                        return true;
                    }

                    File child = new File(dir, line);
                    if (child.isDirectory() && !child.isHidden() && child.canRead()) {
                        if (hasMediaFilesInternal(child, depth + 1)) {
                            reader.close();
                            process.destroy();
                            return true;
                        }
                    }
                } catch (Exception e) {
                    continue;
                }
            }
            reader.close();
            process.waitFor();
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
