package de.melinadanhier.projectflow.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/")
    public String home(Principal principal) {
        if (principal != null) {
            return "redirect:/projects";
        }
        return "home";
    }
}
