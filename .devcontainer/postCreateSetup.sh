#!/bin/bash
set -e

# Set Android SDK root
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

# Install required SDK packages
sdkmanager --sdk_root=$ANDROID_SDK_ROOT --install "platform-tools" "platforms;android-33" "build-tools;33.0.2" "ndk;25.1.8937393"

# Accept all licenses
yes | sdkmanager --licenses --sdk_root=$ANDROID_SDK_ROOT

# Create local.properties for Gradle
if [ ! -f "$PWD/local.properties" ]; then
  echo "sdk.dir=$ANDROID_SDK_ROOT" > "$PWD/local.properties"
fi
