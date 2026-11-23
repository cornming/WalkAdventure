package com.cornming.walkadventure;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * 繪本風《實境冒險明信片》Canvas 合成器（1080 × 1440 高解析直式明信片）：
 * 結合實景拍照、孩子的手指塗鴉圖層、進化旅伴繪本插畫、日期郵戳、當日步數、
 * 彩虹色系鑑定章、親子星等、出隊小隊印章與童言童語，可即時預覽或一鍵分享給家人收藏。
 */
final class PostcardRenderer {
    static final int WIDTH = 1080;
    static final int HEIGHT = 1440;

    private final Context ctx;
    private final StorybookAssets assets;

    PostcardRenderer(Context context) {
        this.ctx = context.getApplicationContext();
        this.assets = new StorybookAssets(context);
    }

    Bitmap renderPostcard(StoryCatalog.MemoryEntry entry, MemoryMediaStore mediaStore) {
        Bitmap out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Typeface regular = Typeface.create("sans", Typeface.NORMAL);
        Typeface bold = Typeface.create("sans", Typeface.BOLD);
        int ink = Color.rgb(58, 42, 32);

        // 1. 暖米色明信片紙底
        c.drawColor(Color.rgb(255, 247, 232));

        // 雙層圓角繪本邊框
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        p.setColor(Color.rgb(225, 198, 158));
        c.drawRoundRect(new RectF(28f, 28f, WIDTH - 28f, HEIGHT - 28f), 36f, 36f, p);

        p.setStrokeWidth(3f);
        p.setColor(Color.rgb(198, 166, 124));
        p.setPathEffect(new DashPathEffect(new float[]{14f, 10f}, 0f));
        c.drawRoundRect(new RectF(46f, 46f, WIDTH - 46f, HEIGHT - 46f), 28f, 28f, p);
        p.setPathEffect(null);

        // 2. 頂部標題與郵戳
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(bold);
        p.setColor(ink);
        p.setTextSize(42f);
        c.drawText("走走冒險 · 實境明信片", 78f, 122f, p);

        p.setTypeface(regular);
        p.setColor(Color.rgb(112, 90, 72));
        p.setTextSize(28f);
        String seasonStr = WorldClock.seasonLabel(entry.season);
        c.drawText(entry.date + "  ·  " + seasonStr, 78f, 168f, p);

        // 右上角圓形步數郵戳
        float stampCx = WIDTH - 156f;
        float stampCy = 132f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5f);
        p.setColor(Color.rgb(212, 92, 62));
        c.drawCircle(stampCx, stampCy, 66f, p);
        p.setStrokeWidth(2f);
        c.drawCircle(stampCx, stampCy, 56f, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(bold);
        p.setTextSize(22f);
        c.drawText("探險郵戳", stampCx, stampCy - 14f, p);
        p.setTextSize(26f);
        c.drawText(entry.stepsOnDay + " 步", stampCx, stampCy + 18f, p);
        p.setTextSize(18f);
        c.drawText("WalkAdventure", stampCx, stampCy + 42f, p);
        p.setTextAlign(Paint.Align.LEFT);

        // 3. 中央實景照片＋孩子手指塗鴉疊圖
        RectF photoBox = new RectF(76f, 214f, WIDTH - 76f, 944f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(238, 224, 200));
        c.drawRoundRect(photoBox, 30f, 30f, p);

        Bitmap photo = mediaStore != null ? mediaStore.loadPreviewBitmap(entry.photoPath, 1080) : null;
        Bitmap doodle = mediaStore != null ? mediaStore.loadDoodleBitmap(entry.doodlePath) : null;
        if (photo != null) {
            Path clip = new Path();
            clip.addRoundRect(photoBox, 30f, 30f, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            Rect src = new Rect();
            DoodleCanvasView.computeCenterCropRect(
                photo.getWidth(), photo.getHeight(),
                Math.round(photoBox.width()), Math.round(photoBox.height()),
                src
            );
            Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            c.drawBitmap(photo, src, photoBox, bmpPaint);
            if (doodle != null) {
                c.drawBitmap(doodle, null, photoBox, bmpPaint);
                doodle.recycle();
            }
            c.restore();
            photo.recycle();
        } else {
            p.setColor(Color.rgb(120, 96, 76));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(34f);
            c.drawText("🌿 實境冒險回憶快照", photoBox.centerX(), photoBox.centerY(), p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        // 照片外框
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        p.setColor(Color.rgb(255, 252, 245));
        c.drawRoundRect(photoBox, 30f, 30f, p);

        // 4. 照片右下角探頭的進化旅伴徽章！
        if (entry.companionId > 0) {
            float petCx = photoBox.right - 74f;
            float petCy = photoBox.bottom - 30f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(255, 245, 224));
            c.drawCircle(petCx, petCy, 76f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5f);
            p.setColor(Color.rgb(235, 184, 92));
            c.drawCircle(petCx, petCy, 76f, p);
            assets.drawCompanion(
                c, petCx, petCy + 2f,
                entry.companionId,
                Math.max(1, entry.companionStage),
                entry.companionShiny,
                CompanionSystem.ACC_NONE,
                2.55f,
                0,
                ink
            );
        }

        // 5. 下方故事與親子印章卡片
        RectF footerCard = new RectF(76f, 976f, WIDTH - 76f, HEIGHT - 76f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(255, 251, 242));
        c.drawRoundRect(footerCard, 28f, 28f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(Color.rgb(230, 210, 180));
        c.drawRoundRect(footerCard, 28f, 28f, p);

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(bold);
        p.setColor(ink);
        p.setTextSize(42f);
        c.drawText(truncate(entry.title, 18), 112f, 1044f, p);

        // 星等與色彩徽章列
        StringBuilder starsSb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            starsSb.append(i < entry.stars ? "★" : "☆");
        }
        p.setColor(Color.rgb(235, 162, 32));
        p.setTextSize(38f);
        c.drawText(starsSb.toString(), 112f, 1102f, p);

        String colorBadge = entry.colorName.isEmpty() ? "🌈 實境探索" : ("🌈 " + entry.colorName);
        if (entry.natureCategory >= 0) {
            NatureVisionEngine.CategoryDef nc = NatureVisionEngine.category(entry.natureCategory);
            colorBadge += "  ·  " + nc.emoji + nc.badgeTitle;
        }
        p.setColor(Color.rgb(46, 107, 52));
        p.setTextSize(28f);
        c.drawText(truncate(colorBadge, 24), 266f, 1100f, p);

        // 童言童語或回憶短句（自動兩行折行）
        String caption = entry.note.isEmpty() ? "今天牽著手一起發現了現實世界的驚喜！" : ("「" + entry.note + "」");
        p.setTypeface(regular);
        p.setColor(Color.rgb(84, 62, 48));
        p.setTextSize(31f);
        drawWrappedText(c, caption, 112f, 1166f, footerCard.width() - 72f, 44f, 2, p);

        // 底部出隊成員印章與語音便條標記
        p.setTypeface(bold);
        p.setColor(Color.rgb(122, 92, 68));
        p.setTextSize(26f);
        String squadLine = String.format(
            Locale.TAIWAN,
            "👨‍👩‍👧 出隊小隊：%s%s",
            entry.squadLabel.isEmpty() ? "探險隊長" : entry.squadLabel,
            entry.audioPath.isEmpty() ? "" : "  ·  🎤 附親子語音便條"
        );
        c.drawText(truncate(squadLine, 32), 112f, HEIGHT - 114f, p);

        return out;
    }

    File exportShareablePng(StoryCatalog.MemoryEntry entry, MemoryMediaStore mediaStore) {
        Bitmap card = renderPostcard(entry, mediaStore);
        File target = MemoryFileProvider.sharePostcardFile(ctx);
        try (FileOutputStream fos = new FileOutputStream(target)) {
            if (card.compress(Bitmap.CompressFormat.PNG, 95, fos)) {
                card.recycle();
                return target;
            }
        } catch (IOException ignored) {
            // 匯出失敗回傳 null
        }
        card.recycle();
        return null;
    }

    private static void drawWrappedText(
        Canvas c,
        String text,
        float x,
        float startY,
        float maxWidth,
        float lineHeight,
        int maxLines,
        Paint paint
    ) {
        if (text == null || text.isEmpty()) return;
        int start = 0;
        int line = 0;
        while (start < text.length() && line < maxLines) {
            int count = paint.breakText(text, start, text.length(), true, maxWidth, null);
            if (count <= 0) break;
            String seg = text.substring(start, start + count);
            if (line == maxLines - 1 && start + count < text.length() && seg.length() > 1) {
                seg = seg.substring(0, seg.length() - 1) + "…";
            }
            c.drawText(seg, x, startY + line * lineHeight, paint);
            start += count;
            line++;
        }
    }

    private static String truncate(String s, int maxChars) {
        if (s == null) return "";
        return s.length() <= maxChars ? s : (s.substring(0, maxChars - 1) + "…");
    }
}
