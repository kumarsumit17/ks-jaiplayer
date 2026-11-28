#!/bin/sh
# Feasibility trial only: builds the native video layer helper into desktop/mac/build/trial.
set -e
cd "$(dirname "$0")"
out=../build/trial
mkdir -p "$out"
xcrun clang -fobjc-arc -O2 -dynamiclib -arch arm64 -arch x86_64 -mmacosx-version-min=12.0 \
  -framework AppKit -framework AVFoundation -framework CoreMedia -framework CoreVideo -framework QuartzCore -framework Metal \
  -o "$out/libjaivideolayer.dylib" JaiVideoLayer.m
echo "$out/libjaivideolayer.dylib"
