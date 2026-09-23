package de.melinadanhier.projectflow.study.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Temporary DTO for the study abort report (removable after the study).
 */
@Getter
@Setter
public class StudyAbortReportForm {

    @Size(max = 1000)
    private String comment;

    @Size(max = 500)
    private String currentPage;
}
