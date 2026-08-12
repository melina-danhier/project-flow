package com.melina.projectflow.plancontainer.template.service;

import com.melina.projectflow.plancontainer.template.mapper.TemplateMapper;
import com.melina.projectflow.plancontainer.template.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateMapper templateMapper;
}
