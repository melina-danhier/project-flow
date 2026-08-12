package com.melina.projectflow.user.controller;

import com.melina.projectflow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
}
