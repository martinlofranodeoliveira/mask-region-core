# Development 0.1.0 contract

This document defines the supported subset and public behavior of package `br.com.lofranotecnologia.maskregion`. No broader image-format or processing compliance is claimed.

## Coordinates, storage and ownership

- Width and height must be positive. Coordinates are zero-based `(x,y)`, x increases across a row, y downwards. Index is `y * width + x`.
- `BitGrid.fromBytes(width,height,bytes[,limits])` accepts exactly one byte per pixel, each 0 or 1. `fromBitSet(width,height,bits[,limits])` accepts set bits only in `[0,width*height)`. Inputs are detached; caller capacity is never cloned blindly. Dimension and scratch checks precede data validation and allocation.
- `width()`, `height()`, `pixelCount()` and `get(x,y)` inspect a grid. `toBytes()` and `toBitSet()` return detached buffers. BitSet accessors are bounded to the grid range.
- No mutable source or result buffer is exposed. Results have value-stable contents. Only BitGrid defines structural `equals` and `hashCode`.
- Invalid coordinates or component IDs produce `InvalidArgument`. Concurrent reads and independent operations are supported. Callers must not mutate their own input arrays/BitSets concurrently with construction.

## Operations

Every operation has a convenience overload using `Limits.DEFAULT` and an overload whose last argument is `Limits`. All operations either return a complete new result or throw; source grids are never modified. Core operations have no clocks, IO, persistence or global mutable state.

### `MaskOps.labelComponents(BitGrid, Connectivity[, Limits]) -> Labeling`

`Connectivity.FOUR` joins edge neighbors; `EIGHT` additionally joins diagonal neighbors. Background has label zero. Labels 1 through `componentCount()` are assigned by first foreground encounter in row-major order, irrespective of union-find roots.

`Labeling` has `width()`, `height()`, `labelAt(x,y)`, detached row-major `int[] labels()`, and `component(label)`. Each `Component` reports `label`, `area`, `minX`, `minY`, `maxXExclusive`, `maxYExclusive`. Its bounding rectangle is `[minX,maxXExclusive) × [minY,maxYExclusive)`, in pixels. An all-background mask has zero components. Labeling uses union-find, not BFS.

### `MaskOps.retainByArea(BitGrid, Connectivity, int minArea[, Limits]) -> Retention`

`minArea >= 0`. Retain each foreground component iff its area is **>= minArea**. The result has `grid()`, `retainedComponents()`, `removedComponents()` and `retainedPixels()`. Counts describe source components; no label image is retained in this result. Zero retains every component; thresholds greater than the pixel count retain none.

### `MaskOps.fillHoles(BitGrid, Connectivity foreground, int maxHoleArea[, Limits]) -> HoleFill`

`maxHoleArea >= 0`. Background uses `foreground.dual()`: foreground FOUR/background EIGHT or foreground EIGHT/background FOUR. A background component is exterior iff it connects to the virtual exterior background frame. Equivalently, it contains any image-border pixel. An enclosed component is filled iff its area is **<= maxHoleArea**. Original foreground remains foreground.

The result has `grid()`, `filledRegions()` and `filledPixels()`. Zero fills nothing and returns an equal, detached grid. The threshold excludes the exterior regardless of its area. Diagonal contact is significant; see the design document.

### `MaskOps.squaredDistanceToBackground(BitGrid[, Limits]) -> DistanceGrid`

For pixel `(x,y)`, return the minimum `(x-bx)^2 + (y-by)^2` over every background pixel center and a one-pixel virtual background frame with coordinates x in `[-1,width]`, y in `[-1,height]` outside the image. An infinite exterior would give the same minimum. Image background has distance zero. A solid 1×1 has distance 1; a solid 3×3 has center 4 and all boundary values 1.

`DistanceGrid` has `width()`, `height()`, `get(x,y)` and detached row-major `long[] values()`. Values are exact integers in squared pixel-center units, not a rounded square root, a physical measurement, a Manhattan metric or a connectivity-dependent distance. Arithmetic uses checked long operations. Infinity is an internal sentinel only and never appears in a returned distance grid.

