package com.course.app.web;

import com.course.app.domain.QuizStatus;
import com.course.app.dto.QuizDto;
import com.course.app.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/ui/quizzes")
@RequiredArgsConstructor
public class QuizUiController {

    private final QuizService service;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("quizzes", service.findAll());
        return "quizzes";
    }

    @PostMapping
    public String create(@RequestParam String title,
                         @RequestParam(required = false) String description,
                         @AuthenticationPrincipal OidcUser user) {
        service.create(new QuizDto(null, title, description, QuizStatus.DRAFT, null, null, List.of()),
                user.getPreferredUsername());
        return "redirect:/ui/quizzes";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable UUID id, @RequestParam String title,
                         @RequestParam(required = false) String description) {
        QuizDto old = service.findById(id);
        service.update(id, new QuizDto(id, title, description, old.status(), null, null, old.questions()));
        return "redirect:/ui/quizzes";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id) {
        service.delete(id);
        return "redirect:/ui/quizzes";
    }
}
