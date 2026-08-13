package com.melina.projectflow.user.controller;

import com.melina.projectflow.user.dto.RegistrationForm;
import com.melina.projectflow.user.exception.DuplicateEmailException;
import com.melina.projectflow.user.service.UserRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class RegistrationController {

    private final UserRegistrationService registrationService;

    @GetMapping("/register")
    public String registrationForm(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registrationForm") RegistrationForm form,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            registrationService.register(form);
        } catch (DuplicateEmailException exception) {
            bindingResult.rejectValue("email", "email.duplicate", exception.getMessage());
            return "auth/register";
        }
        return "redirect:/login?registered";
    }
}
