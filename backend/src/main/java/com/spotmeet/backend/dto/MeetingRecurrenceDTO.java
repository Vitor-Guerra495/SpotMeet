package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.MeetingSeries;
import com.spotmeet.backend.model.MeetingSeries.Frequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Repetition chosen in the form ("Frequência"). Used only when scheduling;
 * the meeting detail returns it so the app can describe the series.
 *
 * Ends on endDate, after the given number of occurrences, or never when both are empty.
 */
public class MeetingRecurrenceDTO {

    @NotNull(message = "Escolha a frequência.")
    private Frequency frequency;

    @Min(value = 1, message = "Repita a cada 1 ou mais.")
    @Max(value = 99, message = "Repita a cada no máximo 99.")
    private Integer interval;

    // ISO days of the week: 1 = Monday ... 7 = Sunday.
    private List<@Min(value = 1, message = "Dia da semana inválido.") @Max(value = 7, message = "Dia da semana inválido.") Integer> weekDays;

    private Boolean monthlyByWeekday;

    private LocalDate endDate;

    @Min(value = 1, message = "Informe quantas vezes a reunião se repete.")
    @Max(value = 365, message = "A reunião pode se repetir no máximo 365 vezes.")
    private Integer occurrences;

    public MeetingRecurrenceDTO() {}

    public static MeetingRecurrenceDTO from(MeetingSeries series) {
        MeetingRecurrenceDTO dto = new MeetingRecurrenceDTO();
        dto.frequency = series.getFrequency();
        dto.interval = series.getRepeatInterval();
        dto.weekDays = series.getWeekDays() == null || series.getWeekDays().isBlank() ? List.of()
                : Arrays.stream(series.getWeekDays().split(",")).map(String::trim).map(Integer::valueOf).toList();
        dto.monthlyByWeekday = series.isMonthlyByWeekday();
        dto.endDate = series.getEndDate();
        dto.occurrences = series.getMaxOccurrences();
        return dto;
    }

    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { this.frequency = frequency; }

    public Integer getInterval() { return interval; }
    public void setInterval(Integer interval) { this.interval = interval; }

    public List<Integer> getWeekDays() { return weekDays; }
    public void setWeekDays(List<Integer> weekDays) { this.weekDays = weekDays; }

    public Boolean getMonthlyByWeekday() { return monthlyByWeekday; }
    public void setMonthlyByWeekday(Boolean monthlyByWeekday) { this.monthlyByWeekday = monthlyByWeekday; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getOccurrences() { return occurrences; }
    public void setOccurrences(Integer occurrences) { this.occurrences = occurrences; }
}
