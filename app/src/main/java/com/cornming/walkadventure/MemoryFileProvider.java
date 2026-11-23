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
 * 嚴格路徑白名單 ContentProvider（零外部套件）：
 * 僅開放兩個固定暫存路徑：
 * 1) /capture/pending_capture.jpg — 供系統相機回寫拍下的尋寶照片
 * 2) /share/postcard_share.png — 供唯讀分享合成好的實境冒險明信片
 */
public class MemoryFileProvider extends ContentProvider {
    static final String MIME_JPEG = "image/jpeg";
    static final String MIME_PNG = "image/png";

    private static final String AUTHORITY_SUFFIX = ".memoryprovider";
    private static final String CAPTURE_DIR = "capture";
    private static final String CAPTURE_NAME = "pending_capture.jpg";
    private static final String CAPTURE_PATH = "/" + CAPTURE_DIR + "/" + CAPTURE_NAME;

    private static final String SHARE_DIR = "share";
    private static final String SHARE_NAME = "postcard_share.png";
    private static final String SHARE_PATH = "/" + SHARE_DIR + "/" + SHARE_NAME;

    private static final String[] DEFAULT_COLUMNS = {
        OpenableColumns.DISPLAY_NAME,
        OpenableColumns.SIZE
    };

    static File pendingCaptureFile(Context context) {
        File dir = new File(context.getCacheDir(), CAPTURE_DIR);
        if (!dir.exists()) {
            boolean ignored = dir.mkdirs();
        }
        return new File(dir, CAPTURE_NAME);
    }

    static Uri uriForPendingCapture(Context context) {
        return new Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(context.getPackageName() + AUTHORITY_SUFFIX)
            .path(CAPTURE_DIR + "/" + CAPTURE_NAME)
            .build();
    }

    static File sharePostcardFile(Context context) {
        File dir = new File(context.getCacheDir(), SHARE_DIR);
        if (!dir.exists()) {
            boolean ignored = dir.mkdirs();
        }
        return new File(dir, SHARE_NAME);
    }

    static Uri uriForSharePostcard(Context context) {
        return new Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(context.getPackageName() + AUTHORITY_SUFFIX)
            .path(SHARE_DIR + "/" + SHARE_NAME)
            .build();
    }

    @Override public boolean onCreate() {
        return true;
    }

    private int matchRoute(Uri uri) {
        Context ctx = getContext();
        if (ctx == null || uri == null) return 0;
        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) return 0;
        if (!(ctx.getPackageName() + AUTHORITY_SUFFIX).equals(uri.getAuthority())) return 0;
        String path = uri.getPath();
        if (CAPTURE_PATH.equals(path)) return 1;
        if (SHARE_PATH.equals(path)) return 2;
        return 0;
    }

    @Override public String getType(Uri uri) {
        int route = matchRoute(uri);
        if (route == 1) return MIME_JPEG;
        if (route == 2) return MIME_PNG;
        return null;
    }

    @Override public Cursor query(
        Uri uri,
        String[] projection,
        String selection,
        String[] selectionArgs,
        String sortOrder
    ) {
        Context ctx = getContext();
        int route = matchRoute(uri);
        if (ctx == null || route == 0) {
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        File file = route == 1 ? pendingCaptureFile(ctx) : sharePostcardFile(ctx);
        String name = route == 1 ? CAPTURE_NAME : SHARE_NAME;
        String[] cols = projection != null ? projection : DEFAULT_COLUMNS;
        String[] actualCols = new String[cols.length];
        Object[] values = new Object[cols.length];
        int count = 0;
        for (String col : cols) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                actualCols[count] = OpenableColumns.DISPLAY_NAME;
                values[count++] = name;
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
        Context ctx = getContext();
        int route = matchRoute(uri);
        if (ctx == null || route == 0) {
            throw new FileNotFoundException("Unsupported URI: " + uri);
        }
        if (route == 2) {
            if (!"r".equals(mode)) {
                throw new FileNotFoundException("Postcard share URI is read-only");
            }
            File file = sharePostcardFile(ctx);
            verifyInsideCache(ctx, file);
            if (!file.isFile()) {
                throw new FileNotFoundException("Postcard share image not found");
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        // route == 1: 系統相機寫入／讀取暫存照片
        File file = pendingCaptureFile(ctx);
        verifyInsideCache(ctx, file);
        int parsedMode = ParcelFileDescriptor.parseMode(mode != null ? mode : "rw");
        return ParcelFileDescriptor.open(file, parsedMode);
    }

    private static void verifyInsideCache(Context ctx, File file) throws FileNotFoundException {
        try {
            String base = ctx.getCacheDir().getCanonicalPath() + File.separator;
            String target = file.getCanonicalPath();
            if (!target.startsWith(base)) {
                throw new FileNotFoundException("Path outside cache");
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to verify path");
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Unsupported");
    }
}
