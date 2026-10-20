#!/bin/sh
# Baja el código de mGBA 0.10.5 (MPL-2.0) a third_party/mgba/, que no se versiona. El módulo
# android/emulador lo compila con el NDK como librería estática (ver su CMakeLists.txt).
# El archivo va fijado por SHA-256: si GitHub sirve otra cosa, no se usa.
# Uso: scripts/bajar-mgba.sh
set -eu
cd "$(dirname "$0")/.."
version="0.10.5"
sha256="91d6fbd32abcbdf030d58d3f562de25ebbc9d56040d513ff8e5c19bee9dacf14"
destino="third_party/mgba"
if [ -f "$destino/version.cmake" ] && grep -q "LIB_VERSION_PATCH 5" "$destino/version.cmake"; then
	echo "mGBA $version ya está en $destino"
	exit 0
fi
archivo=$(mktemp)
trap 'rm -f "$archivo"' EXIT
curl -sSfL -m 300 -o "$archivo" "https://github.com/mgba-emu/mgba/archive/refs/tags/$version.tar.gz"
calculado=$(shasum -a 256 "$archivo" | cut -d' ' -f1)
if [ "$calculado" != "$sha256" ]; then
	echo "el archivo de mGBA no coincide: $calculado (se esperaba $sha256)" >&2
	exit 1
fi
rm -rf "$destino"
mkdir -p "$destino"
tar -xzf "$archivo" -C "$destino" --strip-components 1
echo "mGBA $version en $destino"
