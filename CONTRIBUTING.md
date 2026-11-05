# Contributing

Mask Region Core is original software by Lofrano Tecnologia, under MIT. General organizational information and contact are available at the [official homepage](https://lofranotecnologia.com.br).

## Local checks

Use JDK 21 or newer with `java.desktop` and Bash:

```sh
bash scripts/test.sh
bash scripts/demo.sh
bash scripts/demo.sh --json
```

The scripts compile with `--release 21 -encoding UTF-8 -Xlint:all -Werror`; no Gradle, external dependency or generated lockfile is needed. Do not add compiler suppressions to hide defects. Tests use an explicit asserting runner rather than JUnit. Build output stays in ignored `build/`.

## Changes worth proposing

Describe the mathematical or API behavior, a minimal synthetic counterexample, and its resource implications. Preserve explicit foreground/background topology, row-major determinism, immutable boundaries, integer distances and stable error codes. Update both README languages and the contract when behavior changes. An altered bound must include its derivation and rejection tests.

Regression tests should obtain expected results from a small independent calculation or oracle, not a second call to the production routine. Keep exhaustive 3×3 checks and add boundary/tie cases when changing distance logic. Tests and demos must remain deterministic and offline. Do not introduce clocks, telemetry, network dependencies, real personal data, private client code, screenshots or copied implementations. New fixtures must be synthetic and explained.

Report ordinary bugs or propose changes through the repository's issues or pull requests. Include the JDK version, command, fixed error code and a small synthetic input; avoid host paths or sensitive image content. Security concerns belong in the private channel described in [SECURITY.md](SECURITY.md), not public issues.

Do not add publishing, release automation or remote-deployment workflows. CI needs read-only permissions, pinned actions and timeouts. Contributions must be yours to license under MIT; keep author attribution organizational, without invented contact details.
