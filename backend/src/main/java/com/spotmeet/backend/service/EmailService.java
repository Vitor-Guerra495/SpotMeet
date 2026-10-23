package com.spotmeet.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * Queues account e-mails only after their transaction commits.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private EmailDeliveryService deliveryService;

    /**
     * Sends the e-mail with the registration confirmation code.
     */
    public void sendVerificationEmail(String recipient, String name, String code, LocalDateTime expiresAt) {
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

        sendAfterCommit(recipient, subject, content, code, expiresAt);
    }

    /**
     * Sends the e-mail with the temporary password recovery token.
     */
    public void sendPasswordRecoveryEmail(String recipient, String name, String recoveryToken, LocalDateTime expiresAt) {
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

        sendAfterCommit(recipient, subject, content, recoveryToken, expiresAt);
    }

    /**
     * Sends the authorization code to the current e-mail (step 1 of the LGPD e-mail change).
     */
    public void sendCurrentEmailChangeCode(String recipient, String name, String code, LocalDateTime expiresAt) {
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

        sendAfterCommit(recipient, subject, content, code, expiresAt);
    }

    /**
     * Sends the validation code to the new e-mail (step 2 of the LGPD e-mail change).
     */
    public void sendNewEmailConfirmationCode(String recipient, String name, String code, LocalDateTime expiresAt) {
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

        sendAfterCommit(recipient, subject, content, code, expiresAt);
    }

    public void sendPasswordChangedEmail(String recipient) {
        String subject = "SpotMeet - Senha alterada";
        String content = "Olá,\n\n"
                + "A senha da sua conta SpotMeet foi alterada. As sessões anteriores foram invalidadas.\n\n"
                + "Se você não reconhece esta alteração, utilize a recuperação de senha no aplicativo para proteger sua conta.\n"
                + "O SpotMeet nunca solicita sua senha ou códigos de verificação por e-mail.\n\n"
                + "Equipe SpotMeet";
        afterCommit(() -> deliveryService.sendPasswordChangedEmail(recipient, subject, content),
                "Fila de e-mails indisponível. O aviso de alteração de senha não foi agendado.");
    }

    private void sendAfterCommit(String recipient, String subject, String content, String code, LocalDateTime expiresAt) {
        afterCommit(() -> deliveryService.sendEmail(recipient, subject, content, code, expiresAt),
                "Fila de e-mails indisponível. Será necessário solicitar um novo código.");
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
