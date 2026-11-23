package com.cornming.walkadventure;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 純本機私有回憶媒體管理工具（零外部依賴、不上傳雲端、保護孩子隱私）：
 * - 壓縮儲存尋寶照片（最大邊 1080px JPEG，自動依 EXIF 轉正）
 * - 儲存孩子的手指塗鴉透明圖層（PNG）
 * - 錄製與播放最長 15 秒親子語音便條（AAC .m4a）
 * - 取樣縮圖像素送入 ColorScavenger 執行本機 HSV 色彩分析
 */
final class MemoryMediaStore {
    static final int MAX_VOICE_MS = 15_000;
    private static final int MAX_PHOTO_DIM = 1080;
    private static final int SAMPLE_ANALYSIS_DIM = 96;

    private final Context ctx;
    private MediaRecorder activeRecorder;
    private MediaPlayer activePlayer;
    private File recordingTargetFile;

    MemoryMediaStore(Context context) {
        this.ctx = context.getApplicationContext();
    }

    File photosDir() {
        File d = new File(ctx.getFilesDir(), "memories/photos");
        if (!d.exists()) {
            boolean ignored = d.mkdirs();
        }
        return d;
    }

    File doodlesDir() {
        File d = new File(ctx.getFilesDir(), "memories/doodles");
        if (!d.exists()) {
            boolean ignored = d.mkdirs();
        }
        return d;
    }

    File audioDir() {
        File d = new File(ctx.getFilesDir(), "memories/audio");
        if (!d.exists()) {
            boolean ignored = d.mkdirs();
        }
        return d;
    }

    /** 驗證相對檔名僅含安全字元（防止路徑跳脫），並還原至對應私有目錄。 */
    File resolvePhotoFile(String filename) {
        return resolveSafe(photosDir(), filename);
    }

    File resolveDoodleFile(String filename) {
        return resolveSafe(doodlesDir(), filename);
    }

    File resolveAudioFile(String filename) {
        return resolveSafe(audioDir(), filename);
    }

    private static File resolveSafe(File baseDir, String filename) {
        if (filename == null || filename.trim().isEmpty()) return null;
        String clean = filename.trim();
        if (clean.contains("/") || clean.contains("\\") || clean.contains("..")) return null;
        return new File(baseDir, clean);
    }

    /** 將暫存相機照片壓縮、依 EXIF 轉正後存進 App 私有目錄，回傳相對檔名（失敗回傳 null）。 */
    String importCapturedFile(File sourceFile, String memoryId) {
        if (sourceFile == null || !sourceFile.isFile()) return null;
        int rotation = readExifRotationDegrees(sourceFile);
        Bitmap decoded = decodeBounded(sourceFile.getAbsolutePath(), MAX_PHOTO_DIM);
        if (decoded == null) return null;
        Bitmap oriented = rotateIfNeeded(decoded, rotation);
        String filename = "photo_" + sanitizeId(memoryId) + ".jpg";
        File out = new File(photosDir(), filename);
        boolean ok = writeJpeg(oriented, out, 86);
        oriented.recycle();
        return ok ? filename : null;
    }

