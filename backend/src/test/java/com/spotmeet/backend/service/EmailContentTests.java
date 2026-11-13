package com.spotmeet.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Content of each e-mail; delivery and the commit rules are covered by EmailAsyncTests.
class EmailContentTests {
    private EmailDeliveryService delivery;
    private EmailService email;

    @BeforeEach void setUp() {
        delivery = mock(EmailDeliveryService.class);
        email = new EmailService();
        ReflectionTestUtils.setField(email, "deliveryService", delivery);
    }

    private EmailTemplate.Email notice(String recipient) {
        ArgumentCaptor<EmailTemplate.Email> sent = ArgumentCaptor.forClass(EmailTemplate.Email.class);
        verify(delivery).sendNotice(eq(recipient), sent.capture());
        return sent.getValue();
    }

    private EmailTemplate.Email code(String recipient, String code) {
        ArgumentCaptor<EmailTemplate.Email> sent = ArgumentCaptor.forClass(EmailTemplate.Email.class);
        verify(delivery).sendEmail(eq(recipient), sent.capture(), eq(code), any());
        return sent.getValue();
    }

    private static void assertBoth(EmailTemplate.Email mail, String... parts) {
        for (String part : parts) {
            assertTrue(mail.text().contains(part), part);
            assertTrue(mail.html().contains(part), part);
        }
    }

    @Test void codeEmailsKeepTheirSubjectsAndShowTheCode() {
        LocalDateTime expires = LocalDateTime.now().plusMinutes(5);
        email.sendVerificationEmail("a@example.com", "Mariana", "482913", expires);
        email.sendPasswordRecoveryEmail("b@example.com", "Mariana", "716204", expires);
        email.sendCurrentEmailChangeCode("c@example.com", "Mariana", "305867", expires);
        email.sendNewEmailConfirmationCode("d@example.com", "Mariana", "921438", expires);
        EmailTemplate.Email verification = code("a@example.com", "482913");
        assertEquals("SpotMeet - Confirmação de Cadastro", verification.subject());
        assertBoth(verification, "Olá, Mariana,", "482913", "Vale por 5 minutos");
        assertEquals("SpotMeet - Recuperação de Senha", code("b@example.com", "716204").subject());
        assertBoth(code("c@example.com", "305867"), "c@example.com", "1 de 2: confirmar o e-mail atual");
        assertBoth(code("d@example.com", "921438"), "d@example.com", "2 de 2: validar o novo e-mail");
    }

    @Test void accountNoticesDescribeWhatChanged() {
        email.sendPasswordChangedEmail("a@example.com", "Mariana", EmailService.PasswordOrigin.RECOVERY);
        email.sendEmailChangedEmail("old@example.com", "Mariana", "new@example.com");
        EmailTemplate.Email password = notice("a@example.com");
        assertEquals("SpotMeet - Sua senha foi alterada", password.subject());
        assertBoth(password, "a@example.com", "Pela recuperação de senha", "Esqueci minha senha");
        EmailTemplate.Email changed = notice("old@example.com");
        assertBoth(changed, "old@example.com", "new@example.com", "Seu e-mail de login mudou");
        verify(delivery, never()).sendNotice(eq("new@example.com"), any());
    }

    @Test void organizationNoticesNameTheOrganizationAndWhoDecided() {
        email.sendAccessApprovedEmail("a@example.com", "Mariana", "Centro Acadêmico", "Vitor Guerra");
        email.sendAccessRejectedEmail("b@example.com", "Mariana", "Centro Acadêmico", LocalDateTime.of(2026, 10, 30, 9, 0), "Vitor Guerra");
        email.sendCommitteeInvitationEmail("c@example.com", "Mariana", "Centro Acadêmico", "Financeiro", "Vitor Guerra");
        email.sendOrganizationApprovedEmail("d@example.com", "Mariana", "Centro Acadêmico");
        email.sendOrganizationRejectedEmail("e@example.com", "Mariana", "Centro Acadêmico", "Informe o nome oficial.");
        assertEquals("SpotMeet - Acesso aprovado: Centro Acadêmico", notice("a@example.com").subject());
        assertBoth(notice("a@example.com"), "Você agora faz parte de Centro Acadêmico", "Vitor Guerra");
        assertBoth(notice("b@example.com"), "Sexta-feira, 30 de outubro", "Vitor Guerra");
        EmailTemplate.Email invitation = notice("c@example.com");
        assertEquals("SpotMeet - Convite para a comissão Financeiro", invitation.subject());
        assertBoth(invitation, "Vitor Guerra convidou você", "Financeiro", "Aceitar ou Recusar");
        assertBoth(notice("d@example.com"), "Centro Acadêmico já pode usar o SpotMeet");
        assertBoth(notice("e@example.com"), "Informe o nome oficial.", "O cadastro não foi aprovado");
        assertTrue(notice("e@example.com").html().contains("Organização recusada"));
    }

