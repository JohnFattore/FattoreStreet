# Spring Boot CLAUDE.md

This file provides guidance to Claude Code when working inside `springboot/`.

Conventions are covered by the path-scoped rule `.claude/rules/springboot-java.md`, auto-loaded when working with `springboot/**/*.java`.

## Quality Gates

Run `./mvnw spotless:apply` to fix formatting before committing; CI runs `spotless:check`, `checkstyle:check` and `mvn verify` (JaCoCo coverage floor + SpotBugs/FindSecBugs + PMD + Error Prone). See "Code Quality" in `README.md`. Never pass `-Dquality.skip=true` locally, it exists only for the Docker build stage.

## Writing Tests

@../.claude/skills/springboot-tests/SKILL.md
