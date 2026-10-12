# Dependency impact governance

Open a decision service and choose **Impact** to inspect the evidence affected by a governed change. The workspace combines the live typed model with test, review, promotion, regression, and active-runtime state for that service.

## Read readiness and findings

The scorecard shows the current risk, dependency count, blockers, warnings, and a short source identity. `REVIEWABLE` means no high-risk or blocked finding was detected; medium warnings remain visible because they need judgment but do not silently change the approval workflow.

Findings explain the detected condition, its consequence, and the next action. They cover invalid or unimplemented models, missing or invalid scenarios, uncaptured work, promotion drift, failed or interrupted regression, runtime mismatch, and missing evidence.

## Explore the blast radius

Filter the inventory by upstream types, model nodes, assurance evidence, governance records, or runtime artifacts. Search matches labels, keys, status, and details. The graph entries show upstream/downstream flow and implementation state; upstream object, list, and enum types include transitive references from service fields and graph ports.

**Export evidence** downloads the live map or selected assessment as JSON. Exports contain dependency metadata and fingerprints, not runtime request payloads.

## Capture immutable evidence

Tenant administrators and project owners, editors, and reviewers can choose **Capture assessment**. RuleCraft verifies that the full dependency identity has not changed since the page loaded, then stores immutable evidence and its snapshot fingerprint. If a model, scenario, regression, review, promotion, or deployment changes first, refresh the live analysis and review it again.

Assessment history is newest first. Selecting an entry replaces the live map with the immutable snapshot and clearly marks who captured it and when; **Return to live model** restores current evidence. Project viewers can read both views but cannot capture a new assessment.

Impact assessment is advisory evidence. Approval, promotion, deployment, and rollback continue to use their own governed transitions and permissions.
