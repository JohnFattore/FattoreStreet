# Java Quality Gates for `springboot/`

## Context

`react-app/` gates three linters in CI (ESLint, Stylelint, Prettier) as of commits `c5ce6c46` / `f50f22e5`. The Java module has almost nothing equivalent.

Today `springboot/pom.xml` already configures `spotbugs-maven-plugin` 4.10.3.0 with `findsecbugs-plugin` 1.14.0 and a `jacoco:check` line-coverage floor of 0.76, but **neither runs**. SpotBugs has no `<executions>` block so it is bound to no phase at all, and `jacoco:check` binds to `verify` while `.github/workflows/ci.yml:100` runs only `mvn -B test`. There is no formatter, no import-order rule, no `.editorconfig`, and no static-analysis config file anywhere in the repo. `springboot/README.md:320` already claims `mvn verify` enforces the coverage floor, so the docs are ahead of CI.

Goal: switch CI to `mvn verify` and add mainstream Java checks, staged so CI and the production Docker build stay green at every step.

**Baseline to preserve:** 548 tests passing, 80.55% line / 60% branch coverage, 73 main files (14,070 lines) and 58 test files (11,547 lines).

## Decisions made

| Area | Decision |
|---|---|
| Formatter | Spotless with the **no-reflow step set** only. No google-java-format, no Palantir. Diff is import re-sorting plus the 2 tab-indented files; line wrapping is untouched. |
| Import order | `java, javax, jakarta, org, com, <other>, static` |
| Checkstyle | In. Curated ~30-rule custom ruleset. Not `sun_checks.xml` (Javadoc everywhere) or `google_checks.xml` (hardcodes 2-space). |
| SpotBugs | Bind to `verify`. Keep `effort=Max` / `threshold=Low`; control noise with an exclude filter, not the threshold. |
| PMD | In, curated ruleset, `verify` phase. Overlaps SpotBugs, so the ruleset is trimmed to non-overlapping categories. |
| Error Prone | In, last stage, gated behind a Maven profile so it never runs in the Docker build. |
| Enforcer | In. `requireMavenVersion`, `requireJavaVersion`, `banDuplicatePomDependencyVersions` only. Skip `dependencyConvergence` (noisy under the Boot BOM, catches nothing). |
| Supply chain | Dependabot is **already configured** and needs no work. Optionally add `dependency-review-action`. **Not** the OWASP plugin (NVD API key, ~10min cold run, unactionable transitive failures). |
| Coverage gate | Keep 0.76 for the `verify` switch. Ratchet to 0.78 line + 0.55 branch in a separate follow-up. |
| Docker | `quality.skip` master switch + bump builder image + add `.dockerignore`. Ships first and alone. |
| `ratchetFrom` | Rejected. Needs `.git` (absent in the Docker build stage) and `fetch-depth: 0`, and permanently bifurcates the tree. Unnecessary once the formatter does no reflow. |

## Cross-cutting mechanics

**The `quality.skip` switch.** Every new gate gets `<skip>${quality.skip}</skip>`. `springboot/Dockerfile` copies only `pom.xml` and `src`, so `config/`, `.git` and `.mvn` are absent there; without the switch a `validate`-phase gate breaks the image build. Add to `<properties>` (the pom is **tab-indented**, match it):

```xml
	<properties>
		<java.version>17</java.version>

		<!-- One switch that disables every format/static-analysis gate.
		     The Docker build stage passes -Dquality.skip=true because it copies
		     only pom.xml + src/ (no config/, no .git, no .mvn). Do not remove
		     without also fixing springboot/Dockerfile. -->
		<quality.skip>false</quality.skip>

		<spotless.version>2.46.1</spotless.version>
		<checkstyle.plugin.version>3.6.0</checkstyle.plugin.version>
		<checkstyle.version>10.26.1</checkstyle.version>
		<pmd.plugin.version>3.27.0</pmd.plugin.version>
		<enforcer.version>3.6.2</enforcer.version>
		<spotbugs.annotations.version>4.9.6</spotbugs.annotations.version>
	</properties>
```

Boot 4.1.0's parent `pluginManagement` covers only compiler/failsafe/jar/war/resources/shade, so every new plugin needs an explicit `<version>`. Run `./mvnw versions:display-plugin-updates` once and pin what is current rather than trusting the numbers above.

**Put config at plugin level, not inside `<execution>`.** CI invokes `spotless:check` and `checkstyle:check` as standalone goals, which do **not** inherit `<configuration>` nested inside an `<execution>`. Everything the goal needs (`configLocation`, `<java>` steps, `excludeFilterFile`) goes in the plugin's top-level `<configuration>`; `<executions>` only bind goals to phases. Getting this backwards is the most likely reason a first attempt works under `mvn verify` but not under `mvn spotless:check`.

