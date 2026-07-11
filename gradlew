#!/usr/bin/env sh
set -eu

# Lightweight repo-local Gradle launcher. If an official Gradle wrapper JAR is
# later generated with `gradle wrapper`, this script can be replaced by the
# standard wrapper. Until then it keeps CI/dev builds reproducible enough for
# environments without a system Gradle installation.

GRADLE_VERSION="9.1.0"
ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
CACHE_DIR="$ROOT_DIR/.gradle-wrapper-cache"
DIST_DIR="$CACHE_DIR/gradle-$GRADLE_VERSION"
ZIP_FILE="$CACHE_DIR/gradle-$GRADLE_VERSION-bin.zip"
GRADLE_BIN="$DIST_DIR/bin/gradle"

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$CACHE_DIR"
  if [ ! -f "$ZIP_FILE" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..." >&2
    if command -v curl >/dev/null 2>&1; then
      curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP_FILE"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP_FILE" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    else
      echo "Error: neither gradle, curl, nor wget is available." >&2
      exit 1
    fi
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$ZIP_FILE" -d "$CACHE_DIR"
  else
    echo "Error: unzip is required to unpack Gradle." >&2
    exit 1
  fi
fi

exec "$GRADLE_BIN" "$@"
