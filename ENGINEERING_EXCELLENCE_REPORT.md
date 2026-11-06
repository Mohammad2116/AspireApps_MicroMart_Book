# Engineering evidence for the book companion

The restored Engineering Excellence implementation contains 106 Java tests. The original
acceptance run reported zero failures/errors/skips, with a separate 43-assertion local runtime
harness. Those runtime results are historical evidence for this exact implementation, not
claims that every reader environment or hosted workflow has passed. Fresh publication test
results are recorded in TEST_EVIDENCE.md.

Application line coverage was 393/408 (96.32%) and branch coverage 93/108 (86.11%);
domain coverage was 21/21 lines and 8/8 branches. Coverage gates include every production
class. A controlled stale-owner fault was detected; no broad mutation score was measured.
Security and production readiness remain conditional. Review SECURITY_REVIEW.md and
PRODUCTION_READINESS_CHECKLIST.md before deployment. This is not production certification.

The publication preserves tested source and keeps raw local logs, machine information and
personal commit metadata outside the public repository. See PUBLICATION_PROVENANCE.md.
