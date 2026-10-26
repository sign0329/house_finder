# Apartment Search working agreement

- Stack: Java 21, Spring Boot, Thymeleaf, JDBC, PostgreSQL, Flyway.
- Read docs/requirements.md and docs/architecture.md before changing behavior.
- Keep sample data clearly labeled; never present generated trades as public data.
- Add a meaningful failing test before changing a domain rule, observe the failure, implement, then run ./mvnw verify.
- Do not claim TDD evidence unless the red and green runs were actually observed.
- Store prices as integer 만원 and areas as BigDecimal. Validate all search inputs.
- SQL values use named parameters; sorting uses an explicit allowlist.
- Change schema only through a new Flyway migration.
- Never load test a public API. Use fixtures or a mock source.
- Never commit credentials or .env; do not weaken tests to make CI green.
- Report tests actually executed and environment limitations.
- No deployment, paid resources or API collection are required by the current scope.

- Follow the repository-root CONTRIBUTING.md: use a task branch, open a PR targeting main, and merge only after Java tests passes. Never push changes directly to main or bypass protection.
- Stage explicit project files; preserve unrelated staged IDE/local changes.

- User testing preference: for environment/setup/install work, focus additional verification on the three highest-risk cases; for other non-core features use at most five cases. Preserve existing mandatory CI tests. Core features retain the necessary coverage.
