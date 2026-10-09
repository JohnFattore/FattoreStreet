# Principles

Constraints every branch inherits, each with its reason. A node that needs an exception says so
explicitly and names the principle it's excepting.

### Only commercially free data is stored or shown
The platform is public, and one non-free value that is stored or displayed creates licensing
exposure that deleting code later can't undo. Every value that is persisted, returned by an API or
rendered to a user comes from a source licensed for free commercial use, or is derived only from
such values. A source whose license is unclear is forbidden until it's verified. During development,
non-free sources may be used for verification and diagnostics, as long as the result is temporary,
never stored, never returned to users and never displayed.

### Primary sources are the truth
The value of the platform is that its numbers can be audited, and a derived number is only as
trustworthy as the source it traces back to. When sources disagree, the primary source wins: a
company's own filings over third-party data, and the exchange's own records over aggregators.
Third-party data may flag a suspected error but never overwrites the primary-source value.

### Missing evidence is not evidence of absence
External sources fail intermittently, and a run that treats "I couldn't fetch it" as "it doesn't
exist" deletes good data. An automated process that can't reach its source leaves existing data
untouched and retries later.

### The data stays current without a human
One person runs this, and anything that depends on someone remembering to start it will quietly go
stale. Every recurring data refresh runs on a schedule, and every scheduled run either succeeds or
raises an alert. A run that did no useful work counts as a failure, not a success.

### Be a good citizen of free sources
Free public sources, the SEC above all, throttle or block clients that ask too much, and losing
access would stop the platform. Requests stay within each source's published limits across all of
the platform's processes combined, not just within one. Work is scoped to fit within those limits,
rather than the limits being stretched to fit the work.

### Cheap to run
This is a personally funded project. (inferred) Nothing runs or bills between jobs that doesn't need
to: scheduled work runs on compute that exists only while the job is running. Choosing the more
expensive option needs a reason written down.

### Least privilege, no secrets in the repo
A public repo and a single maintainer leave no room for a leaked credential or an over-broad
permission to go unnoticed. No credential is ever committed, and each service and person gets only
the access it actually uses. A public endpoint exposes only read access to public data. Anything that
changes shared data runs as a scheduled job, not as an endpoint.

### Every change is tested and reviewed
One maintainer has no second pair of eyes except the automated gate, and a gate that can be loosened
quietly guards nothing. Behavior changes ship with tests. Every check must pass before a change
merges, and the bar never gets lowered to make a change pass.

### Merging is deploying
The live site should always match the main branch, and manual deploys drift. Merging is the only way
a change reaches production. When a change has to roll out in a particular order, the change itself
states that order.
