<!--
Sync Impact Report
- Version change: (unfilled template) → 1.0.0
- Modified principles: none prior; all five principles newly ratified:
    I. Commercially-Free Data Only
    II. Tests Ride Every Change
    III. Docs Describe What Exists
    IV. No Secrets in the Repo
    V. Merge Is the Deploy; `terraform apply` Is Not
- Added sections: Additional Constraints, Development Workflow & Quality Gates, Governance
- Removed sections: none (template placeholders replaced)
- Deferred TODOs: none
- Source of authority: principles codify the pre-existing enforced rules in `.claude/rules/`
  (data-licensing-commercial-free, auto-update-tests, auto-update-docs, secrets-check,
  infrastructure) so Spec Kit gates and the repo rules cannot drift apart.
-->

# FattoreStreet Constitution

## Core Principles

### I. Commercially-Free Data Only (NON-NEGOTIABLE)

Only data that is commercially free to use may be persisted in any database table or shown
to end users. Preferred sources: SEC EDGAR, FRED, IEX raw price series. If licensing is
unclear, the data is NOT allowed until verified. yfinance is permitted solely for
development-time verification and diagnostics: ephemeral, in-memory, never stored, never
returned from an API, never rendered in the UI. Derived values (ratios, adjustments,
aggregates) are permitted only when every stored input is commercially free.

Rationale: the platform is public; a single non-free datum persisted or displayed creates
licensing exposure that cannot be cleaned up by deleting code later.

### II. Tests Ride Every Change

Every change to application logic lands with its tests in the same PR: Django views get
`BaseAPITestCase` integration tests, Spring Boot services and controllers get JUnit 5 +
Mockito coverage, React components get Vitest + Testing Library + MSW tests. Deleting code
deletes its tests; the JaCoCo bundle line-coverage floor in `springboot/pom.xml` MUST NOT
be lowered to make a build pass. If coverage drops below the floor, add tests.

Rationale: the CI gate is only as honest as the floor; lowering it converts a regression
into a policy change nobody reviewed.

### III. Docs Describe What Exists

No document in the repo may describe a route, command, flag, or behavior that no longer
exists, and user-facing changes update their docs (`docs/`, app READMEs, `CLAUDE.md`,
affected `.claude/rules/` files) in the same PR that changes the behavior. Historical
journal entries under `django/blog/` are records, not documentation, and are exempt.

Rationale: stale operational docs cause real incidents here (a stale runbook invoking a
deleted admin route, a stale tfvars example masking live schedule drift).

### IV. No Secrets in the Repo

Real credentials never land in git, and `.secrets.baseline` stays empty: false positives
are marked at the source with `# pragma: allowlist secret`, never baselined. Runtime
secrets live in the single AWS Secrets Manager blob `fattorestreet/env`; services receive
them via `SECRETS_ARN` (EC2) or task-definition `secrets` blocks (Fargate). A service is
granted only the keys it actually reads. Local AWS work runs under the scoped
`fattorestreet` profile; on `AccessDenied` the fix is widening the versioned policy in
`deploy/iam/`, never reaching for another credential.

Rationale: least privilege only works if every exception is visible in review; an empty
baseline makes any new finding meaningful.

### V. Merge Is the Deploy; `terraform apply` Is Not

CI on merge to `main` is the only build-and-publish path: images go to GHCR (and ECR for
springboot), and the web tier converges via SSM. For scheduled Fargate jobs the order is
fixed: merge the code so the image exists, then `terraform apply`, then run the task once
by hand and verify exit code 0 before trusting its schedule. A task definition referencing
a runner the image lacks silently degrades to `server` mode and bills forever; a healthy
cluster has zero running tasks between scheduled runs. EventBridge schedules MUST be
placed clear of each other's observed runtimes (not nominal crons), because the SEC rate
limiter is per-process and overlapping tasks earn 403s for both.

Rationale: with `:latest` tags, local Terraform state, and a gitignored tfvars, ordering
discipline is the only thing standing between a merge and an invisible half-deploy.

## Additional Constraints

- Stack: React 18 + TypeScript (Vite, RTK Query), Django 5 + DRF, Spring Boot + Java,
  PostgreSQL, Redis, Nginx, Docker Compose on one ARM64 EC2 instance plus ephemeral
  Fargate one-shots. Region is `us-east-1` everywhere.
- Per-language conventions live in `.claude/rules/` (path-scoped) and are binding; this
  constitution defers to them for operational detail rather than duplicating it.
- Configuration comes from environment variables (service URLs, credentials, API keys);
  infrastructure is declarative and version-controlled.
- Naming: camelCase in the frontend, snake_case in Django, with RTK Query
  `transformResponse` converting at the boundary. No Hungarian notation.

## Development Workflow & Quality Gates

- The full CI gate MUST pass before merge to `main`: React ESLint (zero warnings),
  Stylelint, Prettier check, Sass compile, build, Vitest; Django tests; Spring Boot
  Spotless + Checkstyle + `mvn verify` (tests, JaCoCo floor, SpotBugs/FindSecBugs, PMD,
  Error Prone as errors); detect-secrets scan.
- Features of consequence flow through Spec Kit (`/speckit-specify` → `/speckit-plan` →
  `/speckit-tasks` → `/speckit-implement`), with plan artifacts committed in the PR.
- Work that changes deployed behavior states its deploy ordering explicitly when ordering
  matters (Principle V), including rollback affordances such as schedule enable flags.

## Governance

This constitution supersedes ad-hoc practice. The `.claude/rules/` files are its
operational implementations; a change that weakens a rule enforcing a principle above is a
constitutional amendment and MUST be treated as one. Amendments are made by editing this
file via `/speckit-constitution` in a reviewed PR, with the version bumped semantically:
MAJOR for removing or redefining a principle, MINOR for adding a principle or materially
expanding guidance, PATCH for clarifications. Every `/speckit-plan` Constitution Check
gates against this document; violations require changing the spec, plan, or tasks, not
reinterpreting the principle. Complexity that appears to conflict with a principle must be
justified in the plan's Complexity Tracking table or removed.

**Version**: 1.0.0 | **Ratified**: 2026-08-01 | **Last Amended**: 2026-08-01
