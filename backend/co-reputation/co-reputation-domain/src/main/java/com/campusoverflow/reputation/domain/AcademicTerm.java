package com.campusoverflow.reputation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.Locale;

public final class AcademicTerm {

    public static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");

    private AcademicTerm() {
    }

    public static String termKey(Instant at) {
        LocalDate date = at.atZone(CAMPUS_ZONE).toLocalDate();
        int year = date.getYear();
        int month = date.getMonthValue();

        if (month == 1) {
            return (year - 1) + "-FALL";
        }
        if (month >= 2 && month <= 7) {
            return year + "-SPRING";
        }
        return year + "-FALL";
    }

    public static String weekKey(Instant at) {
        LocalDate date = at.atZone(CAMPUS_ZONE).toLocalDate();
        WeekFields fields = WeekFields.ISO;

        int year = date.get(fields.weekBasedYear());
        int week = date.get(fields.weekOfWeekBasedYear());

        return String.format(Locale.ROOT, "%d-W%02d", year, week);
    }

    public static String monthKey(Instant at) {
        LocalDate date = at.atZone(CAMPUS_ZONE).toLocalDate();
        return String.format(Locale.ROOT, "%04d-%02d",
                date.getYear(), date.getMonthValue());
    }
}
