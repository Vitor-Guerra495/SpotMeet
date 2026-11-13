package com.spotmeet.backend.service;

import com.spotmeet.backend.model.Meeting;
import com.spotmeet.backend.model.Meeting.MeetingModality;
import com.spotmeet.backend.model.MeetingFile;
import com.spotmeet.backend.model.MeetingSeries;
import com.spotmeet.backend.model.MeetingSeries.Frequency;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Texts of a meeting in Portuguese, as the e-mails show them ("Segunda, 2 de novembro", "16:00 às 17:00", "Toda segunda").
 * The app builds the same texts in utils/meetings.ts.
 */
public final class MeetingText {

    /** Data of a meeting already formatted for the e-mails. */
    public record Mail(String title, String committee, String date, String time, String frequency, String modality,
                String location, String link, String agenda, String attachment, String appLink, String when) {

        String[] details() {
            List<String> rows = new ArrayList<>(List.of("Comissão", committee, "Data", date, "Horário", time));
            if (frequency != null) { rows.add("Frequência"); rows.add(frequency); }
            if (modality != null) { rows.add("Modalidade"); rows.add(modality); }
            if (location != null) { rows.add("Local"); rows.add(location); }
            if (link != null) { rows.add("Link"); rows.add(link); }
            if (agenda != null) { rows.add("Pauta"); rows.add(agenda); }
            if (attachment != null) { rows.add("Anexo"); rows.add(attachment + ". Baixe pelo aplicativo."); }
            return rows.toArray(String[]::new);
        }
    }

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("d 'de' MMMM", PT_BR);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd/MM");
    private static final String[] WEEKDAYS = {"segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo"};
    private static final String[] ORDINALS = {"primeira", "segunda", "terceira", "quarta", "última"};
    private static final String[] ORDINALS_MALE = {"primeiro", "segundo", "terceiro", "quarto", "último"};

    private MeetingText() {}

    static Mail mail(Meeting meeting, String appLink) {
        MeetingFile file = meeting.getAttachment();
        return new Mail(meeting.getTitle(), meeting.getCommittee().getName(), dates(meeting), times(meeting),
                meeting.getSeries() == null ? null : capitalize(recurrence(meeting.getSeries())),
                modality(meeting.getModality()), meeting.getLocation(), meeting.getLink(), meeting.getAgenda(),
                file == null ? null : file.getFileName() + " (" + size(file.getSizeBytes()) + ")", appLink, when(meeting));
    }

    static String weekday(DayOfWeek day) {
        return WEEKDAYS[day.getValue() - 1];
    }

    static String day(LocalDate date) {
        String text = weekday(date.getDayOfWeek()) + ", " + MONTH_DAY.format(date);
        return date.getYear() == LocalDate.now().getYear() ? text : text + " de " + date.getYear();
    }

    static String dates(Meeting meeting) {
        LocalDate first = meeting.getStartsAt().toLocalDate();
        LocalDate last = meeting.getEndsAt().toLocalDate();
        return capitalize(first.equals(last) ? day(first) : day(first) + " a " + day(last));
    }

    static String times(Meeting meeting) {
        if (meeting.isAllDay()) return "Dia inteiro";
        LocalDateTime start = meeting.getStartsAt();
        LocalDateTime end = meeting.getEndsAt();
        if (start.toLocalDate().equals(end.toLocalDate())) return TIME.format(start) + " às " + TIME.format(end);
        return TIME.format(start) + " (" + SHORT.format(start) + ") às " + TIME.format(end) + " (" + SHORT.format(end) + ")";
    }

    // Used in the subject: "segunda, 2 de novembro, 16:00".
    static String when(Meeting meeting) {
        String date = day(meeting.getStartsAt().toLocalDate());
        return meeting.isAllDay() ? date + ", dia inteiro" : date + ", " + TIME.format(meeting.getStartsAt());
    }

    static String modality(MeetingModality modality) {
        if (modality == null) return null;
        return switch (modality) {
            case IN_PERSON -> "Presencial";
            case ONLINE -> "Online";
            case HYBRID -> "Híbrida";
        };
    }

    static String size(long bytes) {
        if (bytes < 1024 * 1024) return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        return String.format(PT_BR, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** "toda segunda e quarta, até 15 de dezembro", "a cada 2 meses, na primeira segunda", "todos os dias úteis". */
    static String recurrence(MeetingSeries series) {
        LocalDate anchor = series.getFirstStartsAt().toLocalDate();
        int every = Math.max(1, series.getRepeatInterval());
        Frequency frequency = series.getFrequency();
        String text;
        if (frequency == Frequency.DAILY) {
            text = every == 1 ? "todos os dias" : "a cada " + every + " dias";
        } else if (frequency == Frequency.WEEKLY) {
            Set<DayOfWeek> days = MeetingRecurrence.weekDays(series.getWeekDays());
            if (days.isEmpty()) days = Set.of(anchor.getDayOfWeek());
            boolean workdays = days.equals(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
            if (workdays && every == 1) {
                text = "todos os dias úteis (segunda a sexta)";
            } else {
                String names = joinDays(days);
                text = every == 1 ? everyWeekday(days) + " " + names : "a cada " + every + " semanas, " + onDays(days) + " " + names;
            }
        } else if (frequency == Frequency.MONTHLY) {
            String target = series.isMonthlyByWeekday() ? nthWeekday(anchor) : "dia " + anchor.getDayOfMonth();
            if (every == 1) {
                text = series.isMonthlyByWeekday() ? (male(anchor.getDayOfWeek()) ? "todo " : "toda ") + target + " do mês" : "todo " + target;
            } else {
                text = "a cada " + every + " meses, " + (series.isMonthlyByWeekday() ? (male(anchor.getDayOfWeek()) ? "no " : "na ") : "no ") + target;
            }
        } else {
            String date = MONTH_DAY.format(anchor);
            text = every == 1 ? "todo ano, em " + date : "a cada " + every + " anos, em " + date;
        }
        if (series.getEndDate() != null) text += ", até " + MONTH_DAY.format(series.getEndDate());
        else if (series.getMaxOccurrences() != null) text += ", " + series.getMaxOccurrences() + (series.getMaxOccurrences() == 1 ? " vez" : " vezes");
        return text;
    }

    private static boolean male(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private static String nthWeekday(LocalDate anchor) {
        int nth = Math.min(MeetingRecurrence.weekOfMonth(anchor), 5) - 1;
        DayOfWeek day = anchor.getDayOfWeek();
        return (male(day) ? ORDINALS_MALE[nth] : ORDINALS[nth]) + " " + weekday(day);
    }

    private static String everyWeekday(Set<DayOfWeek> days) {
        return days.stream().allMatch(MeetingText::male) ? "todo" : "toda";
    }

    private static String onDays(Set<DayOfWeek> days) {
        return days.stream().allMatch(MeetingText::male) ? "no" : "na";
    }

    private static String joinDays(Set<DayOfWeek> days) {
        List<String> names = days.stream().sorted().map(MeetingText::weekday).toList();
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " e " + names.get(names.size() - 1);
    }

    static String capitalize(String text) {
        return text == null || text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
