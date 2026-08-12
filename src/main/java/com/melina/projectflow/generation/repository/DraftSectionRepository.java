package com.melina.projectflow.generation.repository;

import com.melina.projectflow.generation.model.DraftSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DraftSectionRepository extends JpaRepository<DraftSection, UUID> {
}
