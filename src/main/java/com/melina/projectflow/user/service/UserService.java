package com.melina.projectflow.user.service;

import com.melina.projectflow.user.mapper.UserMapper;
import com.melina.projectflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
}
