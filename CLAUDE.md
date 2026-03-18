# CLAUDE.md

## Build

```bash
./gradlew :app:assembleDebug           # dev build
./gradlew assembleRelease              # ProGuard release APK (needs release-keystore.jks)
./gradlew clean
```
No test suite. Release signing: `app/build.gradle` `signingConfigs.release` → `release-keystore.jks`.

---

## Modules & SDK

| Module | minSdk | targetSdk | Notes |
|---|---|---|---|
| `app/` | 26 | 34 | main app |
| `media-crypto/` | 24 | 34 | crypto library, zero external deps |

Package: `com.rulerhao.media_protector` · Java 1.8 · **zero external library dependencies** (Android SDK + Java 8 stdlib only).

---

## Architecture

**Pattern**: MVP — `MainActivity` is the View, `MainPresenter` is the Presenter, `MediaRepository` is the Model.

**Controllers** (extracted from MainActivity to reduce God-class growth):

| Controller | Owns |
|---|---|
| `ui/AlbumController` | album navigation state (`inAlbumView`, `currentAlbumDir`), all album dialogs/nav; `Callback` interface for cross-cutting actions |
| `ui/SecuritySettingsController` | PIN/fingerprint settings UI; `Callback` interface |

**Dependency rules** (lower layers must not import from higher):
```
media-crypto/  ←  data/  ←  util/  ←  ui/  ←  activities
```
`media-crypto/` has no Android deps. `data/` may use `media-crypto/` + stdlib only.

**RULE: never call `HeaderObfuscator` directly from `app/` layer.**
Use `FileStreamFactory` instead — it is the single gateway for all encrypted/plain file reads.

---

## File Map

### Activities

| File | Purpose |
|---|---|
| `MainActivity.java` | 3-tab UI (Protected / Browse / Settings); delegates to `AlbumController` and `SecuritySettingsController` |
| `MediaViewerActivity.java` | Full-screen viewer; swipe nav; image via `ZoomableImageView`; video via `VideoPlayerController` |
| `LockScreenActivity.java` | PIN auth; modes: `UNLOCK` / `SETUP` / `CHANGE` (via `EXTRA_MODE`) |
| `DecoyCalculatorActivity.java` | Functional calculator disguise; suffix-match input against PIN → launches real app |
| `DecoyNotesActivity.java` | Notes decoy UI (not wired to disguise system — icon only) |
| `DecoyWeatherActivity.java` | Weather decoy UI (not wired to disguise system — icon only) |
| `SectionViewActivity.java` | Grid of files for one date or folder section |
| `ProtectFileActivity.java` | Entry point for "Share → Protect" intent |
| `MediaProtectorTileService.java` | Quick Settings tile; shows protected count, opens app on tap |
| `ProtectedCountWidget.java` | Home screen widget; shows protected file count |

### Adapters & Builders

| File | Purpose |
|---|---|
| `MediaAdapter.java` | GridView adapter; thumbnails via `ThumbnailLoader`; video badge; selection overlay + circle checkmark |
| `FolderAdapter.java` | ListView for Browse tab; date/folder group headers, ".." parent marker |
| `AlbumAdapter.java` | GridView for album grid; `TYPE_ALBUM` and `TYPE_ADD` items |
| `BrowseListBuilder.java` | Converts flat file list → `FolderAdapter.BrowseItem` list grouped by date or folder |

### ui/ (MVP + Controllers)

| File | Purpose |
|---|---|
| `ui/MainContract.java` | View & Presenter interfaces; key View callbacks: `showFiles`, `showError`, `showOperationResult`, `updateSelectionMode` |
| `ui/MainPresenter.java` | Presenter; `WeakReference<View>`; `volatile boolean destroyed`; selection `Set<File>`; `Handler(mainLooper)` for UI delivery |
| `ui/AlbumController.java` | Album nav + all 8 album dialogs; owns `inAlbumView` / `currentAlbumDir` state; `Callback` for presenter/adapter calls |
| `ui/SecuritySettingsController.java` | PIN lock + fingerprint settings UI; `Callback` interface |
| `ui/OperationCallbackFactory.java` | Factory for encrypt/decrypt `OperationCallback`; handles main-thread posting + weak-ref + destroyed check |
| `ui/ScrollPositionManager.java` | Save/restore scroll positions for Protected, BrowseDate, BrowseFolder lists |