**Config file layout** (deliberately outside `src/`, and deliberately not in the Docker context):

```
springboot/config/checkstyle/checkstyle.xml
springboot/config/checkstyle/suppressions.xml
springboot/config/spotbugs/exclude.xml
springboot/config/pmd/ruleset.xml
```

**Final phase bindings:**

| Goal | Phase | In `mvn test`? | In `mvn package` (Docker)? |
|---|---|---|---|
| `enforcer:enforce` | `validate` | yes | skipped via `quality.skip` |
| `spotless:check` | `validate` | yes | skipped via `quality.skip` |
| `checkstyle:check` | `validate` | yes | skipped via `quality.skip` |
| `jacoco:prepare-agent` | `initialize` | yes | harmless with `-DskipTests` |
| `jacoco:report` | `test` | yes | no |
| `spotbugs:check` | `verify` | no | no |
| `pmd:check` | `verify` | no | no |
| `jacoco:check` | `verify` | no | no |
| Error Prone (compiler arg) | `compile` | yes | disabled by profile |

Lint at `validate` fails in seconds, before the 548-test suite. Heavy analysis at `verify` keeps `mvn test` fast and keeps `package` clean by construction.

---

## Stage 0: `.editorconfig` + blame scaffolding

No CI change, cannot break anything.

New `/.editorconfig` (repo root, covers all four sub-projects):

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 2

[*.java]
indent_size = 4
max_line_length = 120

[*.{py,cfg}]
indent_size = 4

[*.{xml,pom}]
indent_style = tab

[*.md]
trim_trailing_whitespace = false
```

The `[*.xml] indent_style = tab` entry exists so editors stop fighting `springboot/pom.xml`.

New `/.git-blame-ignore-revs` with a header comment; append the Stage 2 reformat SHA to it. GitHub honors this file automatically; locally it needs `git config blame.ignoreRevsFile .git-blame-ignore-revs`.

---

## Stage 1: Docker hardening + `quality.skip` + enforcer

**Merge this first and alone.** It installs the safety net before anything exists that could break the image build. `docker-build.yml` deploys `main` to EC2 via SSM, so confirm that workflow is green on this PR before anything else lands.

`springboot/Dockerfile`, stage 1 only (leave the `eclipse-temurin:19-jre-focal` runtime alone; changing it is a deployment concern, not a linting one):

```dockerfile
# Stage 1: Build
# maven:3.9.9 matches .mvn/wrapper/maven-wrapper.properties; temurin-17 matches
# <java.version>. The old maven:3.8-openjdk-18 is EOL and predates the
# enforcer requireMavenVersion floor.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# -Dquality.skip=true: this stage copies only pom.xml + src, so config/,
# .git and .mvn are absent. Spotless/Checkstyle/enforcer gates run in CI
# (.github/workflows/ci.yml), not here.
RUN mvn -B clean package -DskipTests -Dquality.skip=true
```

Verify the tag is multi-arch: `docker-build.yml` runs on `ubuntu-24.04-arm` because the production EC2 host is a t4g.

New `springboot/.dockerignore`. The `springboot` image builds with `context: springboot` (`docker-build.yml:44-46`), so the repo-root `.dockerignore` does not apply to it; its own header comment says as much and notes that django has its own. Springboot is the only one of the three images without one, so its context currently ships `target/`, hundreds of MB, to the daemon on every build. Match the flat style of `django/.dockerignore`:

```
target/
.git/
.gitignore
.mvn/wrapper/maven-wrapper.jar
config/
*.md
.env
deploy/
```

Enforcer plugin in `springboot/pom.xml`:

```xml
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-enforcer-plugin</artifactId>
				<version>${enforcer.version}</version>
				<configuration>
					<skip>${quality.skip}</skip>
				</configuration>
				<executions>
					<execution>
						<id>enforce-build-environment</id>
						<phase>validate</phase>
						<goals><goal>enforce</goal></goals>
						<configuration>
							<rules>
								<requireMavenVersion><version>[3.9.0,)</version></requireMavenVersion>
								<requireJavaVersion><version>[17,)</version></requireJavaVersion>
								<banDuplicatePomDependencyVersions />
							</rules>
							<fail>true</fail>
						</configuration>
					</execution>
				</executions>
			</plugin>
