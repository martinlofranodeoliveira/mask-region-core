# Design and topology — development 0.1.0

## Boundaries first

The core owns only immutable, positive-sized binary grids. Storage uses private BitSets; factories copy set positions into a bounded allocation instead of cloning potentially oversized caller backing capacity. Exported buffers are detached. Dimensions and operation-specific conservative scratch plans are checked before allocating operation arrays. Configuration can reduce, never increase, the hard default limits.

Core methods are synchronous, reentrant and clock-free. There is no persistence, service connection, telemetry, native code or model dependency. PNG IO and the synthetic filesystem demo are deliberately outside the core. A normal operation returns new result storage; it never edits a source and then attempts rollback.

## Deterministic component labels

The production scan creates one provisional set for each foreground pixel and unions already visited neighbors: left and above for FOUR, plus upper-left and upper-right for EIGHT. Union by rank and path compression bound tree work. Root identifiers are implementation details. A second row-major pass maps each first-seen root to the next public label. A final pass accumulates area and half-open bounding boxes.

Statistics are held in flat primitive arrays rather than one retained object per component. `component(id)` creates a small immutable value on demand. Area retention reuses labeling and copies only pixels belonging to qualifying components. Public counts describe the original components, not an inferred later relabeling.

Expected complexity is O(N alpha(N)) for labeling and area operations, with O(N) primitive storage. These are algorithmic bounds, not benchmark results.

## Dual connectivity, not hidden topology

Consider foreground `1` and background `0`:

```text
100
010
001
```

FOUR gives three foreground components, EIGHT one. Now consider:

```text
011
101
111
```

The center background touches the top-left background only diagonally. With foreground FOUR, background EIGHT connects them, so the center is exterior. With foreground EIGHT, background FOUR separates them, so the center is an enclosed one-pixel hole.

Hole filling labels the complement with the dual adjacency. Any complement region touching an image border connects to the virtual exterior background frame. All other regions are enclosed. Only those enclosed regions satisfying the inclusive area cap are changed. A one-pixel frame suffices even at corners; under either adjacency it is connected and contains background adjacent to every image-border background pixel.

Choosing dual connectivity avoids implicitly assigning both foreground and background the same diagonal behavior. The API does not guess which interpretation the caller intended.

## Exact distance from the minimum definition

For each pixel center p, squared distance is `min_q ((px-qx)^2 + (py-qy)^2)` over real background and the virtual exterior frame. Initialize padded background to zero and foreground to an internal infinity sentinel. Apply the separable one-dimensional transform:

`D(x) = min_q [f(q) + (x-q)^2]`.

The implementation was written from this mathematical contract, not copied from linked implementation code. It builds an integer lower envelope of finite parabolas in increasing site order. For sites r < q, the first integer x where q is strictly better than r is:

`floorDiv(f(q)+q*q-f(r)-r*r, 2*(q-r)) + 1`.

This formula uses floor division even for negative numerators. Earlier sites win exact ties. Envelope starts are increasing; a new site removes predecessors whose winning interval disappears. Each site is added once and removed at most once. Evaluation walks integer x and the start intervals once. Row and column transforms therefore take O(N + width + height) time on the padded grid.

Infinity sites are skipped, never added to arithmetic. Every padded row has finite frame sites and the column pass is finite everywhere. All sums, products and differences in the envelope use checked long operations. The result contains only finite exact integers. No floating intersections, rounded square roots, Manhattan approximation or uncertain tie epsilon is used.

A solid 3×3 illustrates the frame: the boundary is one pixel center away from frame background; the center is two centers away, yielding 4. A diagonal offset `(1,1)` from a real background pixel yields 2, not Manhattan-squared 4.

## PNG is an adapter, not an image-processing framework

Compressed input is staged with a strict cap and a one-byte excess probe. Signature, dimensions, pixel budget, scratch plan and raw model are checked before decoding. Thresholding reads raster samples, because color-converted `getRGB` can change the meaning of an 8-bit gray threshold. Alpha, palette, RGB, low-bit-depth and 16-bit types are rejected. The decoded model is checked too. Reader metadata is ignored and no metadata is exposed by the API.

