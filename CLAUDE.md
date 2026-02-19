# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug      # Build debug APK
./gradlew assembleRelease    # Build release APK (ProGuard enabled)
./gradlew build              # Full build (all modules)
./gradlew clean              # Clean build artifacts
./gradlew :app:assembleDebug           # Build only the app module
./gradlew :media-crypto:assembleRelease # Build only the crypto library
```

There is no test suite configured; the project has no unit or instrumented tests.

## Architecture

**Multi-module Android project (pure Java, API 26+):**
- `app/` — Main application module
- `media-crypto/` — Standalone encryption library module (min SDK 24, no external deps)

**MVP pattern with Repository:**
- `MainContract.java` — Defines `View` (including `showError`, `showProgress`, `showOperationResult`) and `Presenter` interfaces
- `MainActivity.java` — Implements `View`; handles UI, permissions, mode switching
- `MainPresenter.java` — Implements `Presenter`; holds `WeakReference<View>`; owns selection state; constructor-injected with `MediaRepository`; guards all post-destroy callbacks via `volatile boolean destroyed`
- `MediaRepository.java` — Data layer; file scanning via `ExecutorService`; encrypt/decrypt on raw `Thread`; `destroy()` shuts down the executor
- `FolderBrowserActivity.java` — Secondary activity for folder selection; returns path via Intent result; runs `hasMediaFiles` filter on a background thread

**Cryptography (`media-crypto` module):**
- `HeaderObfuscator.java` — AES-128-CTR over the first 1 KB of each file; a random 16-byte nonce is prepended to the `.mprot` file (file grows by 16 bytes). **The AES key is hardcoded** — suitable for obfuscation, not for sensitive data.
- File format: `[16-byte nonce][AES-CTR ciphertext of first 1 KB][rest of file unchanged]`
- Public API: `encrypt(File, File)`, `decrypt(File, File)`, `getDecryptedStream(File)` (returns a `DecryptingInputStream` for thumbnail preview without a temp file)
- Static helpers: `getObfuscatedFile()`, `getOriginalName()`, `isObfuscated()`

**Thumbnail loading:**
- `ThumbnailLoader.java` (`util/`) — LRU bitmap cache (30 entries), 4-thread `ExecutorService`, stale-view guard via `ImageView.setTag`. Call `destroy()` in `Activity.onDestroy()`.
- `MediaAdapter.java` delegates all thumbnail work to `ThumbnailLoader`; call `adapter.destroy()` before `presenter.onDestroy()`.

**Async model:**
- Scans: `MediaRepository.scanExecutor` (single-thread `ExecutorService`) with `ScanCallback.onScanComplete / onScanError`
- Encrypt/decrypt: raw `Thread` with `OperationCallback.onProgress / onComplete`
- Main-thread delivery: `Handler(Looper.getMainLooper())` in `MainPresenter`, guarded by `destroyed` flag
- Thumbnails: `ThumbnailLoader`'s own 4-thread pool

**File scanning:** Uses `File.listFiles()` (not `ProcessBuilder`). Single unified `traverse()` helper with a `FileVisitor` functional interface; depth-limited to 3 levels. Supported media types defined in `FileConfig.java`: `.jpg`, `.jpeg`, `.png`, `.mp4`.

## Key Conventions

- No external library dependencies — only the Android SDK and Java 8 stdlib.
- `FileConfig.java` is the single source of truth for supported extensions and `.mprot` marker.
- `SortOption.java` enumerates the four sort modes: `NAME_ASC`, `NAME_DESC`, `DATE_ASC`, `DATE_DESC`.
- Grid thumbnails use `inSampleSize=4` bitmap downsampling and ViewHolder pattern.
- Release builds have ProGuard minification enabled (`app/build.gradle.kts`).
- `MainPresenter` constructor signature: `MainPresenter(MainContract.View view, MediaRepository repository)` — always inject `MediaRepository` from the call site.
