#!/usr/bin/env bash
# Installs the Android SDK pieces needed to build 澜音 into $ANDROID_HOME
# (default: ~/android-sdk) and writes local.properties. Safe to re-run.
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-9862592_latest.zip"
PLATFORM="platforms;android-36"
BUILD_TOOLS="build-tools;36.0.0"

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

if [[ ! -x "$sdkmanager" ]]; then
  echo "Downloading Android command-line tools..."
  tmp="$(mktemp -d)"
  curl -fsSL "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" -o "$tmp/tools.zip"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi

# `yes` dies of SIGPIPE once sdkmanager stops reading, which pipefail would treat as failure.
(yes || true) | "$sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/dev/null
"$sdkmanager" --sdk_root="$ANDROID_HOME" "platform-tools" "$PLATFORM" "$BUILD_TOOLS" >/dev/null

echo "sdk.dir=$ANDROID_HOME" > "$project_dir/local.properties"

# Optional: MAVEN_MIRROR=google routes Maven Central through Google's mirror for this machine only,
# for environments where repo.maven.apache.org rate-limits (HTTP 429). The project files stay untouched.
if [[ "${MAVEN_MIRROR:-}" == "google" ]]; then
  mkdir -p "$HOME/.gradle/init.d"
  cat > "$HOME/.gradle/init.d/maven-central-mirror.gradle.kts" <<'EOF'
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
fun RepositoryHandler.preferMirror() {
    maven(mirror) { name = "MavenCentralMirror" }
}
beforeSettings {
    pluginManagement.repositories.preferMirror()
    dependencyResolutionManagement.repositories.preferMirror()
}
EOF
  echo "Maven Central mirror enabled in ~/.gradle/init.d"
fi
echo "Android SDK ready at $ANDROID_HOME"
