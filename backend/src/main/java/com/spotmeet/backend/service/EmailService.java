package com.spotmeet.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Transactional e-mail service (account verification and recovery).
 * Uses JavaMailSender with a resilient fallback to local logging.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String sender;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    /**
     * Sends the e-mail with the registration confirmation code.
     * @return true if delivered through real SMTP, false on local fallback.
     */
    public boolean sendVerificationEmail(String recipient, String name, String code) {
        String subject = "SpotMeet - Confirmação de Cadastro";
        String content = "Olá " + (name != null && !name.isBlank() ? name : "Usuário") + ",\n\n"
                + "Seja bem-vindo(a) ao SpotMeet! Para ativar sua conta, utilize o código de validação abaixo:\n\n"
                + "====================================\n"
                + "   CÓDIGO DE VERIFICAÇÃO: " + code + "\n"
                + "====================================\n\n"
                + "Este código é estritamente confidencial e expira em 5 minutos.\n"
                + "Se você não realizou este cadastro, ignore esta mensagem com segurança.\n\n"
                + "Atenciosamente,\n"
                + "Equipe de Segurança SpotMeet";

        return sendEmailSafely(recipient, subject, content, code);
    }

    /**
     * Sends the e-mail with the temporary password recovery token.
     * @return true if delivered through real SMTP, false on local fallback.
     */
    public boolean sendPasswordRecoveryEmail(String recipient, String name, String recoveryToken) {
        String subject = "SpotMeet - Recuperação de Senha";
        String content = "Olá " + (name != null && !name.isBlank() ? name : "Usuário") + ",\n\n"
                + "Recebemos uma solicitação para redefinir a senha da sua conta SpotMeet.\n"
                + "Utilize o código de recuperação abaixo no aplicativo:\n\n"
                + "====================================\n"
                + "   CÓDIGO DE RECUPERAÇÃO: " + recoveryToken + "\n"
                + "====================================\n\n"
                + "Por motivos de segurança, este código expira em 5 minutos.\n"
                + "Se você não solicitou a redefinição de senha, desconsidere este e-mail; suas credenciais permanecem protegidas.\n\n"
                + "Atenciosamente,\n"
                + "Equipe de Segurança SpotMeet";

        return sendEmailSafely(recipient, subject, content, recoveryToken);
    }

    /**
     * Sends the authorization code to the current e-mail (step 1 of the LGPD e-mail change).
     */
    public boolean sendCurrentEmailChangeCode(String recipient, String name, String code) {
        String subject = "SpotMeet - Autorização de Alteração de E-mail (LGPD)";
        String content = "Olá " + (name != null && !name.isBlank() ? name : "Usuário") + ",\n\n"
                + "Recebemos uma solicitação para alterar o e-mail cadastrado na sua conta SpotMeet.\n"
                + "Em conformidade com a LGPD e as políticas de segurança da plataforma, utilize o código de autorização abaixo para confirmar que foi você quem solicitou:\n\n"
                + "====================================\n"
                + "   CÓDIGO DE AUTORIZAÇÃO: " + code + "\n"
                + "====================================\n\n"
                + "Este código expira em 5 minutos.\n"
                + "Se você NÃO solicitou a troca de e-mail, altere sua senha imediatamente, pois alguém pode ter tentado acessar sua conta.\n\n"
                + "Atenciosamente,\n"
                + "Equipe de Segurança & Privacidade SpotMeet";

        return sendEmailSafely(recipient, subject, content, code);
    }

    /**
     * Sends the validation code to the new e-mail (step 2 of the LGPD e-mail change).
     */
    public boolean sendNewEmailConfirmationCode(String recipient, String name, String code) {
        String subject = "SpotMeet - Validação do Novo Endereço de E-mail";
        String content = "Olá " + (name != null && !name.isBlank() ? name : "Usuário") + ",\n\n"
                + "Você solicitou vincular este endereço de e-mail à sua conta SpotMeet.\n"
                + "Para concluir a alteração e ativar este e-mail como seu novo acesso, utilize o código abaixo:\n\n"
                + "====================================\n"
                + "   CÓDIGO DE ATIVAÇÃO: " + code + "\n"
                + "====================================\n\n"
                + "Este código expira em 5 minutos.\n"
                + "Após a confirmação, seu login no SpotMeet passará a ser realizado exclusivamente com este novo endereço.\n\n"
                + "Atenciosamente,\n"
                + "Equipe de Segurança & Privacidade SpotMeet";

        return sendEmailSafely(recipient, subject, content, code);
    }

    /**
     * Sends the e-mail through SMTP with failure handling and a development log fallback.
     */
    private boolean sendEmailSafely(String recipient, String subject, String content, String code) {
        log.info("[SpotMeet-Email] Tentando envio para: {} | Assunto: {}", recipient, subject);

        boolean hasSmtpCredentials = sender != null && !sender.isBlank()
                && smtpPassword != null && !smtpPassword.isBlank();

        if (mailSender != null && hasSmtpCredentials) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(sender);
                message.setTo(recipient);
                message.setSubject(subject);
                message.setText(content);
                mailSender.send(message);
                log.info("[SpotMeet-Email] E-mail SMTP entregue com sucesso para {}", recipient);
                return true;
            } catch (Exception e) {
                log.warn("[SpotMeet-Email] Falha no envio SMTP real ({}). Ativando modo fallback local.", e.getMessage());
            }
        } else {
            log.info("[SpotMeet-Email] Credenciais SMTP (SPRING_MAIL_USERNAME / SPRING_MAIL_PASSWORD) não preenchidas no application.properties. Ativando modo local/desenvolvimento.");
        }

        // Fallback for development and automated tests without SMTP
        log.info("\n"
                + "=================================================================\n"
                + " [SPOTMEET EMAIL EMULADO - MODO DESENVOLVIMENTO / FALLBACK LOCAL] \n"
                + " Para:        " + recipient + "\n"
                + " Assunto:     " + subject + "\n"
                + " Código OTP:  >>> " + code + " <<<\n"
                + " Conteúdo:    \n" + content + "\n"
                + "=================================================================");
        return false;
    }
}
