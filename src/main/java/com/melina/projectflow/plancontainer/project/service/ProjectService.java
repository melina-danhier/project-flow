package com.melina.projectflow.plancontainer.project.service;

import com.melina.projectflow.plancontainer.project.mapper.ProjectMapper;
import com.melina.projectflow.plancontainer.project.repository.ProjectMemberRepository;
import com.melina.projectflow.plancontainer.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMapper projectMapper;
}
