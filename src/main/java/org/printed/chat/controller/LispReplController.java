package org.printed.chat.controller;

import org.printed.chat.interpreter.LispInterpreter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class LispReplController {

    // Muestra la página del REPL
    @GetMapping("/repl")
    public String showReplPage(Model model) {
        return "repl";
    }

    // Maneja las peticiones de evaluación de código Lisp
    @PostMapping("/repl/eval")
    @ResponseBody
    public String evaluateLisp(@RequestBody String lispCode) {
        try {
            LispInterpreter interpreter = new LispInterpreter();
            return interpreter.eval(lispCode).toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}