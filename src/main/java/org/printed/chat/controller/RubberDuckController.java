package org.printed.chat.controller;

import org.printed.chat.model.BlogPost;
import org.printed.chat.service.BlogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class RubberDuckController {

    private final BlogService blogService;

    @Autowired
    public RubberDuckController(BlogService blogService) {
        this.blogService = blogService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "Rubber Duck - Inicio");
        return "home";
    }

    @GetMapping("/blog")
    public String blog(Model model) {
        List<BlogPost> posts = blogService.getAllPosts();
        model.addAttribute("title", "Rubber Duck - Blog");
        model.addAttribute("posts", posts);
        return "blog";
    }

    @GetMapping("/blog/post/{id}")
    public String blogPost(@PathVariable String id, Model model) {
        BlogPost post = blogService.getPostById(id);
        if (post == null) {
            return "redirect:/blog";
        }
        model.addAttribute("title", "Rubber Duck - " + post.getTitle());
        model.addAttribute("post", post);
        return "blogpost";
    }
}