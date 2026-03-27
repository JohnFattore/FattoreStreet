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
| Controller | `@WebMvcTest(MyController.class)` | `MockMvc` + `@MockBean` |
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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockbean.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EdgarController.class)
class EdgarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
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

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class EdgarRepositoryTest {

    @Autowired
    private EdgarRepository repository;

    @Test
    void findsFilingsByTicker() {
        // Entity is auto-persisted via test data or setUp
        var results = repository.findByTicker("MSFT");
        assertFalse(results.isEmpty());
    }
}
```

## Workflow

1. **Identify the class** -- controller, service, repository, or utility
2. **Pick the test slice** -- see table above; prefer the lightest annotation that works
3. **Mock dependencies** -- `@MockBean` for Spring context tests, `@Mock` + `@InjectMocks` for plain Mockito
4. **Write tests**:
   - Happy path with valid inputs
   - Edge cases (null, empty, invalid)
   - For controllers: assert status code + JSON response structure
   - For services: verify repository/external calls with `verify()`
5. **Mirror package structure** -- test class lives in the same package under `src/test/java/`
6. **Run** -- `cd springboot && mvn test` or `mvn -Dtest=ClassName test`
