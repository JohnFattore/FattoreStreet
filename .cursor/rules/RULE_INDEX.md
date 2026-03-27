# Cursor Rules Index

Quick reference for project rules in `/.cursor/rules`.

## Always-Apply Rules

- `project-overview.mdc`
  - High-level architecture and conventions for the monorepo.
  - Applied in every session.

- `auto-update-tests.mdc`
  - Guidance for when to add/update tests after logic changes.
  - Applied in every session.

- `auto-update-docs.mdc`
  - Guidance for when docs should be updated for user-facing changes.
  - Applied in every session.

## Scoped Rules

- `react-typescript.mdc` (`react-app/**/*.{ts,tsx}`)
  - React + TypeScript conventions, RTK Query patterns, styling expectations, and keeping [`Admin.tsx`](react-app/src/pages/Admin.tsx) in sync with new Spring `/admin/*` endpoints (plus model “Affects” lines and tests).

- `django-drf.mdc` (`django/**/*.py`)
  - Django/DRF conventions, API patterns, caching, and auth style.

- `springboot-java.mdc` (`springboot/**/*.java`)
  - Spring Boot architecture conventions and SEC/EDGAR service patterns.

- `infrastructure.mdc` (`kubernetes/**`, `nginx/**`, `aws/**`, Docker/compose files)
  - Infrastructure standards across Docker, Kubernetes, Nginx, and AWS config.

- `llm-local-ai.mdc` (`llm/**`)
  - Local AI safeguards: VRAM-aware defaults, GPU workload coordination, and generated artifact handling.

## Maintenance Notes

- Keep one concern per rule whenever possible.
- Prefer scoped rules for stack-specific guidance.
- Update this index when adding, removing, or renaming rule files.
