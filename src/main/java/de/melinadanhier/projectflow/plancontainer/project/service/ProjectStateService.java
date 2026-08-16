package de.melinadanhier.projectflow.plancontainer.project.service;

import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.plancontainer.project.model.Project;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectLocation;
import de.melinadanhier.projectflow.plancontainer.project.model.ProjectStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectStateService {

    public void changeState(Project project, ProjectStatus status, ProjectLocation location) {
        requireConsistent(status, location);
        project.setStatus(status);
        project.setLocation(location);
    }

    public void requireConsistent(Project project) {
        requireConsistent(project.getStatus(), project.getLocation());
    }

    public void requireConsistent(ProjectStatus status, ProjectLocation location) {
        boolean draftStatus = status == ProjectStatus.DRAFT;
        boolean draftLocation = location == ProjectLocation.DRAFT;
        if (draftStatus != draftLocation) {
            throw new DomainValidationException(
                    "Entwurfsstatus und Entwurfsbereich müssen gemeinsam gesetzt sein."
            );
        }
    }
}
