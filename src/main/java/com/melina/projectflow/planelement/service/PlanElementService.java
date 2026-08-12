package com.melina.projectflow.planelement.service;

import com.melina.projectflow.planelement.mapper.PlanElementMapper;
import com.melina.projectflow.planelement.repository.PlanElementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlanElementService {

    private final PlanElementRepository planElementRepository;
    private final PlanElementMapper planElementMapper;
}
