package com.cipherchat.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI cipherChatOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CipherChat API")
                        .version("0.1.0")
                        .description("""
                                End-to-end encrypted chat. The server stores OpenPGP public keys and \
                                ciphertext only; all encryption and decryption happens in the browser. \
                                Log in via /api/auth/login, then click **Authorize** and paste the token."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
