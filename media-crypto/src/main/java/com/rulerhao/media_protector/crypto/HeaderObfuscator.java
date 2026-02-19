package com.rulerhao.media_protector.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Protects media files by AES-128-CTR-encrypting the first 1 KB of the file
 * and prepending a random 16-byte nonce.
 *
 * File format (.mprot):
 *   [16-byte nonce][AES-128-CTR ciphertext of first min(fileSize, 1024) bytes of original]
 *   [remaining original bytes unchanged]
 *
 * Security note: the AES key is hardcoded in this binary. This provides obfuscation
 * against casual inspection and prevents standard media players from opening the files,
 * but is NOT suitable for protecting sensitive content. For stronger security, derive
 * the key from a user passphrase using PBKDF2 or Argon2.
 */
public class HeaderObfuscator {

    private static final int HEADER_SIZE = 1024; // bytes to encrypt
    private static final int NONCE_SIZE  = 16;   // AES-CTR nonce length

    // 128-bit hardcoded key — replace with passphrase derivation for real security.
    private static final byte[] AES_KEY = {
        (byte) 0x4D, (byte) 0x65, (byte) 0x64, (byte) 0x69,
        (byte) 0x61, (byte) 0x50, (byte) 0x72, (byte) 0x6F,
        (byte) 0x74, (byte) 0x65, (byte) 0x63, (byte) 0x74,
        (byte) 0x6F, (byte) 0x72, (byte) 0x4B, (byte) 0x65
    };

    private static final SecureRandom RANDOM = new SecureRandom();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Encrypts {@code plainFile} into {@code encryptedFile} (.mprot format).
     * The caller is responsible for deleting {@code plainFile} afterwards if desired.
     */
    public void encrypt(File plainFile, File encryptedFile) throws IOException {
        byte[] nonce = new byte[NONCE_SIZE];
        RANDOM.nextBytes(nonce);

        try (FileInputStream fis = new FileInputStream(plainFile);
             FileOutputStream fos = new FileOutputStream(encryptedFile)) {

            // Write nonce
            fos.write(nonce);

            // Read up to HEADER_SIZE bytes, encrypt, write
            byte[] header = new byte[HEADER_SIZE];
            int headerRead = readFully(fis, header);
            if (headerRead > 0) {
                byte[] encrypted = applyAesCtr(nonce, header, headerRead);
                fos.write(encrypted, 0, headerRead);
            }

            // Stream remaining bytes unchanged via NIO channel (efficient for large files).
            // Channels share the file descriptor with fis/fos; closing the outer streams
            // closes the channels, so no separate try-with-resources needed here.
            fos.flush();
            FileChannel srcChannel = fis.getChannel();
            FileChannel dstChannel = fos.getChannel();
            long remaining = srcChannel.size() - srcChannel.position();
            if (remaining > 0) {
                dstChannel.position(dstChannel.size());
                srcChannel.transferTo(srcChannel.position(), remaining, dstChannel);
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("AES encryption failed", e);
        }
    }

    /**
     * Decrypts a {@code .mprot} file back into {@code plainFile}.
     * The caller is responsible for deleting the encrypted file afterwards if desired.
     */
    public void decrypt(File encryptedFile, File plainFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(encryptedFile);
             FileOutputStream fos = new FileOutputStream(plainFile)) {

            // Read nonce
            byte[] nonce = new byte[NONCE_SIZE];
            if (readFully(fis, nonce) < NONCE_SIZE) {
                throw new IOException("Encrypted file too short (missing nonce): " + encryptedFile);
            }

            // Read encrypted header, decrypt, write
            byte[] header = new byte[HEADER_SIZE];
            int headerRead = readFully(fis, header);
            if (headerRead > 0) {
                byte[] decrypted = applyAesCtr(nonce, header, headerRead);
                fos.write(decrypted, 0, headerRead);
            }

            // Stream remaining bytes unchanged
            fos.flush();
            FileChannel srcChannel = fis.getChannel();
            FileChannel dstChannel = fos.getChannel();
            long remaining = srcChannel.size() - srcChannel.position();
            if (remaining > 0) {
                dstChannel.position(dstChannel.size());
                srcChannel.transferTo(srcChannel.position(), remaining, dstChannel);
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("AES decryption failed", e);
        }
    }

    /**
     * Returns an {@link InputStream} that decrypts the file on-the-fly.
     * Suitable for thumbnail generation without creating a temporary copy.
     * The caller must close the returned stream.
     */
    public InputStream getDecryptedStream(File encryptedFile) throws IOException {
        return new DecryptingInputStream(encryptedFile);
    }

    // -------------------------------------------------------------------------
    // Static helpers
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Applies AES-128-CTR to the first {@code len} bytes of {@code data}. */
    private static byte[] applyAesCtr(byte[] nonce, byte[] data, int len)
            throws GeneralSecurityException {
        SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(nonce);
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec); // CTR: enc == dec
        return cipher.doFinal(data, 0, len);
    }

    /** Reads into {@code buf} until full or EOF. Returns total bytes read. */
    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n == -1) break;
            total += n;
        }
        return total;
    }

    // -------------------------------------------------------------------------
    // DecryptingInputStream — on-the-fly decryption for thumbnails/preview
    // -------------------------------------------------------------------------

    private static class DecryptingInputStream extends InputStream {

        private final FileInputStream fis;
        private final Cipher cipher;
        private long headerBytesRead = 0;

        DecryptingInputStream(File encryptedFile) throws IOException {
            fis = new FileInputStream(encryptedFile);

            // Read nonce from the first 16 bytes
            byte[] nonce = new byte[NONCE_SIZE];
            int n = readFully(fis, nonce);
            if (n < NONCE_SIZE) {
                fis.close();
                throw new IOException("Encrypted file too short (missing nonce): " + encryptedFile);
            }

            try {
                SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
                IvParameterSpec ivSpec = new IvParameterSpec(nonce);
                cipher = Cipher.getInstance("AES/CTR/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            } catch (GeneralSecurityException e) {
                fis.close();
                throw new IOException("Failed to initialise AES cipher", e);
            }
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return n == -1 ? -1 : (b[0] & 0xFF);
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = fis.read(buf, off, len);
            if (n <= 0) return n;

            if (headerBytesRead < HEADER_SIZE) {
                // Apply cipher only to the bytes that fall within the encrypted header
                int toCipher = (int) Math.min(n, HEADER_SIZE - headerBytesRead);
                try {
                    byte[] decrypted = cipher.update(buf, off, toCipher);
                    if (decrypted != null) {
                        System.arraycopy(decrypted, 0, buf, off, decrypted.length);
                    }
                } catch (Exception e) {
                    throw new IOException("Stream decryption error", e);
                }
            }
            // Bytes beyond HEADER_SIZE pass through unchanged (they were never encrypted)
            headerBytesRead += n;
            return n;
        }

        @Override
        public void close() throws IOException {
            fis.close();
        }
    }
}
