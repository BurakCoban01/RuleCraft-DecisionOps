# Runtime analytics and artifact benchmark

Open a decision service and choose **Analytics** to monitor persisted runtime evidence and measure the active immutable artifact.

## Read runtime evidence

Choose a 24-hour, 7-day, or 30-day window and optionally narrow the view to sandbox, single API, API batch, or workbench batch traffic. The scorecard reports evaluation volume, success rate, p95 latency, and the newest matching event. The volume chart keeps every time bucket visible, including quiet periods, and hatches failed evaluations.

Channel mix shows where evaluations originated and the p95 latency within each source. **Top error codes** ranks stable server error codes without exposing request values or stack traces.

The source bar and footer state the event grain, time window, bucket grain, and latency boundary. RuleCraft analyzes the newest 10,000 matching traces; if more records match, a visible warning reports the full count and asks you to narrow the filters. **Export snapshot** downloads the currently displayed analytics and benchmark history as JSON.

## Benchmark the active artifact

Tenant administrators and project owners, editors, and reviewers can supply one valid typed request, choose 10–500 measured iterations and 0–50 warmups, then select **Run benchmark**. Project viewers can inspect evidence but cannot start a run. If the active deployment changes after the page loads, refresh before trying again.

The result shows p50, p95, p99, throughput, iteration counts, input/output fingerprints, artifact identity, actor, and time. History is immutable and newest first; select an earlier run to inspect its provenance after a reload.

The benchmark is a bounded, sequential, in-process engine diagnostic. It excludes HTTP, database persistence, trace capture, network latency, concurrency, and load, and it must not be used as production capacity or SLO evidence. Benchmark requests and outputs are fingerprinted; their raw values are not retained, and benchmark executions do not add runtime traces.
