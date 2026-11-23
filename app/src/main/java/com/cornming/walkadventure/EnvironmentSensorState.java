package com.cornming.walkadventure;

/**
 * Phase 3 真實環境感測彩蛋引擎（光線感測 TYPE_LIGHT ＋ 步頻節奏與歇腳偵測，純 Java 100% 離線）：
 * - 走進暗處／夜晚微光（< 18 lux）：每日一次「🌙 夜光小妖彩蛋」（送星砂＋機率掉星夜蛋）
 * - 戶外明亮暖陽（>= 12,000 lux）：每日一次「☀️ 陽光精靈祝福」（替奇幻菜園免費澆水 +200 步）
 * - 活力快走（60 秒內 >= 80 步）：每日一次「💨 疾風追逐戰」（+30 金幣、+120 XP、+10 星砂）
 * - 散步後悠閒停下（走滿 150 步後歇腳 >= 3 分鐘）：每日一次「🍵 路邊歇腳物語」（旅伴羈絆 +20）
 */
final class EnvironmentSensorState {
    static final float DARK_LUX_THRESHOLD = 18f;
    static final float BRIGHT_SUN_LUX_THRESHOLD = 12_000f;
    static final long CADENCE_WINDOW_MS = 60_000L;
    static final long CADENCE_BURST_STEPS = 80L;
    static final long REST_MIN_WALKED_STEPS = 150L;
    static final long REST_PAUSE_MS = 180_000L;

    static final int EVENT_NONE = 0;
    static final int EVENT_NIGHT_SPRITE = 1;
    static final int EVENT_SUN_BLESSING = 2;
    static final int EVENT_WIND_CHASE = 3;
    static final int EVENT_ROADSIDE_REST = 4;

    static final class TriggerResult {
        final int eventType;
        final String title;
        final String message;

        TriggerResult(int eventType, String title, String message) {
            this.eventType = eventType;
            this.title = title;
            this.message = message;
        }

        boolean triggered() {
            return eventType != EVENT_NONE;
        }
    }

    // 持久化狀態（由 SaveCodec 保存）
    String lastNightSpriteDate = "";
    String lastSunBlessingDate = "";
    String lastWindChaseDate = "";
    String lastRoadsideRestDate = "";
    int totalSensorEventsTriggered = 0;

    // 執行期暫存狀態（不寫入存檔）
    transient float lastLux = -1f;
    transient long burstWindowStartMs = 0L;
    transient long burstWindowSteps = 0L;
    transient long sessionStepsSinceRest = 0L;
    transient long lastStepAtMs = 0L;

    /** 依目前環境照度回傳即時氛圍短標（用於探險頁顯示）。 */
    String ambientBadgeLabel() {
        if (lastLux < 0f) return "";
        if (lastLux < DARK_LUX_THRESHOLD) return "🌙 微光秘境 (" + Math.round(lastLux) + " lux)";
        if (lastLux >= BRIGHT_SUN_LUX_THRESHOLD) return "☀️ 燦爛戶外陽光";
        if (lastLux >= 1000f) return "🌤️ 明亮散步光線";
        return "🏡 柔和室內光線";
    }

    /** 處理環境光感測讀數，每日各觸發一次微光／陽光彩蛋。 */
    TriggerResult onLightReading(float lux, String today, boolean hasWalkedToday) {
        if (Float.isNaN(lux) || lux < 0f) {
            return new TriggerResult(EVENT_NONE, "", "");
        }
        lastLux = lux;
        if (today == null || today.isEmpty()) {
            return new TriggerResult(EVENT_NONE, "", "");
        }

        if (lux < DARK_LUX_THRESHOLD && hasWalkedToday && !today.equals(lastNightSpriteDate)) {
            lastNightSpriteDate = today;
            totalSensorEventsTriggered++;
            return new TriggerResult(
                EVENT_NIGHT_SPRITE,
                "🌙 遇見夜光小妖！",
                "樹蔭微光中冒出閃閃發亮的夜光小妖，送你 +14 星砂與滿滿驚喜！"
            );
        }
        if (lux >= BRIGHT_SUN_LUX_THRESHOLD && !today.equals(lastSunBlessingDate)) {
            lastSunBlessingDate = today;
            totalSensorEventsTriggered++;
            return new TriggerResult(
                EVENT_SUN_BLESSING,
                "☀️ 陽光精靈的祝福！",
                "偵測到戶外溫暖陽光！奇幻小菜園免費獲得 +200 步光合作用成長與 +10 星砂！"
            );
        }
        return new TriggerResult(EVENT_NONE, "", "");
    }

    /** 處理步數增量與時間戳，偵測「疾風追逐戰」與「路邊歇腳物語」。 */
    TriggerResult onStepBatch(long deltaSteps, long nowMs, String today) {
        if (deltaSteps <= 0L || today == null || today.isEmpty()) {
            return new TriggerResult(EVENT_NONE, "", "");
        }

        // 先檢查是否剛結束一段 >= 3 分鐘的悠閒歇腳
        if (lastStepAtMs > 0L
            && sessionStepsSinceRest >= REST_MIN_WALKED_STEPS
            && (nowMs - lastStepAtMs) >= REST_PAUSE_MS
            && !today.equals(lastRoadsideRestDate)) {
            lastRoadsideRestDate = today;
            sessionStepsSinceRest = deltaSteps;
            lastStepAtMs = nowMs;
            burstWindowStartMs = nowMs;
            burstWindowSteps = deltaSteps;
            totalSensorEventsTriggered++;
            return new TriggerResult(
                EVENT_ROADSIDE_REST,
                "🍵 路邊歇腳物語！",
                "走了一段路後悠閒休息再出發！旅伴開心蹭蹭你，羈絆 +20、獲得 +8 星砂！"
            );
        }

        if (burstWindowStartMs == 0L || (nowMs - burstWindowStartMs) > CADENCE_WINDOW_MS) {
            burstWindowStartMs = nowMs;
            burstWindowSteps = deltaSteps;
        } else {
            burstWindowSteps += deltaSteps;
        }
        sessionStepsSinceRest += deltaSteps;
        lastStepAtMs = nowMs;

        if (burstWindowSteps >= CADENCE_BURST_STEPS && !today.equals(lastWindChaseDate)) {
            lastWindChaseDate = today;
            totalSensorEventsTriggered++;
            return new TriggerResult(
                EVENT_WIND_CHASE,
                "💨 觸發疾風追逐戰！",
                "輕快步伐追上了風之精靈！額外獲得 +30 金幣、+120 冒險 XP 與 +10 星砂！"
            );
        }
        return new TriggerResult(EVENT_NONE, "", "");
    }
}
