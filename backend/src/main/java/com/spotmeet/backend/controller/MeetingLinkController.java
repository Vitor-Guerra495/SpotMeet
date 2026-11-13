package com.spotmeet.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Page opened by the "Abrir no SpotMeet" button of the meeting e-mails.
 *
 * Mail apps only open web addresses, so the e-mail points here and the page sends the phone to the app
 * (spotmeet://reunioes/{id}). It shows no data of the meeting; the app asks for the login as usual.
 */
@RestController
public class MeetingLinkController {

    /** GET /abrir/reuniao/{meetingId}: public page that opens the meeting in the app. */
    @GetMapping(value = "/abrir/reuniao/{meetingId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> open(@PathVariable Long meetingId) {
        String target = "spotmeet://reunioes/" + meetingId;
        String page = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Abrir no SpotMeet</title></head>"
                + "<body style=\"margin:0;background:#f4f6f9;font-family:Roboto,Arial,Helvetica,sans-serif;color:#1a1a1a\">"
                + "<div style=\"max-width:420px;margin:48px auto;padding:28px 24px;background:#ffffff;border-radius:16px;text-align:center\">"
                + "<p style=\"margin:0 0 8px;font-size:22px;font-weight:700;color:#5c6bc0\">SpotMeet</p>"
                + "<p style=\"margin:0 0 24px;font-size:16px;line-height:24px\">Toque no botão para ver a reunião no aplicativo.</p>"
                + "<a href=\"" + target + "\" style=\"display:inline-block;background:#5c6bc0;color:#ffffff;text-decoration:none;"
                + "font-weight:700;padding:14px 24px;border-radius:12px\">Abrir no SpotMeet</a>"
                + "<p style=\"margin:24px 0 0;font-size:13px;line-height:20px;color:#666666\">Se o aplicativo não abrir, abra o SpotMeet "
                + "e vá em Reuniões. No iPhone pelo Expo Go, este botão não funciona; use o caminho pelo aplicativo.</p>"
                + "</div><script>setTimeout(function(){location.href='" + target + "';},300);</script></body></html>";
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff").body(page);
    }
}
