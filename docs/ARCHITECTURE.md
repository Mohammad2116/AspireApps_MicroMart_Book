# Architecture

The current architecture and trade-offs are in [Architecture decisions](ARCHITECTURE_DECISIONS.md)
and [C4-style views](DIAGRAMS.md). The baseline implementation is preserved in Git history and
`docs/BASELINE-AUDIT.md`; it used network-held database locks. The current relay uses durable
leases and separate publication/finalization boundaries. Both have at-least-once semantics.
