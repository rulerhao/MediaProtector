package com.rulerhao.media_protector.album;

import com.rulerhao.media_protector.crypto.HeaderObfuscator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure utility for filtering and sorting a list of protected media files.
 * No Android UI dependencies — fully testable in isolation.
 *
 * Usage:
 *   List<File> result = MediaFilter.apply(allFiles, albumDir, searchQuery, sortOption);
 */
public class MediaFilter {

    private MediaFilter() {}

    /**
     * Returns a new, filtered and sorted list. The original list is never mutated.
     *
     * @param files     Full set of scanned .mprot files
     * @param albumDir  If non-null, only include files whose parent equals this directory
     * @param query     Case-insensitive substring match on the original filename (empty = no filter)
     * @param sort      Sort order to apply; null means preserve original order
     */
    public static List<File> apply(
            List<File> files,
            File albumDir,
            String query,
            SortOption sort) {

        // 1. Album filter (work on a copy, never mutate the source list)
        List<File> result = new ArrayList<>(files.size());
        if (albumDir == null) {
            result.addAll(files);
        } else {
            for (File f : files) {
                File parent = f.getParentFile();
                if (parent != null && parent.equals(albumDir)) {
                    result.add(f);
                }
            }
        }

        // 2. Search filter
        if (query != null && !query.isEmpty()) {
            List<File> searched = new ArrayList<>(result.size());
            String lowerQuery = query.toLowerCase();
            for (File f : result) {
                if (HeaderObfuscator.getOriginalName(f).toLowerCase().contains(lowerQuery)) {
                    searched.add(f);
                }
            }
            result = searched;
        }

        // 3. Sort
        if (sort != null) {
            Collections.sort(result, (f1, f2) -> {
                switch (sort) {
                    case NAME_ASC:
                        return HeaderObfuscator.getOriginalName(f1)
                                .compareToIgnoreCase(HeaderObfuscator.getOriginalName(f2));
                    case NAME_DESC:
                        return HeaderObfuscator.getOriginalName(f2)
                                .compareToIgnoreCase(HeaderObfuscator.getOriginalName(f1));
                    case DATE_ASC:
                        return Long.compare(f1.lastModified(), f2.lastModified());
                    case DATE_DESC:
                        return Long.compare(f2.lastModified(), f1.lastModified());
                    case SIZE_ASC:
                        return Long.compare(f1.length(), f2.length());
                    case SIZE_DESC:
                        return Long.compare(f2.length(), f1.length());
                    default:
                        return 0;
                }
            });
        }

        return result;
    }
}
