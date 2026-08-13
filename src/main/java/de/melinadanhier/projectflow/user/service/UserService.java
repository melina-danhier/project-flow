package de.melinadanhier.projectflow.user.service;

import de.melinadanhier.projectflow.user.mapper.UserMapper;
import de.melinadanhier.projectflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
}
