package com.fattorestreet.sec_api.testsupport;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/** HS256 JWTs for tests; must match {@code SECRET_KEY} / {@code app.django-jwt-secret} in test properties. */
public final class TestJwtTokens {

    private TestJwtTokens() {
    }

    public static String accessToken(String hs256Secret, long djangoUserId) {
        try {
            byte[] secretBytes = hs256Secret.getBytes(StandardCharsets.UTF_8);
            JWSSigner signer = new MACSigner(secretBytes);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("user_id", djangoUserId)
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                    .issueTime(new Date())
                    .jwtID(UUID.randomUUID().toString())
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                    claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
