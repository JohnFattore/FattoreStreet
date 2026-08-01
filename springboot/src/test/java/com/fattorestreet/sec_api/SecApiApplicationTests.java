package com.fattorestreet.sec_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
class SecApiApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
    }

    @Test
    void testSecretKeyWinsOverAnyDotEnvImport() {
        // application.properties optionally imports springboot/.env; the surefire
        // workingDirectory plus test-profile pinning must keep real secrets out.
        assertEquals("test-jwt-signing-secret-32chars-min!", environment.getProperty("SECRET_KEY"));
    }

    @Test
    void noSpringBootPropertyConsumesTheDjangoSecretKey() {
        // SECRET_KEY is Django's, and Django still needs it. Spring Boot's only consumer was
        // app.django-jwt-secret, feeding the JWT decoder for the retired /admin routes. Nothing
        // reads it now, and the Fargate task definitions no longer inject it -- so a property
        // reappearing here would mean a task started failing on a secret it no longer receives.
        assertNull(environment.getProperty("app.django-jwt-secret"));
    }

    @Test
    void contextStartsWithoutTheLlmServerUrl() {
        // The 10-K summarization generator is retired; its property went with it.
        assertNull(environment.getProperty("llm.server.url"));
    }
}
