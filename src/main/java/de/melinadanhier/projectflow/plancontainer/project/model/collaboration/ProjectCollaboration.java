package de.melinadanhier.projectflow.plancontainer.project.model.collaboration;

import de.melinadanhier.projectflow.plancontainer.template.model.CollaborationMode;

/** Common collaboration behavior shared by project entities, forms and views. */
public interface ProjectCollaboration {

    CollaborationMode getCollaborationMode();

    default boolean isGroupProject() {
        return getCollaborationMode() == CollaborationMode.GROUP;
    }
}
