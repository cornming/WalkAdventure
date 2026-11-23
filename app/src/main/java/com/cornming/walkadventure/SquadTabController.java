package com.cornming.walkadventure;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import java.util.Locale;

/**
 * 「小隊」分頁控制器：
 * 家庭小隊護照（1–6 人新增／編輯／出隊勾選）、營火自動鎖存開關、近 7 天步數戰報、
 * 每日步數任務、四季印章護照、20 項成就稱號、冒險日誌與本機存檔 JSON 備份匯出／還原。
 */
final class SquadTabController {
    interface Host {
        void onStateChanged(String toastMessage);
        void onCopyReportRequested();
        void onBackupRestored(String rawJson);
    }

    private final Context ctx;
    private final Host host;

    SquadTabController(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
    }

    void render(
        LinearLayout container,
        StepProgress progress,
        AdventureState state,
        WorldClock.Snapshot clock
    ) {
        container.removeAllViews();
        String today = clock.isoDate;

        // 1. 家庭小隊護照（純本機、無須帳號）
        UiKit.addSectionHeader(ctx, container, "👨‍👩‍👧 家庭小隊護照（勾選今日一起出門的成員）");
        LinearLayout squadCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_GOLD);
        UiKit.addCardTitle(ctx, squadCard, "今日出隊：" + state.family.formatActiveSquadLabel());
        UiKit.addCardSubtitle(ctx, squadCard, "同一台手機支援 1～6 位家人，填寫生日當天營地還會掛彩旗送生日禮！");

        for (FamilyProfile.Member m : state.family.members) {
            final FamilyProfile.Member member = m;
            LinearLayout mRow = new LinearLayout(ctx);
            mRow.setOrientation(LinearLayout.HORIZONTAL);
            mRow.setPadding(0, UiKit.dp(ctx, 4), 0, UiKit.dp(ctx, 4));

            CheckBox cb = new CheckBox(ctx);
            String roleTag = FamilyProfile.ROLE_CHILD.equals(member.role) ? "小小探險家" : "大人隊長";
            String bdayTag = member.birthday.isEmpty() ? "" : (" · 🎂" + member.birthday);
            cb.setText(member.name + "（" + roleTag + bdayTag + "）");
            cb.setTextColor(ctx.getColor(R.color.ink));
            cb.setChecked(state.family.activeSquadIds.contains(member.id));
            cb.setOnClickListener(v -> {
                boolean changed = state.family.toggleSquadMember(member.id);
                if (!changed) {
                    cb.setChecked(true);
                    host.onStateChanged("至少需要保留一位出隊成員喔！");
                } else {
                    host.onStateChanged("已更新今日出隊名單：" + state.family.formatActiveSquadLabel());
                }
            });
            mRow.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button editBtn = UiKit.makeSmallPillButton(ctx, "編輯", UiKit.COLOR_CARD_BG, ctx.getColor(R.color.ink));
            editBtn.setOnClickListener(v -> showMemberEditorDialog(state, member));
            mRow.addView(editBtn);
            squadCard.addView(mRow, UiKit.matchWrap());
        }

        Button addMemberBtn = UiKit.makeActionButton(
            ctx,
            state.family.members.size() < FamilyProfile.MAX_MEMBERS ? UiKit.COLOR_PRIMARY : UiKit.COLOR_DISABLED_BG,
            state.family.members.size() < FamilyProfile.MAX_MEMBERS ? UiKit.COLOR_PRIMARY_TEXT : UiKit.COLOR_DISABLED_TEXT
        );
        addMemberBtn.setText("➕ 新增家庭成員（" + state.family.members.size() + "/" + FamilyProfile.MAX_MEMBERS + "）");
        addMemberBtn.setEnabled(state.family.members.size() < FamilyProfile.MAX_MEMBERS);
        addMemberBtn.setOnClickListener(v -> showMemberEditorDialog(state, null));
        LinearLayout.LayoutParams addLp = UiKit.matchWrap();
        addLp.setMargins(0, UiKit.dp(ctx, 6), 0, UiKit.dp(ctx, 6));
        squadCard.addView(addMemberBtn, addLp);

