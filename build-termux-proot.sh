#!/usr/bin/env bash
set -euo pipefail

export ANDROID_HOME=${ANDROID_HOME:-$HOME/android-sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

if ! command -v java >/dev/null; then
  echo "Java not found. Install: apt install -y openjdk-17-jdk"
  exit 1
fi

if ! command -v sdkmanager >/dev/null; then
  echo "sdkmanager not found. Install Android commandline-tools first. See README.md"
  exit 1
fi

yes | sdkmanager --licenses >/dev/null
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

./gradlew assembleDebug
