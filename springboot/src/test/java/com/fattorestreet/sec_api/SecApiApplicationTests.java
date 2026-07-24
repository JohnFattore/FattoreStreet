package com.fattorestreet.sec_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals("test-jwt-signing-secret-32chars-min!",
                environment.getProperty("app.django-jwt-secret"));
    }
}