Memory image streams are constructed explicitly instead of changing global ImageIO cache configuration. Caller streams remain caller-owned. Encoding emits only binary-valued grayscale samples. Codec internals, malformed-file validation details and resource consumption are still partly JDK behavior. A compressed cap is useful but does not make ImageIO a hardened hostile-file sandbox or enforce a decode deadline.

## Independent verification

The test oracle uses queue-based BFS, not union-find, for both foreground components and dual-background floods. Its distance oracle enumerates every real background and every frame coordinate and directly compares squared distances. It never calls the production envelope or labels to derive expected answers.

All 512 3×3 masks are compared under both topology choices for exact label arrays, areas, boxes, retention and hole filling for thresholds 0 through 10, and distances. Additional deterministic shapes cover skinny images, empty foreground, solid masks, internal/frame ties and a non-Manhattan diagonal result. Ownership tests mutate input and exported buffers. Adversarial tests exercise dimension precedence, checked accounting overflow, low scratch budgets, rejected PNG models, raw threshold boundaries, truncated input, IO errors and report flush errors.

The demo really writes and rereads synthetic temporary PNGs. Its textual and JSON reports describe asserted numbers, not visual inspection. Test reports are expected output, not claims of a measured deployment or certified processing pipeline.

## Sources and scope

API and mathematical references:

- [JDK BitSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/BitSet.html): mutable backing state motivates bounded detached copies.
- [JDK ImageReader](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/imageio/ImageReader.html): dimension/raw-type preflight and metadata-ignore controls.
- [Brown distance-transform page](https://cs.brown.edu/people/pfelzens/dt/index.html): mathematical distance-transform definition; linked implementation code was not used or copied.

All project implementation and synthetic tests are original. No private client code, personal records or external image fixtures are included. This library does not interpret clinical data, recognize objects, generate vector contours or certify physical measurements.

## Projeto e topologia em português

A biblioteca mantém máscaras imutáveis e valida limites antes dos buffers de operação. BitSet é mutável; por isso a entrada é copiada por posições válidas e nunca por clonagem cega de capacidade. Acessores também devolvem cópias independentes. IO fica no adaptador PNG e na demonstração sintética, não no núcleo.

A rotulagem de produção usa union-find com compressão de caminhos e união por rank. Depois, uma passagem por linhas atribui rótulos públicos estáveis, sem depender dos identificadores das raízes. Áreas e caixas semiabertas ficam em arrays primitivos. Os testes fazem o contrário metodologicamente: BFS com fila, sem union-find.

Nos desenhos acima, três pixels em diagonal podem ser três componentes FOUR ou um EIGHT. No segundo desenho, o fundo central alcança o canto por diagonal somente com fundo EIGHT. O preenchimento impõe conectividade dual: primeiro plano FOUR/fundo EIGHT, ou primeiro plano EIGHT/fundo FOUR. Qualquer região de fundo ligada à borda alcança a moldura virtual externa; somente as demais podem ser preenchidas. Isso torna a decisão topológica explícita.

A distância usa a definição de mínimo de quadrados euclidianos, com uma moldura externa de um pixel. O algoritmo separa linhas e colunas e constrói envelope inferior inteiro de parábolas. O início de vitória de um novo sítio usa divisão inteira para baixo mais um; o sítio anterior vence empates exatos. Infinito é apenas sentinela interna, não entra nas somas nem na saída. Aritmética é verificada, sem ponto flutuante ou aproximação Manhattan. O custo é linear no número de pixels preenchidos pela moldura.

O adaptador PNG aplica limiar às amostras brutas do raster, não a cores convertidas por gama. Verifica tamanho e modelo antes de decodificar, ignora metadados para a API e usa streams de imagem em memória explícitos. Ainda depende do codec JDK para detalhes de arquivos malformados; não é sandbox nem validador integral. O orçamento de buffers não limita heap total ou tempo de execução.

Os oráculos independentes cobrem as 512 máscaras 3×3 em ambas as topologias e procuram o fundo mais próximo por força bruta, incluindo coordenadas externas. Casos adicionais verificam imagens estreitas, empates, entradas vazias de primeiro plano, modelos PNG rejeitados, limites e falhas de IO. A demonstração compara números após gravação e releitura sintéticas, sem exigir inspeção visual.
