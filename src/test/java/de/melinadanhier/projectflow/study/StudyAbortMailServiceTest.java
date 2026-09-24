package de.melinadanhier.projectflow.study;

import de.melinadanhier.projectflow.study.service.StudyAbortMailService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudyAbortMailServiceTest {

    @BeforeAll
    static void muteSimulatedFailureLogs() {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(StudyAbortMailService.class))
                .setLevel(ch.qos.logback.classic.Level.OFF);
    }

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Test
    void sendsMailWithAllFields() {
        StudyAbortMailService service = new StudyAbortMailService(mailSender, "study@example.com");
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant timestamp = Instant.parse("2026-09-22T20:45:00Z");

        service.sendReport("Fehler beim Plan", "/projects/123/plan", sessionId, timestamp);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("study@example.com");
        assertThat(msg.getSubject()).isEqualTo("ProjectFlow \u2013 Studienabbruch");
        assertThat(msg.getText())
                .contains("Fehler beim Plan")
                .contains("22.09.2026 22:45")
                .contains("/projects/123/plan")
                .contains("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void sendsMailWithoutOptionalFields() {
        StudyAbortMailService service = new StudyAbortMailService(mailSender, "study@example.com");
        Instant timestamp = Instant.parse("2026-09-22T20:45:00Z");

        service.sendReport(null, null, null, timestamp);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        String text = captor.getValue().getText();
        assertThat(text).contains("(kein Kommentar)");
        assertThat(text).contains("22.09.2026 22:45");
        assertThat(text).doesNotContain("Seite:");
        assertThat(text).doesNotContain("Study-ID:");
    }

    @Test
    void blankCommentShowsFallback() {
        StudyAbortMailService service = new StudyAbortMailService(mailSender, "study@example.com");

        service.sendReport("   ", null, null, Instant.now());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("(kein Kommentar)");
    }

    @Test
    void mailFailureDoesNotThrow() {
        StudyAbortMailService service = new StudyAbortMailService(mailSender, "study@example.com");
        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.sendReport("test", null, null, Instant.now()))
                .doesNotThrowAnyException();
    }

    @Test
    void noRecipientSkipsMailSilently() {
        StudyAbortMailService service = new StudyAbortMailService(mailSender, "");

        service.sendReport("test", null, null, Instant.now());

        verifyNoInteractions(mailSender);
    }

    @Test
    void nullRecipientSkipsMailSilently() {
        StudyAbortMailService service = new StudyAbortMailService(mailSender, null);

        service.sendReport("test", null, null, Instant.now());

        verifyNoInteractions(mailSender);
    }

    @Test
    void nullMailSenderSkipsMailSilently() {
        StudyAbortMailService service = new StudyAbortMailService(null, "study@example.com");

        assertThatCode(() -> service.sendReport("test", null, null, Instant.now()))
                .doesNotThrowAnyException();
    }

    @Test
    void springInstantiatesServiceWithoutJavaMailSenderBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(StudyAbortMailService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(StudyAbortMailService.class);
                });
    }
}
