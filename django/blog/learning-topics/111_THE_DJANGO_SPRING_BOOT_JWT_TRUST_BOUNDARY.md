# The Django↔Spring Boot JWT trust boundary

_FattoreStreet @ [`f337c3fe`](https://github.com/JohnFattore/FattoreStreet/tree/f337c3fef734f52b9a5a4a696997a3960944a332) — 2026-07-19_

_Source: [#111](https://github.com/JohnFattore/FattoreStreet/issues/111)_

## Overview

FattoreStreet has two backend services — Django (issues auth tokens) and Spring Boot (protects `/admin/**` SEC-data endpoints) — that share zero infrastructure except one thing: a symmetric secret. Django's `SECRET_KEY` doubles as the HMAC signing key for SimpleJWT access tokens, and Spring Boot is configured to decode/verify those same tokens using `app.django-jwt-secret` (which defaults to the same value). Spring Boot never calls Django to check who's making the request — it trusts the JWT signature alone, then makes an authorization decision by inspecting a single claim (`user_id == 1`) baked into the token. This is a clean, low-latency way to share auth across services with completely different frameworks and languages, but it also means the entire admin-access boundary rests on: (a) the shared secret staying secret, (b) both services agreeing on token semantics (lifetime, algorithm, claim names), and (c) Spring Boot's claim-based authorization logic being airtight. It's worth understanding deeply because it's the kind of pattern (stateless, shared-secret, cross-service trust) that shows up constantly in microservice auth and is easy to get subtly wrong.

## Files to read

- `springboot/src/main/java/com/fattorestreet/sec_api/config/SecurityConfig.java` — the whole file (94 lines). Focus on:
  - Lines 30-31: `ADMIN_DJANGO_USER_ID = 1L` — authorization is "is this Django user #1", not a role system
  - Lines 33-42: `jwtDecoder` bean — builds a `NimbusJwtDecoder` directly from the shared secret bytes, HS256 only
  - Lines 44-62: `jwtAuthenticationConverter` / `authoritiesFromUserIdClaim` — how a raw JWT claim becomes a Spring Security `ROLE_ADMIN` grant
  - Lines 74-91: `securityFilterChain` — which paths are `permitAll()` vs `hasRole("ADMIN")`, and that `oauth2ResourceServer().jwt()` is what actually invokes the decoder/converter per-request
- `django/users/urls.py` (11 lines) — `TokenObtainPairView` / `TokenRefreshView` from `djangorestframework-simplejwt`, the only place tokens are minted
- `django/mysite/settings.py` — line 11 (`SECRET_KEY = env("SECRET_KEY")`) and lines 170-178 (`REST_FRAMEWORK` / `DEFAULT_AUTHENTICATION_CLASSES` wiring `JWTAuthentication`). Note there's no `SIMPLE_JWT` settings block anywhere in `settings.py`, so Django is running on djangorestframework-simplejwt's defaults (5-min access token, 1-day refresh token, HS256).
- `springboot/src/test/java/.../config/SecurityConfigTest.java` if present, or search for `TestJwtTokens` under `springboot/src/test/java/.../testsupport/` — see how tests mint tokens (`accessToken(secret, userId)`) to simulate this trust relationship in isolation.
- CLAUDE.md's Architecture → Authentication section for the one-paragraph summary this issue expands on.

## Questions to work through while reading

1. Spring Boot never contacts Django to validate a token — it just verifies the HMAC signature locally. What's the actual attack surface if `SECRET_KEY`/`app.django-jwt-secret` ever leaked (e.g., committed to git, logged, or exposed via a misconfigured env dump)? What could an attacker forge, and what's the blast radius given `hasRole("ADMIN")` gates `/admin/**`?
2. `authoritiesFromUserIdClaim` grants `ROLE_ADMIN` to whichever token carries `user_id: 1`. What happens to that authorization the day Django user #1 is deleted and a new user is later created with the recycled ID 1 (e.g., after a DB reset)? Is that a real risk here given how the app provisions users?
3. Spring Boot's `jwtDecoder` only accepts HS256. If Django's SimpleJWT config or a future dependency bump ever changed the signing algorithm (or someone added an `alg: none` handling path), what would happen? Why does hardcoding `macAlgorithm(MacAlgorithm.HS256)` in `NimbusJwtDecoder.withSecretKey(...)` matter defensively (relate this to the classic "JWT alg confusion" vulnerability class)?
4. Access tokens default to a 5-minute lifetime (no override in `settings.py`). Walk through what happens end-to-end when an admin's access token expires mid-session while calling a Spring Boot `/admin/**` route — does Spring Boot have any refresh mechanism, or does the caller need to go back to Django's `TokenRefreshView` first? (You may want to also skim `react-app/src/App.tsx`'s axios interceptor to see how the frontend handles this today, even though it's out of scope for the Spring Boot side.)
5. Why is claim-based authorization (`user_id == 1`) chosen here instead of Django issuing a proper `is_staff`/role claim in the token payload? What would need to change in both `TokenObtainPairView` (Django) and `authoritiesFromUserIdClaim` (Spring Boot) to support more than one admin user?

## Primer: stateless JWT trust across services

A JWT (JSON Web Token) is a signed, self-contained credential: header + payload + signature, base64url-encoded and dot-separated. When two services share the *signing secret* (as opposed to one service always validating with the other over the network), any service holding the secret can both mint and verify tokens without a shared session store or database lookup — that's what "stateless" auth means. This is fast and scales trivially (no session replication, no extra round-trip to check validity), but it inherits two classic risks: (1) the secret is now a single point of compromise shared across every service that holds it — rotating it requires coordinated deployment everywhere at once; (2) you can't cheaply revoke an individual token before its expiry (no central "logout" without extra infrastructure like a deny-list), so short access-token lifetimes (like the 5-minute default here) matter as a mitigation. HS256 (HMAC-SHA256) is a symmetric algorithm — same secret signs and verifies — which is why both services must have the identical `SECRET_KEY` value; this differs from RS256/ES256 asymmetric setups where a private key signs and a widely-distributed public key verifies, letting you share verification ability without sharing minting ability.

## External references

- JWT structure and claims: https://datatracker.ietf.org/doc/html/rfc7519
- Spring Security OAuth2 Resource Server JWT support (the `oauth2ResourceServer().jwt()` mechanism used in `SecurityConfig.java`): https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html

## Exercise (optional)

Locally, mint a Django access token via `POST /users/api/token/` for a non-admin test user, decode it at https://jwt.io (or `python -c` with `base64`) to see the `user_id` claim, then try hitting a Spring Boot `/admin/**` route with it and confirm you get a 403 rather than a 401 — then do the same with user_id 1 and confirm ROLE_ADMIN is granted. This makes the claim-based authorization concrete rather than theoretical.