    @Test void organizationInvitationBlockAndReactivationNotices() {
        email.sendOrganizationInvitationEmail("a@example.com", "Mariana", "Centro Acadêmico", "Vitor Guerra",
                java.time.Instant.parse("2026-11-02T17:32:00Z"));
        email.sendOrganizationBlockedEmail("b@example.com", "Mariana", "Centro Acadêmico", "Documentação pendente.");
        email.sendOrganizationReactivatedEmail("c@example.com", "Mariana", "Centro Acadêmico");
        EmailTemplate.Email invitation = notice("a@example.com");
        assertEquals("SpotMeet - Convite para a organização Centro Acadêmico", invitation.subject());
        assertBoth(invitation, "Vitor Guerra convidou você", "Vale até", "Aceitar ou Recusar");
        assertFalse(invitation.text().contains("Chave"));
        assertBoth(notice("b@example.com"), "Centro Acadêmico foi bloqueada", "Documentação pendente.", "Para pedir a reativação");
        assertEquals("SpotMeet - Organização reativada: Centro Acadêmico", notice("c@example.com").subject());
        assertBoth(notice("c@example.com"), "Centro Acadêmico voltou a funcionar", "Os integrantes continuam como estavam.");
    }

    @Test void meetingEmailsFollowTheInvitationChangeAndCancelModels() {
        MeetingText.Mail meeting = new MeetingText.Mail("Planejamento semanal", "Financeiro", "Segunda, 2 de novembro", "16:00 às 17:00",
                "Toda segunda", "Híbrida", "Sala 3", "https://meet.exemplo.com.br/x", "Revisar metas", "pauta.pdf (1,2 MB)",
                "http://192.168.0.10:8080/abrir/reuniao/5", "segunda, 2 de novembro, 16:00");
        email.sendMeetingInvitationEmail("a@example.com", "Mariana", meeting, "Vitor Guerra");
        email.sendMeetingChangedEmail("b@example.com", "Mariana", meeting, "Vitor Guerra", List.<String[]>of(new String[] {"Local", "Sala 3", "Sala 5"}));
        email.sendMeetingCanceledEmail("c@example.com", "Mariana", meeting, "Vitor Guerra", "Sala indisponível nesta data.");

        EmailTemplate.Email invitation = notice("a@example.com");
        assertEquals("Convite: Planejamento semanal, segunda, 2 de novembro, 16:00", invitation.subject());
        assertBoth(invitation, "Vitor Guerra convidou você para uma reunião da comissão Financeiro.", "Toda segunda",
                "pauta.pdf (1,2 MB). Baixe pelo aplicativo.", "Cada data tem um convite próprio", "Abrir no SpotMeet");
        assertTrue(invitation.html().contains("href=\"http://192.168.0.10:8080/abrir/reuniao/5\""));
        assertTrue(invitation.html().contains("participa da comissão Financeiro no SpotMeet"));

        EmailTemplate.Email changed = notice("b@example.com");
        assertEquals("Reunião alterada: Planejamento semanal", changed.subject());
        assertBoth(changed, "Sala 3", "Sala 5", "Só esta reunião foi alterada");

        EmailTemplate.Email canceled = notice("c@example.com");
        assertEquals("Reunião cancelada: Planejamento semanal", canceled.subject());
        assertBoth(canceled, "Sala indisponível nesta data.", "As outras reuniões da série continuam agendadas.");
    }

    @Test void aRefusalTellsThePresidentWhoDeclinedAndWhy() {
        MeetingText.Mail meeting = new MeetingText.Mail("Planejamento semanal", "Financeiro", "Segunda, 2 de novembro", "16:00 às 17:00",
                null, null, null, null, null, null, "http://192.168.0.10:8080/abrir/reuniao/5", "segunda, 2 de novembro, 16:00");
        email.sendInvitationDeclinedEmail("a@example.com", "Vitor", meeting, "Mariana", "Viagem marcada.");
        email.sendInvitationDeclinedEmail("b@example.com", "Vitor", meeting, "Bruno", null);

        EmailTemplate.Email withReason = notice("a@example.com");
        assertEquals("Convite recusado: Planejamento semanal", withReason.subject());
        assertBoth(withReason, "Mariana recusou o convite para uma reunião da comissão Financeiro.", "Quem recusou", "Mariana",
                "Segunda, 2 de novembro", "16:00 às 17:00", "Viagem marcada.", "Abrir no SpotMeet");
        assertTrue(withReason.html().contains("preside a comissão Financeiro no SpotMeet"));
        assertBoth(notice("b@example.com"), "Bruno não informou o motivo.");
    }

