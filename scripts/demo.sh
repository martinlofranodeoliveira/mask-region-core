#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
bash scripts/compile.sh
java -ea -Djava.awt.headless=true -cp build/classes \
  br.com.lofranotecnologia.maskregion.Demo "$@"