    /** 從系統相簿挑選的 Uri 壓縮轉存至 App 私有目錄（不留存外部相簿權限）。 */
    String importFromContentUri(Uri uri, String memoryId) {
        if (uri == null) return null;
        File temp = MemoryFileProvider.pendingCaptureFile(ctx);
        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(temp)) {
            if (in == null) return null;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } catch (IOException | SecurityException ignored) {
            return null;
        }
        String saved = importCapturedFile(temp, memoryId);
        if (temp.exists()) {
            boolean ignored = temp.delete();
        }
        return saved;
    }

    String saveDoodleOverlay(Bitmap overlay, String memoryId) {
        if (overlay == null || overlay.isRecycled()) return "";
        String filename = "doodle_" + sanitizeId(memoryId) + ".png";
        File out = new File(doodlesDir(), filename);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (overlay.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                return filename;
            }
        } catch (IOException ignored) {
            // 寫入失敗回傳空字串
        }
        return "";
    }

    /** 從已儲存的私有照片取樣 ~96x96 像素進行本機 HSV 色彩分析（<15ms，100% 離線）。 */
    ColorScavenger.AnalysisResult analyzeStoredPhoto(String photoFilename, int targetColor) {
        File file = resolvePhotoFile(photoFilename);
        if (file == null || !file.isFile()) {
            return ColorScavenger.analyzeRgbPixels(null, targetColor);
        }
        Bitmap sample = decodeBounded(file.getAbsolutePath(), SAMPLE_ANALYSIS_DIM);
        if (sample == null) {
            return ColorScavenger.analyzeRgbPixels(null, targetColor);
        }
        int w = sample.getWidth();
        int h = sample.getHeight();
        int[] pixels = new int[w * h];
        sample.getPixels(pixels, 0, w, 0, 0, w, h);
        sample.recycle();
        return ColorScavenger.analyzeRgbPixels(pixels, targetColor);
    }

    Bitmap loadPreviewBitmap(String photoFilename, int maxDim) {
        File file = resolvePhotoFile(photoFilename);
        if (file == null || !file.isFile()) return null;
        return decodeBounded(file.getAbsolutePath(), Math.max(120, maxDim));
    }

    Bitmap loadDoodleBitmap(String doodleFilename) {
        File file = resolveDoodleFile(doodleFilename);
        if (file == null || !file.isFile()) return null;
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    void deleteMemoryMedia(StoryCatalog.MemoryEntry entry) {
        if (entry == null) return;
        deleteIfExists(resolvePhotoFile(entry.photoPath));
        deleteIfExists(resolveDoodleFile(entry.doodlePath));
        deleteIfExists(resolveAudioFile(entry.audioPath));
    }

    void deleteTempCapture() {
        deleteIfExists(MemoryFileProvider.pendingCaptureFile(ctx));
    }

    // ──────────────── 15 秒親子語音便條錄音與播放 ────────────────

    boolean isRecording() {
        return activeRecorder != null;
    }

    @SuppressWarnings("deprecation")
    boolean startVoiceRecording(String memoryId, Runnable onMaxDurationReached) {
        stopVoicePlayback();
        if (activeRecorder != null) {
            stopVoiceRecording();
        }
        String filename = "voice_" + sanitizeId(memoryId) + ".m4a";
        File out = new File(audioDir(), filename);
        MediaRecorder recorder = Build.VERSION.SDK_INT >= 31
            ? new MediaRecorder(ctx)
            : new MediaRecorder();
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(22050);
            recorder.setAudioEncodingBitRate(48000);
            recorder.setMaxDuration(MAX_VOICE_MS);
            recorder.setOutputFile(out.getAbsolutePath());
            recorder.setOnInfoListener((mr, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopVoiceRecording();
                    if (onMaxDurationReached != null) onMaxDurationReached.run();
                }
            });
            recorder.prepare();
            recorder.start();
            activeRecorder = recorder;
            recordingTargetFile = out;
            return true;
        } catch (IOException | RuntimeException ignored) {
            try {
                recorder.release();
            } catch (RuntimeException ignored2) {
                // 靜默釋放
            }
            deleteIfExists(out);
            activeRecorder = null;
            recordingTargetFile = null;
            return false;
        }
    }

    /** 停止錄音並回傳儲存好的相對檔名（若錄音失敗或過短則回傳空字串）。 */
    String stopVoiceRecording() {
        MediaRecorder rec = activeRecorder;
        File target = recordingTargetFile;
        activeRecorder = null;
        recordingTargetFile = null;
        if (rec != null) {
            try {
                rec.stop();
            } catch (RuntimeException ignored) {
                deleteIfExists(target);
                target = null;
            }
            try {
                rec.release();
            } catch (RuntimeException ignored) {
                // 靜默釋放
            }
        }
        if (target != null && target.isFile() && target.length() > 512L) {
            return target.getName();
        }
        deleteIfExists(target);
        return "";
    }

    boolean playVoiceNote(String audioFilename, Runnable onComplete) {
        stopVoicePlayback();
        File file = resolveAudioFile(audioFilename);
        if (file == null || !file.isFile()) return false;
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(file.getAbsolutePath());
            mp.setOnCompletionListener(player -> {
                stopVoicePlayback();
                if (onComplete != null) onComplete.run();
            });
            mp.prepare();
            mp.start();
            activePlayer = mp;
            return true;
        } catch (IOException | RuntimeException ignored) {
            try {
                mp.release();
            } catch (RuntimeException ignored2) {
                // 靜默釋放
            }
            return false;
        }
    }

    void stopVoicePlayback() {
        MediaPlayer mp = activePlayer;
        activePlayer = null;
        if (mp != null) {
            try {
                if (mp.isPlaying()) mp.stop();
            } catch (RuntimeException ignored) {
                // 靜默忽略
            }
            try {
                mp.release();
            } catch (RuntimeException ignored) {
                // 靜默忽略
            }
        }
    }

    void releaseAll() {
        stopVoiceRecording();
        stopVoicePlayback();
    }

    private static Bitmap decodeBounded(String path, int maxDim) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while ((bounds.outWidth / sample) > maxDim * 2 || (bounds.outHeight / sample) > maxDim * 2) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap raw = BitmapFactory.decodeFile(path, opts);
        if (raw == null) return null;

        int w = raw.getWidth();
        int h = raw.getHeight();
        if (w <= maxDim && h <= maxDim) return raw;
        float ratio = Math.min(maxDim / (float) w, maxDim / (float) h);
        Bitmap scaled = Bitmap.createScaledBitmap(
            raw,
            Math.max(1, Math.round(w * ratio)),
            Math.max(1, Math.round(h * ratio)),
            true
        );
        if (scaled != raw) raw.recycle();
        return scaled;
    }

    private static int readExifRotationDegrees(File file) {
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orient = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orient == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (orient == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (orient == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (IOException ignored) {
            // 無 EXIF 維持 0 度
        }
        return 0;
    }

    private static Bitmap rotateIfNeeded(Bitmap src, int degrees) {
        if (degrees == 0) return src;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        if (rotated != src) src.recycle();
        return rotated;
    }

    private static boolean writeJpeg(Bitmap bmp, File out, int quality) {
        try (FileOutputStream fos = new FileOutputStream(out)) {
            return bmp.compress(Bitmap.CompressFormat.JPEG, quality, fos);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void deleteIfExists(File file) {
        if (file != null && file.exists()) {
            boolean ignored = file.delete();
        }
    }

    private static String sanitizeId(String raw) {
        if (raw == null || raw.trim().isEmpty()) return String.valueOf(System.currentTimeMillis());
        return raw.replaceAll("[^A-Za-z0-9_-]", "");
    }
}
