package com.rulerhao.media_protector.data;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AlbumManager {

    /** Returns all album directories (direct subdirectories of the protected folder). */
    public static List<File> getAlbumDirs(File protectedRoot) {
        List<File> albums = new ArrayList<>();
        File[] children = protectedRoot.listFiles();
        if (children == null) return albums;
        for (File child : children) {
            if (child.isDirectory() && !child.isHidden()) {
                albums.add(child);
            }
        }
        return albums;
    }

    /** Creates a new album directory. Returns the new dir, or null on failure. */
    public static File createAlbum(File protectedRoot, String name) {
        File albumDir = new File(protectedRoot, name);
        if (albumDir.exists()) return null;
        return albumDir.mkdirs() ? albumDir : null;
    }

    /** Returns true if an album with the given name already exists. */
    public static boolean albumExists(File protectedRoot, String name) {
        return new File(protectedRoot, name).exists();
    }

    /**
     * Deletes an album: moves all .mprot files back to protectedRoot, then deletes the dir.
     */
    public static void deleteAlbum(File albumDir, File protectedRoot) throws Exception {
        File[] files = albumDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (FileConfig.isEncryptedFile(file.getName())) {
                    File dest = getUniqueFile(new File(protectedRoot, file.getName()));
                    if (!file.renameTo(dest)) {
                        throw new Exception("Failed to move " + file.getName());
                    }
                }
            }
        }
        albumDir.delete();
    }

    /** Returns the first .mprot file in the dir as cover, or null. */
    public static File getAlbumCover(File albumDir) {
        File[] files = albumDir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && FileConfig.isEncryptedFile(file.getName())) return file;
        }
        return null;
    }

    /** Counts .mprot files directly in the directory (not recursive). */
    public static int getFileCount(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File file : files) {
            if (file.isFile() && FileConfig.isEncryptedFile(file.getName())) count++;
        }
        return count;
    }

    /** Checks if a name is valid for an album (non-empty, no path separators). */
    public static boolean isValidName(String name) {
        return name != null && !name.trim().isEmpty()
                && !name.contains("/") && !name.contains("\\");
    }

    private static File getUniqueFile(File file) {
        if (!file.exists()) return file;
        String name = file.getName();
        String base, ext;
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        else { base = name; ext = ""; }
        int i = 1;
        File parent = file.getParentFile();
        while (file.exists()) file = new File(parent, base + "(" + i++ + ")" + ext);
        return file;
    }
}