### data/

| File | Purpose |
|---|---|
| `data/MediaRepository.java` | Single-thread `scanExecutor`; raw `Thread` for encrypt/decrypt; `traverse()` depth-limited to **3**; `destroy()` shuts executor |
| `data/FileConfig.java` | **Source of truth**: supported extensions (`.jpg .jpeg .png .mp4`), `.mprot` marker, protected folder path |
| `data/AlbumManager.java` | Filesystem album ops: `getAlbumDirs`, `getAlbumCover`, `getFileCount`, `createAlbum`, `deleteAlbum` (moves files to root) |
| `data/MediaFilter.java` | Pure static `apply(files, albumDir, query, sort)` → filtered+sorted copy; never mutates source list |
| `data/SortOption.java` | Enum: `NAME_ASC`, `NAME_DESC`, `DATE_ASC`, `DATE_DESC`, `SIZE_ASC`, `SIZE_DESC` |
| `data/SystemFolderFilter.java` | Set of folder names to skip during scan (e.g. `Android`, `DCIM/.thumbnails`) |

### util/

| File | Purpose |
|---|---|
| `util/FileStreamFactory.java` | **Gateway for all file reads**: `createInputStream(File)`, `isEncrypted(File)`, `getOriginalName(File)`, `isVideo(File)` |
| `util/ThumbnailLoader.java` | Singleton; 4-thread pool; LRU cache (adaptive size); all file access via `FileStreamFactory` |
| `util/EncryptedMediaDataSource.java` | `MediaDataSource` for encrypted video playback; decrypted 1 KB header in memory; skips 16-byte nonce |
| `util/ImageDecoder.java` | Decode images with adaptive `inSampleSize`; uses `FileStreamFactory` |
| `util/VideoFrameExtractor.java` | Extract first frame from video for thumbnail; uses `FileStreamFactory` |
| `util/VideoPlayerController.java` | `MediaPlayer` lifecycle; `SurfaceHolder.Callback`; `Listener` interface for play/pause/complete |
| `util/ZoomableImageView.java` | Pinch-to-zoom + pan `ImageView` |
| `util/ZoomableTextureView.java` | Pinch-to-zoom `TextureView` (video) |
| `util/SecurityHelper.java` | PIN hashing (SHA-256), fingerprint availability check, auto-lock timeout prefs |
| `util/DisguiseHelper.java` | `applyDisguise()` via `PackageManager` component enable/disable; `DisguiseType`: `NONE` / `CALCULATOR` only |
| `util/ThemeHelper.java` | SharedPrefs (`theme_prefs/dark_mode`); default = dark; `applyTheme`, `isDarkMode`, `toggleTheme` |
| `util/OriginalPathStore.java` | Stores original path before encryption → restore-to-original on decrypt |
| `util/PreviewPopup.java` | Long-press preview popup for media grid |
| `util/PullToRefreshLayout.java` | Custom pull-to-refresh wrapper |
| `util/SwipeableTabLayout.java` | Swipe gesture → tab switch callback |
| `util/SkeletonView.java` | Shimmer loading placeholder |
| `util/Constants.java` | Request codes, size constants, timeouts |

### media-crypto/

| File | Purpose |
|---|---|
| `crypto/HeaderObfuscator.java` | AES-128-CTR on first 1 KB; **hardcoded key** (obfuscation, not security); API: `encrypt(File,File)`, `decrypt(File,File)`, `getDecryptedStream(File)` → `DecryptingInputStream`; statics: `getObfuscatedFile()`, `getOriginalName()`, `isObfuscated()` |

### Key Layouts