## Resource accounting and order

`Limits(maxDimension,maxPixels,maxScratchBytes)` may only reduce the hard defaults: dimension 4,096, pixels 1,048,576, scratch 67,108,864 bytes. Dimensions/pixel caps must be positive; scratch may be zero. Invalid limit configurations produce `InvalidArgument`. Dimension positivity is checked first, then per-axis limits, then checked pixel product. Before allocating an operation's buffers, its conservative logical-byte plan must fit the supplied scratch cap.

Let `N = width*height`, `P = (width+2)*(height+2)`, `S = max(width+2,height+2)`, and `C = 4,194,304`.

| Entry point | Reserved logical bytes |
| --- | ---: |
| BitGrid factories | `8 * ceil(N/64)` |
| labelComponents | `40*N + 128` |
| retainByArea, fillHoles | `48*N + 512` |
| squaredDistanceToBackground | `16*P + 32*S + 512` |
| PNG read/write after dimension preflight | `2*(C+1) + 4*N + 4096` |
| PNG compressed staging before header inspection | `2*(C+1)` |

Plans include algorithm-owned primitive-array payloads and result storage, with conservative slack. Composite plans include their labeling work. They exclude the existing immutable source, caller-owned buffers, copies requested by accessors, JVM objects/array headers/reference overhead, GC retention, thread stacks, and ImageIO internal codec/cache allocations. They are not heap ceilings. The maximum supported N fits all default core plans. All plan arithmetic is checked before allocation. Lower budgets can intentionally reject otherwise valid small images.

PNG must first stage compressed bytes to inspect a header; this is not a decoded image or operation buffer. It reads at most `C+1` bytes, rejecting the extra byte. Dimensions, pixel count and final PNG plan are checked before decoded-buffer allocation. A width of 4,097 produces `DimensionLimit` with default limits, even when no valid raster payload follows. Very large or overflow-prone dimension requests are rejected before grid allocation. Accessor copies are bounded by the already validated grid dimensions, not by a supplied per-operation scratch cap.

## PNG subset and IO

`PngAdapter.read(InputStream,int threshold[,Limits])` accepts only PNG with an opaque component grayscale color model, one byte band and 8-bit samples. Threshold is an integer from 0 through 255; foreground is raw raster sample **>= threshold**. No `getRGB`, gamma conversion, compositing or palette expansion is used to define the threshold. Raw type is preflighted before decoding and the returned type is checked again. Alpha-bearing models produce `UnsupportedAlpha`; palette, RGB, sub-8-bit and 16-bit types produce `UnsupportedPng`. PNG alpha via transparency information is rejected when represented by ImageIO as an alpha-bearing raw model.

Only JDK ImageIO is required. Reader input uses seek-forward/ignore-metadata options. Metadata is not exposed or interpreted by this library; the decoder may still parse structural chunks or allocate internal data. Explicit memory image streams avoid ambient disk-cache settings. The compressed cap counts the entire supplied stream, including trailing bytes. This adapter is not a complete PNG validator: CRC, ancillary-chunk and malformed-data behavior also depends on the JDK decoder. It is not a hardened hostile-input sandbox.

`PngAdapter.write(BitGrid,OutputStream[,Limits])` emits opaque 8-bit grayscale PNG with samples 0 or 255 and no application metadata. It flushes output. Encoded bytes need not be identical across JDK versions. Both entry points leave caller streams open; failures may consume input or leave partial output. No transactional IO guarantee is made. Stream blocking, cancellation and wall-clock limits are the caller's responsibility.

## Errors

`MaskException` is unchecked. `code()` is the stable enum; its message is exactly the code name, with no nested decoder exception. Normal JVM errors such as `OutOfMemoryError` are not converted.

