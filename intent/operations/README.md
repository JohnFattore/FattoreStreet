# Operations

## Why

One person runs the platform on a single host and a handful of scheduled jobs (see
[vision](../README.md)). When a service throws errors, a scheduled job stops running, or disk and
memory fill up, there is no operations team watching a wall of screens. Without a single place on
the site to inspect logs, job runs and host capacity, diagnosing a problem means opening an cloud
console or a shell on the server, so minor issues go uninvestigated until they break user-facing
pages.

Operations is built into the main API (Django) and the web app rather than run as a separate
monitoring server. Per *Cheap to run* in [principles](../principles.md), an always-on monitoring
stack would bill around the clock and consume memory on the small host; instead, containers and
scheduled jobs ship their logs and host metrics to AWS CloudWatch with a 30-day retention cap on
service logs, and the main API queries CloudWatch on demand for staff users. The market-data
service stays unauthenticated (see *The market-data service never serves user-owned data* in
[design](../design.md)).

This branch has two parts:

- [Monitoring](monitoring.md): logs, error rates, scheduled-job health and host capacity.
- [Diagnostics](diagnostics.md): author-only data-accuracy checks against third-party sources.

## Requirements

### Operational views are accessible only to staff verified on the server
Logs, error summaries and diagnostic pages can expose internal paths, stack traces and third-party
diagnostic comparisons. Every operational request is checked against the signed-in user's staff
status on the server, and non-staff visitors see the same not-found or error screen as for any
missing page.

### Operational tools are strictly read-only
Applies *Least privilege, no secrets in the repo* from [principles](../principles.md). A web page
that can trigger jobs, restart services or alter data turns a stolen staff session into a way to
corrupt the platform or breach external rate limits. Operational views inspect state and never
change it.

## Non-goals

- **A separate always-on monitoring server.** Running an extra monitoring container or instance all
  month for occasional checks violates *Cheap to run* in [principles](../principles.md).
- **Triggering jobs or restarting services from the browser.** Recurring work starts on its
  schedule, and deployments happen by merging.
