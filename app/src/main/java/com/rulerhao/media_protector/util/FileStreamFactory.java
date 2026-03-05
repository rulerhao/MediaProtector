package com.rulerhao.media_protector.util;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;
import com.rulerhao.media_protector.data.FileConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Factory for creating input streams that handle both encrypted and unencrypted files.
 * Provides a unified API for reading media files regardless of encryption status.
 */
public final class FileStreamFactory {

    private static final HeaderObfuscator obfuscator = new HeaderObfuscator();

    private FileStreamFactory() {} // Prevent instantiation

    /**
     * Creates an input stream for the given file.
     * If the file is encrypted (.mprot), returns a decrypted stream.
     * Otherwise, returns a regular FileInputStream.
     *
     * @param file The file to read
     * @return An InputStream for reading the file's content
     * @throws IOException If the file cannot be read
     */
    public static InputStream createInputStream(File file) throws IOException {
        if (FileConfig.isEncryptedFile(file.getName())) {
            return obfuscator.getDecryptedStream(file);
        } else {
            return new FileInputStream(file);
        }
    }

    /**
     * Checks if a file is encrypted.
     *
     * @param file The file to check
     * @return true if the file is encrypted
     */
    public static boolean isEncrypted(File file) {
        return FileConfig.isEncryptedFile(file.getName());
    }

    /**
     * Gets the original filename for a file (strips .mprot if encrypted).
     *
     * @param file The file
     * @return The original filename
     */
    public static String getOriginalName(File file) {
        if (isEncrypted(file)) {
            return HeaderObfuscator.getOriginalName(file);
        }
        return file.getName();
    }

    /**
     * Checks if a file is a video based on its original name.
     *
     * @param file The file to check
     * @return true if the file is a video
     */
    public static boolean isVideo(File file) {
        String name = getOriginalName(file);
        return FileConfig.isVideoFile(name);
    }
}
