package com.rulerhao.media_protector.album;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.rulerhao.media_protector.R;
import com.rulerhao.media_protector.core.FileConfig;
import com.rulerhao.media_protector.widget.PullToRefreshLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Manages album navigation and album-related dialogs for the Protected tab.
 *
 * <p>Owns the album view state ({@link #isInAlbumView()}, {@link #getCurrentAlbumDir()})
 * and all album UI widgets. Actions that require Presenter or host-Activity state are
 * delegated back through the {@link Callback} interface.
 */
public class AlbumController {

    // ─── Callback ──────────────────────────────────────────────────────────

    public interface Callback {
        /** Open a specific album's file grid (null = show all protected files). */
        void onOpenAlbum(File albumDir);

        /** Notify the host that the empty-state view should be updated. */
        void onShowEmptyState(boolean empty, String msg);

        /** Deselect all items in the protected grid. */
        void deselectAll();

        /** Move files to a target album directory. */
        void moveFilesToAlbum(List<File> files, File targetDir);

        /** Encrypt plain files to the main protected folder. */
        void encryptFiles(List<File> files);

        /** Encrypt plain files into a specific album sub-directory. */
        void encryptFilesToAlbum(List<File> files, File albumDir);

        /**
         * Called after an album directory has been deleted.
         * The host should trigger a fresh scan so the album grid refreshes.
         */
        void onAlbumDeleted();

        /** Clear selection state in the Browse tab adapter. */
        void clearBrowseSelection();

        /**
         * Refresh the album grid using the current file list.
         * Called after creating an album when there are no files to move.
         */
        void onRefreshAlbumGrid();
    }

    // ─── Fields ────────────────────────────────────────────────────────────

    private final Activity activity;
    private final Callback callback;

    // Views owned by this controller
    private final View albumBar;
    private final GridView albumGridView;
    private final View albumBreadcrumbBar;
    private final TextView tvAlbumBreadcrumb;
    private final View searchBar;
    private final PullToRefreshLayout pullToRefreshProtected;
    private final View selectionBar;
    private final AlbumAdapter albumAdapter;

    // State
    private boolean inAlbumView = true;
    private File currentAlbumDir = null;

    // ─── Constructor ───────────────────────────────────────────────────────

    public AlbumController(
            Activity activity,
            Callback callback,
            View albumBar,
            GridView albumGridView,
            View albumBreadcrumbBar,
            TextView tvAlbumBreadcrumb,
            View searchBar,
            PullToRefreshLayout pullToRefreshProtected,
            View selectionBar,
            AlbumAdapter albumAdapter) {
        this.activity = activity;
        this.callback = callback;
        this.albumBar = albumBar;
        this.albumGridView = albumGridView;
        this.albumBreadcrumbBar = albumBreadcrumbBar;
        this.tvAlbumBreadcrumb = tvAlbumBreadcrumb;
        this.searchBar = searchBar;
        this.pullToRefreshProtected = pullToRefreshProtected;
        this.selectionBar = selectionBar;
        this.albumAdapter = albumAdapter;
    }

    // ─── State accessors ───────────────────────────────────────────────────

    public boolean isInAlbumView() {
        return inAlbumView;
    }

    public File getCurrentAlbumDir() {
        return currentAlbumDir;
    }

    // ─── Navigation ────────────────────────────────────────────────────────

    /** Switches the Protected tab to the album grid view. */
    public void showAlbumView(List<File> allFiles) {
        inAlbumView = true;
        currentAlbumDir = null;
        albumBar.setVisibility(View.VISIBLE);
        albumGridView.setVisibility(View.VISIBLE);
        albumBreadcrumbBar.setVisibility(View.GONE);
        searchBar.setVisibility(View.GONE);
        pullToRefreshProtected.setVisibility(View.GONE);
        selectionBar.setVisibility(View.GONE);
        callback.deselectAll();
        buildAndShowAlbumGrid(allFiles);
    }

    /** Opens a specific album (or all files if albumDir == null). */
    public void openAlbum(File albumDir) {
        inAlbumView = false;
        currentAlbumDir = albumDir;
        albumBar.setVisibility(View.GONE);
        albumGridView.setVisibility(View.GONE);
        pullToRefreshProtected.setVisibility(View.VISIBLE);
        searchBar.setVisibility(View.VISIBLE);
        if (albumDir != null) {
            albumBreadcrumbBar.setVisibility(View.VISIBLE);
            tvAlbumBreadcrumb.setText(albumDir.getName());
        } else {
            albumBreadcrumbBar.setVisibility(View.GONE);
        }
        callback.onOpenAlbum(albumDir);
    }

    /** Rebuilds the album grid from the given file list. */
    public void buildAndShowAlbumGrid(List<File> allFiles) {
        albumBar.setVisibility(View.VISIBLE);
        albumGridView.setVisibility(View.VISIBLE);
        pullToRefreshProtected.setVisibility(View.GONE);
        albumBreadcrumbBar.setVisibility(View.GONE);
        searchBar.setVisibility(View.GONE);

        File protectedRoot = FileConfig.getProtectedFolder();
        List<AlbumAdapter.AlbumItem> items = new ArrayList<>();

        // "All Files" card
        File allCover = allFiles.isEmpty() ? null : allFiles.get(0);
        items.add(new AlbumAdapter.AlbumItem(
                null,
                activity.getString(R.string.album_all) + " Files",
                allFiles.size(),
                allCover));

        // One card per album sub-directory
        for (File dir : AlbumManager.getAlbumDirs(protectedRoot)) {
            File cover = AlbumManager.getAlbumCover(dir);
            int count = AlbumManager.getFileCount(dir);
            items.add(new AlbumAdapter.AlbumItem(dir, dir.getName(), count, cover));
        }

        // "New Album" add card
        items.add(new AlbumAdapter.AlbumItem());

        albumAdapter.setItems(items);
        callback.onShowEmptyState(false, "");
    }

    // ─── Dialogs ───────────────────────────────────────────────────────────

    public void showMoveToAlbumDialog(Set<File> selected) {
        if (selected.isEmpty()) return;

        File protectedRoot = FileConfig.getProtectedFolder();
        List<File> albumDirs = AlbumManager.getAlbumDirs(protectedRoot);

        List<String> options = new ArrayList<>();
        options.add(activity.getString(R.string.album_create));
        for (File dir : albumDirs) options.add(dir.getName());
        options.add(activity.getString(R.string.album_remove_from));

        new AlertDialog.Builder(activity)
                .setTitle(R.string.album_move_to)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    List<File> toMove = new ArrayList<>(selected);
                    callback.deselectAll();
                    if (which == 0) {
                        showCreateAlbumDialog(toMove);
                    } else if (which == options.size() - 1) {
                        callback.moveFilesToAlbum(toMove, protectedRoot);
                    } else {
                        callback.moveFilesToAlbum(toMove, albumDirs.get(which - 1));
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    public void showEncryptToAlbumDialog(List<File> filesToEncrypt) {
        if (filesToEncrypt.isEmpty()) return;

        File protectedRoot = FileConfig.getProtectedFolder();
        List<File> albumDirs = AlbumManager.getAlbumDirs(protectedRoot);

        List<String> options = new ArrayList<>();
        options.add(activity.getString(R.string.encrypt_to_main));
        for (File dir : albumDirs) options.add(dir.getName());
        options.add(activity.getString(R.string.album_create));

        new AlertDialog.Builder(activity)
                .setTitle(R.string.encrypt_to_album_title)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    callback.clearBrowseSelection();
                    if (which == 0) {
                        callback.encryptFiles(filesToEncrypt);
                    } else if (which == options.size() - 1) {
                        showCreateAlbumForEncryptDialog(filesToEncrypt);
                    } else {
                        callback.encryptFilesToAlbum(filesToEncrypt, albumDirs.get(which - 1));
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    public void showCreateAlbumForEncryptDialog(List<File> filesToEncrypt) {
        EditText input = new EditText(activity);
        input.setHint(R.string.album_name_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout container = new LinearLayout(activity);
        container.setPadding(48, 32, 48, 0);
        container.addView(input);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.album_create_title)
                .setView(container)
                .setPositiveButton(R.string.btn_create, (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (!AlbumManager.isValidName(name)) return;
                    File protectedRoot = FileConfig.getProtectedFolder();
                    if (AlbumManager.albumExists(protectedRoot, name)) {
                        Toast.makeText(activity, R.string.album_exists, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File newAlbum = AlbumManager.createAlbum(protectedRoot, name);
                    if (newAlbum == null) {
                        Toast.makeText(activity, R.string.error_generic, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(activity, R.string.album_created, Toast.LENGTH_SHORT).show();
                    callback.encryptFilesToAlbum(filesToEncrypt, newAlbum);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    public void showCreateAlbumDialog(List<File> filesToMove) {
        EditText input = new EditText(activity);
        input.setHint(R.string.album_name_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout container = new LinearLayout(activity);
        container.setPadding(48, 32, 48, 0);
        container.addView(input);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.album_create_title)
                .setView(container)
                .setPositiveButton(R.string.btn_create, (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (!AlbumManager.isValidName(name)) return;
                    File protectedRoot = FileConfig.getProtectedFolder();
                    if (AlbumManager.albumExists(protectedRoot, name)) {
                        Toast.makeText(activity, R.string.album_exists, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File newAlbum = AlbumManager.createAlbum(protectedRoot, name);
                    if (newAlbum == null) {
                        Toast.makeText(activity, R.string.error_generic, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(activity, R.string.album_created, Toast.LENGTH_SHORT).show();
                    if (filesToMove != null && !filesToMove.isEmpty()) {
                        callback.moveFilesToAlbum(filesToMove, newAlbum);
                    } else {
                        callback.onRefreshAlbumGrid();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    public void showAlbumOptionsDialog(AlbumAdapter.AlbumItem album) {
        new AlertDialog.Builder(activity)
                .setTitle(album.name)
                .setItems(new String[]{activity.getString(R.string.album_delete)}, (d, which) ->
                        new AlertDialog.Builder(activity)
                                .setTitle(R.string.album_delete)
                                .setMessage("\"" + album.name + "\" "
                                        + activity.getString(R.string.album_delete_message))
                                .setPositiveButton(R.string.album_delete, (d2, w2) ->
                                        new Thread(() -> {
                                            try {
                                                AlbumManager.deleteAlbum(
                                                        album.dir,
                                                        FileConfig.getProtectedFolder());
                                                activity.runOnUiThread(() -> {
                                                    Toast.makeText(activity,
                                                            R.string.album_deleted,
                                                            Toast.LENGTH_SHORT).show();
                                                    inAlbumView = true;
                                                    currentAlbumDir = null;
                                                    callback.onAlbumDeleted();
                                                });
                                            } catch (Exception e) {
                                                activity.runOnUiThread(() -> Toast.makeText(
                                                        activity,
                                                        R.string.error_generic,
                                                        Toast.LENGTH_SHORT).show());
                                            }
                                        }).start())
                                .setNegativeButton(R.string.btn_cancel, null)
                                .show())
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
