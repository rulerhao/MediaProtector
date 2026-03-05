---
name: optimize-apk
description: Analyze and optimize APK size for minimal footprint
allowed-tools:
  - Bash
  - Read
  - Edit
  - Glob
  - Grep
---

Analyze and optimize MediaProtector APK size.

## Current Optimizations

- ProGuard/R8 with `proguard-android-optimize.txt`
- R8 full mode enabled (`android.enableR8.fullMode=true`)
- Aggressive optimizations in `app/proguard-rules.pro`
- Resource shrinking (`isShrinkResources = true`)
- Log removal in release builds
- Vector drawables only (no bitmaps)

## Analysis Steps

1. Build release APK:
   ```bash
   ./gradlew clean assembleRelease
   ```

2. List APK contents:
   ```bash
   unzip -l app/build/outputs/apk/release/app-release-unsigned.apk
   ```

3. Identify large components:
   - `classes.dex` - code size
   - `resources.arsc` - compiled resources
   - `res/` - layouts, drawables

## Optimization Techniques

### Code Size
- Remove unused classes/methods
- Inline small methods
- Remove debug code
- Use ProGuard `-assumenosideeffects` for Log calls

### Resources
- Remove unused strings, layouts, drawables
- Use vector drawables instead of PNGs
- Minimize XML attributes
- Use style inheritance

### ProGuard Rules
```proguard
# More optimization passes
-optimizationpasses 10

# Repackage classes
-repackageclasses ''

# Remove logging
-assumenosideeffects class android.util.Log { *; }
```

## Size Targets

| Component | Target | Warning |
|-----------|--------|---------|
| Total APK | < 100KB | > 150KB |
| classes.dex | < 60KB | > 80KB |
| resources.arsc | < 15KB | > 25KB |
