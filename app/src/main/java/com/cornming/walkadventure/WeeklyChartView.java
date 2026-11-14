package com.cornming.walkadventure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 戰報頁使用的近 7 天步數長條圖：顯示每日達標基準線、今日高亮長條與簡潔步數標籤。
 */
final class WeeklyChartView extends View {
    private static final long DAILY_WARMUP_GOAL = AdventureCatalog.DAILY_QUESTS[0].targetSteps;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();
    private final Typeface regular = Typeface.create("sans", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans", Typeface.BOLD);
    private final int inkColor;
    private List<AdventureState.DailyRecord> records = Collections.emptyList();

    WeeklyChartView(Context context) {
        super(context);
        inkColor = context.getColor(R.color.ink);
        dashedPaint.setStyle(Paint.Style.STROKE);
        dashedPaint.setStrokeWidth(dp(1.5f));
        dashedPaint.setColor(Color.rgb(198, 170, 134));
        dashedPaint.setPathEffect(new DashPathEffect(new float[]{dp(5), dp(4)}, 0));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setRecords(List<AdventureState.DailyRecord> newRecords) {
        this.records = newRecords != null ? newRecords : Collections.emptyList();
        StringBuilder desc = new StringBuilder("近 7 天步數戰報圖表。");
        for (AdventureState.DailyRecord rec : this.records) {
            desc.append(shortDateLabel(rec.date)).append(" ").append(rec.steps).append(" 步；");
        }
        setContentDescription(desc.toString());
        invalidate();
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            getResources().getDisplayMetrics()
        );
    }

    private float sp(float value) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value,
            getResources().getDisplayMetrics()
        );
    }

    private static String shortDateLabel(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) return "--";
        int month = parseTwo(isoDate.substring(5, 7));
        int day = parseTwo(isoDate.substring(8, 10));
        return month > 0 && day > 0 ? (month + "/" + day) : isoDate.substring(5);
    }

    private static int parseTwo(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String formatCompactSteps(long steps) {
        if (steps >= 1000L) {
            return String.format(Locale.ROOT, "%.1fk", steps / 1000.0);
        }
        return String.valueOf(steps);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desiredHeight = Math.round(dp(168));
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (records.isEmpty()) return;

        float width = getWidth();
        float height = getHeight();
        float padH = dp(10);
        float topArea = dp(24);
        float bottomArea = dp(26);
        float plotHeight = Math.max(dp(40), height - topArea - bottomArea);
        float baseLineY = topArea + plotHeight;

        long maxSteps = DAILY_WARMUP_GOAL * 2L;
        for (AdventureState.DailyRecord rec : records) {
            if (rec.steps > maxSteps) maxSteps = rec.steps;
        }

        // 1,500 步暖身目標基準線
        float goalRatio = Math.min(1f, DAILY_WARMUP_GOAL / (float) maxSteps);
        float goalY = baseLineY - plotHeight * goalRatio;
        canvas.drawLine(padH, goalY, width - padH, goalY, dashedPaint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(regular);
        paint.setTextSize(sp(10));
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(Color.rgb(150, 122, 92));
        canvas.drawText("1.5k 目標", width - padH, goalY - dp(3), paint);

        int count = records.size();
        float slotWidth = (width - padH * 2f) / count;
        float barWidth = Math.min(dp(26), slotWidth * 0.56f);
        float radius = dp(6);

        for (int i = 0; i < count; i++) {
            AdventureState.DailyRecord rec = records.get(i);
            float cx = padH + slotWidth * (i + 0.5f);
            float ratio = maxSteps > 0 ? Math.min(1f, rec.steps / (float) maxSteps) : 0f;
            float barH = rec.steps > 0 ? Math.max(dp(6), plotHeight * ratio) : dp(3);
            boolean isToday = (i == count - 1);

            if (isToday) {
                paint.setColor(Color.rgb(245, 191, 66));
            } else if (rec.steps >= DAILY_WARMUP_GOAL) {
                paint.setColor(Color.rgb(112, 175, 116));
            } else {
                paint.setColor(Color.rgb(219, 204, 181));
            }

            barRect.set(cx - barWidth * 0.5f, baseLineY - barH, cx + barWidth * 0.5f, baseLineY);
            canvas.drawRoundRect(barRect, radius, radius, paint);

            // 步數數值
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(isToday ? bold : regular);
            paint.setTextSize(sp(11));
            paint.setColor(inkColor);
            canvas.drawText(formatCompactSteps(rec.steps), cx, baseLineY - barH - dp(5), paint);

            // 日期標籤
            paint.setTypeface(isToday ? bold : regular);
            paint.setTextSize(sp(11));
            paint.setColor(isToday ? inkColor : Color.rgb(122, 100, 82));
            canvas.drawText(isToday ? "今天" : shortDateLabel(rec.date), cx, baseLineY + dp(17), paint);
        }
    }
}