```

If the base-image bump is dropped for any reason, the Maven floor must drop to `[3.8.1,)` or the image build fails at `validate` before `quality.skip` matters for the other plugins.

**CI green story:** CI still runs `mvn -B test`, which now runs enforcer at `validate`. Both requirements pass on `ubuntu-latest` + temurin 17.

---

## Stage 2: Spotless

Plugin block:

```xml
			<plugin>
				<groupId>com.diffplug.spotless</groupId>
				<artifactId>spotless-maven-plugin</artifactId>
				<version>${spotless.version}</version>
				<configuration>
					<skip>${quality.skip}</skip>
					<java>
						<includes>
							<include>src/main/java/**/*.java</include>
							<include>src/test/java/**/*.java</include>
						</includes>
						<toggleOffOn />
						<removeUnusedImports />
						<importOrder>
							<!-- trailing empty group = everything else; \# = statics last -->
							<order>java,javax,jakarta,org,com,,\#</order>
						</importOrder>
						<trimTrailingWhitespace />
						<endWithNewline />
						<indent>
							<spaces>true</spaces>
							<spacesPerTab>4</spacesPerTab>
						</indent>
					</java>
				</configuration>
				<executions>
					<execution>
						<id>spotless-check</id>
						<phase>validate</phase>
						<goals><goal>check</goal></goals>
					</execution>
				</executions>
			</plugin>
```

Notes:
- No `<googleJavaFormat/>` and no `<palantirJavaFormat/>`. That is the point: nothing reflows, so brace placement, wrapping and the 205 lines over 120 chars are untouched.
- If the pinned Spotless version rejects `<indent>` inside `<java>`, drop it and convert `SecApiApplication.java` and `SecApiApplicationTests.java` to spaces by hand. `.editorconfig` plus Checkstyle's `FileTabCharacter` (Stage 3) covers regressions.
- No `<pom>`/`sortPom` block; it would reformat the tab-indented pom for no benefit.
- `removeUnusedImports` does **not** expand `java.util.*` into explicit imports. Wildcard expansion is Stage 3's manual job.

Execution order for the PR: run `./mvnw spotless:apply`, run `./mvnw -B test` and confirm 548/0, then commit the reformat **separately** from the pom change and append that SHA to `/.git-blame-ignore-revs`.

CI step, inserted before the test step in the `springboot` job:

```yaml
      - name: Format check (Spotless)
        run: ./mvnw -B -ntp spotless:check
```

**CI green story:** the plugin and the `spotless:apply` output ship in the same PR. Docker is safe because `spotless:check` binds to `validate` (which *is* part of `package`) but `quality.skip=true` short-circuits it. This is exactly the hazard Stage 1 exists to fix.

---

## Stage 3: Checkstyle

Not redundant with Spotless. Spotless owns whitespace, import order and unused imports; Checkstyle owns semantics Spotless has no concept of. Zero overlap once the whitespace modules are left out, which the ruleset below does deliberately.

New `springboot/config/checkstyle/checkstyle.xml`, a `Checker` with `severity=error`, `charset=UTF-8`, a `SuppressionFilter` pointing at `${config_loc}/suppressions.xml`, `FileTabCharacter`, and a `TreeWalker` containing `SuppressionCommentFilter` plus:

- **Imports:** `AvoidStarImport` with **`allowStaticMemberImports=true`** (load-bearing: it legalizes the 67 `import static org.junit.jupiter.api.Assertions.*` / `Mockito.*` wildcards in tests while still banning the 25 type wildcards in main), `RedundantImport`, `UnusedImports`, `IllegalImport` (`sun, com.sun, jdk.internal, junit.framework`; illegal classes `org.junit.Assert`, `org.junit.Test`).
- **Correctness:** `EqualsHashCode`, `CovariantEquals`, `EqualsAvoidNull`, `StringLiteralEquality`, `FallThrough`, `MissingSwitchDefault`, `DefaultComesLast`, `SimplifyBooleanExpression`, `SimplifyBooleanReturn`, `EmptyStatement`, `NoFinalizer`, `NoClone`, `UnusedLocalVariable`, `MissingOverride`, `EmptyCatchBlock` (`exceptionVariableName=expected|ignored`).
- **Structure:** `NeedBraces`, `OneStatementPerLine`, `MultipleVariableDeclarations`, `ArrayTypeStyle`, `UpperEll`, `ModifierOrder`, `RedundantModifier`, `FinalClass`, `HideUtilityClassConstructor`, `InterfaceIsType`, `OuterTypeFilename`, `PackageDeclaration`.
- **Naming:** `PackageName` with `format="^[a-z]+(\.[a-z][a-z0-9_]*)*$"` (the default pattern rejects the underscore in `sec_api`), `TypeName`, `MethodName`, `MemberName`, `ParameterName`, `LocalVariableName`, `LocalFinalVariableName`, `ConstantName`.

Add a header comment stating the ownership split: **Checkstyle owns semantics, Spotless owns whitespace and line length. Never add whitespace, wrapping or `LineLength` modules here**, they will fight `spotless:apply` and produce an unfixable build.

Deliberately excluded, with reasons worth recording in that comment: `MagicNumber` (a finance codebase is full of `252` trading days and tolerance constants; hoisting each makes the code worse), `LineLength` (two owners equals an unfixable build, and the current max is 236 chars), `IllegalCatch` (fires on the 51 intentional `catch (Exception)` sites already excluded in SpotBugs), the Javadoc modules (no Javadoc culture, thousands of violations), `FinalParameters` / `DesignForExtension` / `VisibilityModifier` (the last fights JPA entities), `CyclomaticComplexity` / `NPathComplexity` (would fail immediately on the XBRL mapping and corporate-action detectors; useful as a report, terrible as a gate).

New `springboot/config/checkstyle/suppressions.xml`: suppress everything under `target/`, and suppress `HideUtilityClassConstructor` under `src/test/java/`.

Plugin block:

```xml
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-checkstyle-plugin</artifactId>
				<version>${checkstyle.plugin.version}</version>
				<dependencies>
					<dependency>
						<groupId>com.puppycrawl.tools</groupId>
						<artifactId>checkstyle</artifactId>
						<version>${checkstyle.version}</version>
					</dependency>
				</dependencies>
				<configuration>
					<skip>${quality.skip}</skip>
					<configLocation>config/checkstyle/checkstyle.xml</configLocation>
					<includeTestSourceDirectory>true</includeTestSourceDirectory>
					<consoleOutput>true</consoleOutput>
					<failsOnError>true</failsOnError>
					<violationSeverity>error</violationSeverity>
					<linkXRef>false</linkXRef>
				</configuration>
				<executions>
					<execution>
						<id>checkstyle-check</id>
						<phase>validate</phase>
						<goals><goal>check</goal></goals>
					</execution>
				</executions>
			</plugin>
