package org.printed.chat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RubberDuckController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "Rubber Duck - Inicio");
        return "home";
    }

    @GetMapping("/blog")
    public String blog(Model model) {
        model.addAttribute("title", "Rubber Duck - Blog");
        return "blog";
    }
}