        CheckBox autoVaultCb = new CheckBox(ctx);
        autoVaultCb.setText("平日模式自動把步數寶箱＆神祕蛋存進「營火盒」，留給晚上跟孩子一起開箱享加成");
        autoVaultCb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        autoVaultCb.setTextColor(ctx.getColor(R.color.ink));
        autoVaultCb.setChecked(state.family.autoVaultBigRewards);
        autoVaultCb.setOnClickListener(v -> {
            state.family.autoVaultBigRewards = autoVaultCb.isChecked();
            host.onStateChanged(state.family.autoVaultBigRewards
                ? "🔥 已開啟平日營火盒自動鎖存！"
                : "🎁 已改為步數寶箱立即發放");
        });
        squadCard.addView(autoVaultCb);
        container.addView(squadCard, UiKit.cardMargins(ctx));

        // 2. 近 7 天步數與戰報圖表
        LinearLayout chartCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
        UiKit.addCardTitle(ctx, chartCard, "📊 近 7 天步數與冒險戰報");
        UiKit.addCardSubtitle(ctx, chartCard, String.format(
            Locale.TAIWAN,
            "🔥 連續達標 %d 天（最佳 %d 天） · 累計 %d 步 · 完成 %d 次奇遇",
            state.effectiveStreak(today),
            state.bestStreakDays,
            progress.lifetime,
            state.encountersResolved
        ));
        WeeklyChartView chart = new WeeklyChartView(ctx);
        chart.setRecords(state.lastSevenDays(progress, today));
        LinearLayout.LayoutParams chartLp = UiKit.matchWrap();
        chartLp.setMargins(0, UiKit.dp(ctx, 8), 0, UiKit.dp(ctx, 8));
        chartCard.addView(chart, chartLp);