```

`${config_loc}` resolves to the directory of `configLocation`, so `suppressions.xml` is found automatically.

**Wildcard expansion must ship in this same PR.** `AvoidStarImport` fails on 25 main-source files today: `java.util.*` (12), `jakarta.persistence.*` (10, all entities), `java.io.*` (2), `org.springframework.http.*` (1). Expand them via IDE organize-imports with the star threshold set to 999, then run `./mvnw spotless:apply` so the new imports land in the Stage 2 order. The 10 entity files gain roughly 8 explicit imports each and are the bulk of the diff.

Sanity check before pushing: `grep -rn "^import .*\*;" src/main/java` returns nothing, and the only remaining wildcards under `src/test/java` are `import static` lines.

CI step:

```yaml
      - name: Lint (Checkstyle)
        run: ./mvnw -B -ntp checkstyle:check
```

**Risk note:** this is the highest-risk stage, and the risk is the import expansion, not the tooling. IDE optimize-imports can silently reorder or drop things. Run the full suite locally and review the diff for anything that is not an import line.

---

## Stage 4: Switch CI to `mvn verify`

Do this alone so a failure is unambiguous. Replace `.github/workflows/ci.yml:99-100`:

```yaml
      - name: Build, test & verify
        run: ./mvnw -B -ntp verify
```

Switching to `./mvnw` pins Maven 3.9.9 and matches the enforcer floor. `verify` now runs enforcer, `spotless:check`, `checkstyle:check`, tests, `jacoco:report`, `spring-boot:repackage`, then `jacoco:check`. Expect +30 to +60s for the repackage, which is a bonus: CI now catches packaging failures that only `docker-build.yml` would have found.

**Keep the coverage floor at 0.76 in this PR.** Actual is 80.55% line, 4.5 points of headroom.

Add report upload with `if: always()`, which matters because the job stops at the first failed step and a coverage failure would otherwise give the threshold message without the HTML report showing which package dropped:

```yaml
      - name: Upload JaCoCo report
        if: always()
        uses: actions/upload-artifact@v5
        with:
          name: springboot-jacoco
          path: springboot/target/site/jacoco/
          retention-days: 7
