# Upgrade the Spring Boot service to Java 25 LTS

## Context

`springboot/Dockerfile` currently builds on `maven:3.9.9-eclipse-temurin-17` and runs on
`eclipse-temurin:19-jre-focal`. Both halves of that runtime line are dead:

- **Java 19** is a non-LTS release that went EOL in March 2023.
- **`focal`** (Ubuntu 20.04) left standard support in April 2025, so the apt layer that
  installs `curl unzip jq ca-certificates` + AWS CLI v2 is pulling from a distro that no
  longer gets routine security updates.

Dependabot has never offered a fix because `eclipse-temurin:19-jre-focal` is a dead tag
*family*, not an outdated tag: there is no newer version within `19`/`focal` to bump to.
The move has to be made by hand.

There is also a second-order problem: the jar is compiled for 17 and run on 19, so the
build and runtime JDKs already disagree. Bumping only the runtime image would fix the EOL
exposure but leave that drift in place.

**Intended outcome:** the whole Spring Boot toolchain (build JDK, compile target, CI, and
container runtime) lands on **Java 25 LTS**, supported into the 2030s, with build and
runtime versions equal for the first time. Spring Boot 4.1 supports Java 17 through 26,
so 25 is comfortably inside the supported range and is the newest LTS available.

Java 26 was considered and rejected: it is the newest stable release, but non-LTS, and
goes EOL around September 2026, which is exactly how the repo ended up on Java 19.

## Target versions

| Thing | Now | Target | Why |
|---|---|---|---|
| Runtime base image | `eclipse-temurin:19-jre-focal` | `eclipse-temurin:25-jre-noble` | LTS JRE on Ubuntu 24.04; arm64 published |
| Build base image | `maven:3.9.9-eclipse-temurin-17` | `maven:3.9.16-eclipse-temurin-25` | newest Maven 3.9.x on JDK 25 |
| `<java.version>` | `17` | `25` | compile target follows the runtime |
| Enforcer `requireJavaVersion` | `[17,)` | `[25,)` | stop a stale local JDK compiling a jar the image can't run |
| Enforcer `requireMavenVersion` | `[3.9.0,)` | `[3.9.0,)` (unchanged) | floor is still correct |
| Maven wrapper | `3.9.9` | `3.9.16` | keeps the "wrapper == builder image" invariant the Dockerfile comment asserts |
| CI `setup-java` | `17` | `25` | must match the enforcer floor |
| Error Prone | `2.36.0` | `2.50.0` | **required**: 2.36.0 predates JDK 25 and cannot run on it |
| maven-pmd-plugin | `3.27.0` | `3.28.0` | **required**: 3.27.0 bundles PMD 7.14.0, which rejects `targetJdk 25`; 3.28.0 bundles PMD 7.17.0 (Java 25 support landed in PMD 7.16.0) |
| spotbugs-annotations | `4.9.6` | `4.10.3` | consistency with `spotbugs-maven-plugin` 4.10.3.0 |

Already Java 25-ready, leave alone: JaCoCo `0.8.15` (officially supports class files
through Java 26), `spotbugs-maven-plugin` `4.10.3.0` (documented on JDK 25), Checkstyle
`10.26.1` + plugin `3.6.0`, Spotless `2.46.1`, FindSecBugs `1.14.0`, enforcer `3.6.2`.

`springboot/.mvn/jvm.config` needs **no change**. Its ten `--add-exports` /`--add-opens`
flags are byte-for-byte the set Error Prone still documents for JDK 16+, and 2.50.0 wants
the same ones.

## Changes

### 1. `springboot/Dockerfile`

Two `FROM` lines plus the comment block above them.

- Line 5: `FROM maven:3.9.16-eclipse-temurin-25 AS build`
- Line 15: `FROM eclipse-temurin:25-jre-noble`
- Rewrite the lines 2-4 comment so it names 3.9.16/25 and explains the LTS choice instead
  of describing the old `maven:3.8-openjdk-18` problem.