| Code | Meaning |
| --- | --- |
| InvalidArgument | Null argument, invalid threshold/area/coordinate/ID/limit/CLI flag |
| InvalidDimensions | Nonpositive dimension |
| DimensionLimit | An axis exceeds configured cap |
| PixelLimit | Product exceeds configured pixel cap |
| ScratchLimit | Buffer plan exceeds cap or accounting arithmetic overflows |
| InvalidData | Invalid binary input or failed demo numeric invariant |
| ArithmeticLimit | Checked distance arithmetic overflows |
| CompressedLimit | PNG input contains more than 4 MiB |
| UnsupportedAlpha | Alpha-bearing PNG model |
| UnsupportedPng | Unsupported PNG type or unavailable PNG provider |
| InvalidPng | Invalid signature or decoder/header failure |
| IoError | Caller stream read/write/flush failure or CLI filesystem/report failure |

Preflight-invalid thresholds are rejected before reading a stream. Input stream IO errors during staging are `IoError`; decoder IO/runtime failures are `InvalidPng`. Multiple-invalid-argument precedence beyond the stated preflight sequence is not part of the contract.

## CLI contract

`Demo` accepts no arguments or exactly `--json`. It only creates synthetic temporary PNGs. Success is exit zero with one line: `PASS components=1 filled=1 center_d2=4`, or JSON `{"status":"PASS","components":1,"filled":1,"center_d2":4}`. Failure is exit one with `ERROR <Code>` on stderr. Report flush errors also fail. Output is textual and requires no image viewer.

## Contrato da API em português

A versão de desenvolvimento 0.1.0 usa coordenadas `(x,y)` iniciadas em zero e arrays por linhas, índice `y*largura+x`. Dimensões são positivas. `BitGrid.fromBytes` recebe exatamente um byte 0/1 por pixel; `fromBitSet` rejeita bits fora da imagem. Entradas e acessores são cópias independentes. Resultados são imutáveis e uma operação que falha não altera a origem.

`labelComponents` recebe FOUR ou EIGHT, usa union-find e devolve rótulos estáveis pelo primeiro pixel de cada componente em ordem de linhas. Zero é fundo. `component(id)` informa área e caixa semiaberta `[minX,maxXExclusive) × [minY,maxYExclusive)`. `retainByArea` mantém área **>= minArea** e informa componentes mantidos/removidos e pixels mantidos. `fillHoles` preenche fundo fechado com área **<= maxHoleArea**, informando regiões e pixels preenchidos. Limiares de área não podem ser negativos; zero não preenche buracos.

No preenchimento, primeiro plano FOUR exige fundo EIGHT e primeiro plano EIGHT exige fundo FOUR. Fundo ligado à borda é exterior por uma moldura virtual externa de um pixel. A mesma moldura participa da distância euclidiana quadrática exata. `squaredDistanceToBackground` retorna `long`, zero no fundo, 1 na borda de máscara sólida e 4 no centro de uma máscara sólida 3×3. Conectividade não altera a distância.

Os limites e fórmulas da tabela acima são normativos também para esta seção. Os valores padrão são máximos: 4.096 por eixo, 1.048.576 pixels e 64 MiB de buffers lógicos. Orçamento não inclui overhead JVM, origem existente, cópias pedidas por acessores ou estruturas internas ImageIO, nem limita tempo ou heap total. As verificações precedem buffers de operação e decodificação; a entrada comprimida precisa ser lida primeiro, limitada a 4 MiB mais um byte de detecção.

PNG deve ser cinza opaco de 8 bits, um canal byte. O primeiro plano é amostra bruta **>= limiar**, entre 0 e 255. Alfa retorna `UnsupportedAlpha`; paleta, RGB, baixa profundidade e 16 bits retornam `UnsupportedPng`. Não há composição, conversão silenciosa ou uso de `getRGB`. Streams pertencem ao chamador; falhas podem consumir entrada ou deixar saída parcial. Metadados não são expostos, e ImageIO não é sandbox contra imagens hostis nem validador integral de PNG.

Erros são `MaskException` com código da tabela e mensagem fixa igual ao nome, sem dados ou caminhos. Erros normais da JVM não são convertidos. A CLI aceita somente ausência de argumentos ou `--json`, verifica PNGs sintéticos e produz a linha de sucesso definida acima. Falhas geram saída não zero, inclusive erro de descarga do relatório.
