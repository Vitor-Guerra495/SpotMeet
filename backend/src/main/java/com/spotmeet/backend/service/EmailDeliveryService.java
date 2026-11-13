package com.spotmeet.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class EmailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String sender;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Async("emailExecutor")
    public void sendEmail(String recipient, EmailTemplate.Email email, String code, LocalDateTime expiresAt) {
        if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
            log.info("Envio de e-mail descartado: código expirado.");
            return;
        }

        if (sender == null || sender.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            // Local QA fallback, only when SMTP is not configured.
            log.info("[SpotMeet-Email] Código local de QA: >>> {} <<< | Assunto: {}", code, email.subject());
            return;
        }

        sendMessage(recipient, email);
    }

    /** Notices without a code (password changed, access approved, invitation and so on). */
    @Async("emailExecutor")
    public void sendNotice(String recipient, EmailTemplate.Email email) {
        if (sender == null || sender.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            log.info("Aviso por e-mail não enviado: SMTP não configurado. Assunto: {}", email.subject());
            return;
        }
        sendMessage(recipient, email);
    }

    private void sendMessage(String recipient, EmailTemplate.Email email) {
        if (mailSender == null) {
            log.warn("Serviço SMTP indisponível para o envio de e-mail.");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(sender);
            helper.setTo(recipient);
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            mailSender.send(message);
            log.info("E-mail encaminhado ao servidor SMTP.");
        } catch (Exception exception) {
            log.warn("Não foi possível encaminhar o e-mail ao servidor SMTP.");
        }
    }
}