| File | Key views |
|---|---|
| `activity_main.xml` | `albumBar`, `albumGridView`, `albumBreadcrumbBar`, `gridView`, `browseListView`, `settingsPage`, `selectionBar`, `searchBar`, bottom nav (`navProtected/Original/Settings`) |
| `activity_media_viewer.xml` | `imageView`(ZoomableImageView), `surfaceView`, `viewerTopBar`, `videoControls`, `seekBar`, `tvDuration` |
| `item_media.xml` | Thumbnail `ImageView` (112dp), filename bar, video badge, `selectionOverlay` + `selectionCheck` (circle ✓ top-right) |
| `item_album.xml` | Cover `ImageView`, `album_scrim` gradient, `albumName`/`albumCount` overlay |
| `item_album_add.xml` | Dashed-border card with `+` (add album) |

### Key Drawables / Resources

| File | Notes |
|---|---|
| `values/attrs.xml` | `colorToolbar`, `colorToolbarText`, `colorToolbarIcon`, `colorSurface`, `colorDivider`, `colorThumbPlaceholder` |
| `values/themes.xml` | `Theme.Minimal.Dark` (default) + `Theme.Minimal.Light` |
| `values/colors.xml` | Static colors: `tab_indicator`, `tab_unselected`, `action_encrypt`, `selection_overlay`, `badge_bg`, `primary_red` |
| `album_scrim.xml` | Transparent→black gradient (angle 270) for album card text legibility |
| `selection_check_bg.xml` | Filled oval using `@color/tab_indicator` for selection checkmark |

---

## Cryptography

File format: `[16-byte random nonce][AES-128-CTR ciphertext of first 1 KB][rest of file verbatim]`
Encrypted filename = original name + `.mprot`. File size grows by 16 bytes.

---

## Key Patterns

### Theming (every Activity)
```java
// In onCreate() — BEFORE setContentView():
ThemeHelper.applyTheme(this);
setContentView(R.layout.…);
// Store theme state:
appliedDark = ThemeHelper.isDarkMode(this);
// In onResume():
if (ThemeHelper.isDarkMode(this) != appliedDark) recreate();
```
Use `?attr/colorToolbar` etc. in layouts — **never hardcode toolbar colors**. Resolve attrs at runtime with `TypedValue` when needed in code.

### Album structure
Albums are real filesystem subdirectories under the protected root (`.MediaProtector/`).
Moving files = `File.renameTo()` — no re-encryption. `AlbumManager` is the only class that touches album directories.

### Disguise
Only `CALCULATOR` is active. `DecoyNotes` and `DecoyWeather` Activities exist but are not registered as activity-aliases. `DecoyCalculatorActivity` unlocks via PIN suffix-match: any numeric input ending with the user's PIN triggers navigation to `MainActivity`.

---

## Async / Threading

| What | How |
|---|---|
| Scan | `MediaRepository.scanExecutor` (single thread) → `ScanCallback` → `Handler(mainLooper)` in `MainPresenter` |
| Encrypt/Decrypt | Raw `Thread` → `OperationCallback.onProgress/onComplete` → same `Handler`, gated by `destroyed` |
| Thumbnails | `ThumbnailLoader` 4-thread pool; stale-guard via `ImageView.setTag(file.path)` |
| Image decode (viewer) | `ioExecutor` single thread in `MediaViewerActivity`; stale-guard via `final File target` capture |

---

## Known Pitfalls

### MediaPlayer surface lifecycle (video→video)
`release()` without `setDisplay(null)` leaves the codec attached; next `prepareAsync()` throws `IllegalStateException`.
```java
// Required in releasePlayer():
try { mediaPlayer.setDisplay(null); } catch (IllegalStateException ignored) {}
try { mediaPlayer.stop(); }          catch (IllegalStateException ignored) {}
mediaPlayer.release();
mediaPlayer = null;
```
`setupVideo()` always forces `SurfaceView` GONE→VISIBLE so `surfaceCreated()` fires on a clean surface.

### MIUI MediaMetadataRetriever crash
`setDataSource(MediaDataSource)` throws `RuntimeException` (status `0x80000000`) on MIUI. Catch `Exception` (not `IOException`) in `ThumbnailLoader` / `VideoFrameExtractor`.

### Sort must not mutate source list
`MediaFilter.apply()` always works on a copy. Never call `Collections.sort()` on `allProtectedFiles` directly.
