# KeyApp

Minimal Android custom keyboard / IME skeleton.

## Features

- English QWERTY raw input
- Japanese 12-key flick input skeleton
- Clipboard paste panel
- Resize toggle skeleton
- Simple Apple-like rounded translucent visual
- Java-only implementation for easier CLI builds

## Build in Termux proot Ubuntu

```bash
apt update
apt install -y openjdk-17-jdk git wget unzip zip curl
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -O cmdline-tools.zip
unzip cmdline-tools.zip
mv cmdline-tools latest
cat >> ~/.bashrc <<'ENV'
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
ENV
source ~/.bashrc
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
cd keyapp
gradle assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Enable keyboard

Android Settings -> System -> Keyboard -> On-screen keyboard -> Manage keyboards -> KeyApp Keyboard
