package com.rulerhao.media_protector.util;

import android.media.MediaDataSource;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * A {@link MediaDataSource} for AES-header-obfuscated (.mprot) video files.
 *
 * <p>On construction the decrypted 1 KB header is read once into an in-memory cache via
 * {@link HeaderObfuscator#getDecryptedStream(File)}.  Random-access reads within that region
 * are served directly from the cache.  Reads beyond 1 KB go to a {@link RandomAccessFile}
 * (that region of the file was never encrypted, so no decryption is needed).
 *
 * <p>File layout on disk:
 * <pre>
 *   [ 16-byte nonce ][ AES-CTR encrypted first min(size, 1024) bytes ][ rest unchanged ]
 * </pre>
 * Logical position 0 maps to the first byte of the original file content (after the nonce).
 *
 * <p>Call {@link #close()} when the owning {@link android.media.MediaPlayer} is released.
 */
public class EncryptedMediaDataSource extends MediaDataSource {

    private static final int HEADER_SIZE = 1024;
    private static final int NONCE_SIZE  = 16;

    private final byte[]           decryptedHeader; // length ≤ HEADER_SIZE
    private final RandomAccessFile raf;
    private final long             logicalSize;     // file.length() - NONCE_SIZE

    public EncryptedMediaDataSource(File encryptedFile) throws IOException {
        long rawSize = encryptedFile.length();
        if (rawSize < NONCE_SIZE) {
            throw new IOException("File too short to be a valid .mprot file: " + encryptedFile);
        }
        logicalSize = rawSize - NONCE_SIZE;

        // Decrypt and cache the header (up to HEADER_SIZE bytes).
        int headerBytes = (int) Math.min(HEADER_SIZE, logicalSize);
        decryptedHeader = new byte[headerBytes];
        HeaderObfuscator obfuscator = new HeaderObfuscator();
        try (InputStream is = obfuscator.getDecryptedStream(encryptedFile)) {
            int total = 0;
            while (total < headerBytes) {
                int n = is.read(decryptedHeader, total, headerBytes - total);
                if (n < 0) break;
                total += n;
            }
        }

        raf = new RandomAccessFile(encryptedFile, "r");
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if (position < 0 || position >= logicalSize) return -1;

        // Clamp to bytes actually available from this position.
        int toRead = (int) Math.min(size, logicalSize - position);
        int bytesRead = 0;

        // Part 1: bytes within the in-memory decrypted header cache.
        if (position < decryptedHeader.length) {
            int fromCache = (int) Math.min(toRead, decryptedHeader.length - position);
            System.arraycopy(decryptedHeader, (int) position, buffer, offset, fromCache);
            bytesRead += fromCache;
            position  += fromCache;
            offset    += fromCache;
        }

        // Part 2: bytes beyond the header — passthrough via RandomAccessFile.
        // Logical position maps to file position + NONCE_SIZE (the nonce bytes are skipped).
        if (bytesRead < toRead) {
            raf.seek(position + NONCE_SIZE);
            int n = raf.read(buffer, offset, toRead - bytesRead);
            if (n > 0) bytesRead += n;
        }

        return bytesRead == 0 ? -1 : bytesRead;
    }

    @Override
    public long getSize() {
        return logicalSize;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}
