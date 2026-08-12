package com.melina.projectflow;

import com.melina.projectflow.common.model.MutableEntity;
import com.melina.projectflow.generation.model.PlanDraft;
import com.melina.projectflow.plancontainer.model.PlanContainer;
import com.melina.projectflow.plancontainer.project.model.Project;
import com.melina.projectflow.plancontainer.template.model.Template;
import com.melina.projectflow.planelement.model.PlanElement;
import com.melina.projectflow.planelement.model.PlanSection;
import com.melina.projectflow.user.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStructureTest {

    @Test
    void placesCoreModelTypesInTheirFeaturePackages() {
        assertThat(User.class.getPackageName()).isEqualTo("com.melina.projectflow.user.model");
        assertThat(PlanContainer.class.getPackageName()).isEqualTo("com.melina.projectflow.plancontainer.model");
        assertThat(Project.class.getPackageName()).isEqualTo("com.melina.projectflow.plancontainer.project.model");
        assertThat(Template.class.getPackageName()).isEqualTo("com.melina.projectflow.plancontainer.template.model");
        assertThat(PlanElement.class.getPackageName()).isEqualTo("com.melina.projectflow.planelement.model");
        assertThat(PlanSection.class.getPackageName()).isEqualTo("com.melina.projectflow.planelement.model");
        assertThat(PlanDraft.class.getPackageName()).isEqualTo("com.melina.projectflow.generation.model");
        assertThat(MutableEntity.class.getPackageName()).isEqualTo("com.melina.projectflow.common.model");
    }
}
