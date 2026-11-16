#!/usr/bin/env bash
set -e

# Path to keystore properties file
KEYSTORE_PROPS="${WISHY_KEYSTORE_PROPS:-$HOME/.wishy-keys/keystore.properties}"
KEYSTORE_PROPS="${KEYSTORE_PROPS/#\~/$HOME}"

if [ ! -f "$KEYSTORE_PROPS" ]; then
    echo "Error: Keystore properties file not found at $KEYSTORE_PROPS" >&2
    echo "Please create $KEYSTORE_PROPS with storeFile, storePassword, keyAlias, and keyPassword." >&2
    exit 1
fi

echo "Keystore properties file found at $KEYSTORE_PROPS"

# Run Gradle assembleRelease
echo "Building release APK with Gradle..."
./gradlew :app:assembleRelease

# Extract versionName from app/build.gradle.kts
VERSION_NAME=$(grep -E 'versionName\s*=\s*' app/build.gradle.kts | sed -E 's/.*versionName\s*=\s*"([^"]+)".*/\1/')
if [ -z "$VERSION_NAME" ]; then
    echo "Error: Could not determine versionName from app/build.gradle.kts" >&2
    exit 1
fi

RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$RELEASE_APK" ]; then
    echo "Error: Release APK file not found at $RELEASE_APK" >&2
    exit 1
fi

OUT_APK="WishyLauncher-v${VERSION_NAME}.apk"
cp "$RELEASE_APK" "$OUT_APK"
echo "Copied release APK to $OUT_APK"

# Find apksigner
APKSIGNER_BIN=$(which apksigner 2>/dev/null || true)
if [ -z "$APKSIGNER_BIN" ]; then
    SDK_PATH="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
    if [ -d "$SDK_PATH/build-tools" ]; then
        APKSIGNER_BIN=$(find "$SDK_PATH/build-tools" -name apksigner 2>/dev/null | sort -V | tail -n 1 || true)
    fi
fi

if [ -z "$APKSIGNER_BIN" ] || [ ! -x "$APKSIGNER_BIN" ]; then
    echo "Error: apksigner tool not found in PATH or Android SDK." >&2
    exit 1
fi

echo "Verifying APK signing certificate..."
"$APKSIGNER_BIN" verify --print-certs "$OUT_APK"

echo "Release build and verification complete: $OUT_APK"
