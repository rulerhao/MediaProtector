package com.rulerhao.media_protector.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * A lightweight obfuscator that XORs the header of a file.
 * Purpose: Prevent standard media players from recognizing the file.
 * Speed: Extremely fast (O(1) relative to file size), only processes the first N bytes.
 */
public class HeaderObfuscator {

    private static final int HEADER_SIZE = 1024; // 1KB header
    private static final byte KEY = (byte) 0xAA; // Simple XOR key

    /**
     * Obfuscates or Deobfuscates the file in-place.
     * Since XOR is symmetric, calling this twice restores the original file.
     * This is the fastest method as it avoids copying the whole file.
     */
    public void processInPlace(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            byte[] buffer = new byte[HEADER_SIZE];
            int bytesRead = raf.read(buffer);

            if (bytesRead > 0) {
                for (int i = 0; i < bytesRead; i++) {
                    buffer[i] ^= KEY;
                }
                raf.seek(0);
                raf.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * Obfuscates from input to output.
     * Useful if you don't want to modify the original file inplace.
     */
    public void obfuscate(File in, File out) throws IOException {
        copyAndProcess(in, out);
    }

    /**
     * Deobfuscates from input to output.
     */
    public void deobfuscate(File in, File out) throws IOException {
        copyAndProcess(in, out);
    }

    private void copyAndProcess(File in, File out) throws IOException {
        try (FileInputStream fis = new FileInputStream(in);
             FileOutputStream fos = new FileOutputStream(out)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            boolean firstBlock = true;

            while ((bytesRead = fis.read(buffer)) != -1) {
                if (firstBlock) {
                    int length = Math.min(bytesRead, HEADER_SIZE);
                    for (int i = 0; i < length; i++) {
                        buffer[i] ^= KEY;
                    }
                    firstBlock = false;
                }
                fos.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * Returns an InputStream that decrypts the file on the fly.
     * This allows playing/previewing the file without creating a temporary copy.
     */
    public InputStream getDecryptedStream(File file) throws IOException {
        return new DecryptingInputStream(file);
    }

    public static File getObfuscatedFile(File original) {
        return new File(original.getParent(), original.getName() + ".mprot");
    }

    public static boolean isObfuscated(File file) {
        return file.getName().endsWith(".mprot");
    }

    public static String getOriginalName(File obfuscated) {
        String name = obfuscated.getName();
        if (name.endsWith(".mprot")) {
            return name.substring(0, name.length() - ".mprot".length());
        }
        return name;
    }

    private class DecryptingInputStream extends InputStream {
        private final FileInputStream fis;
        private long bytesReadTotal = 0;

        public DecryptingInputStream(File file) throws IOException {
            this.fis = new FileInputStream(file);
        }

        @Override
        public int read() throws IOException {
            int b = fis.read();
            if (b != -1 && bytesReadTotal < HEADER_SIZE) {
                b ^= KEY;
            }
            if (b != -1) bytesReadTotal++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = fis.read(b, off, len);
            if (n > 0) {
                long start = bytesReadTotal;
                long end = start + n;

                if (start < HEADER_SIZE) {
                    int limit = (int) Math.min(n, HEADER_SIZE - start);
                    for (int i = 0; i < limit; i++) {
                        b[off + i] ^= KEY;
                    }
                }
                bytesReadTotal += n;
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            fis.close();
        }

        @Override
        public long skip(long n) throws IOException {
            // For simplicity, we just verify skip behavior if needed, but standard skip might not track bytesReadTotal correctly if delegated.
            // Correct implementation should track bytesReadTotal unless we rely on getChannel().position().
            // Ideally we override skip to ensure we track position or just read and discard.
            // For now, allow super.skip but warn: simple skip might desync our XOR logic if we skip *within* the header.
            // However, for thumbnails/playback, skips usually happen *after* header or we just seek.
            // Proper implementation:
            long skipped = fis.skip(n);
            bytesReadTotal += skipped;
            return skipped;
        }
    }
}

