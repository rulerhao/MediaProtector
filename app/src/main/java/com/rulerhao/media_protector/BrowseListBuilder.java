package com.rulerhao.media_protector;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared helper that converts a flat file list into browse adapter items.
 * Used by both {@link FolderBrowserActivity} and {@link MainActivity}.
 */
class BrowseListBuilder {

    /** Groups files by calendar day, newest day first. */
    static List<FolderAdapter.BrowseItem> buildDateItems(List<File> allFiles) {
        List<File> sorted = new ArrayList<>(allFiles);
        Collections.sort(sorted, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        SimpleDateFormat dayFmt = new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH);
        Map<String, List<File>> byDate = new LinkedHashMap<>();
        for (File f : sorted) {
            String day = dayFmt.format(new Date(f.lastModified())).toUpperCase(Locale.ENGLISH);
            List<File> group = byDate.get(day);
            if (group == null) { group = new ArrayList<>(); byDate.put(day, group); }
            group.add(f);
        }

        List<FolderAdapter.BrowseItem> result = new ArrayList<>();
        for (Map.Entry<String, List<File>> entry : byDate.entrySet()) {
            List<File> group = entry.getValue();
            String[] paths = toPaths(group);

            FolderAdapter.BrowseItem header =
                    new FolderAdapter.BrowseItem(FolderAdapter.TYPE_DATE_HEADER);
            header.title    = entry.getKey();
            header.subtitle = group.size() + " " + (group.size() == 1 ? "item" : "items");
            header.paths    = paths;  // store paths for header click
            result.add(header);

            FolderAdapter.BrowseItem strip =
                    new FolderAdapter.BrowseItem(FolderAdapter.TYPE_MEDIA_STRIP);
            strip.files = group.toArray(new File[0]);
            strip.paths = paths;
            result.add(strip);
        }
        return result;
    }

    /**
     * Groups files by parent folder, folders sorted alphabetically.
     * Files within each folder are sorted newest-first.
     */
    static List<FolderAdapter.BrowseItem> buildFolderItems(List<File> allFiles) {
        Map<File, List<File>> byFolder = new LinkedHashMap<>();
        for (File f : allFiles) {
            File parent = f.getParentFile();
            if (parent == null) continue;
            List<File> group = byFolder.get(parent);
            if (group == null) { group = new ArrayList<>(); byFolder.put(parent, group); }
            group.add(f);
        }

        List<Map.Entry<File, List<File>>> entries = new ArrayList<>(byFolder.entrySet());
        Collections.sort(entries,
                (a, b) -> a.getKey().getName().compareToIgnoreCase(b.getKey().getName()));

        List<FolderAdapter.BrowseItem> result = new ArrayList<>();
        for (Map.Entry<File, List<File>> entry : entries) {
            File       folder = entry.getKey();
            List<File> group  = entry.getValue();

            List<File> sortedGroup = new ArrayList<>(group);
            Collections.sort(sortedGroup,
                    (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            String[] paths = toPaths(sortedGroup);

            FolderAdapter.BrowseItem header =
                    new FolderAdapter.BrowseItem(FolderAdapter.TYPE_FOLDER_HEADER);
            header.title    = folder.getName();
            header.subtitle = group.size() + " " + (group.size() == 1 ? "item" : "items");
            header.folder   = folder;
            header.paths    = paths;  // store paths for header click
            result.add(header);

            FolderAdapter.BrowseItem strip =
                    new FolderAdapter.BrowseItem(FolderAdapter.TYPE_MEDIA_STRIP);
            strip.files = sortedGroup.toArray(new File[0]);
            strip.paths = paths;
            result.add(strip);
        }
        return result;
    }

    static String[] toPaths(List<File> files) {
        String[] paths = new String[files.size()];
        for (int i = 0; i < files.size(); i++) paths[i] = files.get(i).getAbsolutePath();
        return paths;
    }
}
