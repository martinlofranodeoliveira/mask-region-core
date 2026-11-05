# Mask Region Core

Development version **0.1.0** — original open-source Java software by **Lofrano Tecnologia**. A bounded binary-grid library for deterministic connected components, dual-connectivity hole filling, area retention and exact squared Euclidean distance to background. No native library, external dependency, model, service, telemetry or required image viewer.

- [Português](README.pt-BR.md)
- [Precise API and resource contract](docs/contract.md)
- [Design, topology and mathematical rationale](docs/design.md)
- [Official homepage / general contact](https://lofranotecnologia.com.br)
- [Repository](https://github.com/martinlofranodeoliveira/mask-region-core)

## Build, test and demo

Prerequisites: JDK 21 or newer, including `javac` and the `java.desktop` module; Bash for these scripts. No Gradle, Maven or dependency download is needed. From the project directory:

```sh
bash scripts/compile.sh
bash scripts/test.sh
bash scripts/demo.sh
bash scripts/demo.sh --json
```

Compilation uses `--release 21 -encoding UTF-8 -Xlint:all -Werror`. Classes are placed in `build/classes`; tests in `build/test-classes`. For a local application, compile against `build/classes` and include it on the runtime classpath. The package is `br.com.lofranotecnologia.maskregion`.

Expected test report:

```text
PASS core: exhaustive 512 masks x 2 connectivities; BFS and brute-force oracles
PASS PNG: raw samples, model rejection, limits, IO failures, synthetic demo
PASS all tests
```

Expected default demo report:

```text
PASS components=1 filled=1 center_d2=4
```

With `--json`, the single report line is:

```json
{"status":"PASS","components":1,"filled":1,"center_d2":4}
```

The demo writes a synthetic 3×3 ring to a temporary PNG, rereads it, fills its center, writes and rereads the filled mask, and checks the exact numerical results. It removes only its own two files and temporary directory. No files are retained, no image viewer is opened and no external images are read. CLI argument, IO, cleanup and report-flush failures exit nonzero with fixed diagnostics, without paths or pixel data. A synthetic PNG round trip is an algorithm test, not visual/browser QA.

## Small API example

```java
import br.com.lofranotecnologia.maskregion.BitGrid;
import br.com.lofranotecnologia.maskregion.Connectivity;
import br.com.lofranotecnologia.maskregion.Labeling;
import br.com.lofranotecnologia.maskregion.MaskOps;

BitGrid ring = BitGrid.fromBytes(3, 3, new byte[] {
    1, 1, 1,
    1, 0, 1,
    1, 1, 1
});
MaskOps.HoleFill fill = MaskOps.fillHoles(ring, Connectivity.FOUR, 1);
Labeling labels = MaskOps.labelComponents(fill.grid(), Connectivity.FOUR);
Labeling.Component component = labels.component(1);
long centerD2 = MaskOps.squaredDistanceToBackground(fill.grid()).get(1, 1);
// filledPixels=1; component.area()=9; bounds [0,3) × [0,3); centerD2=4.
// ring still has a background center.
```

`MaskOps.retainByArea(grid, connectivity, minArea)` retains components with area **>=** the threshold. `fillHoles` fills enclosed background regions with area **<=** its threshold. Both return a new immutable grid and counts. Label zero is background; positive labels follow first row-major foreground encounter, independent of union-find roots. Array and BitSet inputs are detached; array and BitSet accessors return detached copies.

For PNG streams:

```java
import java.io.InputStream;
import java.io.OutputStream;
import br.com.lofranotecnologia.maskregion.BitGrid;
import br.com.lofranotecnologia.maskregion.PngAdapter;

class MaskStreams {
    static void threshold(InputStream input, OutputStream output) {
        BitGrid grid = PngAdapter.read(input, 128);
        PngAdapter.write(grid, output);
    }
}
```

The caller owns and closes these streams. Only opaque 8-bit grayscale PNG is accepted. Foreground is **raw raster sample >= threshold**, not a gamma-converted `getRGB` value. Alpha is rejected as `UnsupportedAlpha`; indexed, RGB and 16-bit input is rejected, not converted.

## Topology is an explicit input

A diagonal chain of three pixels has three components under foreground FOUR and one under EIGHT. Hole filling pairs foreground FOUR with background EIGHT, or foreground EIGHT with background FOUR. Thus a center background pixel touching a corner background pixel diagonally can escape under background EIGHT but not FOUR. This choice is not inferred from image content.

A one-pixel virtual exterior frame is background. It determines exterior-background connectivity and distances. A solid 3×3 mask has distance squared 1 on the boundary and 4 at the center. These are squared pixel-center units, not physical units. Distance is Euclidean, not Manhattan, and does not depend on connectivity.

## Why this design

Union-find provides efficient component grouping; a separate row-major pass supplies stable labels. An original integer lower-envelope transform evaluates the separable minimum of squared parabolas, with checked arithmetic and explicit tie handling. Independent test references instead use BFS floods and brute-force nearest-background searches. Every one of the 512 binary 3×3 masks is tested under both foreground topologies, including every area threshold from 0 through 10. Skinny, empty-foreground, solid, diagonal, tie, IO and mutation cases supplement that enumeration.

Defaults are at most 4,096 per dimension, 1,048,576 pixels and a conservative 64 MiB algorithm-owned logical-buffer budget. Smaller limits can be supplied. Dimensions and operation scratch are checked before operation arrays; PNG compressed staging must precede header inspection. Resource formulas and exclusions are specified in the contract.

## Limitations

This is a development release, not a production-readiness or broad standards-compliance claim. It implements a documented PNG subset, not arbitrary image formats. ImageIO is **not** a hardened hostile-image sandbox. Algorithm scratch excludes JVM object overhead and ImageIO codec internals; the budget is not a process heap ceiling or decode-time limit. Compressed input is capped at 4 MiB, but blocking streams still require caller-controlled timeouts/cancellation. Normal JVM allocation failures remain possible.

No clinical interpretation, object recognition, vector contours, browser UI, certified image processing or physical measurement certification is provided. All included data is synthetic. There are no claimed users, deployment results or benchmarks.

MIT licensed, copyright 2026 Lofrano Tecnologia. See [security reporting](SECURITY.md) and [contribution guidance](CONTRIBUTING.md).
