package de.melinadanhier.projectflow.study.service;

import de.melinadanhier.projectflow.user.model.User;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudyEnrollmentService {

    private final StudyTrackingService trackingService;
    private final StudyUserService studyUserService;

    @Transactional
    public User start(HttpSession session) {
        trackingService.start(session);
        return studyUserService.createAnonymousStudyUser();
    }
}
