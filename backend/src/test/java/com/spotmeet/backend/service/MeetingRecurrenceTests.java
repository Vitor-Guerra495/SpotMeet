package com.spotmeet.backend.service;

import com.spotmeet.backend.model.Committee;
import com.spotmeet.backend.model.Meeting;
import com.spotmeet.backend.model.MeetingFile;
import com.spotmeet.backend.model.MeetingSeries;
import com.spotmeet.backend.model.MeetingSeries.Frequency;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.MeetingFileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Dates of the repetition rules, their texts and the check of the agenda attachment.
class MeetingRecurrenceTests {

    private static MeetingSeries series(Frequency frequency, LocalDate anchor) {
        MeetingSeries series = new MeetingSeries();
        series.setFrequency(frequency);
        series.setFirstStartsAt(anchor.atTime(16, 0));
        return series;
    }

    private static List<LocalDate> dates(MeetingSeries series, LocalDate until) {
        return MeetingRecurrence.window(series, null, until).dates();
    }

    @Test
    void weeklyRuleUsesTheChosenDaysAndTheInterval() {
        // 02/11/2026 is a Monday.
        MeetingSeries series = series(Frequency.WEEKLY, LocalDate.of(2026, 11, 2));
        series.setWeekDays("1,3");
        assertEquals(List.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 11)),
                dates(series, LocalDate.of(2026, 11, 12)));
        series.setRepeatInterval(2);
        assertEquals(List.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 16)),
                dates(series, LocalDate.of(2026, 11, 17)));
        assertEquals("a cada 2 semanas, na segunda e quarta", MeetingText.recurrence(series));
    }

    @Test
    void anAnchorOutsideTheChosenDaysStartsOnTheNextOne() {
        MeetingSeries series = series(Frequency.WEEKLY, LocalDate.of(2026, 11, 3));
        series.setWeekDays("1");
        assertEquals(LocalDate.of(2026, 11, 9), MeetingRecurrence.firstDate(series, LocalDate.of(2026, 12, 31)));
    }

    @Test
    void monthlyRuleRepeatsTheDayOrTheWeekdayOfTheMonth() {
        MeetingSeries byDay = series(Frequency.MONTHLY, LocalDate.of(2026, 1, 31));
        assertEquals(List.of(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 5, 31)),
                dates(byDay, LocalDate.of(2026, 6, 1)));
        assertEquals("todo dia 31", MeetingText.recurrence(byDay));

        MeetingSeries firstMonday = series(Frequency.MONTHLY, LocalDate.of(2026, 11, 2));
        firstMonday.setMonthlyByWeekday(true);
        firstMonday.setRepeatInterval(2);
        assertEquals(List.of(LocalDate.of(2026, 11, 2), LocalDate.of(2027, 1, 4), LocalDate.of(2027, 3, 1)),
                dates(firstMonday, LocalDate.of(2027, 3, 31)));
        assertEquals("a cada 2 meses, na primeira segunda", MeetingText.recurrence(firstMonday));

        // 30/11/2026 is the fifth Monday: read as "the last Monday".
        MeetingSeries lastMonday = series(Frequency.MONTHLY, LocalDate.of(2026, 11, 30));
        lastMonday.setMonthlyByWeekday(true);
        assertEquals(List.of(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 25)),
                dates(lastMonday, LocalDate.of(2027, 1, 31)));
        assertEquals("toda última segunda do mês", MeetingText.recurrence(lastMonday));
    }

    @Test
    void yearlyAndDailyRulesAndTheirEnds() {
        MeetingSeries yearly = series(Frequency.YEARLY, LocalDate.of(2028, 2, 29));
        assertEquals(List.of(LocalDate.of(2028, 2, 29), LocalDate.of(2032, 2, 29)), dates(yearly, LocalDate.of(2032, 3, 1)));

        MeetingSeries daily = series(Frequency.DAILY, LocalDate.of(2026, 11, 2));
        daily.setMaxOccurrences(3);
        MeetingRecurrence.Window window = MeetingRecurrence.window(daily, null, LocalDate.of(2026, 12, 31));
        assertEquals(3, window.dates().size());
        assertTrue(window.exhausted());
        assertEquals("todos os dias, 3 vezes", MeetingText.recurrence(daily));

        daily.setMaxOccurrences(null);
        daily.setEndDate(LocalDate.of(2026, 11, 5));
        assertEquals(4, dates(daily, LocalDate.of(2026, 12, 31)).size());
        assertEquals("todos os dias, até 5 de novembro", MeetingText.recurrence(daily));

        // Only the dates after the last one created come back.
        assertEquals(List.of(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5)),
                MeetingRecurrence.window(daily, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 12, 31)).dates());
    }

    @Test
    void textsOfWorkdaysWeekendsAndTimes() {
        MeetingSeries workdays = series(Frequency.WEEKLY, LocalDate.of(2026, 11, 2));
        workdays.setWeekDays("1,2,3,4,5");
        assertEquals("todos os dias úteis (segunda a sexta)", MeetingText.recurrence(workdays));
        MeetingSeries saturday = series(Frequency.WEEKLY, LocalDate.of(2026, 11, 7));
        assertEquals("todo sábado", MeetingText.recurrence(saturday));

        Meeting meeting = new Meeting();
        meeting.setStartsAt(LocalDateTime.of(2026, 11, 2, 22, 0));
        meeting.setEndsAt(LocalDateTime.of(2026, 11, 3, 1, 0));
        assertEquals("22:00 (02/11) às 01:00 (03/11)", MeetingText.times(meeting));
        meeting.setAllDay(true);
        assertEquals("Dia inteiro", MeetingText.times(meeting));
        assertEquals("1,2 MB", MeetingText.size(1_258_291));
        assertEquals("860 KB", MeetingText.size(880_640));
    }

    private static MeetingFileService fileService(MeetingAccess access) {
        MeetingFileService service = new MeetingFileService();
        MeetingFileRepository files = mock(MeetingFileRepository.class);
        when(files.save(any())).thenAnswer(call -> { MeetingFile file = call.getArgument(0); file.setId(7L); return file; });
        ReflectionTestUtils.setField(service, "fileRepo", files);
        ReflectionTestUtils.setField(service, "access", access);
        return service;
    }

    private static byte[] docx() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document/>".getBytes());
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test
    void attachmentTypeComesFromTheContent() throws Exception {
        MeetingAccess access = mock(MeetingAccess.class);
        Committee committee = new Committee("Eventos", null, null);
        committee.setId(10L);
        User president = new User();
        president.setId(1L);
        when(access.findCommittee(10L)).thenReturn(committee);
        when(access.findUser("ana@example.com")).thenReturn(president);
        MeetingFileService service = fileService(access);

        var pdf = service.upload(10L, "ana@example.com", new MockMultipartFile("file", "../pauta da reunião.pdf", "text/plain", "%PDF-1.7 conteúdo".getBytes()));
        assertEquals("pauta da reunião.pdf", pdf.fileName());
        assertEquals("application/pdf", pdf.contentType());

        var word = service.upload(10L, "ana@example.com", new MockMultipartFile("file", "ata", null, docx()));
        assertEquals("ata.docx", word.fileName());

        var renamed = service.upload(10L, "ana@example.com", new MockMultipartFile("file", "foto.pdf", null, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}));
        assertEquals("foto.jpg", renamed.fileName());

        for (byte[] bad : new byte[][] {"<script>".getBytes(), new byte[] {0x50, 0x4B, 0x03, 0x04, 0}, new byte[0]}) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.upload(10L, "ana@example.com", new MockMultipartFile("file", "x.pdf", null, bad)));
        }
        byte[] big = new byte[MeetingFileService.MAX_BYTES + 1];
        big[0] = 0x25; big[1] = 0x50; big[2] = 0x44; big[3] = 0x46; big[4] = 0x2D;
        assertThrows(IllegalArgumentException.class, () -> service.upload(10L, "ana@example.com", new MockMultipartFile("file", "x.pdf", null, big)));
    }

    @Test
    void onlyTheFileOfThisCommitteeSentByThePresidentCanBeUsed() {
        MeetingFileService service = fileService(mock(MeetingAccess.class));
        MeetingFileRepository files = (MeetingFileRepository) ReflectionTestUtils.getField(service, "fileRepo");
        Committee committee = new Committee("Eventos", null, null);
        committee.setId(10L);
        User president = new User();
        president.setId(1L);
        User other = new User();
        other.setId(2L);
        MeetingFile file = new MeetingFile();
        file.setId(7L);
        file.setCommittee(committee);
        file.setUploadedBy(other);
        when(files.findById(7L)).thenReturn(java.util.Optional.of(file));

        assertNull(service.resolve(null, committee, president, null));
        assertThrows(SecurityException.class, () -> service.resolve(7L, committee, president, null));
        file.setUploadedBy(president);
        assertSame(file, service.resolve(7L, committee, president, null));
    }
}
