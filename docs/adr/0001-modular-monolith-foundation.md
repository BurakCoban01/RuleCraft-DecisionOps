# ADR 0001: Modular monolith foundation

- Status: Accepted
- Date: 2026-08-29

## Context

RuleCraft DecisionOps needs strongly typed business-rule domains, transactionally consistent workflows, tenant isolation, auditability, and a separately deliverable web client. The repository starts empty and must remain efficient on constrained local hardware.

## Decision

Use a modular monolith with:

- Java 25 and Spring Boot 4.1 for the API and decision runtime;
- Maven Wrapper for reproducible backend builds;
- PostgreSQL as the authoritative runtime store, managed with Flyway migrations;
- React 19, TypeScript and Vite for the web application;
- OpenAPI as the public HTTP contract;
- package boundaries under `com.rulecraft.decisionops` for platform, identity, catalog, authoring, execution and governance domains;
- optional infrastructure only behind adapters and disabled by default.

The initial foundation exposes only truthful platform capability and health surfaces. Identity, catalog and authoring behavior remain owned by their feature modules.

## Consequences

- Domain modules can share one transaction boundary without premature distributed-system cost.
- Server-side authorization remains authoritative; client state never grants access.
- PostgreSQL-specific behavior requires database-backed integration coverage before persistence changes are accepted.
- The web client can deploy independently while using one typed API boundary.
- Breaking module or API boundary changes require a follow-up ADR and compatibility evidence.

## Version evidence

The selected versions were checked against primary documentation on 2026-08-29: Spring Boot 4.1.1 supports Java through 26, Node 24 is an LTS line, React 19.2 is current, and current Vite supports the installed Node 24 runtime.