`noble` is glibc, so the existing apt + `awscli-exe-linux-$(uname -m).zip` install at
lines 20-25 works unchanged (alpine would break it; AWS CLI v2 ships no musl build). The
`-Dquality.skip=true` on line 12 stays: `.mvn/` and `config/` are still outside the build
context, so the errorprone profile must stay deactivated there.

### 2. `springboot/pom.xml`

- Line 31: `<java.version>25</java.version>`
- Line 56: `<errorprone.version>2.50.0</errorprone.version>`
- Line 55: `<pmd.plugin.version>3.28.0</pmd.plugin.version>`
- Line 54: `<spotbugs.annotations.version>4.10.3</spotbugs.annotations.version>`
- Lines 192-194: `requireJavaVersion` -> `[25,)`
- Lines 187-188: update the comment that names `maven:3.9.9`

Nothing else in the pom is version-coupled. The Boot 4.1 parent derives
`maven.compiler.release` from `<java.version>`, and maven-pmd-plugin derives `targetJdk`
from the same property, which is exactly why the PMD bump is mandatory.

### 3. `springboot/.mvn/wrapper/maven-wrapper.properties`

Point `distributionUrl` at `apache-maven-3.9.16-bin.zip`. Leave `wrapperUrl`
(maven-wrapper 3.3.2) alone.

### 4. `.github/workflows/ci.yml`

In the `springboot` job, `java-version: "17"` -> `"25"` (around line 96). Distribution
stays `temurin`, `cache: maven` stays. No other job touches Java.

`docker-build.yml` needs **no change**: it builds from the Dockerfile on
`ubuntu-24.04-arm`, and both new base images publish linux/arm64.

### 5. Terraform / deploy

**No changes.** `springboot/deploy/terraform/main.tf` pins `runtime_platform` ARM64 and
`image_tag = "latest"` for both task definitions; the new image is still arm64 and still
`:latest`. `deploy/docker-compose.yml`, `deploy/docker-compose.dev.yml` and `deploy/run.sh`
reference the image by name only.

### 6. Docs

Mechanical version-string updates, all currently saying "Java 17":

- `CLAUDE.md:13` (component table row for the SEC microservice)
- `README.md:55`
- `springboot/README.md:28`, `:59`, `:333` (the enforcer row: "Maven 3.9+, Java 25+")
- `.claude/rules/springboot-java.md:30` ("Java 17 features are available...") -> Java 25,
  and refresh the feature examples (records/sealed/pattern matching are now table stakes;
  virtual threads, sequenced collections, pattern matching for switch are the new ones)

While in `springboot/README.md:28`, also fix the stale **"Spring Boot 3.4.2"** on that same
line: the pom parent has been `4.1.0` for a while and the README never caught up.

## Expected fallout, and how to handle it

This is the part that will take the actual time. Two independent sources of new build
failures, both in `mvn verify`, neither visible until the toolchain is switched:

**a) Error Prone 2.36.0 -> 2.50.0 is fourteen releases of new checks**, and
`springboot/pom.xml:428` sets `-Werror` on main sources, so *any* new WARNING-tier finding
fails the build. Triage each one against the convention already documented in the pom at
lines 62-63: fix the code where the check is right, and only add a `-Xep:<Check>:OFF` to
`errorprone.checks.common` with a comment saying why it does not apply. Do not remove
`-Werror` to make the build pass.

Also confirm the nine check names currently referenced (`MissingSummary`, `NonApiType`,
`InlineFormatString`, `MixedMutabilityReturnType`, `ArrayRecordComponent`,
`JavaTimeDefaultTimeZone`, `UnusedMethod`, `UnusedVariable`, `UnusedNestedClass`) all still
exist in 2.50.0. Error Prone hard-fails on an unrecognised `-Xep:` flag, so a rename would
surface immediately as a compiler error rather than a silent no-op.

**b) `-Xlint:deprecation` on JDK 25.** javac will now warn about APIs deprecated between
17 and 25 that were fine before, and `-Werror` turns each into a failure. These are real
findings and should be fixed rather than suppressed.

Test sources deliberately do not get `-Werror` (`errorprone.checks.test`, pom line 90), so
fallout there is advisory only.

