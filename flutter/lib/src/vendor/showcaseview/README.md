# Vendored: showcaseview

This directory is a vendored copy of [`showcaseview`](https://pub.dev/packages/showcaseview)
**v5.1.0** (© Simform Solutions, MIT — see `LICENSE`), copied into the Digia
Engage SDK so we can patch it for exact cross-platform guide UI parity.

Why vendored (not a pub dependency):
- The published `Showcase.withWidget` path draws no arrow, and the resolved
  tooltip position is internal to its render delegate — so a custom container
  can't draw a correct, flip-aware arrow. Vendoring lets us expose that.

Modifications from upstream are marked with `// DIGIA:` comments. Keep that
convention so the diff against upstream stays auditable.
