---
description: Implement Google Drive backup/restore
---

Add Google Drive backup/restore functionality to ZenDrive.

**Prerequisites:**
- Google Drive API enabled in Google Cloud Console
- OAuth 2.0 credentials configured
- `google-services.json` in `app/` directory

**Implementation steps:**

1. Add dependency in `app/build.gradle.kts`:
   ```kotlin
   implementation("com.google.android.gms:play-services-auth:20.7.0")
   implementation("com.google.http-client:google-http-client-gson:1.42.3")
   ```

2. Create `GoogleDriveHelper.kt`:
   - Handle sign-in with `GoogleSignInClient`
   - Request scopes: `DriveScopes.DRIVE_FILE`
   - Use `Drive` service for file operations

3. Backup flow:
   - Export Room database to JSON or temp file
   - Upload to Drive with `drive.files().create()`
   - Store file metadata in preferences

4. Restore flow:
   - List backup files from Drive
   - Download selected file
   - Replace database or import records

5. Handle permissions:
   - `android.permission.INTERNET`
   - `android.permission.ACCESS_NETWORK_STATE`

**Important:** All data stays local; Drive is only for user-initiated backup/restore.
