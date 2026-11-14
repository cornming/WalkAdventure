package com.cornming.walkadventure;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * 唯讀 ContentProvider：只開放分享已通過驗證的更新 APK，不接受外部寫入或路徑參數。
 */
public class ApkFileProvider extends ContentProvider {
    static final String MIME_APK = "application/vnd.android.package-archive";
    private static final String AUTHORITY_SUFFIX = ".apkprovider";
    private static final String UPDATE_DIR_NAME = "updates";
    private static final String UPDATE_APK_NAME = "WalkAdventure-update.apk";
    private static final String UPDATE_URI_PATH = "/" + UPDATE_DIR_NAME + "/" + UPDATE_APK_NAME;
    private static final String[] DEFAULT_COLUMNS = {
        OpenableColumns.DISPLAY_NAME,
        OpenableColumns.SIZE
    };

    static File updateDir(Context context) {
        return new File(context.getCacheDir(), UPDATE_DIR_NAME);
    }

    static File updateApkFile(Context context) {
        return new File(updateDir(context), UPDATE_APK_NAME);
    }

    static Uri uriForUpdateApk(Context context) {
        return new Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(context.getPackageName() + AUTHORITY_SUFFIX)
            .path(UPDATE_DIR_NAME + "/" + UPDATE_APK_NAME)
            .build();
    }

    @Override public boolean onCreate() {
        return true;
    }

    private boolean isValidUpdateUri(Uri uri) {
        Context context = getContext();
        if (context == null || uri == null) return false;
        return ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
            && (context.getPackageName() + AUTHORITY_SUFFIX).equals(uri.getAuthority())
            && UPDATE_URI_PATH.equals(uri.getPath());
    }

    @Override public String getType(Uri uri) {
        return isValidUpdateUri(uri) ? MIME_APK : null;
    }

    @Override public Cursor query(
        Uri uri,
        String[] projection,
        String selection,
        String[] selectionArgs,
        String sortOrder
    ) {
        Context context = getContext();
        if (context == null || !isValidUpdateUri(uri)) {
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        String[] cols = projection != null ? projection : DEFAULT_COLUMNS;
        String[] actualCols = new String[cols.length];
        Object[] values = new Object[cols.length];
        File file = updateApkFile(context);
        int count = 0;
        for (String col : cols) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                actualCols[count] = OpenableColumns.DISPLAY_NAME;
                values[count++] = UPDATE_APK_NAME;
            } else if (OpenableColumns.SIZE.equals(col)) {
                actualCols[count] = OpenableColumns.SIZE;
                values[count++] = file.exists() ? file.length() : 0L;
            }
        }
        String[] trimmedCols = new String[count];
        Object[] trimmedValues = new Object[count];
        System.arraycopy(actualCols, 0, trimmedCols, 0, count);
        System.arraycopy(values, 0, trimmedValues, 0, count);
        MatrixCursor cursor = new MatrixCursor(trimmedCols, 1);
        cursor.addRow(trimmedValues);
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        if (context == null || !isValidUpdateUri(uri)) {
            throw new FileNotFoundException("Unsupported URI: " + uri);
        }
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Only read-only access is supported");
        }
        File dir = updateDir(context);
        File file = updateApkFile(context);
        try {
            String dirCanonical = dir.getCanonicalPath() + File.separator;
            String fileCanonical = file.getCanonicalPath();
            if (!fileCanonical.startsWith(dirCanonical)) {
                throw new FileNotFoundException("Invalid update APK path");
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to verify APK path");
        }
        if (!file.isFile()) {
            throw new FileNotFoundException("Update APK not found");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }
}