```

Confirm the current `upload-artifact` major; this repo pins `checkout@v7` and `setup-java@v5`.

### Stage 4b (follow-up PR): ratchet the gate

Replace the `jacoco` `check` execution's rules with a `LINE` minimum of `0.78` plus a new `BRANCH` minimum of `0.55`. Rationale: 0.78 keeps ~2.5 points of headroom, enough that a normal PR touching `client/` (42.5%) or `filing/` (42.6%) will not trip it. The branch rule matters more than the line bump, since 60% branch coverage is the real weak spot and is currently ungated entirely. **Do not** add a per-`PACKAGE` rule: with two packages at ~42%, any package minimum either fails today or is theatre. Reserve 0.80+ for a PR that actually adds `client/` and `filing/` tests.

---

## Stage 5: SpotBugs, actually bound

Replace the existing plugin block:

```xml
			<plugin>
				<groupId>com.github.spotbugs</groupId>
				<artifactId>spotbugs-maven-plugin</artifactId>
				<version>4.10.3.0</version>
				<configuration>
					<skip>${quality.skip}</skip>
					<effort>Max</effort>
					<threshold>Low</threshold>
					<xmlOutput>true</xmlOutput>
					<htmlOutput>true</htmlOutput>
					<includeTests>false</includeTests>
					<failOnError>true</failOnError>
					<excludeFilterFile>config/spotbugs/exclude.xml</excludeFilterFile>
					<plugins>
						<plugin>
							<groupId>com.h3xstream.findsecbugs</groupId>
							<artifactId>findsecbugs-plugin</artifactId>
							<version>1.14.0</version>
						</plugin>
					</plugins>
				</configuration>
				<executions>
					<execution>
						<id>spotbugs-check</id>
						<phase>verify</phase>
						<goals><goal>check</goal></goals>
					</execution>
				</executions>
			</plugin>
```

**Keep `threshold=Low`.** Raising to `Medium` would silence FindSecBugs patterns that report at Low while *not* silencing `EI_EXPOSE_REP`, which is already Medium. The threshold is the wrong instrument; a targeted exclude filter is the right one.

New `springboot/config/spotbugs/exclude.xml`, a `FindBugsFilter` where every block carries a written rationale (this file is a policy document, and reviewers should push back on additions):

1. **`model/` package:** `EI_EXPOSE_REP`, `EI_EXPOSE_REP2`, `EI_EXPOSE_STATIC_REP2`, `MS_EXPOSE_REP`. Hibernate requires live references to relationship collections; defensive copying breaks dirty checking and lazy loading.
2. **Records with collection components:** `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` scoped to `corporateaction`, `index`, `controller`, `economic`, `util`. 17 of 63 records carry `List`/`Map`/`Set`/array components; the canonical constructor cannot defensively copy without a hand-written compact constructor, and every one is built from `List.of()` / `Map.ofEntries()` / `stream().toList()` and never mutated.
3. **`DM_CONVERT_CASE` / `DM_DEFAULT_LOCALE`:** the ~41 sites format SEC tickers, CIKs and XBRL tags, which are ASCII machine identifiers, not user-facing text. Mark REVISIT if this output ever becomes user-facing.
4. **`REC_CATCH_EXCEPTION`:** 51 sites, all in per-ticker/per-filing loops where "log and continue" is the deliberate policy rather than aborting a multi-hour ingest.
5. **`CRLF_INJECTION_LOGS`:** ~49 log statements concatenate SEC-sourced identifiers into CloudWatch. Mark REVISIT if user-supplied strings ever reach the logger.
6. **`SecurityConfig` only** (scoped to the single class, do not widen): `SPRING_CSRF_PROTECTION_DISABLED` and `PERMISSIVE_CORS`. CSRF is disabled at `config/SecurityConfig.java:72` because this is a token-authenticated JSON API with no cookie-backed session and `SessionCreationPolicy.STATELESS`; the permissive matchers at `:74-88` are the documented public read-only endpoints.

Add the annotations dependency for one-off suppressions (compile-time only, does not ship):

```xml
		<dependency>
			<groupId>com.github.spotbugs</groupId>
			<artifactId>spotbugs-annotations</artifactId>
			<version>${spotbugs.annotations.version}</version>
			<scope>provided</scope>
			<optional>true</optional>
		</dependency>
