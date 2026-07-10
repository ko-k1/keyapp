#!/usr/bin/env bash
set -euo pipefail

revision=7f1973ba0ee6c94483253ecfa70ff33dd084d5c3
root=$(git -C "$(dirname "${BASH_SOURCE[0]}")/.." rev-parse --show-toplevel)
work=${MOZC_BUILD_DIR:-"${TMPDIR:-/tmp}/keyapp-mozc-build"}

if [[ $(uname -s) != Linux || $(uname -m) != x86_64 ]]; then
  echo "Mozc Android prebuilts require an x86_64 Linux host (NDK r29 has x86_64 host tools)." >&2
  exit 1
fi
for command in git python3 bazelisk unzip sha256sum readelf nm; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done
python3 -c 'import sys; assert sys.version_info >= (3, 12)' ||
  { echo "Python 3.12 or newer is required." >&2; exit 1; }

if [[ ! -d "$work/.git" ]]; then
  git clone --filter=blob:none https://github.com/google/mozc.git "$work"
fi
git -C "$work" fetch --depth=1 origin "$revision"
[[ -z $(git -C "$work" status --porcelain) ]] ||
  { echo "Mozc build checkout has local changes: $work" >&2; exit 1; }
git -C "$work" checkout --detach "$revision"

cd "$work/src"
python3 build_tools/update_deps.py
bazelisk build package //data_manager/oss:mozc.data --config oss_android --config release_build

stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
unzip -q bazel-bin/android/jni/native_libs.zip -d "$stage/native"
arm64="$stage/native/libs/arm64-v8a/libmozc.so"
x86_64="$stage/native/libs/x86_64/libmozc.so"
data=bazel-bin/data_manager/oss/mozc.data
[[ -s "$arm64" && -s "$x86_64" && -s "$data" ]] ||
  { echo "Mozc build outputs are incomplete." >&2; exit 1; }
readelf -h "$arm64" | grep -q 'AArch64'
readelf -h "$x86_64" | grep -Eq 'X86-64|Advanced Micro Devices X86-64'
[[ $(readelf -lW "$arm64" | awk '$1 == "LOAD" { print $NF }' | sort -u) == 0x4000 ]]
[[ $(readelf -lW "$x86_64" | awk '$1 == "LOAD" { print $NF }' | sort -u) == 0x4000 ]]
nm -D "$arm64" | grep -q Java_com_google_android_apps_inputmethod_libs_mozc_session_MozcJNI_initialize
nm -D "$x86_64" | grep -q Java_com_google_android_apps_inputmethod_libs_mozc_session_MozcJNI_initialize

version_file="$stage/data-version"
python3 data_manager/gen_data_version.py --tag=oss --mozc_version_template=version.bzl --output="$version_file"
version=$(<"$version_file")
[[ "$version" =~ ^[0-9]+\.[0-9]+\.oss$ ]] ||
  { echo "Unexpected Mozc data version: $version" >&2; exit 1; }

install -Dm755 "$arm64" "$root/app/src/main/jniLibs/arm64-v8a/libmozc.so"
install -Dm755 "$x86_64" "$root/app/src/debug/jniLibs/x86_64/libmozc.so"
install -Dm644 "$data" "$root/app/src/main/assets/mozc/mozc.data"
data_hash=$(sha256sum "$data" | awk '{print $1}')
arm64_hash=$(sha256sum "$arm64" | awk '{print $1}')
x86_64_hash=$(sha256sum "$x86_64" | awk '{print $1}')

manifest="$root/app/src/main/assets/mozc/manifest.properties"
{
  echo "mozc.revision=$revision"
  echo "bazel.version=9.0.2"
  echo "ndk.version=r29"
  echo "data.version=$version"
  echo "data.sha256=$data_hash"
  echo "arm64-v8a.sha256=$arm64_hash"
  echo "x86_64.sha256=$x86_64_hash"
} > "$manifest"

sha256sum -c <(printf '%s  %s\n' "$data_hash" "$root/app/src/main/assets/mozc/mozc.data")
sha256sum -c <(printf '%s  %s\n' "$arm64_hash" "$root/app/src/main/jniLibs/arm64-v8a/libmozc.so")
sha256sum -c <(printf '%s  %s\n' "$x86_64_hash" "$root/app/src/debug/jniLibs/x86_64/libmozc.so")
echo "Installed Mozc $revision data $version prebuilts."
