package com.spotmeet.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
    public void sendEmail(String recipient, String subject, String content, String code, LocalDateTime expiresAt) {
        if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
            log.info("Envio de e-mail descartado: código expirado.");
            return;
        }

        if (sender == null || sender.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            // Local QA fallback, only when SMTP is not configured.
            log.info("[SpotMeet-Email] Código local de QA: >>> {} <<< | Assunto: {}", code, subject);
            return;
        }

        sendMessage(recipient, subject, content);
    }

    @Async("emailExecutor")
    public void sendPasswordChangedEmail(String recipient, String subject, String content) {
        if (sender == null || sender.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            log.info("Aviso de alteração de senha não enviado: SMTP não configurado.");
            return;
        }
        sendMessage(recipient, subject, content);
    }

    private void sendMessage(String recipient, String subject, String content) {
        if (mailSender == null) {
            log.warn("Serviço SMTP indisponível para o envio de e-mail.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("E-mail encaminhado ao servidor SMTP.");
        } catch (Exception exception) {
            log.warn("Não foi possível encaminhar o e-mail ao servidor SMTP.");
        }
    }
}
