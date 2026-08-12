package com.melina.projectflow.plancontainer.template.repository;

import com.melina.projectflow.plancontainer.template.model.Template;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TemplateRepository extends JpaRepository<Template, UUID> {
}