```

Policy: a whole **category** goes in `exclude.xml` with a rationale; a single intentional **site** gets `@SuppressFBWarnings(value = "...", justification = "...")` at the narrowest scope, with the justification mandatory in review.

**Bring-up procedure.** `spotbugs:check` fails on the first finding, so do not iterate with it:

1. `./mvnw -B verify -DskipTests spotbugs:spotbugs` (the `spotbugs` goal reports without failing)
2. Read `target/spotbugs.html`, tally patterns
3. Adjust `exclude.xml` until `./mvnw -B spotbugs:check` is clean
4. Only then add the `<execution>` binding

Expect 100 to 200 raw findings, dominated by the categories above; the filter should take that to single digits. FindSecBugs should be near-silent: zero SQL concatenation, zero `printStackTrace`, zero `Random`, zero redirects, zero `exec`, zero disabled SSL, and all 3 stream sites already use try-with-resources. **Fix any genuine leftovers in code** (`NP_NULL_ON_SOME_PATH`, `RV_RETURN_VALUE_IGNORED`, `SIC_INNER_SHOULD_BE_STATIC`) rather than extending the filter. That is the entire value of the stage. Budget roughly half a day of triage.

Add a SpotBugs report upload step alongside the JaCoCo one.

---

## Stage 6: Supply chain (mostly already done)

**Dependabot is already configured** at `.github/dependabot.yml` and covers all five ecosystems this plan would have proposed: `uv` (`/django`), `npm` (`/react-app`), `maven` (`/springboot`), `docker` (`/django`, `/springboot`, `/nginx`) and `github-actions` (`/`), all weekly, with minor/patch grouping and considered `ignore` rules for the ESLint 9 and React 19 migrations. **No changes needed.**

Two consequences for the rest of this plan:

- The Stage 1 base-image bump is something Dependabot is already watching (`docker` ecosystem, `/springboot`) and has not produced. `maven:3.8-openjdk-18` and `eclipse-temurin:19-jre-focal` are dead tag families rather than outdated tags, so Dependabot has no newer version within the same family to offer. The bump has to be done by hand, which is what Stage 1 does.
- Once Stage 2 lands, consider adding the new build plugins to a `build-plugins` group in the existing `maven` block (`com.diffplug.spotless:*`, `com.github.spotbugs:*`, `com.puppycrawl.tools:*`, `org.apache.maven.plugins:*`, `org.jacoco:*`) so plugin bumps arrive as one PR instead of five. This is a small edit to the existing `groups:` map, not a new file.

Separately: `gh api repos/.../dependabot/alerts` currently returns **30 open alerts**. Triaging that backlog is out of scope here but worth its own pass.

The one genuinely new item is `dependency-review-action`, which is a different mechanism from Dependabot: it blocks a PR that *introduces* a high-severity CVE, rather than filing an update PR after the fact. Optional, roughly 5s, no config:

```yaml
  dependency-review:
    name: Dependency review
    runs-on: ubuntu-latest
    if: github.event_name == 'pull_request'
    steps:
      - uses: actions/checkout@v7
      - uses: actions/dependency-review-action@v4
        with:
          fail-on-severity: high
```

This adds a job to `ci.yml` and does not affect existing jobs. Skip the stage entirely if you don't want the extra required check.

---

## Stage 7: PMD

PMD overlaps SpotBugs and Checkstyle heavily, so the ruleset is trimmed to the categories the other two do not cover: `bestpractices` and `design` rules about unused private members and needless control flow, plus `errorprone` rules about assignment and comparison mistakes. Skip `codestyle` entirely (Spotless and Checkstyle own it) and skip the `documentation` and `performance` categories (noisy, low signal).

New `springboot/config/pmd/ruleset.xml` referencing individual rules rather than whole category files. Start from this set and trim during bring-up: `UnusedPrivateField`, `UnusedPrivateMethod`, `UnusedFormalParameter`, `UnusedAssignment`, `UselessParentheses`, `UselessReturn`, `CollapsibleIfStatements`, `AvoidBranchingStatementAsLastInLoop`, `CompareObjectsWithEquals`, `BrokenNullCheck`, `MisplacedNullCheck`, `OverrideBothEqualsAndHashcode`, `ReturnEmptyCollectionRatherThanNull`, `SimplifiedTernary`.

Plugin bound to `verify` with `<skip>${quality.skip}</skip>`, `<rulesets><ruleset>config/pmd/ruleset.xml</ruleset></rulesets>`, `<includeTests>false</includeTests>`, `<printFailingErrors>true</printFailingErrors>`, and `<failOnViolation>true</failOnViolation>`. Also bind `cpd-check` only if the duplication report comes back clean; otherwise leave CPD as a manual `./mvnw pmd:cpd` report and do not gate on it.

Bring-up is the same shape as SpotBugs: run `./mvnw pmd:pmd` (report-only), read `target/site/pmd.html`, trim the ruleset or fix the code, and bind the execution last.

**Honest caveat, recorded because it was raised and overridden:** most of what this ruleset catches, SpotBugs at `threshold=Low` also catches. Its incremental value here is `UnusedPrivateMethod` and `UnusedFormalParameter`, which SpotBugs does not report. If bring-up produces a ruleset with fewer than about five surviving rules, drop the stage rather than carrying a third tool.

---

## Stage 8: Error Prone

Highest-risk stage, last for a reason: Error Prone runs as a compiler plugin during `compile`, which means it would otherwise execute inside the Docker build stage. Gate it behind a profile that is inactive whenever `-Dquality.skip` is passed on the command line:

```xml
	<profiles>
		<profile>
			<id>errorprone</id>
			<activation>
				<!-- Active unless -Dquality.skip is passed. Maven property
				     activation inspects command-line -D and settings.xml, so
				     the Dockerfile's -Dquality.skip=true deactivates this. -->
				<property><name>!quality.skip</name></property>
			</activation>
			<build>
				<plugins>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-compiler-plugin</artifactId>
						<configuration>
							<compilerArgs>
								<arg>-XDcompilePolicy=simple</arg>
								<arg>--should-stop=ifError=FLOW</arg>
								<arg>-Xplugin:ErrorProne</arg>
							</compilerArgs>
							<annotationProcessorPaths>
								<path>
									<groupId>com.google.errorprone</groupId>
									<artifactId>error_prone_core</artifactId>
									<version>2.36.0</version>
								</path>
							</annotationProcessorPaths>
						</configuration>
					</plugin>
				</plugins>
			</build>
		</profile>
	</profiles>
