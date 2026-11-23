package com.cornming.walkadventure;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.Locale;

/**
 * Phase 2 實境相機尋寶與冒險明信片工坊控制器：
 * 負責尋寶題目選擇、本機 HSV 分析回饋、親子星級裁判、手指塗鴉彈窗、
 * 15 秒語音便條錄製／試聽、以及 1080×1440 明信片全螢幕賞圖與分享。
 */
final class PostcardWorkshopController {
    interface Host {
        void onLaunchSystemCamera(int huntId);
        void onLaunchPhotoPicker(int huntId);
        boolean hasRecordAudioPermission();
        void onRequestRecordAudioPermission(Runnable onGranted);
        void onStateChanged(String toastMessage);
    }

    private final Context ctx;
    private final Host host;
    private final MemoryMediaStore mediaStore;
    private final PostcardRenderer renderer;

    PostcardWorkshopController(Context ctx, Host host, MemoryMediaStore mediaStore) {
        this.ctx = ctx;
        this.host = host;
        this.mediaStore = mediaStore;
        this.renderer = new PostcardRenderer(ctx);
    }

    /** 開啟 12 題相機尋寶選單（彩虹七色＋5 項自然親子觀察）。 */
    void showHuntPickerDialog(AdventureState state) {
        String[] labels = new String[ColorScavenger.HUNTS.length];
        for (int i = 0; i < ColorScavenger.HUNTS.length; i++) {
            ColorScavenger.HuntDef h = ColorScavenger.HUNTS[i];
            String prefix;
            if (h.isRainbowColorHunt()) {
                prefix = state.isRainbowColorCollected(h.targetColor) ? "✅ [已收集] " : "🌈 [彩虹] ";
            } else {
                prefix = "📸 [觀察] ";
            }
            labels[i] = prefix + h.title + "（+" + h.baseCoins + "金幣 +" + h.baseStarSand + "星砂）";
        }
        new AlertDialog.Builder(ctx)
            .setTitle("📸 選擇今天的實境相機尋寶任務")
            .setItems(labels, (d, which) -> showCaptureSourceDialog(ColorScavenger.HUNTS[which].id))
            .setNegativeButton("取消", null)
            .show();
    }

    /** 針對指定尋寶題目，讓家長孩子選擇「立即拍照」或「從相簿挑剛拍的照片」。 */
    void showCaptureSourceDialog(int huntId) {
        ColorScavenger.HuntDef hunt = ColorScavenger.huntById(huntId);
        new AlertDialog.Builder(ctx)
            .setTitle("📸 " + hunt.title)
            .setMessage(hunt.prompt + "\n\n（所有照片僅存於手機本機 App 私有空間，不上傳雲端，安心保護孩子隱私！）")
            .setPositiveButton("📷 開啟相機拍照", (d, w) -> host.onLaunchSystemCamera(huntId))
            .setNeutralButton("🖼️ 挑選已拍照片", (d, w) -> host.onLaunchPhotoPicker(huntId))
            .setNegativeButton("取消", null)
            .show();
    }

