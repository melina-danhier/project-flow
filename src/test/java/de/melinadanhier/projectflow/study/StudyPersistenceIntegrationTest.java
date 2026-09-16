package de.melinadanhier.projectflow.study;

import de.melinadanhier.projectflow.study.domain.StudySessionStatus;
import de.melinadanhier.projectflow.study.repository.StudyEventRepository;
import de.melinadanhier.projectflow.study.repository.StudySessionRepository;
import de.melinadanhier.projectflow.study.service.StudyTrackingService;
import de.melinadanhier.projectflow.study.service.StudyUserService;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class StudyPersistenceIntegrationTest {

    @Autowired
    private StudySessionRepository sessions;

    @Autowired
    private StudyEventRepository events;

    @Autowired
    private UserRepository users;

    @Test
    void startsUseDistinctGeneratedInternalIdsAndPersistNoCredentials() {
        Instant now = Instant.parse("2026-09-16T12:00:00Z");
        StudyTrackingService tracking = new StudyTrackingService(
                sessions, events, Clock.fixed(now, ZoneOffset.UTC));
        StudyUserService studyUsers = new StudyUserService(
                users, mock(SecurityContextRepository.class));

        var firstId = tracking.start(new MockHttpSession());
        var secondId = tracking.start(new MockHttpSession());
        var studyUser = studyUsers.createAnonymousStudyUser();
        sessions.flush();
        users.flush();

        assertThat(firstId).isNotNull().isNotEqualTo(secondId);
        assertThat(sessions.count()).isEqualTo(2);
        assertThat(events.count()).isEqualTo(2);
        assertThat(sessions.findAll())
                .allSatisfy(study -> {
                    assertThat(study.getConsentGivenAt()).isEqualTo(now);
                    assertThat(study.getStartedAt()).isEqualTo(now);
                    assertThat(study.getStatus()).isEqualTo(StudySessionStatus.ACTIVE);
                });
        assertThat(studyUser.getEmail()).isNull();
        assertThat(studyUser.getPasswordHash()).isNull();
        assertThat(studyUser.isStudyAccount()).isTrue();
    }
}
