package com.spotmeet.backend.service;

import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Locale;

/**
 * Builds the HTML and the plain-text version of a SpotMeet e-mail from the same blocks.
 * Every value is escaped, so names and organization names are shown as plain text.
 */
final class EmailTemplate {

    record Email(String subject, String text, String html) {}

    enum Tone {
        SUCCESS("#e7f1e8", "#296f2d"), DANGER("#fbeaea", "#bc2424");

        private final String background;
        private final String color;

        Tone(String background, String color) {
            this.background = background;
            this.color = color;
        }
    }

    static final String ACCOUNT_FOOTER = "Você recebeu este e-mail porque uma ação foi feita na sua conta SpotMeet.";
    static final String MEMBER_FOOTER = "Você recebeu este e-mail porque participa do SpotMeet.";

    private static final String FONT = "Roboto,Arial,Helvetica,sans-serif";
    private static final String TEXT = "#1a1a1a";
    private static final String MUTED = "#666666";
    private static final String LINE = "#e3e8ec";
    private static final String PANEL = "#ebedf7";
    private static final String WARNING = "#feeddb";
    private static final String WARNING_TEXT = "#a04f00";

    private final String subject;
    private final String preheader;
    private final StringBuilder html = new StringBuilder();
    private final StringBuilder text = new StringBuilder();

    EmailTemplate(String subject, String preheader, String name, String intro) {
        this.subject = oneLine(subject);
        this.preheader = preheader;
        String greeting = "Olá, " + (name != null && !name.isBlank() ? name : "Usuário") + ",";
        html.append("<p style=\"margin:0 0 8px;").append(font(400, 16, 24, TEXT)).append("\">").append(e(greeting)).append("</p>")
            .append("<p style=\"margin:0 0 20px;").append(font(400, 16, 24, MUTED)).append("\">").append(e(intro)).append("</p>");
        text.append(greeting).append("\n\n").append(intro).append("\n\n");
    }

