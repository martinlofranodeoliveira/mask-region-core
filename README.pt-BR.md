# Mask Region Core

Versão de desenvolvimento **0.1.0** — software original de código aberto da **Lofrano Tecnologia**. Biblioteca Java com limites explícitos para componentes conexos, retenção por área, preenchimento de buracos com conectividade dual e distância euclidiana quadrática exata ao fundo. Sem bibliotecas nativas, dependências externas, modelos, serviços ou telemetria.

- [English](README.md)
- [Contrato preciso, incluindo API em português](docs/contract.md)
- [Projeto e topologia em inglês e português](docs/design.md)
- [Página oficial / contato geral](https://lofranotecnologia.com.br)
- [Repositório](https://github.com/martinlofranodeoliveira/mask-region-core)

## Compilar, testar e demonstrar

Requisitos: JDK 21 ou superior, com `javac` e módulo `java.desktop`; Bash para os scripts. Não é necessário Gradle, Maven ou download de dependências. No diretório do projeto:

```sh
bash scripts/compile.sh
bash scripts/test.sh
bash scripts/demo.sh
bash scripts/demo.sh --json
```

A compilação usa `--release 21 -encoding UTF-8 -Xlint:all -Werror`. As classes ficam em `build/classes`, e os testes em `build/test-classes`. Para integrar localmente, use `build/classes` no classpath de compilação e execução. O pacote é `br.com.lofranotecnologia.maskregion`.

Saída esperada dos testes:

```text
PASS core: exhaustive 512 masks x 2 connectivities; BFS and brute-force oracles
PASS PNG: raw samples, model rejection, limits, IO failures, synthetic demo
PASS all tests
```

Saída padrão da demonstração:

```text
PASS components=1 filled=1 center_d2=4
```

Com `--json`, há uma única linha legível por máquina:

```json
{"status":"PASS","components":1,"filled":1,"center_d2":4}
```

A demonstração grava um anel sintético 3×3 em PNG temporário, relê a imagem, preenche o centro, grava e relê a máscara preenchida e verifica os resultados numéricos. Remove somente seus dois arquivos e seu diretório temporário. Não exige visualizador, não abre imagens externas e não preserva arquivos. Falhas de argumento, IO, limpeza ou descarga da saída geram código de saída diferente de zero e diagnóstico fixo, sem caminhos ou pixels. Esse teste de ida e volta não é inspeção visual nem QA de navegador.

## Exemplo pequeno

```java
import br.com.lofranotecnologia.maskregion.BitGrid;
import br.com.lofranotecnologia.maskregion.Connectivity;
import br.com.lofranotecnologia.maskregion.Labeling;
import br.com.lofranotecnologia.maskregion.MaskOps;

BitGrid anel = BitGrid.fromBytes(3, 3, new byte[] {
    1, 1, 1,
    1, 0, 1,
    1, 1, 1
});
MaskOps.HoleFill resultado = MaskOps.fillHoles(anel, Connectivity.FOUR, 1);
Labeling rotulos = MaskOps.labelComponents(resultado.grid(), Connectivity.FOUR);
long centroD2 = MaskOps.squaredDistanceToBackground(resultado.grid()).get(1, 1);
// filledPixels() = 1; component(1).area() = 9; centroD2 = 4.
// A máscara anel continua com centro de fundo.
```

`retainByArea` mantém componentes com área **>= minArea**; `fillHoles` preenche regiões de fundo fechadas com área **<= maxHoleArea**. Os resultados incluem máscara imutável nova e contagens. Rótulos positivos seguem o primeiro pixel de cada componente em ordem de linhas; zero indica fundo. Caixas são semiabertas: mínimo incluído e máximo excluído. Entradas e acessores de arrays e BitSet são cópias independentes.

O adaptador `PngAdapter.read(input, 128)` aceita somente PNG cinza opaco de 8 bits; o chamador fecha o stream. O primeiro plano corresponde a **amostra bruta do raster >= limiar**, sem conversão gama por `getRGB`. `PngAdapter.write(grid, output)` grava 0/255 em cinza de 8 bits e descarrega a saída, sem fechar o stream. Alfa gera `UnsupportedAlpha`; paleta, RGB e 16 bits são recusados, sem conversão silenciosa.

## Topologia e decisões de projeto

Três pixels em diagonal formam três componentes com primeiro plano FOUR e um com EIGHT. O preenchimento sempre usa fundo dual: EIGHT para primeiro plano FOUR, ou FOUR para primeiro plano EIGHT. Portanto um contato diagonal pode permitir que o fundo central alcance o exterior em um caso, mas não no outro. A biblioteca não esconde essa escolha.

Uma moldura virtual de um pixel fora da imagem é fundo, tanto para detecção do exterior quanto para distância. Uma máscara 3×3 sólida tem distância quadrática 1 na borda e 4 no centro. São unidades quadráticas de centros de pixels, não medidas físicas. A distância é euclidiana exata, independente da conectividade.

Union-find agrupa componentes e uma passagem posterior estabiliza os rótulos. A distância usa envelope inferior inteiro de parábolas, com aritmética verificada e empates definidos. Os testes independentes usam BFS e busca exaustiva pelo fundo mais próximo. Todas as 512 máscaras 3×3 são verificadas nas duas topologias, com limiares de área de 0 a 10, além de casos estreitos, vazios, sólidos, empates, mutação e falhas de IO.

Os limites padrão são 4.096 por dimensão, 1.048.576 pixels e 64 MiB de orçamento conservador de buffers lógicos do algoritmo. É possível reduzir os limites. Dimensões e orçamento são validados antes dos arrays de operação; a leitura comprimida do PNG necessariamente precede a inspeção do cabeçalho. Consulte as fórmulas no contrato.

## Limitações

Esta é uma versão de desenvolvimento, sem afirmação de prontidão para produção ou conformidade ampla com padrões. Somente um subconjunto de PNG é implementado. ImageIO **não é** uma barreira endurecida contra imagens hostis. O orçamento exclui overhead de objetos da JVM e estruturas internas do codec ImageIO; não é limite de heap do processo nem de tempo de decodificação. A entrada comprimida é limitada a 4 MiB; streams bloqueantes exigem controle de tempo/cancelamento pelo chamador. Falhas normais de alocação da JVM ainda são possíveis.

Não há interpretação clínica, reconhecimento de objetos, contornos vetoriais, interface gráfica, certificação de processamento ou medição física. Todas as amostras são sintéticas. Não há alegações de usuários, implantação ou desempenho medido.

Licença MIT, copyright 2026 Lofrano Tecnologia. Veja [segurança](SECURITY.md) e [contribuições](CONTRIBUTING.md).
