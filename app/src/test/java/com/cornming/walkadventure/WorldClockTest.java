package com.cornming.walkadventure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import org.junit.Test;

public class WorldClockTest {
    @Test public void classifiesTimeOfDayAccurately() {
        assertEquals(WorldClock.TIME_DAWN, WorldClock.classifyTimeOfDay(5));
        assertEquals(WorldClock.TIME_DAWN, WorldClock.classifyTimeOfDay(8));
        assertEquals(WorldClock.TIME_DAY, WorldClock.classifyTimeOfDay(9));
        assertEquals(WorldClock.TIME_DAY, WorldClock.classifyTimeOfDay(15));
        assertEquals(WorldClock.TIME_DUSK, WorldClock.classifyTimeOfDay(16));
        assertEquals(WorldClock.TIME_DUSK, WorldClock.classifyTimeOfDay(18));
        assertEquals(WorldClock.TIME_NIGHT, WorldClock.classifyTimeOfDay(19));
        assertEquals(WorldClock.TIME_NIGHT, WorldClock.classifyTimeOfDay(2));
        assertTrue(WorldClock.isDarkSky(WorldClock.TIME_NIGHT));
        assertFalse(WorldClock.isDarkSky(WorldClock.TIME_DAY));
    }

    @Test public void classifiesSeasonsAndCrossYearWinterKey() {
        WorldClock.Snapshot spring = WorldClock.fromDateTime(LocalDateTime.of(2026, 4, 4, 10, 0));
        assertEquals(WorldClock.SEASON_SPRING, spring.season);
        assertEquals("2026-SPRING", spring.seasonKey);
        assertEquals("兒童探險節", spring.holidayName);

        WorldClock.Snapshot summer = WorldClock.fromDateTime(LocalDateTime.of(2026, 8, 8, 17, 30));
        assertEquals(WorldClock.SEASON_SUMMER, summer.season);
        assertEquals("2026-SUMMER", summer.seasonKey);
        assertEquals("超人爸爸節", summer.holidayName);
        assertTrue(summer.isNightOrDusk());

        WorldClock.Snapshot decWinter = WorldClock.fromDateTime(LocalDateTime.of(2026, 12, 25, 20, 0));
        assertEquals(WorldClock.SEASON_WINTER, decWinter.season);
        assertEquals("2026-WINTER", decWinter.seasonKey);
        assertEquals("暖心聖誕祭", decWinter.holidayName);

        WorldClock.Snapshot janWinter = WorldClock.fromDateTime(LocalDateTime.of(2027, 1, 15, 7, 0));
        assertEquals(WorldClock.SEASON_WINTER, janWinter.season);
        assertEquals("2026-WINTER", janWinter.seasonKey);
    }

    @Test public void normalizesAndMatchesFamilyBirthdays() {
        assertEquals("05-08", WorldClock.normalizeBirthdayInput("5/8"));
        assertEquals("11-22", WorldClock.normalizeBirthdayInput(" 11-22 "));
        assertEquals("", WorldClock.normalizeBirthdayInput("13-40"));
        assertEquals("", WorldClock.normalizeBirthdayInput("invalid"));

        WorldClock.Snapshot snap = WorldClock.fromDateAndHour("2026-05-08", 11);
        assertTrue(WorldClock.matchesBirthday("05-08", snap));
        assertFalse(WorldClock.matchesBirthday("05-09", snap));
    }
}
