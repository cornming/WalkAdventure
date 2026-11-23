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
import java.util.List;
import java.util.Locale;

/**
 * Phase 3 & 4 繪本風高解析長圖與 QR 卡片合成器（純 Android Canvas、零外部依賴）：
 * 1) 《年度家庭冒險回憶長圖冊》：1080px 寬動態高度海報，彙整全家總步數與公里數、圖鑑進度、至多 6 張精選明信片與童言童語
 * 2) 《離線 QR 實體藏寶卡／家族禮物卡》：960 × 1260 繪本風可列印／分享卡片，內嵌標準可掃描 QR Code 與可愛備用短碼
 */
final class YearbookRenderer {
    static final int YEARBOOK_WIDTH = 1080;
    static final int QR_CARD_WIDTH = 960;
    static final int QR_CARD_HEIGHT = 1260;

    private final Context ctx;
    private final StorybookAssets assets;

    YearbookRenderer(Context context) {
        this.ctx = context.getApplicationContext();
        this.assets = new StorybookAssets(context);
    }

    /** 合成《家庭年度冒險回憶長圖冊》（動態高度，安全上限 2900px 避免低階機 OOM）。 */
    Bitmap renderYearbookPoster(
        StepProgress progress,
        AdventureState state,
        MemoryMediaStore mediaStore,
        WorldClock.Snapshot clock
    ) {
        List<StoryCatalog.MemoryEntry> memories = state.stories.memories;
        int showCards = Math.min(6, memories.size());
        int rows = (showCards + 1) / 2;
        int headerH = 560;
        int cardRowH = 490;
        int footerH = 310;
        int emptyH = showCards == 0 ? 220 : 0;
        int totalHeight = Math.min(3100, headerH + rows * cardRowH + emptyH + footerH);

        Bitmap out = Bitmap.createBitmap(YEARBOOK_WIDTH, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Typeface bold = Typeface.create("sans", Typeface.BOLD);
        Typeface regular = Typeface.create("sans", Typeface.NORMAL);
        int ink = Color.rgb(58, 42, 32);

        // 1. 暖米紙底與雙層繪本框
        c.drawColor(Color.rgb(255, 247, 232));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(7f);
        p.setColor(Color.rgb(225, 198, 158));
        c.drawRoundRect(new RectF(26f, 26f, YEARBOOK_WIDTH - 26f, totalHeight - 26f), 36f, 36f, p);

        // 2. 封面頂部橫幅
        RectF coverBox = new RectF(64f, 64f, YEARBOOK_WIDTH - 64f, 496f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(255, 236, 194));
        c.drawRoundRect(coverBox, 32f, 32f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setColor(Color.rgb(230, 178, 84));
        c.drawRoundRect(coverBox, 32f, 32f, p);

        // 慶典彩旗裝飾
        int[] flags = {0xFFE86688, 0xFFF5BF42, 0xFF548FD6, 0xFF70AF74, 0xFFED8B3B};
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 12; i++) {
            float fx = 96f + i * 74f;
            p.setColor(flags[i % flags.length]);
            Path tri = new Path();
            tri.moveTo(fx, 68f);
            tri.lineTo(fx + 46f, 68f);
            tri.lineTo(fx + 23f, 102f);
            tri.close();
            c.drawPath(tri, p);
        }

        p.setTypeface(bold);
        p.setColor(ink);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(48f);
        c.drawText("走走冒險 · 家庭回憶畫冊", YEARBOOK_WIDTH * 0.5f, 178f, p);

        p.setTypeface(regular);
        p.setColor(Color.rgb(112, 86, 66));
        p.setTextSize(28f);
        String yearStr = clock != null && clock.isoDate.length() >= 4 ? clock.isoDate.substring(0, 4) : "2026";
        String squadLine = yearStr + " 年度特輯  ·  出隊小隊：" + truncate(state.family.formatActiveSquadLabel(), 18);
        c.drawText(squadLine, YEARBOOK_WIDTH * 0.5f, 224f, p);

        // 四格年度數據儀表板
        double km = (progress != null ? progress.lifetime : 0L) * 0.00065;
        String[] statTitles = {
            String.format(Locale.TAIWAN, "%,d 步", progress != null ? progress.lifetime : 0L),
            String.format(Locale.TAIWAN, "%.1f 公里", km),
            String.format(Locale.TAIWAN, "%d / %d 類", state.natureDex.unlockedCount(), NatureVisionEngine.CATEGORY_COUNT),
            String.format(Locale.TAIWAN, "%d 張 / %d 格", memories.size(), state.footprints.discoveredCount())
        };
        String[] statSubs = {"累計真實步數", "全家散步里程", "自然生態圖鑑", "明信片／迷霧格"};
        float boxW = (coverBox.width() - 84f) / 4f;
        for (int i = 0; i < 4; i++) {
            float bx = coverBox.left + 24f + i * (boxW + 12f);
            RectF sb = new RectF(bx, 260f, bx + boxW, 456f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(255, 251, 242));
            c.drawRoundRect(sb, 22f, 22f, p);
            p.setTypeface(bold);
            p.setColor(Color.rgb(46, 107, 52));
            p.setTextSize(30f);
            c.drawText(statTitles[i], sb.centerX(), sb.centerY() - 8f, p);
            p.setTypeface(regular);
            p.setColor(Color.rgb(112, 90, 72));
            p.setTextSize(22f);
            c.drawText(statSubs[i], sb.centerX(), sb.centerY() + 34f, p);
        }

        // 3. 精選明信片雙欄畫廊
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(bold);
        p.setColor(ink);
        p.setTextSize(34f);
        c.drawText("📸 精選實境冒險回憶（彩虹 " + state.rainbowCollectedCount() + "/7 · 旅伴 "
            + state.companions.ownedCount() + "/12）", 68f, 542f, p);

        if (showCards == 0) {
            RectF emptyBox = new RectF(64f, 574f, YEARBOOK_WIDTH - 64f, 750f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(248, 238, 218));
            c.drawRoundRect(emptyBox, 26f, 26f, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(regular);
            p.setColor(Color.rgb(118, 94, 74));
            p.setTextSize(28f);
            c.drawText("帶孩子拍下第一張「實境相機尋寶」照片，", emptyBox.centerX(), emptyBox.centerY() - 12f, p);
            c.drawText("這裡就會自動拼貼出滿滿的家庭散步畫冊喔！", emptyBox.centerX(), emptyBox.centerY() + 34f, p);
            p.setTextAlign(Paint.Align.LEFT);
        } else {
            float colW = (YEARBOOK_WIDTH - 64f * 2 - 28f) * 0.5f;
            for (int i = 0; i < showCards; i++) {
                StoryCatalog.MemoryEntry mem = memories.get(i);
                int row = i / 2;
                int col = i % 2;
                float left = 64f + col * (colW + 28f);
                float top = 568f + row * cardRowH;
                drawMemoryCollageCard(c, p, bold, regular, ink, left, top, colW, cardRowH - 24f, mem, mediaStore);
            }
        }

        // 4. 底部進化旅伴印章與年度結語
        float footTop = totalHeight - footerH + 24f;
        RectF footCard = new RectF(64f, footTop, YEARBOOK_WIDTH - 64f, totalHeight - 56f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(233, 245, 227));
        c.drawRoundRect(footCard, 28f, 28f, p);

        int cid = state.activeCompanionId();
        CompanionSystem.OwnedCompanion oc = state.companions.getOwned(cid);
        if (cid != CompanionSystem.ID_NONE) {
            assets.drawCompanion(
                c, footCard.left + 92f, footCard.centerY() + 4f,
                cid, oc != null ? oc.stage : 1, oc != null && oc.shiny,
                oc != null ? oc.accessory : CompanionSystem.ACC_NONE,
                2.2f, 0, ink
            );
        }

        float tx = cid != CompanionSystem.ID_NONE ? (footCard.left + 178f) : (footCard.left + 36f);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(bold);
        p.setColor(Color.rgb(46, 107, 52));
        p.setTextSize(32f);
        c.drawText("「你走的每一步，都是全家的冒險與回憶」", tx, footCard.top + 78f, p);

        p.setTypeface(regular);
        p.setColor(ink);
        p.setTextSize(25f);
        String summaryFooter = String.format(
            Locale.TAIWAN,
            "🔥 連勝最佳 %d 天 · 🎁 實體尋寶 %d 次 · 📖 讀完 %d 本故事",
            state.bestStreakDays,
            state.treasure.totalTreasuresClaimed,
            state.stories.completedArcCount()
        );
        c.drawText(summaryFooter, tx, footCard.top + 128f, p);
        p.setColor(Color.rgb(112, 90, 72));
        p.setTextSize(22f);
        c.drawText("WalkAdventure 2.0 · 100% 本機離線珍藏製作 · " + (clock != null ? clock.isoDate : ""), tx, footCard.top + 172f, p);

        return out;
    }

    private void drawMemoryCollageCard(
        Canvas c,
        Paint p,
        Typeface bold,
        Typeface regular,
        int ink,
        float left,
        float top,
        float w,
        float h,
        StoryCatalog.MemoryEntry mem,
        MemoryMediaStore mediaStore
    ) {
        RectF box = new RectF(left, top, left + w, top + h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(255, 251, 242));
        c.drawRoundRect(box, 24f, 24f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(Color.rgb(228, 210, 182));
        c.drawRoundRect(box, 24f, 24f, p);

        RectF photoBox = new RectF(left + 18f, top + 18f, left + w - 18f, top + 286f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(236, 224, 200));
        c.drawRoundRect(photoBox, 18f, 18f, p);

        Bitmap photo = mediaStore != null ? mediaStore.loadPreviewBitmap(mem.photoPath, 480) : null;
        if (photo != null) {
            Path clip = new Path();
            clip.addRoundRect(photoBox, 18f, 18f, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            Rect src = new Rect();
            DoodleCanvasView.computeCenterCropRect(
                photo.getWidth(), photo.getHeight(),
                Math.round(photoBox.width()), Math.round(photoBox.height()),
                src
            );
            Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            c.drawBitmap(photo, src, photoBox, bp);
            Bitmap doodle = mediaStore.loadDoodleBitmap(mem.doodlePath);
            if (doodle != null) {
                c.drawBitmap(doodle, null, photoBox, bp);
                doodle.recycle();
            }
            c.restore();
            photo.recycle();
        }

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(bold);
        p.setColor(ink);
        p.setTextSize(26f);
        c.drawText(truncate(mem.title, 13), left + 22f, top + 328f, p);

        String natureTag = mem.natureCategory >= 0
            ? (" · " + NatureVisionEngine.category(mem.natureCategory).emoji + NatureVisionEngine.category(mem.natureCategory).name)
            : "";
        p.setTypeface(regular);
        p.setColor(Color.rgb(112, 90, 72));
        p.setTextSize(21f);
        c.drawText(mem.date + natureTag, left + 22f, top + 364f, p);

        String quote = mem.note.isEmpty() ? "🌈 " + mem.colorName + " · " + "★".repeat(Math.max(1, mem.stars))
            : "「" + truncate(mem.note, 16) + "」";
        p.setColor(Color.rgb(66, 98, 66));
        p.setTextSize(22f);
        c.drawText(quote, left + 22f, top + 404f, p);

        p.setColor(Color.rgb(138, 112, 90));
        p.setTextSize(19f);
        c.drawText("👨‍👩‍👧 " + truncate(mem.squadLabel.isEmpty() ? "探險隊長" : mem.squadLabel, 18), left + 22f, top + 440f, p);
    }

    /** 合成《離線 QR 藏寶卡 / 家族禮物卡》繪本分享圖（960 × 1260，可列印剪下或傳給家人）。 */
    Bitmap renderQrCardBitmap(
        String headerBadge,
        String mainTitle,
        String description,
        String qrPayload,
        String backupCodeText,
        int accentRgb
    ) {
        Bitmap out = Bitmap.createBitmap(QR_CARD_WIDTH, QR_CARD_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Typeface bold = Typeface.create("sans", Typeface.BOLD);
        Typeface regular = Typeface.create("sans", Typeface.NORMAL);
        int ink = Color.rgb(58, 42, 32);

        c.drawColor(Color.rgb(255, 247, 232));

        // 雙層圓角繪本邊框
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(7f);
        p.setColor(accentRgb);
        c.drawRoundRect(new RectF(28f, 28f, QR_CARD_WIDTH - 28f, QR_CARD_HEIGHT - 28f), 36f, 36f, p);
        p.setStrokeWidth(3f);
        p.setPathEffect(new DashPathEffect(new float[]{14f, 10f}, 0f));
        c.drawRoundRect(new RectF(48f, 48f, QR_CARD_WIDTH - 48f, QR_CARD_HEIGHT - 48f), 28f, 28f, p);
        p.setPathEffect(null);

        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(bold);
        p.setColor(accentRgb);
        p.setTextSize(32f);
        c.drawText(headerBadge, QR_CARD_WIDTH * 0.5f, 118f, p);

        p.setColor(ink);
        p.setTextSize(46f);
        c.drawText(truncate(mainTitle, 16), QR_CARD_WIDTH * 0.5f, 186f, p);

        // QR 碼白底卡片
        Bitmap qrBmp = QrCodeGenerator.renderBitmap(qrPayload, 520, Color.rgb(42, 30, 22), Color.WHITE);
        float qrSize = 540f;
        float qrLeft = (QR_CARD_WIDTH - qrSize) * 0.5f;
        float qrTop = 236f;
        RectF qrFrame = new RectF(qrLeft - 20f, qrTop - 20f, qrLeft + qrSize + 20f, qrTop + qrSize + 20f);
        p.setColor(Color.WHITE);
        c.drawRoundRect(qrFrame, 28f, 28f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setColor(Color.rgb(230, 212, 186));
        c.drawRoundRect(qrFrame, 28f, 28f, p);

        Paint bmpPaint = new Paint();
        bmpPaint.setFilterBitmap(false); // 保持 QR 方塊銳利不模糊！
        c.drawBitmap(qrBmp, null, new RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize), bmpPaint);
        qrBmp.recycle();

        // 謎語／祝福卡區塊
        RectF descBox = new RectF(82f, 834f, QR_CARD_WIDTH - 82f, 1044f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(255, 251, 242));
        c.drawRoundRect(descBox, 24f, 24f, p);

        p.setTypeface(regular);
        p.setColor(ink);
        p.setTextSize(30f);
        drawCenteredWrapped(c, description, descBox.centerX(), descBox.top + 66f, descBox.width() - 56f, 44f, 3, p);

        // 底部備用短碼／說明
        p.setTypeface(bold);
        p.setColor(accentRgb);
        p.setTextSize(32f);
        c.drawText(truncate(backupCodeText, 30), QR_CARD_WIDTH * 0.5f, 1118f, p);

        p.setTypeface(regular);
        p.setColor(Color.rgb(120, 96, 76));
        p.setTextSize(23f);
        c.drawText("走走冒險 2.0 · 100% 離線親子實境互動卡", QR_CARD_WIDTH * 0.5f, 1184f, p);
        p.setTextAlign(Paint.Align.LEFT);
        return out;
    }

    File exportShareableYearbookPng(
        StepProgress progress,
        AdventureState state,
        MemoryMediaStore mediaStore,
        WorldClock.Snapshot clock
    ) {
        Bitmap bmp = renderYearbookPoster(progress, state, mediaStore, clock);
        return writePng(bmp, MemoryFileProvider.shareYearbookFile(ctx));
    }

    File exportShareableQrCardPng(
        String headerBadge,
        String mainTitle,
        String description,
        String qrPayload,
        String backupCodeText,
        int accentRgb
    ) {
        Bitmap bmp = renderQrCardBitmap(headerBadge, mainTitle, description, qrPayload, backupCodeText, accentRgb);
        return writePng(bmp, MemoryFileProvider.shareQrCardFile(ctx));
    }

    private static File writePng(Bitmap bmp, File target) {
        try (FileOutputStream fos = new FileOutputStream(target)) {
            if (bmp.compress(Bitmap.CompressFormat.PNG, 94, fos)) {
                bmp.recycle();
                return target;
            }
        } catch (IOException ignored) {
            // 寫檔失敗回傳 null
        }
        bmp.recycle();
        return null;
    }

    private static void drawCenteredWrapped(
        Canvas c,
        String text,
        float cx,
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
            c.drawText(seg, cx, startY + line * lineHeight, paint);
            start += count;
            line++;
        }
    }

    private static String truncate(String s, int maxChars) {
        if (s == null) return "";
        return s.length() <= maxChars ? s : (s.substring(0, maxChars - 1) + "…");
    }
}
