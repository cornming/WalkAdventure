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
 * 僅開放四個固定暫存路徑：
 * 1) /capture/pending_capture.jpg — 供系統相機回寫拍下的尋寶照片
 * 2) /share/postcard_share.png — 供唯讀分享合成好的實境冒險明信片
 * 3) /share/qr_card_share.png — 供唯讀分享離線 QR 藏寶卡與家庭禮物卡
 * 4) /share/yearbook_share.png — 供唯讀分享年度家庭回憶長圖冊
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

    private static final String QR_SHARE_NAME = "qr_card_share.png";
    private static final String QR_SHARE_PATH = "/" + SHARE_DIR + "/" + QR_SHARE_NAME;

    private static final String YEARBOOK_SHARE_NAME = "yearbook_share.png";
    private static final String YEARBOOK_SHARE_PATH = "/" + SHARE_DIR + "/" + YEARBOOK_SHARE_NAME;

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
        return shareFile(context, SHARE_NAME);
    }

    static Uri uriForSharePostcard(Context context) {
        return shareUri(context, SHARE_NAME);
    }

    static File shareQrCardFile(Context context) {
        return shareFile(context, QR_SHARE_NAME);
    }

    static Uri uriForShareQrCard(Context context) {
        return shareUri(context, QR_SHARE_NAME);
    }

    static File shareYearbookFile(Context context) {
        return shareFile(context, YEARBOOK_SHARE_NAME);
    }

    static Uri uriForShareYearbook(Context context) {
        return shareUri(context, YEARBOOK_SHARE_NAME);
    }

    private static File shareFile(Context context, String name) {
        File dir = new File(context.getCacheDir(), SHARE_DIR);
        if (!dir.exists()) {
            boolean ignored = dir.mkdirs();
        }
        return new File(dir, name);
    }

    private static Uri shareUri(Context context, String name) {
        return new Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(context.getPackageName() + AUTHORITY_SUFFIX)
            .path(SHARE_DIR + "/" + name)
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
        if (QR_SHARE_PATH.equals(path)) return 3;
        if (YEARBOOK_SHARE_PATH.equals(path)) return 4;
        return 0;
    }

    private File fileForRoute(Context ctx, int route) {
        if (route == 1) return pendingCaptureFile(ctx);
        if (route == 2) return sharePostcardFile(ctx);
        if (route == 3) return shareQrCardFile(ctx);
        if (route == 4) return shareYearbookFile(ctx);
        return null;
    }

    private String nameForRoute(int route) {
        if (route == 1) return CAPTURE_NAME;
        if (route == 2) return SHARE_NAME;
        if (route == 3) return QR_SHARE_NAME;
        if (route == 4) return YEARBOOK_SHARE_NAME;
        return "";
    }

    @Override public String getType(Uri uri) {
        int route = matchRoute(uri);
        if (route == 1) return MIME_JPEG;
        if (route >= 2 && route <= 4) return MIME_PNG;
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
        File file = ctx != null ? fileForRoute(ctx, route) : null;
        if (ctx == null || route == 0 || file == null) {
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        String name = nameForRoute(route);
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
        File file = ctx != null ? fileForRoute(ctx, route) : null;
        if (ctx == null || route == 0 || file == null) {
            throw new FileNotFoundException("Unsupported URI: " + uri);
        }
        if (route >= 2) {
            if (!"r".equals(mode)) {
                throw new FileNotFoundException("Share URI is read-only");
            }
            verifyInsideCache(ctx, file);
            if (!file.isFile()) {
                throw new FileNotFoundException("Share image not found");
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        // route == 1: 系統相機寫入／讀取暫存照片
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
