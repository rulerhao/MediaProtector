package com.rulerhao.media_protector.album;

/**
 * Sort options for the protected media grid.
 * Defined here so any layer (presenter, filter, UI) can reference it
 * without depending on MainActivity.
 */
public enum SortOption {
    NAME_ASC,
    NAME_DESC,
    DATE_ASC,
    DATE_DESC,
    SIZE_ASC,
    SIZE_DESC
}