    EmailTemplate badge(String label, Tone tone) {
        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 14px\"><tr><td style=\"background:")
            .append(tone.background).append(";border-radius:999px;padding:5px 12px;").append(font(700, 13, 18, tone.color)).append("\">")
            .append(e(label)).append("</td></tr></table>");
        return this;
    }

    EmailTemplate heading(String title) {
        html.append("<h1 style=\"margin:0 0 20px;").append(font(700, 26, 32, TEXT)).append("\">").append(e(title)).append("</h1>");
        text.append(title).append("\n\n");
        return this;
    }

    EmailTemplate code(String label, String code) {
        html.append(table()).append("<tr><td align=\"center\" style=\"background:").append(PANEL).append(";border-radius:12px;padding:22px 18px\">")
            .append("<p style=\"margin:0 0 6px;").append(font(500, 13, 20, MUTED)).append(";text-transform:uppercase;letter-spacing:.6px\">").append(e(label)).append("</p>")
            .append("<p style=\"margin:0 0 6px;padding-left:10px;font:700 36px/44px 'Roboto Mono',Consolas,Menlo,monospace;letter-spacing:10px;color:").append(TEXT).append("\">").append(e(code)).append("</p>")
            .append("<p style=\"margin:0;").append(font(400, 13, 20, MUTED)).append("\">Vale por 5 minutos. Não compartilhe este código.</p>")
            .append("</td></tr></table>").append(space(20));
        text.append(label).append(": ").append(code).append("\n").append("Vale por 5 minutos. Não compartilhe este código.\n\n");
        return this;
    }

    /** Label and value rows, given in pairs. */
    EmailTemplate details(String... labelsAndValues) {
        html.append(table(" style=\"border-top:1px solid " + LINE + "\""));
        for (int i = 0; i < labelsAndValues.length; i += 2) {
            String border = i + 2 < labelsAndValues.length ? "border-bottom:1px solid " + LINE + ";" : "";
            html.append("<tr><td valign=\"top\" width=\"96\" style=\"padding:12px 12px 12px 0;").append(border).append(font(500, 13, 20, MUTED)).append("\">")
                .append(e(labelsAndValues[i])).append("</td><td valign=\"top\" style=\"padding:12px 0;").append(border).append(font(400, 16, 24, TEXT))
                .append(";word-break:break-word;white-space:pre-line\">").append(e(labelsAndValues[i + 1])).append("</td></tr>");
            text.append(labelsAndValues[i]).append(": ").append(labelsAndValues[i + 1]).append("\n");
        }
        html.append("</table>").append(space(24));
        text.append("\n");
        return this;
    }

    /** Shows the moderation reason before the details. */
    EmailTemplate reason(String reason) {
        html.append(table()).append("<tr><td style=\"background:").append(PANEL).append(";border-radius:12px;padding:16px 18px\">")
            .append("<p style=\"margin:0 0 4px;").append(font(500, 13, 20, MUTED)).append("\">Motivo</p>")
            .append("<p style=\"margin:0;").append(font(400, 16, 24, TEXT)).append("\">").append(e(reason)).append("</p>")
            .append("</td></tr></table>").append(space(20));
        text.append("Motivo: ").append(reason).append("\n\n");
        return this;
    }

    EmailTemplate emailChange(String oldEmail, String newEmail) {
        return changes(List.<String[]>of(new String[] {"E-mail de login", oldEmail, newEmail}));
    }

    /** Label, old value and new value of each change, the old one struck through. */
    EmailTemplate changes(List<String[]> rows) {
        html.append(table()).append("<tr><td style=\"background:").append(PANEL).append(";border-radius:12px;padding:6px 18px\">");
        for (String[] row : rows) {
            html.append("<p style=\"margin:10px 0;").append(font(400, 16, 24, TEXT)).append("\">")
                .append("<span style=\"").append(font(500, 13, 20, MUTED)).append("\">").append(e(row[0])).append("</span><br>")
                .append("<span style=\"color:").append(MUTED).append(";text-decoration:line-through;word-break:break-all\">").append(e(row[1])).append("</span> para ")
                .append("<b style=\"word-break:break-all\">").append(e(row[2])).append("</b></p>");
            text.append(row[0]).append(": ").append(row[1]).append(" para ").append(row[2]).append("\n");
        }
        html.append("</td></tr></table>").append(space(20));
        text.append("\n");
        return this;
    }

    EmailTemplate section(String label) {
        html.append("<p style=\"margin:0 0 10px;").append(font(700, 12, 18, MUTED)).append(";letter-spacing:.8px;text-transform:uppercase\">")
            .append(e(label)).append("</p>");
        text.append(label.toUpperCase(Locale.ROOT)).append("\n");
        return this;
    }

    /** Button to a web address of the server; skipped when there is no address. */
    EmailTemplate button(String label, String url) {
        if (url == null || !url.matches("(?i)^https?://\\S+$")) return this;
        html.append(table()).append("<tr><td align=\"center\">")
            .append("<a href=\"").append(e(url)).append("\" style=\"display:inline-block;background:#5c6bc0;border-radius:12px;padding:12px 22px;")
            .append(font(700, 15, 22, "#ffffff")).append(";text-decoration:none\">").append(e(label)).append("</a>")
            .append("</td></tr></table>").append(space(16));
        text.append(label).append(": ").append(url).append("\n\n");
        return this;
    }

    EmailTemplate note(String title, String body) {
        return box(PANEL, TEXT, title, body);
    }

    EmailTemplate warning(String title, String body) {
        return box(WARNING, WARNING_TEXT, title, body);
    }

    EmailTemplate paragraph(String body) {
        html.append("<p style=\"margin:0 0 8px;").append(font(400, 14, 21, MUTED)).append("\">").append(e(body)).append("</p>").append(space(12));
        text.append(body).append("\n\n");
        return this;
    }

    Email build(String footer, String signature) {
        String page = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta name=\"color-scheme\" content=\"light dark\">"
                + "<title>" + e(subject) + "</title></head><body style=\"margin:0\">"
                + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;font-size:1px;line-height:1px;color:#ffffff\">" + e(preheader) + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f6f9\"><tr><td align=\"center\" style=\"padding:12px\">"
                + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;max-width:600px;background:#ffffff;border-radius:16px;overflow:hidden\">"
                + "<tr><td style=\"background:#5c6bc0;padding:18px 24px;" + font(700, 22, 28, "#ffffff") + "\">SpotMeet</td></tr>"
                + "<tr><td style=\"padding:28px 24px 8px\">" + html + "</td></tr>"
                + "<tr><td style=\"padding:8px 24px 28px\"><p style=\"margin:0;padding-top:16px;border-top:1px solid " + LINE + ";" + font(400, 12, 18, MUTED) + "\">"
                + e(footer) + "<br>" + e(signature) + "</p></td></tr>"
                + "</table></td></tr></table></body></html>";
        String plain = text + "--\n" + footer + "\n" + signature;
        return new Email(subject, plain, page);
    }

    private EmailTemplate box(String background, String titleColor, String title, String body) {
        html.append(table()).append("<tr><td style=\"background:").append(background).append(";border-radius:12px;padding:16px 18px\">")
            .append("<p style=\"margin:0 0 4px;").append(font(700, 15, 22, titleColor)).append("\">").append(e(title)).append("</p>")
            .append("<p style=\"margin:0;").append(font(400, 14, 22, TEXT)).append("\">").append(e(body)).append("</p>")
            .append("</td></tr></table>").append(space(12));
        text.append(title).append("\n").append(body).append("\n\n");
        return this;
    }

    private static String table() {
        return table("");
    }

    private static String table(String style) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"" + style + ">";
    }

    private static String space(int height) {
        return "<div style=\"height:" + height + "px;line-height:" + height + "px\">&nbsp;</div>";
    }

    private static String font(int weight, int size, int lineHeight, String color) {
        return "font:" + weight + " " + size + "px/" + lineHeight + "px " + FONT + ";color:" + color;
    }

    private static String e(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8");
    }

    /** Line breaks are not allowed in the subject. */
    static String oneLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }
}