Note that (a) cannot be de-risked by landing the Error Prone bump separately first: 2.43.0+
requires JDK 21 or newer to run, so 2.50.0 is unusable until the JDK moves. The two are
one atomic change. Plan on working the findings down on the branch before opening the PR.

## Verification

Local prerequisite: this machine has only Homebrew OpenJDK 17
(`java -version` -> `17.0.18`). Install a JDK 25 first, e.g. `brew install openjdk@25`,
and point `JAVA_HOME` at it for the session. Local Maven is already 3.9.16, matching the
new wrapper and builder image.

1. **Full quality gate, the same sequence CI runs:**
   ```bash
   cd springboot
   ./mvnw -B -ntp spotless:check      # confirms Spotless/GJF removeUnusedImports works on 25
   ./mvnw -B -ntp checkstyle:check    # confirms Checkstyle 10.26.1 runs on 25
   ./mvnw -B -ntp verify              # errorprone + tests + jacoco floor + spotbugs + PMD
   ```
   `verify` is the one that matters: it is the only step that exercises Error Prone, the
   PMD `targetJdk 25` path, JaCoCo's class-file reader, and SpotBugs' BCEL bytecode parse
   against real Java 25 class files (major version 69).

2. **Confirm the compile target actually moved** (guards against the release flag being
   ignored):
   ```bash
   javap -v -cp target/classes com/fattorestreet/sec_api/util/MarketTime.class | grep major
   # expect: major version: 69
   ```

3. **Build the image for the deployment architecture and check both JVMs:**
   ```bash
   docker buildx build --platform linux/arm64 -t springboot:jdk25 springboot/
   docker run --rm --entrypoint java springboot:jdk25 -version   # expect Temurin 25
   ```
   arm64 explicitly, because the EC2 host is Graviton and both Fargate task definitions
   pin `cpu_architecture = "ARM64"`. This also proves the AWS CLI v2 install still
   succeeds on noble, which the entrypoint depends on for `SECRETS_ARN`.

4. **Run the service end to end** via the dev compose stack, which builds from the
   Dockerfile rather than pulling GHCR:
   ```bash
   docker compose -f deploy/docker-compose.infra.yml -f deploy/docker-compose.dev.yml up -d springboot
   docker logs -f springboot   # Boot 4.1 banner, no JVM/module warnings, Flyway migrations apply
   curl -s localhost:8080/actuator/health   # or a known public endpoint such as /fred-data
   ```
   Watch specifically for Hibernate/ByteBuddy complaints about an unsupported class file
   version at proxy-generation time, which is the classic way a JDK bump fails at runtime
   rather than at build time.

5. **After merge**, `docker-build.yml` publishes to GHCR *and* the `fattorestreet-hist-load`
   ECR repo in the same step, and both task definitions pin `:latest`, so the next nightly
   Fargate run picks up Java 25 automatically. Don't wait for the schedule: trigger one
   `hist-load` task manually and confirm it reaches `STOPPED` with exit code 0 rather than
   silently degrading. Use the `aws-inspect` skill to read the CloudWatch logs and to
   confirm the cluster is back to zero running tasks afterwards.

**Rollback:** the change is confined to base image tags and plugin versions, so reverting
the commit and re-running `docker-build.yml` restores the old image. The Fargate task
definitions are untouched, so no `terraform apply` is involved in either direction.

## Out of scope

Called out because they are adjacent and tempting, but are separate decisions:

- **No JVM flags added.** There is currently no `-XX:MaxRAMPercentage` or `-Xmx` anywhere
  in the repo; the JVM runs on container defaults in compose, `run.sh`, and Fargate
  (1024 CPU / 4096 MiB). Worth doing, but it is a memory-tuning change, not a version bump.
- **Container still runs as root.** No `USER` directive is being added here.
- **No move to alpine or distroless.** The AWS CLI v2 + jq entrypoint requires glibc and a
  package manager.
- **Not touching Maven 4.** Only `4.0.0-rc-*` images exist; staying on Maven 3.9.x.
- **Django and nginx images are untouched.**
