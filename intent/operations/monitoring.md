# Monitoring

## Why

A single maintainer needs to tell at a glance whether the platform is healthy and drill into logs
when something goes wrong. If logs live only inside ephemeral containers or scattered across cloud
consoles, a failed job or a spike in server errors takes too long to trace back to its cause.

## Requirements

### Every service and scheduled job ships its logs to a central store with bounded retention
When a container is replaced on deploy or a one-shot job exits, any logs kept only inside that
container disappear with it, while unbounded log storage quietly grows the monthly bill. All
web-facing services and scheduled jobs send their logs to the central log store, and service logs
expire after 30 days.

### Log shipping never stalls a running service
If the log store slows down or is unreachable, a service that waits on log delivery stops answering
users. Log delivery buffers and drops rather than blocking request handling.

### The author can search and filter logs by service, time window, severity and text
Reading raw log streams end to end doesn't scale once traffic and background jobs mix together. The
monitoring view lets a staff user pick an allowed log source, narrow the time window, filter by
severity level or search text, and page through results.

### Log queries are restricted to an explicit allowlist of platform sources
An endpoint that accepts arbitrary log group names would let a compromised token probe unrelated
logs in the cloud account. The server accepts only known platform source keys and rejects everything
else.

### Error and warning counts are summarized per service over recent hours
A quiet stream of warnings or errors is easy to miss in a raw log tail until users complain. The
dashboard summarizes recent error and warning counts across each service so anomalies stand out immediately.

### Every scheduled job's latest run, exit status and staleness are visible
Applies *The data stays current without a human* from [principles](../principles.md). Alerting on a
non-zero exit catches a crash, but a schedule that silently stops firing raises no failure event.
The dashboard records every completed run—successes as well as failures—and flags any job whose last
successful run is older than its scheduled cadence.

### Host capacity and web traffic errors are visible alongside application logs
Application logs don't show a disk that is nearly full, memory pressure from a container, or gateway
errors that never reached a backend. The dashboard shows recent host CPU, memory and disk usage
together with request rates and server-error counts from the reverse proxy.

## Non-goals

- **Real-time log streaming.** Periodic refresh and on-demand queries are enough for a single
  maintainer without holding open long-lived connections.
- **Automated pager escalation from the dashboard.** Scheduled-job failures already send email
  alerts; dashboard views are for inspection.
