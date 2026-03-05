---
name: clean-unused
description: Find and remove unused resources, strings, layouts, and code
allowed-tools:
  - Bash
  - Grep
  - Glob
  - Read
  - Edit
---

Scan MediaProtector for unused code and resources to reduce APK size.

## Instructions

1. **Find unused strings** in `res/values/strings.xml`:
   - For each string name, search Java files and layouts
   - Report strings with zero references

2. **Find unused layouts** in `res/layout/`:
   - Check if layout is referenced in Java (`R.layout.xxx`) or other XMLs
   - Report unused layout files

3. **Find unused drawables** in `res/drawable/`:
   - Check references in Java and XML files
   - Report unused drawable files

4. **Find unused Java classes**:
   - Check if class is referenced in AndroidManifest or other Java files
   - Report potentially unused classes

5. **Present findings** and ask user before deleting

## Search Patterns

```bash
# Find string usage (excluding strings.xml itself)
grep -r "string_name" app/src/main --include="*.java" --include="*.xml" | grep -v "strings.xml"

# Find layout usage
grep -r "R.layout.layout_name" app/src/main --include="*.java"
grep -r "@layout/layout_name" app/src/main --include="*.xml"

# Find drawable usage
grep -r "R.drawable.drawable_name" app/src/main --include="*.java"
grep -r "@drawable/drawable_name" app/src/main --include="*.xml"
```

## Important

- Always ask before deleting
- Some resources may be used via reflection or runtime
- Check AndroidManifest.xml for activity/service references
