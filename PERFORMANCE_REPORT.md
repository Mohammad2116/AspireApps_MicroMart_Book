# Performance evidence scope

The earlier Engineering Excellence acceptance used three 15-second k6 runs after a five-second
warm-up, five virtual users, authenticated lookup/create/order/limiter traffic and a two-CPU,
768 MiB application limit. All met local p95 <500 ms, p99 <1,000 ms and error-rate <1% budgets.
A measured outbox backlog prompted a 250 ms relay delay; repeated runs then met a sampled
oldest-pending-age budget below five seconds. This does not establish production capacity.

Machine identifiers and raw workstation samples are intentionally not published. Reproduce
with performance/workload.js and scripts/runtime_validation.py; record your own hardware,
load, latency, resource and backlog observations. Long-duration soak, saturation and HA
failover remain deployment validation work.
