#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
mkdir -p build/classes build/test-classes
javac --release 21 -encoding UTF-8 -Xlint:all -Werror \
  -d build/classes src/main/java/br/com/lofranotecnologia/maskregion/*.java
javac --release 21 -encoding UTF-8 -Xlint:all -Werror \
  -cp build/classes -d build/test-classes \
  src/test/java/br/com/lofranotecnologia/maskregion/*.java
