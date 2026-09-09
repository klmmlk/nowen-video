#!/usr/bin/env bash
# 重建 media3-decoder-av1-1.5.1.aar：在 androidx/media 1.5.1 tag 上应用本仓库维护的
# C++ 补丁（脚本/媒体 3 解码器 av1 补丁/），输出到指定目录。
#
# 用法：
#   ./scripts/build-media3-decoder-av1.sh <output-dir> <android-sdk-path> <ndk-version>
#
# 例：
#   ./scripts/build-media3-decoder-av1.sh /tmp/aar-out "$ANDROID_HOME" 25.1.8937393
set -euo pipefail

OUT_DIR="${1:?missing output dir}"
SDK_DIR="${2:?missing sdk dir}"
NDK_VERSION="${3:?missing ndk version}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PATCH_DIR="$REPO_ROOT/scripts/media3-decoder-av1-patches"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# 解析为相对于调用方 cwd 的绝对路径；脚本内部会 cd 到临时目录，
# 相对 OUT_DIR 会在那里生效导致产物拷错位置（CI 已验证踩到）。
mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

# Git Bash (Windows) 传入的 /d/AndroidSDK 风格路径要转成 Gradle 认识的
# Windows 路径；native Windows 路径 (D:/...) 与 Linux CI 的绝对路径原样保留。
if command -v cygpath >/dev/null 2>&1; then
  SDK_DIR="$(cygpath -m "$SDK_DIR")"
fi

echo "[media3] cloning androidx/media @ 1.5.1 ..."
git clone --depth 1 --branch 1.5.1 \
  https://github.com/androidx/media.git "$WORK/media3"

cd "$WORK/media3/libraries/decoder_av1/src/main/jni"
echo "[media3] cloning cpu_features v0.9.0 ..."
git clone --depth 1 --branch v0.9.0 \
  https://github.com/google/cpu_features.git
echo "[media3] cloning libgav1 v0.19.0 ..."
git clone --depth 1 --branch v0.19.0 \
  https://chromium.googlesource.com/codecs/libgav1 libgav1
cd libgav1
echo "[media3] cloning abseil-cpp 20240116.2 ..."
git clone --depth 1 --branch 20240116.2 \
  https://github.com/abseil/abseil-cpp.git third_party/abseil-cpp
cd "$WORK/media3"

echo "[media3] applying C++ patches ..."
git apply \
  "$PATCH_DIR/gav1_jni.cc.patch" \
  "$PATCH_DIR/CMakeLists.txt.patch"

echo "[media3] writing local.properties ..."
mkdir -p "$OUT_DIR"
cat > local.properties <<EOF
sdk.dir=$SDK_DIR
ndk.dir=$SDK_DIR/ndk/$NDK_VERSION
EOF

echo "[media3] running gradle :lib-decoder-av1:assembleRelease ..."
cd "$WORK/media3"
./gradlew :lib-decoder-av1:assembleRelease --no-daemon --console=plain

cp -v libraries/decoder_av1/buildout/outputs/aar/lib-decoder-av1-release.aar \
  "$OUT_DIR/media3-decoder-av1-1.5.1.aar"

echo "[media3] done: $OUT_DIR/media3-decoder-av1-1.5.1.aar"
