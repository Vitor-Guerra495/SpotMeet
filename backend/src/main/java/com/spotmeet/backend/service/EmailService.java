package com.spotmeet.backend.service;

import com.spotmeet.backend.service.EmailTemplate.Email;
import com.spotmeet.backend.service.EmailTemplate.Tone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.spotmeet.backend.service.EmailTemplate.ACCOUNT_FOOTER;
import static com.spotmeet.backend.service.EmailTemplate.MEMBER_FOOTER;

/**
 * Builds the SpotMeet e-mails and queues them only after their transaction commits.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR);
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM', às' HH:mm", PT_BR);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("d 'de' MMMM', às' HH:mm", PT_BR);

    private static final String SECURITY = "Equipe de Segurança SpotMeet";
    private static final String PRIVACY = "Equipe de Segurança & Privacidade SpotMeet";
    private static final String TEAM = "Equipe SpotMeet";
    private static final String NOT_SCHEDULED = "Fila de e-mails indisponível. O aviso não foi agendado.";

    /** Where the password change came from, shown in the notice. */
    public enum PasswordOrigin { APP, RECOVERY }

    @Autowired
    private EmailDeliveryService deliveryService;

    /**
     * Sends the e-mail with the registration confirmation code.
     */
    public void sendVerificationEmail(String recipient, String name, String code, LocalDateTime expiresAt) {
        Email email = new EmailTemplate("SpotMeet - Confirmação de Cadastro",
                "Use o código para ativar sua conta. Ele vale por 5 minutos.", name,
                "Seja bem-vindo(a) ao SpotMeet! Para ativar sua conta, utilize o código de validação abaixo no aplicativo.")
                .heading("Confirme seu cadastro")
                .code("Código de verificação", code)
                .note("Não foi você?", "Se você não fez este cadastro, ignore esta mensagem com segurança.")
                .build(ACCOUNT_FOOTER, SECURITY);
        sendCodeAfterCommit(recipient, email, code, expiresAt);
    }

    /**
     * Sends the e-mail with the temporary password recovery code.
     */
    public void sendPasswordRecoveryEmail(String recipient, String name, String recoveryToken, LocalDateTime expiresAt) {
        Email email = new EmailTemplate("SpotMeet - Recuperação de Senha",
                "Use o código para criar uma nova senha. Ele vale por 5 minutos.", name,
                "Recebemos uma solicitação para redefinir a senha da sua conta SpotMeet. Utilize o código de recuperação abaixo no aplicativo.")
                .heading("Recupere sua senha")
                .code("Código de recuperação", recoveryToken)
                .note("Não pediu a redefinição?", "Desconsidere este e-mail. Suas credenciais permanecem protegidas.")
                .build(ACCOUNT_FOOTER, SECURITY);
        sendCodeAfterCommit(recipient, email, recoveryToken, expiresAt);
    }

    /**
     * Sends the authorization code to the current e-mail (step 1 of the LGPD e-mail change).
     */
    public void sendCurrentEmailChangeCode(String recipient, String name, String code, LocalDateTime expiresAt) {
        Email email = new EmailTemplate("SpotMeet - Autorização de Alteração de E-mail (LGPD)",
                "Confirme que foi você antes de trocar o e-mail da conta.", name,
                "Recebemos uma solicitação para alterar o e-mail da sua conta SpotMeet. Em conformidade com a LGPD, utilize o código abaixo para confirmar que foi você.")
                .heading("Autorize a troca de e-mail")
                .code("Código de autorização", code)
                .details("Conta", recipient, "Etapa", "1 de 2: confirmar o e-mail atual")
                .warning("Não foi você?", "Altere sua senha imediatamente, pois alguém pode ter tentado acessar sua conta.")
                .build(ACCOUNT_FOOTER, PRIVACY);
        sendCodeAfterCommit(recipient, email, code, expiresAt);
    }

    /**
     * Sends the validation code to the new e-mail (step 2 of the LGPD e-mail change).
     */
    public void sendNewEmailConfirmationCode(String recipient, String name, String code, LocalDateTime expiresAt) {
        Email email = new EmailTemplate("SpotMeet - Validação do Novo Endereço de E-mail",
                "Use o código para ativar o novo e-mail da sua conta.", name,
                "Você pediu para vincular este endereço à sua conta SpotMeet. Para concluir a alteração, utilize o código abaixo no aplicativo.")
                .heading("Valide o novo e-mail")
                .code("Código de ativação", code)
                .details("Novo e-mail", recipient, "Etapa", "2 de 2: validar o novo e-mail")
                .note("O que muda", "Depois da confirmação, o login no SpotMeet passa a ser feito somente com este novo endereço.")
                .build(ACCOUNT_FOOTER, PRIVACY);
        sendCodeAfterCommit(recipient, email, code, expiresAt);
    }

    public void sendPasswordChangedEmail(String recipient, String name, PasswordOrigin origin) {
        Email email = new EmailTemplate("SpotMeet - Sua senha foi alterada", "SpotMeet - Sua senha foi alterada", name,
                "A senha da sua conta SpotMeet foi alterada.")
                .badge("Senha alterada", Tone.SUCCESS)
                .heading("Sua senha foi alterada")
                .details("Conta", recipient, "Quando", now(),
                        "Origem", origin == PasswordOrigin.RECOVERY ? "Pela recuperação de senha" : "Pelo aplicativo")
                .warning("Não foi você?", "Toque em Esqueci minha senha, na tela de entrada, e crie uma nova senha agora. "
                        + "Depois, avise o líder da sua organização.")
                .build(ACCOUNT_FOOTER, SECURITY);
        sendNoticeAfterCommit(recipient, email, "Fila de e-mails indisponível. O aviso de alteração de senha não foi agendado.");
    }

    /** Sent to the previous address, so the owner of the account notices an unexpected change. */
    public void sendEmailChangedEmail(String previousEmail, String name, String newEmail) {
        Email email = new EmailTemplate("SpotMeet - O e-mail da sua conta foi alterado", "SpotMeet - O e-mail da sua conta foi alterado", name,
                "O e-mail da sua conta SpotMeet foi alterado.")
                .badge("E-mail alterado", Tone.SUCCESS)
                .heading("Seu e-mail de login mudou")
                .emailChange(previousEmail, newEmail)
                .details("Quando", now())
                .warning("Não foi você?", "Alguém pode ter acessado sua conta. Entre em contato com o líder da sua organização para recuperar o acesso.")
                .build(ACCOUNT_FOOTER, PRIVACY);
        sendNoticeAfterCommit(previousEmail, email, NOT_SCHEDULED);
    }

    public void sendAccessApprovedEmail(String recipient, String name, String organization, String approvedBy) {
        Email email = new EmailTemplate("SpotMeet - Acesso aprovado: " + organization, "SpotMeet - Acesso aprovado: " + organization, name,
                "Boas notícias: o líder da organização aprovou sua solicitação.")
                .badge("Acesso aprovado", Tone.SUCCESS)
                .heading("Você agora faz parte de " + organization)
                .details("Organização", organization, "Perfil", "Membro", "Aprovado por", approvedBy)
                .note("Próximo passo", "Abra o SpotMeet e toque em Organização para ver as comissões e as próximas reuniões.")
                .build(MEMBER_FOOTER, TEAM);
        sendNoticeAfterCommit(recipient, email, NOT_SCHEDULED);
    }

    public void sendAccessRejectedEmail(String recipient, String name, String organization, LocalDateTime requestedAt, String decidedBy) {
        Email email = new EmailTemplate("SpotMeet - Solicitação de acesso recusada: " + organization,
                "SpotMeet - Solicitação de acesso recusada: " + organization, name,
                "O líder da organização respondeu à sua solicitação de acesso.")
                .badge("Solicitação recusada", Tone.DANGER)
                .heading("Seu acesso não foi aprovado")
                .details("Organização", organization, "Solicitado em", requestedAt == null ? "Não informado" : format(DAY, requestedAt),
                        "Decidido por", decidedBy)
                .note("E agora?", "Fale com o líder para entender a decisão. Se for o caso, você pode enviar uma nova solicitação "
                        + "com a chave de acesso, pelo aplicativo.")
                .build(MEMBER_FOOTER, TEAM);
        sendNoticeAfterCommit(recipient, email, NOT_SCHEDULED);
    }

    public void sendCommitteeInvitationEmail(String recipient, String name, String organization, String committee, String invitedBy) {
        Email email = new EmailTemplate("SpotMeet - Convite para a comissão " + committee, "SpotMeet - Convite para a comissão " + committee, name,
                invitedBy + " convidou você para participar de uma comissão.")
                .heading("Convite para a comissão " + committee)
                .details("Organização", organization, "Comissão", committee, "Convidado por", invitedBy)
                .note("Responda pelo aplicativo", "Abra o SpotMeet, vá em Organização e toque em Aceitar ou Recusar.")
                .paragraph("Você só passa a ver as reuniões da comissão depois de aceitar.")
                .build(MEMBER_FOOTER, TEAM);
        sendNoticeAfterCommit(recipient, email, NOT_SCHEDULED);
    }

    public void sendOrganizationApprovedEmail(String recipient, String name, String organization) {
        Email email = new EmailTemplate("SpotMeet - Organização aprovada: " + organization, "SpotMeet - Organização aprovada: " + organization, name,
                "A administração do SpotMeet analisou o cadastro da sua organização.")
                .badge("Organização aprovada", Tone.SUCCESS)
                .heading(organization + " já pode usar o SpotMeet")
                .details("Organização", organization, "Analisada em", now(), "Por", "Administração do SpotMeet")
                .note("Comece por aqui", "Compartilhe a chave de acesso para os integrantes pedirem entrada e crie as primeiras comissões.")
                .build(MEMBER_FOOTER, TEAM);
        sendNoticeAfterCommit(recipient, email, NOT_SCHEDULED);
    }

    public void sendOrganizationRejectedEmail(String recipient, String name, String organization, String reason) {
        Email email = new EmailTemplate("SpotMeet - Organização não aprovada: " + organization,
                "SpotMeet - Organização não aprovada: " + organization, name,
                "A administração do SpotMeet analisou o cadastro da sua organização.")
                .badge("Organização recusada", Tone.DANGER)
                .heading("O cadastro não foi aprovado")
                .reason(reason)
                .details("Organização", organization, "Analisada em", now())
                .paragraph("Corrija o que foi apontado e envie a organização novamente pelo aplicativo. "
                        + "Se tiver dúvidas sobre a decisão, fale com a administração do SpotMeet.")
                .build(MEMBER_FOOTER, TEAM);
        sendNoticeAfterCommit(recipient, email, NOT_SCHEDULED);
    }

    /** The invitation is answered in the app; the access key is not sent. */
    public void sendOrganizationInvitationEmail(String recipient, String name, String organization, String invitedBy, Instant expiresAt) {
        Email email = new EmailTemplate("SpotMeet - Convite para a organização " + organization,
                "Responda ao convite pelo aplicativo. Ele vale por 24 horas.", name,
                invitedBy + " convidou você para participar de uma organização no SpotMeet.")
                .heading("Convite para a organização " + organization)
                .details("Organização", organization, "Convidado por", invitedBy,
                        "Vale até", expiresAt == null ? "Não informado" : format(MOMENT, LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault())))
                .note("Responda pelo aplicativo", "Abra o SpotMeet, vá em Organização e toque em Aceitar ou Recusar. "
                        + "Ao aceitar, você entra na organização como membro.")
                .paragraph("Se você não conhece esta organização, ignore esta mensagem. Sem resposta, o convite vence sozinho.")
                .build(MEMBER_FOOTER, TEAM);
        sendNoticeAfterCommit(recipient, email, NOT_SCHEDULED);
    }

    public void sendOrganizationBlockedEmail(String recipient, String name, String organization, String reason) {
        Email email = new EmailTemplate("SpotMeet - Organização bloqueada: " + organization,
                "Sua organização foi bloqueada pela administração.", name,
                "A administração do SpotMeet alterou o status da sua organização.")
                .badge("Organização bloqueada", Tone.DANGER)
                .heading(organization + " foi bloqueada")
                .reason(reason)
                .details("Organização", organization, "Bloqueada em", now(), "Por", "Administração do SpotMeet")
                .warning("O que muda", "Enquanto estiver bloqueada, não é possível criar comissões, agendar reuniões, "
                        + "convidar pessoas nem fazer ou aprovar solicitações de acesso.")
                .paragraph("Para pedir a reativação, fale com a administração do SpotMeet.")
                .build(MEMBER_FOOTER, TEAM);
        sendNoticeAfterCommit(recipient, email, NOT_SCHEDULED);
    }

    public void sendOrganizationReactivatedEmail(String recipient, String name, String organization) {
        Email email = new EmailTemplate("SpotMeet - Organização reativada: " + organization,
                "Sua organização foi reativada.", name,
                "A administração do SpotMeet alterou o status da sua organização.")
                .badge("Organização reativada", Tone.SUCCESS)
                .heading(organization + " voltou a funcionar")
                .details("Organização", organization, "Reativada em", now(), "Por", "Administração do SpotMeet")
                .note("Tudo liberado", "Já é possível criar comissões, agendar reuniões e aprovar solicitações de acesso de novo. "
                        + "Os integrantes continuam como estavam.")
                .build(MEMBER_FOOTER, TEAM);
        sendNoticeAfterCommit(recipient, email, NOT_SCHEDULED);
    }

    /** One e-mail per series: each date of a series has its own answer in the app. */
    public void sendMeetingInvitationEmail(String recipient, String name, MeetingText.Mail meeting, String invitedBy) {
        EmailTemplate template = new EmailTemplate("Convite: " + meeting.title() + ", " + meeting.when(),
                "Convite para " + meeting.title() + ".", name,
                invitedBy + " convidou você para uma reunião da comissão " + meeting.committee() + ".")
                .heading(meeting.title())
                .details(meeting.details())
                .note("Responda pelo aplicativo", "Abra o SpotMeet, vá em Reuniões e toque em Aceitar ou Recusar.")
                .button("Abrir no SpotMeet", meeting.appLink());
        if (meeting.frequency() != null) {
            template.paragraph("Esta reunião se repete: " + meeting.frequency().toLowerCase(PT_BR) + ". Cada data tem um convite próprio "
                    + "no aplicativo, e você aceita ou recusa cada uma separadamente.");
        }
        sendNoticeAfterCommit(recipient, template.build(committeeFooter(meeting), TEAM), NOT_SCHEDULED);
    }

    /** Changes come as label, old value and new value. */
    public void sendMeetingChangedEmail(String recipient, String name, MeetingText.Mail meeting, String changedBy, List<String[]> changes) {
        EmailTemplate template = new EmailTemplate("Reunião alterada: " + meeting.title(), "A reunião " + meeting.title() + " mudou.", name,
                changedBy + " alterou a reunião da comissão " + meeting.committee() + ".")
                .heading(meeting.title())
                .section("O que mudou")
                .changes(changes)
                .section("Reunião completa")
                .details(meeting.details())
                .button("Abrir no SpotMeet", meeting.appLink());
        if (meeting.frequency() != null) {
            template.note("Só esta reunião foi alterada", meeting.date() + ". As outras reuniões da série continuam como estavam. "
                    + "Confira se ainda pode participar e responda pelo aplicativo.");
        } else {
            template.note("Confira sua resposta", "Se ainda puder participar, não precisa fazer nada. Se não puder, recuse pelo aplicativo.");
        }
        sendNoticeAfterCommit(recipient, template.build(committeeFooter(meeting), TEAM), NOT_SCHEDULED);
    }

    public void sendMeetingCanceledEmail(String recipient, String name, MeetingText.Mail meeting, String canceledBy, String reason) {
        EmailTemplate template = new EmailTemplate("Reunião cancelada: " + meeting.title(), "A reunião " + meeting.title() + " foi cancelada.", name,
                canceledBy + " cancelou uma reunião da comissão " + meeting.committee() + ".")
                .badge("Reunião cancelada", Tone.DANGER)
                .heading(meeting.title());
        if (reason != null) template.reason(reason);
        template.details(cancelDetails(meeting));
        if (meeting.frequency() != null) template.paragraph("Só esta reunião foi cancelada. As outras reuniões da série continuam agendadas.");
        sendNoticeAfterCommit(recipient, template.build(committeeFooter(meeting), TEAM), NOT_SCHEDULED);
    }

    /** Sent to the president when someone declines the invitation. */
    public void sendInvitationDeclinedEmail(String recipient, String name, MeetingText.Mail meeting, String declinedBy, String reason) {
        EmailTemplate template = new EmailTemplate("Convite recusado: " + meeting.title(), declinedBy + " não vai participar de " + meeting.title() + ".", name,
                declinedBy + " recusou o convite para uma reunião da comissão " + meeting.committee() + ".")
                .badge("Convite recusado", Tone.DANGER)
                .heading(meeting.title())
                .details("Quem recusou", declinedBy, "Data", meeting.date(), "Horário", meeting.time());
        if (reason != null) template.reason(reason);
        else template.paragraph(declinedBy + " não informou o motivo.");
        template.button("Abrir no SpotMeet", meeting.appLink());
        String footer = "Você recebeu este e-mail porque preside a comissão " + meeting.committee() + " no SpotMeet.";
        sendNoticeAfterCommit(recipient, template.build(footer, TEAM), NOT_SCHEDULED);
    }

    /** Sent to each present person, except the writer, when the minutes go to approval. */
    public void sendMinutesForApprovalEmail(String recipient, String name, MeetingText.Mail meeting, String writer, LocalDateTime sentAt) {
        Email email = new EmailTemplate("Ata para aprovar: " + meeting.title(), "Leia a ata de " + meeting.title() + " e responda.", name,
                writer + " enviou a ata de uma reunião da comissão " + meeting.committee() + ". Leia e diga se você aprova ou reprova.")
                .section("Ata para aprovar")
                .heading(meeting.title())
                .details("Comissão", meeting.committee(), "Data", meeting.date(), "Horário", meeting.time(),
                        "Escrita por", writer, "Enviada em", format(DAY_TIME, sentAt))
                .note("Por que você recebeu", "Você registrou entrada nesta reunião, por isso pode responder. "
                        + "A ata vale pela maioria de quem responder.")
                .paragraph("Se reprovar, escreva o motivo. O presidente corrige a ata e envia de novo, e a contagem recomeça.")
                .button("Abrir no SpotMeet", meeting.appLink())
                .build(committeeFooter(meeting), TEAM);
        sendNoticeAfterCommit(recipient, email, NOT_SCHEDULED);
    }

    public void sendMinutesApprovedEmail(String recipient, String name, MeetingText.Mail meeting, String result, LocalDateTime closedAt) {
        Email email = new EmailTemplate("Ata aprovada: " + meeting.title(), "A ata de " + meeting.title() + " foi aprovada.", name,
                "A aprovação da ata de uma reunião da comissão " + meeting.committee() + " foi encerrada.")
                .badge("Ata aprovada", Tone.SUCCESS)
                .heading(meeting.title())
                .details("Resultado", result + "\nA maioria de quem respondeu aprovou.")
                .details("Comissão", meeting.committee(), "Data", meeting.date(), "Horário", meeting.time(),
                        "Aprovada em", format(DAY_TIME, closedAt))
                .note("Ata fechada", "Uma ata aprovada não pode mais ser editada. Você pode lê-la a qualquer momento no SpotMeet.")
                .button("Abrir no SpotMeet", meeting.appLink())
                .build(committeeFooter(meeting), TEAM);
        sendNoticeAfterCommit(recipient, email, NOT_SCHEDULED);
    }

    /** Reasons come as name and reason; the president gets the instructions to correct and send again. */
    public void sendMinutesRejectedEmail(String recipient, String name, MeetingText.Mail meeting, String result, String why,
                                         List<String[]> reasons, LocalDateTime closedAt, boolean president) {
        EmailTemplate template = new EmailTemplate("Ata reprovada: " + meeting.title(), "A ata de " + meeting.title() + " não foi aprovada.", name,
                "A aprovação da ata de uma reunião da comissão " + meeting.committee() + " foi encerrada, e a ata não foi aprovada.")
                .badge("Ata reprovada", Tone.DANGER)
                .heading(meeting.title())
                .details("Resultado", result + "\n" + why);
        if (!reasons.isEmpty()) {
            template.section("Motivos").details(reasons.stream().flatMap(Arrays::stream).toArray(String[]::new));
        }
        template.details("Comissão", meeting.committee(), "Data", meeting.date(), "Horário", meeting.time(),
                "Encerrada em", format(DAY_TIME, closedAt));
        if (president) {
            template.note("O que fazer", "Corrija a ata e envie de novo. A contagem de aprovações recomeça do zero.");
        } else {
            template.note("O que acontece agora", "O presidente vai corrigir a ata e enviar de novo. Você recebe um novo e-mail para responder.");
        }
        template.button("Abrir no SpotMeet", meeting.appLink());
        String footer = president ? "Você recebeu este e-mail porque preside a comissão " + meeting.committee() + " no SpotMeet."
                : committeeFooter(meeting);
        sendNoticeAfterCommit(recipient, template.build(footer, TEAM), NOT_SCHEDULED);
    }

    private static String[] cancelDetails(MeetingText.Mail meeting) {
        List<String> rows = new ArrayList<>(List.of("Data", meeting.date(), "Horário", meeting.time()));
        if (meeting.location() != null) { rows.add("Local"); rows.add(meeting.location()); }
        return rows.toArray(String[]::new);
    }

    private static String committeeFooter(MeetingText.Mail meeting) {
        return "Você recebeu este e-mail porque participa da comissão " + meeting.committee() + " no SpotMeet.";
    }

    private static String now() {
        return format(MOMENT, LocalDateTime.now());
    }

    private static String format(DateTimeFormatter formatter, LocalDateTime value) {
        String text = formatter.format(value);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void sendCodeAfterCommit(String recipient, Email email, String code, LocalDateTime expiresAt) {
        afterCommit(() -> deliveryService.sendEmail(recipient, email, code, expiresAt),
                "Fila de e-mails indisponível. Será necessário solicitar um novo código.");
    }

    private void sendNoticeAfterCommit(String recipient, Email email, String rejectionMessage) {
        if (recipient == null || recipient.isBlank()) return;
        afterCommit(() -> deliveryService.sendNotice(recipient, email), rejectionMessage);
    }

    private void afterCommit(Runnable send, String rejectionMessage) {
        Runnable dispatch = () -> {
            try {
                send.run();
            } catch (TaskRejectedException exception) {
                log.warn(rejectionMessage);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }
}