        Button copyBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_PRIMARY, UiKit.COLOR_PRIMARY_TEXT);
        copyBtn.setText(R.string.copy_report);
        copyBtn.setOnClickListener(v -> host.onCopyReportRequested());
        chartCard.addView(copyBtn, UiKit.matchWrap());
        container.addView(chartCard, UiKit.cardMargins(ctx));

        // 3. 今日步數任務
        UiKit.addSectionHeader(ctx, container, "🎯 今日步數任務");
        long todaySteps = progress.todayStepsOn(today);
        for (AdventureCatalog.DailyQuestDef def : AdventureCatalog.DAILY_QUESTS) {
            boolean reached = todaySteps >= def.targetSteps;
            boolean claimed = state.isDailyQuestClaimed(def.index, today);
            LinearLayout qCard = UiKit.makeSectionCard(ctx, claimed ? UiKit.COLOR_CARD_GREEN : UiKit.COLOR_CARD_BG);
            UiKit.addCardTitle(ctx, qCard, String.format(
                Locale.TAIWAN,
                "%s（%d / %d 步）",
                def.title,
                Math.min(todaySteps, def.targetSteps),
                def.targetSteps
            ));
            int previewCoins = state.dailyQuestCoinReward(def.index);
            UiKit.addCardSubtitle(ctx, qCard, String.format(
                Locale.TAIWAN,
                "獎勵：+%d 金幣、+%d 冒險 XP、+%d 星砂",
                previewCoins,
                def.xpReward,
                4 + def.index * 3
            ));
            qCard.addView(UiKit.makeHorizontalProgress(ctx, (int) def.targetSteps, (int) Math.min(todaySteps, def.targetSteps)));

            Button qBtn = UiKit.makeActionButton(
                ctx,
                claimed ? UiKit.COLOR_CARD_GREEN : (reached ? UiKit.COLOR_PRIMARY : UiKit.COLOR_DISABLED_BG),
                claimed ? UiKit.COLOR_PRIMARY : (reached ? UiKit.COLOR_PRIMARY_TEXT : UiKit.COLOR_DISABLED_TEXT)
            );
            qBtn.setText(claimed ? "今日已領取 ✓" : (reached ? "領取任務獎勵！" : "繼續散步以達成"));
            qBtn.setEnabled(reached && !claimed);
            qBtn.setOnClickListener(v -> {
                AdventureState.ActionResult res = state.claimDailyQuest(progress, def.index, today);
                host.onStateChanged(res.message);
            });
            qCard.addView(qBtn, UiKit.matchWrap());
            container.addView(qCard, UiKit.cardMargins(ctx));
        }

        // 4. 四季護照印章
        UiKit.addSectionHeader(ctx, container, String.format(
            Locale.TAIWAN,
            "🌸 %s 護照印章（%d / %d）",
            WorldClock.seasonLabel(clock.season),
            state.stories.seasonStampCount(),
            StoryCatalog.SEASON_STAMP_COUNT
        ));
        LinearLayout stampCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
        for (StoryCatalog.SeasonStampDef st : StoryCatalog.SEASON_STAMPS) {
            boolean got = state.stories.isSeasonStampUnlocked(st.index);
            TextView tv = new TextView(ctx);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setTextColor(got ? UiKit.COLOR_PRIMARY : UiKit.COLOR_SUBTITLE);
            tv.setPadding(0, UiKit.dp(ctx, 2), 0, UiKit.dp(ctx, 2));
            tv.setText((got ? "💮 " : "⬜ ") + st.title + " — " + st.condition);
            stampCard.addView(tv);
        }
        container.addView(stampCard, UiKit.cardMargins(ctx));

        // 5. 成就稱號徽章（20 項：10 經典 + 10 項 2.0 親子／營地成就）
        int unlockedTotal = 0;
        for (int i = 0; i < AdventureCatalog.ACHIEVEMENTS.length; i++) {
            if (state.isAchievementUnlocked(i)) unlockedTotal++;
        }
        for (int i = 0; i < AdventureCatalog.EXTRA_ACHIEVEMENTS.length; i++) {
            if (state.isExtraAchievementUnlocked(i)) unlockedTotal++;
        }
        UiKit.addSectionHeader(ctx, container, String.format(
            Locale.TAIWAN,
            "🏆 成就稱號徽章（已解鎖 %d / %d）",
            unlockedTotal,
            AdventureCatalog.ACHIEVEMENTS.length + AdventureCatalog.EXTRA_ACHIEVEMENTS.length
        ));
        if (state.pioneerBadge) {
            LinearLayout pCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_GOLD);
            UiKit.addCardTitle(ctx, pCard, "🏅 元老開拓者徽章（限定紀念）");
            UiKit.addCardSubtitle(ctx, pCard, "感謝你從舊版一路陪伴走走冒險進化到 2.0！");
            container.addView(pCard, UiKit.cardMargins(ctx));
        }
        for (AdventureCatalog.AchievementDef def : AdventureCatalog.EXTRA_ACHIEVEMENTS) {
            boolean unlocked = state.isExtraAchievementUnlocked(def.index);
            LinearLayout card = UiKit.makeSectionCard(ctx, unlocked ? UiKit.COLOR_CARD_GOLD : UiKit.COLOR_CARD_MUTED);
            UiKit.addCardTitle(ctx, card, String.format(
                Locale.TAIWAN, "%s %s（+%d 金幣、+10 星砂）", unlocked ? "🌟" : "🔒", def.title, def.coinReward
            ));
            UiKit.addCardSubtitle(ctx, card, def.conditionText);
            container.addView(card, UiKit.cardMargins(ctx));
        }
        for (AdventureCatalog.AchievementDef def : AdventureCatalog.ACHIEVEMENTS) {
            boolean unlocked = state.isAchievementUnlocked(def.index);
            LinearLayout card = UiKit.makeSectionCard(ctx, unlocked ? UiKit.COLOR_CARD_GOLD : UiKit.COLOR_CARD_MUTED);
            UiKit.addCardTitle(ctx, card, String.format(
                Locale.TAIWAN, "%s %s（+%d 金幣）", unlocked ? "🏆" : "🔒", def.title, def.coinReward
            ));
            UiKit.addCardSubtitle(ctx, card, def.conditionText);
            container.addView(card, UiKit.cardMargins(ctx));
        }

        // 6. 冒險日誌
        UiKit.addSectionHeader(ctx, container, "📜 冒險日誌紀錄");
        LinearLayout logCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
        if (state.adventureLog.isEmpty()) {
            UiKit.addCardSubtitle(ctx, logCard, "目前還沒有冒險日誌，跨出第一步就能紀錄奇遇囉！");
        } else {
            for (AdventureState.LogEntry entry : state.adventureLog) {
                TextView tv = new TextView(ctx);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                tv.setTextColor(ctx.getColor(R.color.ink));
                tv.setPadding(0, UiKit.dp(ctx, 2), 0, UiKit.dp(ctx, 2));
                tv.setText(entry.date.isEmpty() ? entry.summary : (entry.date + " · " + entry.summary));
                logCard.addView(tv);
            }
        }
        container.addView(logCard, UiKit.cardMargins(ctx));

        // 7. 本機存檔離線備份與還原（換機安心保底）
        UiKit.addSectionHeader(ctx, container, "💾 本機存檔離線備份");
        LinearLayout backupCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
        UiKit.addCardSubtitle(ctx, backupCard, "所有資料皆純本機儲存、不傳雲端。換手機前可複製 2.0 營地備份碼保存。");
        Button exportBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_WARM_BTN, ctx.getColor(R.color.ink));
        exportBtn.setText("📋 複製備份 JSON");
        exportBtn.setOnClickListener(v -> {
            String json = SaveCodec.serialize(state);
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("WalkAdventure v2 Backup", json));
                host.onStateChanged("已複製 2.0 營地備份資料到剪貼簿！");
            }
        });
        Button importBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_CARD_MUTED, ctx.getColor(R.color.ink));
        importBtn.setText("📥 貼上還原備份");
        importBtn.setOnClickListener(v -> showImportBackupDialog());
        LinearLayout bRow = UiKit.makeButtonRow(ctx, exportBtn, importBtn);
        LinearLayout.LayoutParams bLp = UiKit.matchWrap();
        bLp.setMargins(0, UiKit.dp(ctx, 8), 0, 0);
        backupCard.addView(bRow, bLp);
        container.addView(backupCard, UiKit.cardMargins(ctx));
    }

    private void showMemberEditorDialog(AdventureState state, FamilyProfile.Member existing) {
        LinearLayout form = new LinearLayout(ctx);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(UiKit.dp(ctx, 20), UiKit.dp(ctx, 12), UiKit.dp(ctx, 20), UiKit.dp(ctx, 4));

        EditText nameInput = new EditText(ctx);
        nameInput.setHint("暱稱（例：爸爸、大寶、小寶）");
        if (existing != null) nameInput.setText(existing.name);
        form.addView(nameInput);

        EditText bdayInput = new EditText(ctx);
        bdayInput.setHint("生日月-日（選填，例：05-18）");
        bdayInput.setInputType(InputType.TYPE_CLASS_TEXT);
        if (existing != null && !existing.birthday.isEmpty()) bdayInput.setText(existing.birthday);
        form.addView(bdayInput);

        RadioGroup roleGroup = new RadioGroup(ctx);
        roleGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbAdult = new RadioButton(ctx);
        rbAdult.setText("大人隊長");
        RadioButton rbChild = new RadioButton(ctx);
        rbChild.setText("小小探險家");
        roleGroup.addView(rbAdult);
        roleGroup.addView(rbChild);
        boolean isChild = existing != null && FamilyProfile.ROLE_CHILD.equals(existing.role);
        rbChild.setChecked(isChild);
        rbAdult.setChecked(!isChild);
        form.addView(roleGroup);

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
            .setTitle(existing == null ? "新增家庭成員" : "編輯成員護照")
            .setView(form)
            .setPositiveButton("儲存", (d, w) -> {
                String name = nameInput.getText().toString();
                String bday = bdayInput.getText().toString();
                String role = rbChild.isChecked() ? FamilyProfile.ROLE_CHILD : FamilyProfile.ROLE_ADULT;
                int color = FamilyProfile.PRESET_COLORS[state.family.members.size() % FamilyProfile.PRESET_COLORS.length];
                int avatar = state.family.members.size() % FamilyProfile.AVATAR_COUNT;
                AdventureState.ActionResult res = existing == null
                    ? state.family.addMember(name, avatar, color, bday, role)
                    : state.family.updateMember(existing.id, name, existing.avatarIndex, existing.colorRgb, bday, role);
                host.onStateChanged(res.message);
            })
            .setNegativeButton("取消", null);

        if (existing != null && state.family.members.size() > 1) {
            builder.setNeutralButton("移除成員", (d, w) -> {
                AdventureState.ActionResult res = state.family.removeMember(existing.id);
                host.onStateChanged(res.message);
            });
        }
        builder.show();
    }

    private void showImportBackupDialog() {
        EditText input = new EditText(ctx);
        input.setHint("貼上之前匯出的 2.0 備份 JSON");
        input.setMinLines(4);
        input.setMaxLines(8);
        new AlertDialog.Builder(ctx)
            .setTitle("貼上還原 2.0 營地資料")
            .setView(input)
            .setPositiveButton("確認還原", (d, w) -> host.onBackupRestored(input.getText().toString()))
            .setNegativeButton("取消", null)
            .show();
    }
}
