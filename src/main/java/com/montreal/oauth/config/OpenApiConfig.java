package com.montreal.oauth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(apiServers());
    }

    private Info apiInfo() {
        return new Info()
                .title("Montreal OAuth API")
                .version("1.0")
                .contact(apiContact())
                .description("API de autenticação e autorização com funcionalidades de redefinição de senha")
                .license(apiLicense());
    }

    private Contact apiContact() {
        return new Contact()
                .name("Montreal")
                .email("contato@montreal.com.br")
                .url("https://www.montreal.com.br");
    }

    private License apiLicense() {
        return new License()
                .name("MIT License")
                .url("https://www.montreal.com.br/");
    }

    private List<Server> apiServers() {
        List<Server> servers = new ArrayList<>();

        if ("dev".equalsIgnoreCase(activeProfile)) {
            servers.add(createServer("http://localhost:8080", "Development environment"));
        } else if ("local".equalsIgnoreCase(activeProfile)) {
            servers.add(createServer("http://localhost:8080", "Localhost environment"));
        } else if ("hml".equalsIgnoreCase(activeProfile)) {
            servers.add(createServer("https://homol-recupera2.montreal.com.br", "Homologation environment"));
        } else {
            servers.add(createServer("http://localhost:8080", "Default environment"));
        }

        return servers;
    }

    private Server createServer(String url, String description) {
        Server server = new Server();
        server.setUrl(url);
        server.setDescription(description);
        return server;
    }
}