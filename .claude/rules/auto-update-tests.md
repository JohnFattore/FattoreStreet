# Auto-Update Tests

After making code changes to application logic, check whether tests need updating. Do this silently as part of your normal workflow -- do NOT ask the user for permission to update tests.

## When to Update

| Change Type | Action |
|---|---|
| New Django view/endpoint | Add integration test in `django/tests/test_<app>.py` using `BaseAPITestCase` |
| Modified Django view logic or serializer | Update existing tests to cover the new behavior |
| New/modified Spring Boot endpoint | Add/update test in `springboot/src/test/java/.../controller/MainControllerTest.java` |
| New/modified Spring Boot service method | Add/update test in the matching `...ServiceTest.java` using Mockito |
| New React component or page | Add tests in `react-app/__tests__/` using `renderWithProviders` and MSW |
| Modified React component behavior | Update existing test assertions to match new behavior |
| New API endpoint consumed by React | Add MSW handler in `react-app/__tests__/mocks/handlers.ts` |

## How to Update

- Follow the patterns in the existing test files -- do not introduce new testing libraries
- For Django: use `@patch()` for external APIs, `BaseAPITestCase` for setup, test both auth and unauth paths
- For Spring Boot: use `@ExtendWith(MockitoExtension.class)` for services, `@WebMvcTest` + `@MockitoBean` for controllers
- For React: use `renderWithProviders` from `testutils.tsx`, `screen.findByText` for async, `userEvent` for interactions (direct API -- no `.setup()`, matching existing tests)
- Only add/modify tests for the changed code -- do not rewrite unrelated tests
- Run the relevant test suite after changes to verify nothing is broken

## Skip When

- The change is test-only (updating existing tests, adding new test files)
- The change is documentation-only or styling-only with no logic impact
- The change is a pure refactor that doesn't alter public behavior (existing tests already cover it)
