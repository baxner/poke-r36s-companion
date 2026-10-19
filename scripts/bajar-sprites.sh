#!/bin/sh
# Baja los sprites de la 3.ª generación (386 especies: normal, shiny e ícono de menú) del
# repositorio público de PokeAPI a android/app/src/main/assets/sprites/ (no se versiona: son
# de Nintendo / Game Freak, para uso personal). Uso: scripts/bajar-sprites.sh
set -eu
cd "$(dirname "$0")/.."
base="https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon"
destino="android/app/src/main/assets/sprites"
mkdir -p "$destino/normal" "$destino/shiny" "$destino/icono"
lista=$(mktemp)
trap 'rm -f "$lista"' EXIT
for n in $(seq 1 386); do
	[ -s "$destino/normal/$n.png" ] || echo "$base/versions/generation-iii/emerald/$n.png $destino/normal/$n.png" >> "$lista"
	[ -s "$destino/shiny/$n.png" ] || echo "$base/versions/generation-iii/emerald/shiny/$n.png $destino/shiny/$n.png" >> "$lista"
	[ -s "$destino/icono/$n.png" ] || echo "$base/versions/generation-vii/icons/$n.png $destino/icono/$n.png" >> "$lista"
done
# 8 descargas a la vez; curl -f para que un 404 no deje un archivo vacío.
xargs -P 8 -n 2 sh -c 'curl -sSf -m 30 -o "$2" "$1" || { echo "falló: $1" >&2; rm -f "$2"; }' _ < "$lista"
echo "sprites: $(ls "$destino/normal" | wc -l | tr -d ' ') normales, $(ls "$destino/shiny" | wc -l | tr -d ' ') shiny, $(ls "$destino/icono" | wc -l | tr -d ' ') íconos en $destino"
