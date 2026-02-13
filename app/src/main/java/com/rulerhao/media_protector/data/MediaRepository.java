package com.rulerhao.media_protector.data;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MediaRepository {

    public interface ScanCallback {
        void onScanComplete(List<File> files);
    }

    public void scanFiles(File rootDir, ScanCallback callback) {
        new Thread(() -> {
            List<File> result = new ArrayList<>();
            scanRecursive(rootDir, result);
            if (callback != null) {
                callback.onScanComplete(result);
            }
        }).start();
    }

    private void scanRecursive(File dir, List<File> result) {
        File[] list = dir.listFiles();
        if (list == null)
            return;

        for (File file : list) {
            if (file.isDirectory()) {
                scanRecursive(file, result);
            } else {
                if (HeaderObfuscator.isObfuscated(file)) {
                    result.add(file);
                }
            }
        }
    }

    public void scanUnencryptedFiles(File rootDir, ScanCallback callback) {
        new Thread(() -> {
            List<File> result = new ArrayList<>();
            scanUnencryptedRecursive(rootDir, result);
            if (callback != null) {
                callback.onScanComplete(result);
            }
        }).start();
    }

    private void scanUnencryptedRecursive(File dir, List<File> result) {
        File[] list = dir.listFiles();
        if (list == null)
            return;

        for (File file : list) {
            if (file.isDirectory()) {
                scanUnencryptedRecursive(file, result);
            } else {
                String name = file.getName().toLowerCase();
                if (!name.endsWith(".mprot") &&
                        (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                                || name.endsWith(".mp4"))) {
                    result.add(file);
                }
            }
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
}
