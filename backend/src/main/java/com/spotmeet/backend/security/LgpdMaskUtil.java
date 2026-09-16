package com.spotmeet.backend.security;

/**
 * Anonymization and masking utility for sensitive data (LGPD compliance).
 * Ensures PII is not exposed in reports or logs.
 */
public class LgpdMaskUtil {

    private LgpdMaskUtil() {}

    /**
     * Masks an e-mail address as j***o@domain.com.
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "[NÃO INFORMADO]";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }

        return local.substring(0, 2) + "***" + local.charAt(local.length() - 1) + domain;
    }

    /**
     * Masks a CNPJ as XX.XXX.XXX/0001-XX.
     */
    public static String maskCnpj(String cnpj) {
        if (cnpj == null || cnpj.isBlank()) {
            return "[NÃO CADASTRADO]";
        }
        String digits = cnpj.replaceAll("\\D", "");
        if (digits.length() == 14) {
            return digits.substring(0, 2) + ".***.***/" + digits.substring(8, 12) + "-**";
        }
        return "**.***.***/****-**";
    }

    /**
     * Sanitizes details or messages by removing passwords or tokens in plain text.
     */
    public static String sanitizeDetails(String text) {
        if (text == null) return "";
        return text
                .replaceAll("(?i)(password|senha|secret|token)=[^,;\\s]+", "$1=***PROTEGIDO***")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*", "Bearer ***PROTEGIDO***");
    }
}
