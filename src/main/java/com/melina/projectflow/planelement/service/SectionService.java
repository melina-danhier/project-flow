package com.melina.projectflow.planelement.service;

import com.melina.projectflow.planelement.mapper.PlanElementMapper;
import com.melina.projectflow.planelement.repository.PlanSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final PlanSectionRepository planSectionRepository;
    private final PlanElementMapper planElementMapper;
}