    @Test void minutesEmailsFollowTheApprovalModels() {
        MeetingText.Mail meeting = new MeetingText.Mail("Fechamento de outubro", "Financeiro", "Sexta, 30 de outubro", "10:00 às 11:00",
                null, null, "Sala 3", null, null, null, "http://192.168.0.10:8080/abrir/reuniao/1042", "sexta, 30 de outubro, 10:00");
        LocalDateTime moment = LocalDateTime.of(2026, 10, 30, 11, 20);
        email.sendMinutesForApprovalEmail("a@example.com", "Mariana", meeting, "Vitor Guerra", moment);
        email.sendMinutesApprovedEmail("b@example.com", "Mariana", meeting, "4 aprovaram, 1 reprovou, 2 sem resposta.", moment);
        email.sendMinutesRejectedEmail("c@example.com", "Vitor", meeting, "2 aprovaram, 3 reprovaram, 2 sem resposta.",
                "A maioria de quem respondeu reprovou.", List.<String[]>of(new String[] {"Carlos Lima", "A decisão sobre o orçamento não está na ata."}),
                moment, true);

        EmailTemplate.Email forApproval = notice("a@example.com");
        assertEquals("Ata para aprovar: Fechamento de outubro", forApproval.subject());
        assertBoth(forApproval, "Vitor Guerra enviou a ata de uma reunião da comissão Financeiro.", "30 de outubro, às 11:20",
                "Você registrou entrada nesta reunião, por isso pode responder.", "Abrir no SpotMeet");
        assertTrue(forApproval.html().contains("href=\"http://192.168.0.10:8080/abrir/reuniao/1042\""));

        EmailTemplate.Email approved = notice("b@example.com");
        assertEquals("Ata aprovada: Fechamento de outubro", approved.subject());
        assertBoth(approved, "4 aprovaram, 1 reprovou, 2 sem resposta.", "A maioria de quem respondeu aprovou.", "Uma ata aprovada não pode mais ser editada.");

        EmailTemplate.Email rejected = notice("c@example.com");
        assertEquals("Ata reprovada: Fechamento de outubro", rejected.subject());
        assertBoth(rejected, "Carlos Lima", "A decisão sobre o orçamento não está na ata.", "Corrija a ata e envie de novo.");
        assertTrue(rejected.html().contains("preside a comissão Financeiro"));
    }

    @Test void theButtonOnlyAcceptsWebAddresses() {
        MeetingText.Mail meeting = new MeetingText.Mail("Reunião", "Eventos", "Segunda, 2 de novembro", "Dia inteiro",
                null, null, null, null, null, null, "javascript:alert(1)", "segunda, 2 de novembro, dia inteiro");
        email.sendMeetingInvitationEmail("a@example.com", "Ana", meeting, "Vitor");
        assertFalse(notice("a@example.com").html().contains("javascript"));
    }

    @Test void valuesAreEscapedInTheHtmlAndSubjectsStayOnOneLine() {
        email.sendCommitteeInvitationEmail("a@example.com", "<b>Ana</b>", "Org & Cia", "Fin\r\nBcc: x@example.com", "<script>");
        EmailTemplate.Email mail = notice("a@example.com");
        assertFalse(mail.subject().contains("\n"));
        assertFalse(mail.subject().contains("\r"));
        assertFalse(mail.html().contains("<b>Ana</b>"));
        assertFalse(mail.html().contains("<script>"));
        assertTrue(mail.html().contains("Org &amp; Cia"));
        assertTrue(mail.text().contains("<b>Ana</b>"));
    }

    @Test void headerHasOnlyTheNameAndNoExternalImage() {
        email.sendOrganizationApprovedEmail("a@example.com", null, "Equipe");
        EmailTemplate.Email mail = notice("a@example.com");
        assertFalse(mail.html().contains("<img"));
        assertTrue(mail.html().contains(">SpotMeet</td>"));
        assertTrue(mail.text().startsWith("Olá, Usuário,"));
    }

    @Test void blankRecipientsAreSkipped() {
        for (String recipient : List.of("", " ")) email.sendAccessApprovedEmail(recipient, "Ana", "Org", "Líder");
        email.sendAccessApprovedEmail(null, "Ana", "Org", "Líder");
        verifyNoInteractions(delivery);
    }
}
