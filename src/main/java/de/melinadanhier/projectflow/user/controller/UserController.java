package de.melinadanhier.projectflow.user.controller;

import de.melinadanhier.projectflow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
}
