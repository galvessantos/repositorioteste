package com.montreal.oauth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/public")
    public String publicEndpoint() {
        return "Este endpoint é público e deve funcionar sem autenticação!";
    }

    @GetMapping("/health")
    public String health() {
        return "API está funcionando!";
    }
}