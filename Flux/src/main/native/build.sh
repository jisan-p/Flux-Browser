#!/bin/sh
set -eu
jdk="$1"
arch="$3"
out="$2/native/$arch"
# Match the running JVM even when building under Rosetta.
if [ "$arch" = aarch64 ]; then arch=arm64; fi
mkdir -p "$out"
xcrun clang -fobjc-arc -fblocks -dynamiclib -O2 -Wall -Wextra -Wno-unused-parameter \
  -arch "$arch" -mmacosx-version-min=12.0 -I"$jdk/include" -I"$jdk/include/darwin" \
  -framework Cocoa -framework WebKit \
  "$(dirname "$0")/FluxWebKit.m" -o "$out/libfluxwebkit.dylib"
