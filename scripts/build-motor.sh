#!/bin/sh
# Genera el motor PKHeX que la app lleva dentro (android/app/src/main/assets/motor/, no se
# versiona). Requisitos: Node 20+. Uso: scripts/build-motor.sh
set -eu
cd "$(dirname "$0")/../engine-bridge"
npm ci --no-audit --no-fund 2>/dev/null || npm install --no-audit --no-fund
npm run build
