# Publication test evidence

On 2026-11-06, the Engineering Excellence Git bundle was restored into a fresh directory.
`./mvnw -B -ntp spotless:check clean verify -Pintegration,sbom,static` passed: **106 tests,
0 failures, 0 errors, 0 skipped**, build wall time 2 minutes. The suite comprises 56 Surefire
and 50 Failsafe tests. Both whole-module coverage gates passed. Outcome-only records are
in reports/restored-publication-tests.json; local paths and environment properties are omitted.

The public source snapshot preserves application/domain source, tests, migrations and Maven
build configuration. The sanitized public checkout was then independently rebuilt with the same command:
106 tests, zero failures/errors/skips, wall time 1 minute 57 seconds. Both coverage
gates and static review passed. See reports/publication-tests.json.
The historical 43-assertion runtime acceptance is separate and is not counted as Java tests.
The repository's own hosted CI status is independent of these local results.
