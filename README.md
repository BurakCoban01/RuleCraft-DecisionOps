# RuleCraft DecisionOps

A versioned business-rules and decision-management platform with a Java / Spring Boot API, React / TypeScript web client, and PostgreSQL persistence.

## Implemented capabilities

Platform foundation, identity and access, typed catalog, decision graph, decision tables, expression authoring, decision execution, test scenarios, regression matrices, version review, approval and promotion, runtime sandbox.

## Local development

Requirements: Java 25, Node.js 24, pnpm 10, Docker Compose. Copy `.env.example` to `.env` and replace the database password. Run `docker compose --env-file .env up -d db`, export `RULECRAFT_DB_PASSWORD`, then run `./mvnw spring-boot:run` from `api/`. In another terminal, run `pnpm install --frozen-lockfile` and `pnpm dev` from `web/`. The web client opens at `http://127.0.0.1:50111`. Windows users can use `mvnw.cmd`.

## Verification

Run `./mvnw verify` in `api/`; run `pnpm lint`, `pnpm test`, and `pnpm build` in `web/`. Browser acceptance requires the local application and database.

## Structure

- `api/`: application services, decision runtime, migrations, and tests.
- `web/`: typed browser client and browser acceptance tests.
- `docs/`: architecture decisions and product workflows.
