package com.cornming.walkadventure;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 真實環境時鐘：依本機時間與日期判斷時段、四季、台灣節慶與家庭壽星，
 * 讓天空色調、季節粒子、限定奇遇與神祕蛋隨現實世界自然變化（100% 離線）。
 */
final class WorldClock {
    static final int TIME_DAWN = 0;   // 05:00 - 08:59
    static final int TIME_DAY = 1;    // 09:00 - 15:59
    static final int TIME_DUSK = 2;   // 16:00 - 18:59
    static final int TIME_NIGHT = 3;  // 19:00 - 04:59

    static final int SEASON_SPRING = 0; // 3-5 月
    static final int SEASON_SUMMER = 1; // 6-8 月
    static final int SEASON_AUTUMN = 2; // 9-11 月
    static final int SEASON_WINTER = 3; // 12-2 月

    static final class Snapshot {
        final String isoDate;
        final int year;
        final int month;
        final int dayOfMonth;
        final int hour;
        final int timeOfDay;
        final int season;
        final String seasonKey;
        final String holidayName;

        Snapshot(
            String isoDate,
            int year,
            int month,
            int dayOfMonth,
            int hour,
            int timeOfDay,
            int season,
            String seasonKey,
            String holidayName
        ) {
            this.isoDate = isoDate;
            this.year = year;
            this.month = month;
            this.dayOfMonth = dayOfMonth;
            this.hour = hour;
            this.timeOfDay = timeOfDay;
            this.season = season;
            this.seasonKey = seasonKey;
            this.holidayName = holidayName;
        }

        String monthDayKey() {
            return String.format(java.util.Locale.ROOT, "%02d-%02d", month, dayOfMonth);
        }

        boolean isNightOrDusk() {
            return timeOfDay == TIME_DUSK || timeOfDay == TIME_NIGHT;
        }
    }

    private WorldClock() {}

    static Snapshot now() {
        return fromDateTime(LocalDateTime.now());
    }

    static Snapshot fromDateTime(LocalDateTime dt) {
        LocalDateTime safe = dt != null ? dt : LocalDateTime.of(2026, 1, 1, 10, 0);
        int year = safe.getYear();
        int month = safe.getMonthValue();
        int day = safe.getDayOfMonth();
        int hour = safe.getHour();
        int tod = classifyTimeOfDay(hour);
        int season = classifySeason(month);
        String seasonKey = seasonYearKey(year, month, season);
        String holiday = detectHoliday(month, day);
        return new Snapshot(
            safe.toLocalDate().toString(),
            year,
            month,
            day,
            hour,
            tod,
            season,
            seasonKey,
            holiday
        );
    }

    static Snapshot fromDateAndHour(String isoDate, int hour) {
        LocalDate date = parseDateSafe(isoDate);
        if (date == null) {
            date = LocalDate.of(2026, 1, 1);
        }
        int clampedHour = Math.max(0, Math.min(23, hour));
        return fromDateTime(date.atTime(clampedHour, 0));
    }

    static int classifyTimeOfDay(int hour) {
        int h = ((hour % 24) + 24) % 24;
        if (h >= 5 && h <= 8) return TIME_DAWN;
        if (h >= 9 && h <= 15) return TIME_DAY;
        if (h >= 16 && h <= 18) return TIME_DUSK;
        return TIME_NIGHT;
    }

    static int classifySeason(int month) {
        if (month >= 3 && month <= 5) return SEASON_SPRING;
        if (month >= 6 && month <= 8) return SEASON_SUMMER;
        if (month >= 9 && month <= 11) return SEASON_AUTUMN;
        return SEASON_WINTER;
    }

    static String seasonYearKey(int year, int month, int season) {
        // 1-2 月歸入跨年冬季（例如 2027-01 屬於 2026-WINTER）以維持連續一季護照
        int effectiveYear = (season == SEASON_WINTER && month <= 2) ? (year - 1) : year;
        return effectiveYear + "-" + seasonCode(season);
    }

    static String seasonCode(int season) {
        switch (season) {
            case SEASON_SPRING: return "SPRING";
            case SEASON_SUMMER: return "SUMMER";
            case SEASON_AUTUMN: return "AUTUMN";
            case SEASON_WINTER:
            default: return "WINTER";
        }
    }

    static String seasonLabel(int season) {
        switch (season) {
            case SEASON_SPRING: return "春日賞花季";
            case SEASON_SUMMER: return "夏日星空季";
            case SEASON_AUTUMN: return "秋日落葉季";
            case SEASON_WINTER:
            default: return "冬日暖火季";
        }
    }

    static String timeOfDayLabel(int timeOfDay) {
        switch (timeOfDay) {
            case TIME_DAWN: return "清晨微光";
            case TIME_DAY: return "陽光漫步";
            case TIME_DUSK: return "魔幻黃昏";
            case TIME_NIGHT:
            default: return "星夜微風";
        }
    }

    static String detectHoliday(int month, int day) {
        if (month == 1 && day == 1) return "新年開走日";
        if (month == 4 && day == 4) return "兒童探險節";
        if (month == 5 && day >= 8 && day <= 14) return "溫馨感恩週";
        if (month == 8 && day == 8) return "超人爸爸節";
        if (month == 10 && day == 10) return "秋高氣爽慶典";
        if (month == 10 && day == 31) return "南瓜糖果夜";
        if (month == 12 && day >= 24 && day <= 25) return "暖心聖誕祭";
        return "";
    }

    /** 天空上緣 RGB 色碼（ARGB 格式） */
    static int skyTopRgb(int timeOfDay) {
        switch (timeOfDay) {
            case TIME_DAWN: return 0xFFFAD8C2;
            case TIME_DAY: return 0xFFCFEAF8;
            case TIME_DUSK: return 0xFFF2AD78;
            case TIME_NIGHT:
            default: return 0xFF28385C;
        }
    }

    /** 天空下緣 RGB 色碼（ARGB 格式） */
    static int skyBottomRgb(int timeOfDay) {
        switch (timeOfDay) {
            case TIME_DAWN: return 0xFFFFF1D6;
            case TIME_DAY: return 0xFFFFF7E8;
            case TIME_DUSK: return 0xFFFCE2B8;
            case TIME_NIGHT:
            default: return 0xFF475C88;
        }
    }

    static boolean isDarkSky(int timeOfDay) {
        return timeOfDay == TIME_NIGHT;
    }

    static boolean matchesBirthday(String mmdd, Snapshot snap) {
        if (mmdd == null || snap == null) return false;
        String clean = mmdd.trim();
        if (clean.length() != 5 || clean.charAt(2) != '-') return false;
        return clean.equals(snap.monthDayKey());
    }

    static String normalizeBirthdayInput(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim().replace('/', '-');
        if (trimmed.isEmpty()) return "";
        String[] parts = trimmed.split("-");
        if (parts.length != 2) return "";
        try {
            int m = Integer.parseInt(parts[0].trim());
            int d = Integer.parseInt(parts[1].trim());
            if (m < 1 || m > 12 || d < 1 || d > 31) return "";
            return String.format(java.util.Locale.ROOT, "%02d-%02d", m, d);
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private static LocalDate parseDateSafe(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