```

Verify on a clean JDK 17 before committing. Error Prone forks javac internals and may need `--add-exports jdk.compiler/com.sun.tools.javac.{api,file,parser,tree,util}=ALL-UNNAMED` plus `--add-opens` for `code` and `comp`. If so, add `springboot/.mvn/jvm.config` with those flags; it is discovered from the project base directory so it works with both `./mvnw` and CI's Maven, and it is not copied into the Docker build stage, which is irrelevant because the profile is off there.

Bring-up: run `./mvnw -B compile` and triage. Error Prone's default `ERROR`-severity checks are few and high-signal, but any that fire must be fixed or downgraded per-check via `-Xep:CheckName:WARN`. Do not enable the `WARNING`-tier checks initially. **NullAway is explicitly out of scope:** it requires Error Prone first plus annotating 14k lines with `@Nullable`, a multi-week project against SEC data that is nullable nearly everywhere.

If this stage costs more than a day of triage, drop it. Stages 1 through 7 stand on their own.

---

## Final `springboot` job in `.github/workflows/ci.yml`

Separate lint steps rather than one opaque `mvn verify`, mirroring the `frontend` job. A single step gives one red X and the reviewer has to open logs to learn whether it was formatting, a test, coverage or SpotBugs; separate steps put the answer in the job summary. Cost is 10 to 15s of extra JVM startup against a 548-test suite.

```yaml
  springboot:
    name: Spring Boot (lint, test, verify)
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: springboot
    steps:
      - uses: actions/checkout@v7

      - name: Setup Java
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "17"
          cache: maven

      # Fast gates first: these fail in seconds, before the 548-test suite.
      - name: Format check (Spotless)
        run: ./mvnw -B -ntp spotless:check

      - name: Lint (Checkstyle)
        run: ./mvnw -B -ntp checkstyle:check

      # verify = tests + jacoco:report + repackage + spotbugs:check + pmd:check + jacoco:check
      - name: Build, test & verify
        run: ./mvnw -B -ntp verify

      - name: Upload JaCoCo report
        if: always()
        uses: actions/upload-artifact@v5
        with:
          name: springboot-jacoco
          path: springboot/target/site/jacoco/
          retention-days: 7

      - name: Upload SpotBugs report
        if: always()
        uses: actions/upload-artifact@v5
        with:
          name: springboot-spotbugs
          path: |
            springboot/target/spotbugsXml.xml
            springboot/target/spotbugs.html
          retention-days: 7