    /** 拍照或挑圖完成後開啟《實境明信片工坊》：HSV 色彩分析＋親子評星＋塗鴉＋語音便條。 */
    void showWorkshopDialog(
        StepProgress progress,
        AdventureState state,
        int huntId,
        String memoryId,
        String photoFilename,
        ColorScavenger.AnalysisResult analysis,
        WorldClock.Snapshot clock
    ) {
        ColorScavenger.HuntDef hunt = ColorScavenger.huntById(huntId);
        final int[] selectedStars = {analysis != null ? analysis.suggestedStars : 3};
        final String[] doodleFilename = {""};
        final String[] audioFilename = {""};

        ScrollView scroll = new ScrollView(ctx);
        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(UiKit.dp(ctx, 18), UiKit.dp(ctx, 12), UiKit.dp(ctx, 18), UiKit.dp(ctx, 8));
        scroll.addView(body);

        // 1. 照片預覽圖
        ImageView preview = new ImageView(ctx);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setContentDescription("剛拍下的尋寶照片預覽");
        Bitmap previewBmp = mediaStore.loadPreviewBitmap(photoFilename, 720);
        if (previewBmp != null) {
            preview.setImageBitmap(previewBmp);
        }
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 200)
        );
        prevLp.setMargins(0, 0, 0, UiKit.dp(ctx, 8));
        body.addView(preview, prevLp);

        // 2. 本機 HSV 色彩分析結果卡
        LinearLayout infoCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_GOLD);
        UiKit.addCardTitle(ctx, infoCard, "🔎 本機色彩偵測結果");
        UiKit.addCardSubtitle(ctx, infoCard, analysis != null ? analysis.feedbackText : "已收下照片！");
        body.addView(infoCard, UiKit.cardMargins(ctx));

        // 3. 親子裁判打星列（1★ / 2★ / 3★）
        TextView starHeader = new TextView(ctx);
        starHeader.setText("⭐ 親子裁判評星（由大人和孩子一起決定星等）：");
        starHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        starHeader.setTextColor(ctx.getColor(R.color.ink));
        starHeader.setPadding(0, UiKit.dp(ctx, 2), 0, UiKit.dp(ctx, 6));
        body.addView(starHeader);

        LinearLayout starRow = new LinearLayout(ctx);
        starRow.setOrientation(LinearLayout.HORIZONTAL);
        Button[] starBtns = new Button[3];
        Runnable refreshStars = () -> {
            for (int i = 0; i < 3; i++) {
                boolean active = (i + 1) == selectedStars[0];
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(UiKit.dp(ctx, 12));
                bg.setColor(active ? UiKit.COLOR_PRIMARY : UiKit.COLOR_CARD_MUTED);
                starBtns[i].setBackground(bg);
                starBtns[i].setTextColor(active ? UiKit.COLOR_PRIMARY_TEXT : ctx.getColor(R.color.ink));
            }
        };
        for (int i = 0; i < 3; i++) {
            final int starVal = i + 1;
            Button sb = UiKit.makeSmallPillButton(ctx, starVal == 1 ? "★ 1星" : (starVal == 2 ? "★★ 2星" : "★★★ 3星"),
                UiKit.COLOR_CARD_MUTED, ctx.getColor(R.color.ink));
            sb.setOnClickListener(v -> {
                selectedStars[0] = starVal;
                refreshStars.run();
            });
            starBtns[i] = sb;
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            slp.setMargins(UiKit.dp(ctx, 3), 0, UiKit.dp(ctx, 3), 0);
            starRow.addView(sb, slp);
        }
        refreshStars.run();
        LinearLayout.LayoutParams srLp = UiKit.matchWrap();
        srLp.setMargins(0, 0, 0, UiKit.dp(ctx, 10));
        body.addView(starRow, srLp);

        // 4. 塗鴉與 15 秒語音便條創作列
        Button doodleBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_WARM_BTN, ctx.getColor(R.color.ink));
        doodleBtn.setText("🎨 手指塗鴉");
        doodleBtn.setOnClickListener(v -> showDoodleDialog(photoFilename, memoryId, savedDoodle -> {
            doodleFilename[0] = savedDoodle;
            doodleBtn.setText(savedDoodle.isEmpty() ? "🎨 手指塗鴉" : "🎨 已加塗鴉 ✓");
        }));

        Button voiceBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_ACCENT_STAR, UiKit.COLOR_PRIMARY_TEXT);
        voiceBtn.setText("🎤 錄 15 秒語音");
        Runnable toggleRecordAction = () -> {
            if (mediaStore.isRecording()) {
                String savedAudio = mediaStore.stopVoiceRecording();
                audioFilename[0] = savedAudio;
                voiceBtn.setText(savedAudio.isEmpty() ? "🎤 錄 15 秒語音" : "🎧 已錄語音 ✓");
                host.onStateChanged(savedAudio.isEmpty() ? "錄音太短未儲存，可再試一次" : "🎤 語音便條錄製完成！");
            } else {
                boolean started = mediaStore.startVoiceRecording(memoryId, () -> voiceBtn.post(() -> {
                    audioFilename[0] = "voice_" + memoryId + ".m4a";
                    voiceBtn.setText("🎧 已錄語音 ✓");
                }));
                if (started) {
                    voiceBtn.setText("⏹️ 錄音中…點停止");
                } else {
                    host.onStateChanged("無法啟動麥克風錄音");
                }
            }
        };
        voiceBtn.setOnClickListener(v -> {
            if (!host.hasRecordAudioPermission()) {
                host.onRequestRecordAudioPermission(toggleRecordAction);
                return;
            }
            toggleRecordAction.run();
        });

        LinearLayout toolRow = UiKit.makeButtonRow(ctx, doodleBtn, voiceBtn);
        LinearLayout.LayoutParams trLp = UiKit.matchWrap();
        trLp.setMargins(0, 0, 0, UiKit.dp(ctx, 10));
        body.addView(toolRow, trLp);

        // 5. 童言童語輸入欄
        EditText noteInput = new EditText(ctx);
        noteInput.setHint("寫下孩子剛剛說的好玩的話或發現（選填）");
        body.addView(noteInput, UiKit.matchWrap());

        AlertDialog dialog = new AlertDialog.Builder(ctx)
            .setTitle("📮 實境明信片工坊 · " + hunt.title)
            .setView(scroll)
            .setCancelable(false)
            .setPositiveButton("💌 製成明信片領獎！", (d, w) -> {
                if (mediaStore.isRecording()) {
                    String savedAudio = mediaStore.stopVoiceRecording();
                    if (!savedAudio.isEmpty()) audioFilename[0] = savedAudio;
                }
                AdventureState.PhotoHuntOutcome outcome = state.completePhotoHunt(
                    progress,
                    memoryId,
                    huntId,
                    photoFilename,
                    doodleFilename[0],
                    audioFilename[0],
                    noteInput.getText().toString(),
                    selectedStars[0],
                    analysis,
                    clock
                );
                if (outcome.evictedOldest != null) {
                    mediaStore.deleteMemoryMedia(outcome.evictedOldest);
                }
                host.onStateChanged(outcome.message);
                if (outcome.ok && outcome.created != null) {
                    showPostcardViewerDialog(outcome.created, state);
                }
            })
            .setNegativeButton("捨棄照片", (d, w) -> {
                mediaStore.releaseAll();
                StoryCatalog.MemoryEntry temp = new StoryCatalog.MemoryEntry(
                    memoryId, "", "", "", photoFilename, audioFilename[0], doodleFilename[0],
                    "", huntId, "", -1, 1, 0L, 0, 1, false, 0
                );
                mediaStore.deleteMemoryMedia(temp);
            })
            .create();
        dialog.show();
    }

    private interface DoodleSavedCallback {
        void onDoodleSaved(String doodleFilename);
    }

    private void showDoodleDialog(String photoFilename, String memoryId, DoodleSavedCallback callback) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 10), UiKit.dp(ctx, 14), UiKit.dp(ctx, 6));

        DoodleCanvasView canvas = new DoodleCanvasView(ctx);
        Bitmap photo = mediaStore.loadPreviewBitmap(photoFilename, 720);
        canvas.setBackgroundPhoto(photo);
        box.addView(canvas, UiKit.matchWrap());

        // 6 色蠟筆選色列
        LinearLayout colorRow = new LinearLayout(ctx);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER);
        colorRow.setPadding(0, UiKit.dp(ctx, 8), 0, UiKit.dp(ctx, 6));
        for (int color : DoodleCanvasView.PALETTE) {
            View dot = new View(ctx);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(color);
            gd.setStroke(UiKit.dp(ctx, 2), Color.rgb(160, 138, 114));
            dot.setBackground(gd);
            dot.setContentDescription("選擇畫筆顏色");
            dot.setOnClickListener(v -> canvas.setStrokeColor(color));
            int sz = UiKit.dp(ctx, 34);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(sz, sz);
            dlp.setMargins(UiKit.dp(ctx, 5), 0, UiKit.dp(ctx, 5), 0);
            colorRow.addView(dot, dlp);
        }
        box.addView(colorRow, UiKit.matchWrap());

        // 粗細、復原、清空按鈕列
        final boolean[] thick = {false};
        Button brushBtn = UiKit.makeSmallPillButton(ctx, "細筆", UiKit.COLOR_CARD_MUTED, ctx.getColor(R.color.ink));
        brushBtn.setOnClickListener(v -> {
            thick[0] = !thick[0];
            canvas.setThickBrush(thick[0]);
            brushBtn.setText(thick[0] ? "粗筆" : "細筆");
        });
        Button undoBtn = UiKit.makeSmallPillButton(ctx, "↩️ 復原", UiKit.COLOR_WARM_BTN, ctx.getColor(R.color.ink));
        undoBtn.setOnClickListener(v -> canvas.undo());
        Button clearBtn = UiKit.makeSmallPillButton(ctx, "🧹 清空", UiKit.COLOR_CARD_MUTED, ctx.getColor(R.color.ink));
        clearBtn.setOnClickListener(v -> canvas.clear());

        LinearLayout ctrlRow = new LinearLayout(ctx);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        ctrlRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.setMargins(UiKit.dp(ctx, 3), 0, UiKit.dp(ctx, 3), 0);
        ctrlRow.addView(brushBtn, clp);
        ctrlRow.addView(undoBtn, new LinearLayout.LayoutParams(clp));
        ctrlRow.addView(clearBtn, new LinearLayout.LayoutParams(clp));
        box.addView(ctrlRow, UiKit.matchWrap());

        new AlertDialog.Builder(ctx)
            .setTitle("🎨 在照片上自由塗鴉！")
            .setView(box)
            .setPositiveButton("套用塗鴉", (d, w) -> {
                if (canvas.hasStrokes()) {
                    Bitmap overlay = canvas.renderTransparentOverlay(928, 730);
                    String saved = mediaStore.saveDoodleOverlay(overlay, memoryId);
                    if (overlay != null) overlay.recycle();
                    callback.onDoodleSaved(saved);
                } else {
                    callback.onDoodleSaved("");
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /** 完整顯示合成好的繪本風《實境冒險明信片》，支援播放語音便條、分享圖片與刪除。 */
    void showPostcardViewerDialog(StoryCatalog.MemoryEntry entry, AdventureState state) {
        if (entry == null) return;
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 10), UiKit.dp(ctx, 14), UiKit.dp(ctx, 8));
        scroll.addView(box);

        Bitmap rendered = renderer.renderPostcard(entry, mediaStore);
        ImageView iv = new ImageView(ctx);
        iv.setAdjustViewBounds(true);
        iv.setImageBitmap(rendered);
        iv.setContentDescription("實境冒險明信片：" + entry.title);
        box.addView(iv, UiKit.matchWrap());

        if (!entry.audioPath.isEmpty()) {
            Button playBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_ACCENT_STAR, UiKit.COLOR_PRIMARY_TEXT);
            playBtn.setText("🎧 播放這張明信片的親子語音便條");
            playBtn.setOnClickListener(v -> {
                playBtn.setText("🔊 播放中…");
                boolean ok = mediaStore.playVoiceNote(entry.audioPath, () ->
                    playBtn.post(() -> playBtn.setText("🎧 再聽一次親子語音便條"))
                );
                if (!ok) {
                    playBtn.setText("🎧 找不到語音檔案");
                }
            });
            LinearLayout.LayoutParams plp = UiKit.matchWrap();
            plp.setMargins(0, UiKit.dp(ctx, 10), 0, 0);
            box.addView(playBtn, plp);
        }

        AlertDialog viewer = new AlertDialog.Builder(ctx)
            .setTitle("📮 " + entry.title + "（" + entry.date + "）")
            .setView(scroll)
            .setPositiveButton("📤 分享明信片", (d, w) -> sharePostcard(entry))
            .setNeutralButton("🗑️ 刪除", (d, w) -> confirmDeleteMemory(entry, state))
            .setNegativeButton("關閉", (d, w) -> mediaStore.stopVoicePlayback())
            .create();
        viewer.setOnDismissListener(d -> mediaStore.stopVoicePlayback());
        viewer.show();
    }

    private void sharePostcard(StoryCatalog.MemoryEntry entry) {
        File png = renderer.exportShareablePng(entry, mediaStore);
        if (png == null) {
            host.onStateChanged("無法產生分享圖片，請稍後再試");
            return;
        }
        Uri uri = MemoryFileProvider.uriForSharePostcard(ctx);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(MemoryFileProvider.MIME_PNG);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_TEXT, String.format(
            Locale.TAIWAN,
            "【走走冒險 實境明信片｜%s】%s（出隊：%s）",
            entry.date, entry.title, entry.squadLabel
        ));
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            ctx.startActivity(Intent.createChooser(share, "分享這張實境冒險明信片"));
        } catch (ActivityNotFoundException ignored) {
            host.onStateChanged("找不到可用的分享應用程式");
        }
    }

    private void confirmDeleteMemory(StoryCatalog.MemoryEntry entry, AdventureState state) {
        new AlertDialog.Builder(ctx)
            .setTitle("刪除這張回憶明信片？")
            .setMessage("刪除後會移除本機照片與語音檔案，但保留已獲得的金幣、星砂與彩虹圖鑑進度。")
            .setPositiveButton("確認刪除", (d, w) -> {
                StoryCatalog.MemoryEntry removed = state.stories.removeMemoryById(entry.id);
                if (removed != null) {
                    mediaStore.deleteMemoryMedia(removed);
                }
                host.onStateChanged("已刪除明信片「" + entry.title + "」");
            })
            .setNegativeButton("保留", null)
            .show();
    }
}
