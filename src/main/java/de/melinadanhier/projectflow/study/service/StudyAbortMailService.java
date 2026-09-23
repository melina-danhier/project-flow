package de.melinadanhier.projectflow.study.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Temporary service for sending study abort report emails (removable after the study).
 * Failures are logged but never prevent the actual study abort.
 */
@Service
@Slf4j
public class StudyAbortMailService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                    .withZone(ZoneId.of("Europe/Berlin"));

    private final JavaMailSender mailSender;
    private final String recipientAddress;

    public StudyAbortMailService(
            JavaMailSender mailSender,
            @Value("${projectflow.study.abort-report-mail-to:}") String recipientAddress
    ) {
        this.mailSender = mailSender;
        this.recipientAddress = recipientAddress;
    }

    /**
     * Sends a study abort report email. All parameters except {@code timestamp} are optional.
     *
     * @param comment       user-provided reason (may be null or blank)
     * @param currentPage   the page the user was on (diagnostic only, may be null)
     * @param studySessionId the anonymous study session ID (may be null)
     * @param timestamp     server-side timestamp of the abort
     */
    public void sendReport(String comment, String currentPage, UUID studySessionId, Instant timestamp) {
        if (recipientAddress == null || recipientAddress.isBlank()) {
            log.warn("Studienabbruch-Mail nicht gesendet: Keine Empfängeradresse konfiguriert (STUDY_ABORT_MAIL_TO).");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientAddress);
            message.setSubject("ProjectFlow \u2013 Studienabbruch");
            message.setText(buildBody(comment, currentPage, studySessionId, timestamp));

            mailSender.send(message);
            log.info("Studienabbruch-Mail erfolgreich gesendet.");
        } catch (MailException ex) {
            log.error("Studienabbruch-Mail konnte nicht gesendet werden.", ex);
        }
    }

    private String buildBody(String comment, String currentPage, UUID studySessionId, Instant timestamp) {
        StringBuilder sb = new StringBuilder();

        sb.append("Kommentar:\n");
        if (comment != null && !comment.isBlank()) {
            sb.append(comment.strip());
        } else {
            sb.append("(kein Kommentar)");
        }

        sb.append("\n\nTechnische Informationen:\n");
        sb.append("Zeitpunkt: ").append(FORMATTER.format(timestamp)).append("\n");

        if (currentPage != null && !currentPage.isBlank()) {
            sb.append("Seite: ").append(currentPage.strip()).append("\n");
        }

        if (studySessionId != null) {
            sb.append("Study-ID: ").append(studySessionId).append("\n");
        }

        return sb.toString();
    }
}