```

`-ntp` suppresses download-progress spam. Fail-fast is the GitHub default and is what we want; `if: always()` on the artifact steps is the escape hatch. Steps 1 and 2 re-run inside `verify` (both bind to `validate`), roughly 15s of duplication, which is worth keeping as a cheap check that the phase bindings actually work.

Rejected: a separate parallel `springboot-lint` job. Better wall-clock, but it duplicates JDK setup and `~/.m2` cache restore (30 to 40s) and doubles the required-checks list. Not worth it at this repo size.

`docker-build.yml` needs no YAML changes; only `springboot/Dockerfile` changes, in Stage 1.

---

## Docker hazard table

| Hazard | Trigger | Mitigation | Stage |
|---|---|---|---|
| `spotless:check` at `validate` is part of `package` | any unformatted file | `-Dquality.skip=true` + `<skip>` | 1 + 2 |
| `checkstyle.xml` not in build context | Checkstyle at `validate` | same skip; do **not** move config under `src/` | 1 + 3 |
| `spotbugs/exclude.xml`, `pmd/ruleset.xml` not in context | `verify`-phase goals | unreachable from `package`, plus the skip | 1 + 5 + 7 |
| Enforcer `[3.9,)` vs image Maven 3.8 | enforcer at `validate` | bump base image to `maven:3.9.9-eclipse-temurin-17` | 1 |
| Error Prone runs at `compile`, which `package` reaches | any Error Prone finding | profile deactivated by `-Dquality.skip` | 8 |
| `springboot/target/` shipped as build context | every build | new `.dockerignore` | 1 |

---

## Documentation and rules updates

Repo rules require these; fold each into the stage that introduces the tool.

- **`springboot/README.md`**: new "Code Quality" section after `### Run Tests` (line 314) with the `spotless:apply` / `spotless:check` / `checkstyle:check` / `verify` commands, a gate/phase/config/report table, the formatting rules (4-space, 120-column soft limit, wildcards banned in `src/main` but static wildcards allowed in tests), the suppression policy, and a note that `-Dquality.skip=true` exists only for the Docker build. Line 320 already promises `mvn verify` enforces the coverage floor; as of Stage 4 that finally becomes true in CI.
- **`.claude/rules/springboot-java.md`**: new "Formatting & Static Analysis" section after `## Code Style`, covering indent and column limit, `spotless:apply` before committing, the wildcard-import rule, the import order, the SpotBugs suppression policy (category to `exclude.xml` with rationale, single site to `@SuppressFBWarnings` with a mandatory justification), and the Checkstyle/Spotless ownership split.
- **`springboot/CLAUDE.md`**: short "Quality Gates" section pointing at the README and warning against `-Dquality.skip=true` locally.
- **`.claude/skills/code-review/SKILL.md`**: two lines under the Spring Boot checklist at line 35, for wildcard imports and for justified suppressions. Formatting and import-order items deliberately do **not** go on the checklist, since automating them is the point.
- **Root `CLAUDE.md`**: update the CI paragraph, which currently describes the springboot job as tests only.

**`.pre-commit-config.yaml`: do not add a Maven hook.** A `./mvnw spotless:apply` hook costs 8 to 15s of JVM and plugin resolution on every commit in a repo where most commits touch TypeScript or Python. That trains `git commit -n`, which also disables detect-secrets. Ship a commented-out `pre-push`-stage variant with a one-line explanation and let it be opt-in.

---

## Verification

Per stage, before opening the PR:

```bash
cd springboot
./mvnw -B -ntp spotless:check      # Stage 2+
./mvnw -B -ntp checkstyle:check    # Stage 3+
./mvnw -B -ntp verify              # Stage 4+: 548 tests, coverage, SpotBugs, PMD
```

Baseline assertion at every stage: **548 tests, 0 failures, 0 errors, 0 skipped**, and JaCoCo line coverage at or above 0.76 (currently 80.55%).

Docker, which the CI checks do not cover:

```bash
docker build -t sec-api-test springboot/
```

This must succeed with the `config/` directory present *and* prove the `quality.skip` path works. To confirm the skip is actually doing something rather than passing by accident, temporarily introduce a formatting violation (add trailing whitespace to any `.java` file), then verify that `./mvnw spotless:check` **fails** while `docker build` still **succeeds**. Revert afterward.

Also run `uvx pre-commit run detect-secrets --all-files` before each push, per `.claude/rules/secrets-check.md`.

Per-PR CI expectations:

| Stage | Watch |
|---|---|
| 1 | `docker-build` job green on the PR (it deploys `main` to EC2 on merge) |
| 2 | `spotless:check` green; reformat commit is imports-only |
| 3 | `checkstyle:check` green; diff review confirms nothing but import lines changed |
| 4 | `jacoco:check` passes at 0.76 for the first time ever in CI |
| 5 | `spotbugs:check` clean after filter tuning; genuine findings fixed in code, not excluded |
| 7 | PMD ruleset retains at least ~5 useful rules, else drop the stage |
| 8 | Error Prone compiles clean on JDK 17; `docker build` still succeeds with the profile off |

Per `.claude/rules/include-plan-files-in-prs` convention, commit this plan file with the first PR in the series.
