package com.melina.projectflow.plancontainer.project.controller;

import com.melina.projectflow.plancontainer.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
}
