package com.spotmeet.backend.service;

import com.spotmeet.backend.model.MeetingSeries;
import com.spotmeet.backend.model.MeetingSeries.Frequency;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dates of a repetition rule, counted from the date chosen in the form (the anchor).
 *
 * The dates are recalculated from the anchor every time, so the rule needs no other state
 * than the last date already created.
 */
final class MeetingRecurrence {

    /** Dates after {@code after} (or from the anchor) up to {@code until}, and whether the rule has no dates left after them. */
    record Window(List<LocalDate> dates, boolean exhausted) {}

    private MeetingRecurrence() {}

    static Set<DayOfWeek> weekDays(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).map(String::trim).map(Integer::parseInt)
                .map(DayOfWeek::of).collect(Collectors.toSet());
    }

    static String weekDaysText(Set<DayOfWeek> days) {
        return days.stream().map(DayOfWeek::getValue).sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    static Window window(MeetingSeries series, LocalDate after, LocalDate until) {
        LocalDate anchor = series.getFirstStartsAt().toLocalDate();
        Set<DayOfWeek> days = weekDays(series.getWeekDays());
        LocalDate last = series.getEndDate() != null && series.getEndDate().isBefore(until) ? series.getEndDate() : until;
        Integer max = series.getMaxOccurrences();

        List<LocalDate> dates = new ArrayList<>();
        int count = 0;
        for (LocalDate day = anchor; !day.isAfter(last); day = day.plusDays(1)) {
            if (!matches(series, anchor, days, day)) continue;
            count++;
            if (after == null || day.isAfter(after)) dates.add(day);
            if (max != null && count >= max) return new Window(dates, true);
        }
        boolean exhausted = series.getEndDate() != null && !series.getEndDate().isAfter(until);
        return new Window(dates, exhausted);
    }

    /** First date of the rule, which can be later than the anchor (a Tuesday anchor for a rule on Mondays). */
    static LocalDate firstDate(MeetingSeries series, LocalDate limit) {
        List<LocalDate> dates = window(series, null, limit).dates();
        return dates.isEmpty() ? null : dates.get(0);
    }

    private static boolean matches(MeetingSeries series, LocalDate anchor, Set<DayOfWeek> days, LocalDate day) {
        int every = Math.max(1, series.getRepeatInterval());
        Frequency frequency = series.getFrequency();
        if (frequency == Frequency.DAILY) {
            return ChronoUnit.DAYS.between(anchor, day) % every == 0;
        }
        if (frequency == Frequency.WEEKLY) {
            Set<DayOfWeek> chosen = days.isEmpty() ? Set.of(anchor.getDayOfWeek()) : days;
            return chosen.contains(day.getDayOfWeek()) && ChronoUnit.WEEKS.between(weekStart(anchor), weekStart(day)) % every == 0;
        }
        if (frequency == Frequency.MONTHLY) {
            if (ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(day)) % every != 0) return false;
            if (!series.isMonthlyByWeekday()) return day.getDayOfMonth() == anchor.getDayOfMonth();
            return day.getDayOfWeek() == anchor.getDayOfWeek() && sameWeekOfMonth(anchor, day);
        }
        return ChronoUnit.YEARS.between(YearMonth.from(anchor), YearMonth.from(day)) % every == 0
                && day.getMonth() == anchor.getMonth() && day.getDayOfMonth() == anchor.getDayOfMonth();
    }

    // Weeks start on Sunday, as in the app and in the Brazilian calendar.
    private static LocalDate weekStart(LocalDate day) {
        return day.minusDays(day.getDayOfWeek().getValue() % 7);
    }

    static int weekOfMonth(LocalDate day) {
        return (day.getDayOfMonth() - 1) / 7 + 1;
    }

    // A fifth weekday only exists in some months, so it is read as "the last one".
    private static boolean sameWeekOfMonth(LocalDate anchor, LocalDate day) {
        int nth = weekOfMonth(anchor);
        if (nth >= 5) return day.plusWeeks(1).getMonth() != day.getMonth();
        return weekOfMonth(day) == nth;
    }
}
