package org.printed.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.printed.chat.interpreter.LispInterpreter;

@SpringBootApplication
public class RubberDuckApplication {
    public static void main(String[] args) {
        SpringApplication.run(RubberDuckApplication.class, args);
    }
}