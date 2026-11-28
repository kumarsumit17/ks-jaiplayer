#!/usr/bin/env bash
# Builds FFmpeg's AC-3 and E-AC-3 decoders (with libavutil and libswresample) as static libraries
# for the Android ABIs given, for the JNI wrapper in src/main/cpp (specs/licences.md, "FFmpeg").
# Downloads the pinned release and checks its SHA-256 first. LGPL 2.1 build: no --enable-gpl,
# no --enable-nonfree. Based on Media3's decoder_ffmpeg/src/main/jni/build_ffmpeg.sh (Apache 2.0).
#
#   build-ffmpeg.sh <ndk dir> <download dir> <output dir> <min sdk> <abi>...
set -euo pipefail
NDK="$1"; DOWNLOADS="$2"; OUT="$3"; API="$4"; shift 4; ABIS=("$@")
VERSION="6.0.1"
SHA256="9b16b8731d78e596b4be0d720428ca42df642bb2d78342881ff7f5bc29fc9623"
URL="https://ffmpeg.org/releases/ffmpeg-$VERSION.tar.xz"
DECODERS=(ac3 eac3)

mkdir -p "$DOWNLOADS"
TARBALL="$DOWNLOADS/ffmpeg-$VERSION.tar.xz"
sum() { if command -v sha256sum > /dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
if [[ ! -f "$TARBALL" || "$(sum "$TARBALL")" != "$SHA256" ]]; then
    curl -fsSL --retry 5 -o "$TARBALL.part" "$URL"
    [[ "$(sum "$TARBALL.part")" == "$SHA256" ]] || { echo "FFmpeg download: wrong SHA-256" >&2; exit 1; }
    mv "$TARBALL.part" "$TARBALL"
fi

SRC="$OUT/src"
rm -rf "$SRC"; mkdir -p "$SRC"
tar -xJf "$TARBALL" -C "$SRC" --strip-components=1

HOST="$(uname -s | tr '[:upper:]' '[:lower:]')-x86_64" # The NDK ships x86_64 host tools; Apple chips run them.
TOOLS="$NDK/toolchains/llvm/prebuilt/$HOST/bin"
[[ -d "$TOOLS" ]] || { echo "No NDK toolchain at $TOOLS" >&2; exit 1; }
JOBS="$(nproc 2> /dev/null || sysctl -n hw.ncpu 2> /dev/null || echo 4)"
COMMON=(--target-os=android --enable-static --disable-shared --disable-doc --disable-programs
    --disable-everything --disable-avdevice --disable-avformat --disable-swscale --disable-postproc
    --disable-avfilter --disable-symver --enable-swresample --disable-v4l2-m2m --disable-vulkan
    --disable-network --disable-iconv --disable-zlib --disable-bzlib --disable-lzma --disable-debug
    --enable-small --nm="$TOOLS/llvm-nm" --ar="$TOOLS/llvm-ar" --ranlib="$TOOLS/llvm-ranlib"
    --strip="$TOOLS/llvm-strip")
for d in "${DECODERS[@]}"; do COMMON+=("--enable-decoder=$d"); done

cd "$SRC"
for abi in "${ABIS[@]}"; do
    case "$abi" in
        arm64-v8a) ARGS=(--arch=aarch64 --cpu=armv8-a --cross-prefix="$TOOLS/aarch64-linux-android$API-") ;;
        armeabi-v7a) ARGS=(--arch=arm --cpu=armv7-a --cross-prefix="$TOOLS/armv7a-linux-androideabi$API-"
            --extra-cflags="-march=armv7-a -mfloat-abi=softfp" --extra-ldflags="-Wl,--fix-cortex-a8") ;;
        x86_64) ARGS=(--arch=x86_64 --cpu=x86-64 --cross-prefix="$TOOLS/x86_64-linux-android$API-" --disable-asm) ;;
        *) echo "Unsupported ABI $abi" >&2; exit 1 ;;
    esac
    ./configure --prefix="$OUT/$abi" "${ARGS[@]}" "${COMMON[@]}" > "$OUT/configure-$abi.log"
    make -j"$JOBS" > "$OUT/make-$abi.log"
    make install-libs install-headers > /dev/null
    make distclean > /dev/null
done
cp COPYING.LGPLv2.1 "$OUT/COPYING.LGPLv2.1"
echo "$VERSION" > "$OUT/VERSION"
