package de.melinadanhier.projectflow.plancontainer.template.repository;

import de.melinadanhier.projectflow.plancontainer.template.model.Template;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface TemplateRepository extends JpaRepository<Template, UUID> {

    List<Template> findAllByActiveTrueOrderByTitleAsc();

    Optional<Template> findByIdAndActiveTrue(UUID templateId);
}
