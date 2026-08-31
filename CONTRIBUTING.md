# Contributing to Systar

English | [简体中文](CONTRIBUTING.zh-CN.md)

Thanks for your interest in improving Systar! This document describes how to set up a
development environment and what to keep in mind when submitting changes.

## Development setup

```bash
# Backend — H2 development profile (no external database required)
./mvnw spring-boot:run -pl extensions/systar-server -Dspring-boot.run.profiles=dev

# Frontend
cd frontend
npm install
npm run dev

# Tests
./mvnw clean test -o      # backend (offline mode; use a clean build — see below)
cd frontend && npm test   # frontend
```

- JDK 17 is required (the project targets Java 17).
- The Maven Wrapper (`./mvnw`) pins the supported Maven version — no local Maven install
  needed. Offline builds (`-o`) work against `lib/maven-repo/` for the vendored
  BACnet4J artifacts.
- Always run full regression with `./mvnw clean test -o`. Without `clean`, test JVMs are
  reused across modules and Spring application contexts can bleed between modules,
  causing intermittent failures.

## Code conventions

- Follow the **SOLID** design principles; keep implementations as simple and maintainable
  as the requirement allows.
- **Test-driven development** — write the failing test first. Unit test coverage should
  stay above 90%.
- Java 17, official Java style guide. Identifiers and field names in English.
- Define magic numbers as named constants instead of inlining them.
- Never swallow runtime errors silently — log them or propagate them.
- Defensive programming focuses on missing or malformed input data (validate external
  input at the boundary).
- Look at every change from an architectural perspective: a local fix that contradicts
  the surrounding design is not done.

## Database changes

Systar supports MySQL and H2 through a dialect adapter. Any schema or seed-data change
must ship **both** variants, in sync:

- `sql/mysql/ddl/`, `sql/mysql/data/`
- `sql/h2/ddl/`, `sql/h2/data/`

H2 scripts must avoid MySQL-specific syntax (`ENGINE=InnoDB`, `COLLATE`, column-level
`COMMENT`) and use `MERGE INTO` instead of `ON DUPLICATE KEY UPDATE`.

Database exceptions must propagate — never catch-and-ignore; schema readiness is
guaranteed by the initializer, not by defensive try/catch in business code.

## Submitting changes

1. Fork the repository and create a topic branch for your change.
2. Make your change with the conventions above; add or update tests.
3. Run the full test suites (`./mvnw clean test -o` and `npm test`) and make sure they
   pass. Set a short timeout when running individual tests (a hanging test is a bug).
4. Open a pull request describing **what** changed and **why**. For bug fixes, include
   the failing scenario (inputs, expected vs. actual behavior).

Commit messages follow the Conventional Commits style (`feat:`, `fix:`, `docs:`,
`refactor:`, `test:`, `chore:` ...), one logical change per commit.

## Reporting issues

When reporting a bug, please include: the module involved, the exact steps or inputs to
reproduce, the expected behavior, the actual behavior (logs / error messages), and your
environment (JDK version, database type, OS).

## License

By contributing to Systar, you agree that your contributions are licensed under the
GNU General Public License v3.0 only, the same license as the project
(see [LICENSE](LICENSE) and [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)).
