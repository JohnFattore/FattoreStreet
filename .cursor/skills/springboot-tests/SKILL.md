---
name: springboot-tests
description: Write JUnit 5 tests for Spring Boot controllers, services, repositories, and utilities. Use when the user asks to create, add, or write tests for Java code in springboot/.
---

# Spring Boot Test Writing

## Stack

JUnit 5 (Jupiter) + Mockito + Spring Boot Test (`spring-boot-starter-test`)

## File Conventions

- Tests mirror the main source tree: `springboot/src/test/java/com/fattorestreet/sec_api/`
- Example: class `com.fattorestreet.sec_api.fundamentals.EdgarService` → test in `com/fattorestreet/sec_api/fundamentals/EdgarServiceTest.java`
- Reference: `QuarterUtilsTest.java` for plain unit test style
- Run: `cd springboot && mvn test`
- Run single: `mvn -Dtest=EdgarServiceTest test`

## Test Slices

Pick the right annotation based on what you're testing:

| Target | Annotation | Notes |
|--------|-----------|-------|
| Utility/POJO | None | Plain JUnit, no Spring context |
| Service | `@ExtendWith(MockitoExtension.class)` | `@Mock` + `@InjectMocks` |
| Controller | `@WebMvcTest(MyController.class)` | `MockMvc` + `@MockitoBean` |
| Repository | `@DataJpaTest` | In-memory DB, auto-rollback |
| Full integration | `@SpringBootTest` | Full context, use sparingly |

## Test Patterns

### Utility / Plain Unit Test

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuarterUtilsTest {
    @Test
    void parsesQuarterString() {
        assertEquals(1, QuarterUtils.parseQuarter("Q1-2024"));
    }
}
```

### Service Test (Mockito)

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EdgarServiceTest {

    @Mock
    private EdgarRepository edgarRepository;

    @InjectMocks
    private EdgarService edgarService;

    @Test
    void returnsDataForValidTicker() {
        when(edgarRepository.findByTicker("AAPL")).thenReturn(List.of(mockEntity));
        var result = edgarService.getFilings("AAPL");
        assertFalse(result.isEmpty());
        verify(edgarRepository).findByTicker("AAPL");
    }
}
```

### Controller Test (MockMvc)

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EdgarController.class)
class EdgarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EdgarService edgarService;

    @Test
    void getFilingsReturns200() throws Exception {
        when(edgarService.getFilings("AAPL")).thenReturn(List.of());
        mockMvc.perform(get("/api/edgar/filings").param("ticker", "AAPL"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray());
    }
}
```

### Repository Test (DataJpaTest)

Boot 4 relocated the JPA test slice: `@DataJpaTest` lives in `spring-boot-data-jpa-test` and `TestEntityManager` in `spring-boot-jpa-test` (both already declared in the pom). Use `@ActiveProfiles("test")` so the H2 URL with `NON_KEYWORDS=YEAR` is used — `spring.test.database.replace=none` in `application-test.properties` keeps it from being swapped out.

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class EdgarRepositoryTest {

    @Autowired
    private EdgarRepository repository;
    @Autowired
    private TestEntityManager em;

    @Test
    void findsFilingsByTicker() {
        // Seed with em.persist(...), then em.flush(); em.clear() before querying
        var results = repository.findByTicker("MSFT");
        assertFalse(results.isEmpty());
    }
}
```

## Shared Test Helpers (`testsupport/`)

- `TestJwtTokens` — mints HS256 JWTs for controller auth tests (`accessToken(secret, userId)`, plus an expiry-aware overload); pair with `@Import(SecurityConfig.class)` and `@TestPropertySource(properties = "SECRET_KEY=...")`
- `PcapTestData` — builds IEX TOPS pcap byte streams programmatically (no committed binaries) for `PcapParser`/`IexHistService` tests

## Coverage

JaCoCo runs with `mvn test`; report at `target/site/jacoco/index.html`. `mvn verify` fails if bundle line coverage drops below the floor configured in `pom.xml` (`jacoco:check`) — raise the floor when coverage meaningfully improves, never lower it to make a build pass.

## Workflow

1. **Identify the class** -- controller, service, repository, or utility
2. **Pick the test slice** -- see table above; prefer the lightest annotation that works
3. **Mock dependencies** -- `@MockitoBean` for Spring context tests, `@Mock` + `@InjectMocks` for plain Mockito
4. **Write tests**:
   - Happy path with valid inputs
   - Edge cases (null, empty, invalid)
   - For controllers: assert status code + JSON response structure
   - For services: verify repository/external calls with `verify()`
5. **Mirror package structure** -- test class lives in the same package under `src/test/java/`
6. **Run** -- `cd springboot && mvn test` or `mvn -Dtest=ClassName test`
