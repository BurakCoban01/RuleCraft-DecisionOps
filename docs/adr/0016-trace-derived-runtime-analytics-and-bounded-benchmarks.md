# ADR 0016: Trace-derived runtime analytics and bounded benchmarks

## Status

Accepted — 2026-08-29

## Context

RuleCraft retains privacy-safe, immutable decision traces for every sandbox, workbench batch, and decision API evaluation. Operators need useful runtime health evidence without introducing a second event model, retaining raw requests, or presenting development-machine timings as production capacity. Authors also need a repeatable way to compare the currently active immutable artifact without creating synthetic runtime traces.

## Decision

- Runtime analytics derive from `decision_trace`, at one evaluation per row. Every query is tenant, project, and service scoped and excludes future events. Supported windows are 24 hourly buckets, 28 six-hour buckets over seven days, and 30 daily buckets over thirty days.
- The primary indicators are evaluation volume, success rate, and p95 engine-boundary latency. Supporting evidence includes p50/p99/max latency, channel mix, failed counts, and the five most frequent stable error codes. Empty buckets remain present so missing activity is not mistaken for missing data.
- A query analyzes at most the newest 10,000 matching traces and returns the total matching count plus a truncation flag. The UI discloses source, grain, window, freshness, and truncation instead of implying full coverage.
- A benchmark evaluates the active immutable deployment sequentially in process. It performs one unmeasured validation, zero to fifty warmups, and ten to five hundred measured iterations. It excludes HTTP, persistence, tracing, network, concurrency, and load behavior, so it is an artifact diagnostic rather than a capacity test or service-level objective.
- Benchmark creation is limited to tenant administrators and project owners, editors, and reviewers. Project viewers may inspect analytics and immutable benchmark history. The service row is locked so benchmark, deployment, and version-sensitive operations cannot race past the active-artifact check.
- Benchmark requests are idempotent by tenant, service, and client request ID. The request identity canonically covers the expected deployment, typed inputs, and iteration settings. A changed active deployment is rejected as stale.
- Immutable benchmark records retain the deployment/version identity, artifact/input/output SHA-256 fingerprints, aggregate timings, actor attribution, and timestamp. Raw inputs and outputs are never retained or audited. History is capped at 1,000 records per service and the workspace returns the newest 50.
- Benchmark executions do not create decision traces and therefore cannot inflate operational volume or success metrics.

## Consequences

Operators get source-backed runtime trends and stable diagnostics from existing privacy-safe evidence. Governance roles can compare active artifacts reproducibly while preserving provenance and avoiding false production-capacity claims. The implementation remains local and synchronous by design.

runtime analytics does not add external telemetry infrastructure, percentile sketches, retention/legal hold, alerting, distributed load generation, SLO enforcement, or cross-service portfolio analytics. Security, observability, and recovery hardening remain operational security work.
