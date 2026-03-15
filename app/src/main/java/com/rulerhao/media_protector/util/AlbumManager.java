package com.rulerhao.media_protector.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages virtual albums for organizing protected files.
 * Albums are stored in SharedPreferences as JSON.
 */
public class AlbumManager {

    private static final String PREFS_NAME = "album_prefs";
    private static final String KEY_ALBUMS = "albums";
    private static final String KEY_FILE_ALBUMS = "file_albums";

    public static final String DEFAULT_ALBUM = "All";

    private AlbumManager() {}

    /**
     * Gets the list of all album names.
     */
    public static List<String> getAlbums(Context context) {
        List<String> albums = new ArrayList<>();
        albums.add(DEFAULT_ALBUM); // "All" is always first

        try {
            String json = getPrefs(context).getString(KEY_ALBUMS, "[]");
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                String album = array.getString(i);
                if (!album.equals(DEFAULT_ALBUM)) {
                    albums.add(album);
                }
            }
        } catch (JSONException e) {
            // Return just default album on error
        }
        return albums;
    }

    /**
     * Creates a new album.
     * @return true if created, false if album already exists
     */
    public static boolean createAlbum(Context context, String albumName) {
        if (albumName == null || albumName.trim().isEmpty() || albumName.equals(DEFAULT_ALBUM)) {
            return false;
        }

        List<String> albums = getAlbums(context);
        if (albums.contains(albumName)) {
            return false;
        }

        try {
            String json = getPrefs(context).getString(KEY_ALBUMS, "[]");
            JSONArray array = new JSONArray(json);
            array.put(albumName);
            getPrefs(context).edit().putString(KEY_ALBUMS, array.toString()).apply();
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * Deletes an album. Files in the album are not deleted, just unassigned.
     */
    public static void deleteAlbum(Context context, String albumName) {
        if (albumName == null || albumName.equals(DEFAULT_ALBUM)) {
            return;
        }

        try {
            // Remove from album list
            String json = getPrefs(context).getString(KEY_ALBUMS, "[]");
            JSONArray array = new JSONArray(json);
            JSONArray newArray = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                String album = array.getString(i);
                if (!album.equals(albumName)) {
                    newArray.put(album);
                }
            }

            // Remove all file associations for this album
            String fileAlbumsJson = getPrefs(context).getString(KEY_FILE_ALBUMS, "{}");
            JSONObject fileAlbums = new JSONObject(fileAlbumsJson);
            Iterator<String> keys = fileAlbums.keys();
            while (keys.hasNext()) {
                String fileName = keys.next();
                String fileAlbum = fileAlbums.optString(fileName);
                if (albumName.equals(fileAlbum)) {
                    keys.remove();
                }
            }

            getPrefs(context).edit()
                    .putString(KEY_ALBUMS, newArray.toString())
                    .putString(KEY_FILE_ALBUMS, fileAlbums.toString())
                    .apply();
        } catch (JSONException e) {
            // Ignore errors
        }
    }

    /**
     * Renames an album.
     */
    public static boolean renameAlbum(Context context, String oldName, String newName) {
        if (oldName == null || oldName.equals(DEFAULT_ALBUM) ||
            newName == null || newName.trim().isEmpty() || newName.equals(DEFAULT_ALBUM)) {
            return false;
        }

        List<String> albums = getAlbums(context);
        if (!albums.contains(oldName) || albums.contains(newName)) {
            return false;
        }

        try {
            // Update album list
            String json = getPrefs(context).getString(KEY_ALBUMS, "[]");
            JSONArray array = new JSONArray(json);
            JSONArray newArray = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                String album = array.getString(i);
                newArray.put(album.equals(oldName) ? newName : album);
            }

            // Update file associations
            String fileAlbumsJson = getPrefs(context).getString(KEY_FILE_ALBUMS, "{}");
            JSONObject fileAlbums = new JSONObject(fileAlbumsJson);
            Iterator<String> keys = fileAlbums.keys();
            List<String> keysToUpdate = new ArrayList<>();
            while (keys.hasNext()) {
                String fileName = keys.next();
                String fileAlbum = fileAlbums.optString(fileName);
                if (oldName.equals(fileAlbum)) {
                    keysToUpdate.add(fileName);
                }
            }
            for (String fileName : keysToUpdate) {
                fileAlbums.put(fileName, newName);
            }

            getPrefs(context).edit()
                    .putString(KEY_ALBUMS, newArray.toString())
                    .putString(KEY_FILE_ALBUMS, fileAlbums.toString())
                    .apply();
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * Assigns a file to an album.
     */
    public static void setFileAlbum(Context context, String fileName, String albumName) {
        try {
            String json = getPrefs(context).getString(KEY_FILE_ALBUMS, "{}");
            JSONObject fileAlbums = new JSONObject(json);

            if (albumName == null || albumName.equals(DEFAULT_ALBUM)) {
                fileAlbums.remove(fileName);
            } else {
                fileAlbums.put(fileName, albumName);
            }

            getPrefs(context).edit().putString(KEY_FILE_ALBUMS, fileAlbums.toString()).apply();
        } catch (JSONException e) {
            // Ignore errors
        }
    }

    /**
     * Assigns multiple files to an album.
     */
    public static void setFilesAlbum(Context context, List<String> fileNames, String albumName) {
        try {
            String json = getPrefs(context).getString(KEY_FILE_ALBUMS, "{}");
            JSONObject fileAlbums = new JSONObject(json);

            for (String fileName : fileNames) {
                if (albumName == null || albumName.equals(DEFAULT_ALBUM)) {
                    fileAlbums.remove(fileName);
                } else {
                    fileAlbums.put(fileName, albumName);
                }
            }

            getPrefs(context).edit().putString(KEY_FILE_ALBUMS, fileAlbums.toString()).apply();
        } catch (JSONException e) {
            // Ignore errors
        }
    }

    /**
     * Gets the album a file belongs to.
     * @return album name or null if not assigned to any album
     */
    public static String getFileAlbum(Context context, String fileName) {
        try {
            String json = getPrefs(context).getString(KEY_FILE_ALBUMS, "{}");
            JSONObject fileAlbums = new JSONObject(json);
            String album = fileAlbums.optString(fileName, null);
            return album != null && !album.isEmpty() ? album : null;
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Gets all files in a specific album.
     * @param albumName the album name, or DEFAULT_ALBUM/"All" for all files
     * @return set of file names in the album
     */
    public static Set<String> getFilesInAlbum(Context context, String albumName) {
        Set<String> files = new HashSet<>();

        if (albumName == null || albumName.equals(DEFAULT_ALBUM)) {
            return files; // Empty set means "all files" (no filtering)
        }

        try {
            String json = getPrefs(context).getString(KEY_FILE_ALBUMS, "{}");
            JSONObject fileAlbums = new JSONObject(json);
            Iterator<String> keys = fileAlbums.keys();
            while (keys.hasNext()) {
                String fileName = keys.next();
                String fileAlbum = fileAlbums.optString(fileName);
                if (albumName.equals(fileAlbum)) {
                    files.add(fileName);
                }
            }
        } catch (JSONException e) {
            // Return empty set on error
        }

        return files;
    }

    /**
     * Gets album file counts.
     * @return map of album name to file count
     */
    public static Map<String, Integer> getAlbumCounts(Context context) {
        Map<String, Integer> counts = new HashMap<>();

        try {
            String json = getPrefs(context).getString(KEY_FILE_ALBUMS, "{}");
            JSONObject fileAlbums = new JSONObject(json);
            Iterator<String> keys = fileAlbums.keys();
            while (keys.hasNext()) {
                String fileName = keys.next();
                String album = fileAlbums.optString(fileName);
                if (album != null && !album.isEmpty()) {
                    counts.put(album, counts.getOrDefault(album, 0) + 1);
                }
            }
        } catch (JSONException e) {
            // Return empty map on error
        }

        return counts;
    }

    /**
     * Removes file album assignment when file is deleted or decrypted.
     */
    public static void removeFile(Context context, String fileName) {
        try {
            String json = getPrefs(context).getString(KEY_FILE_ALBUMS, "{}");
            JSONObject fileAlbums = new JSONObject(json);
            fileAlbums.remove(fileName);
            getPrefs(context).edit().putString(KEY_FILE_ALBUMS, fileAlbums.toString()).apply();
        } catch (JSONException e) {
            // Ignore errors
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
