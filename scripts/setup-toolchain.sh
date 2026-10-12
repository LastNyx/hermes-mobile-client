#!/usr/bin/env bash
# Portable build toolchain for hermes-mobile-client: JDK 17 (Temurin) + Android SDK
# (cmdline-tools, platform-tools, platform 35, build-tools 35.0.0). No admin, no PATH edits.
#
#   scripts/setup-toolchain.sh            # Windows (git-bash) or Linux (Arch etc.), x64
#
# Installs into $TOOLS_DIR (default: C:/home/tools on Windows, ~/tools on Linux), then writes
# android/local.properties (sdk.dir) and prints the JAVA_HOME to use. Re-running is a no-op
# for whatever is already installed.
set -euo pipefail
cd "$(dirname "$0")/.."
REPO="$(pwd)"

CMDLINE_TOOLS_BUILD="${CMDLINE_TOOLS_BUILD:-13114758}"
PLATFORM="platforms;android-35"
BUILD_TOOLS="build-tools;35.0.0"
BUILD_TOOLS_AGP="build-tools;34.0.0"   # AGP 8.7 asks for this one itself; fetch it up front

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) OS=windows; TOOLS_DIR="${TOOLS_DIR:-/c/home/tools}"; EXE=".exe"; BAT=".bat" ;;
  Linux)                OS=linux;   TOOLS_DIR="${TOOLS_DIR:-$HOME/tools}";  EXE="";     BAT="" ;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac
mkdir -p "$TOOLS_DIR"
JDK_DIR="$TOOLS_DIR/jdk-17"
SDK_DIR="$TOOLS_DIR/android-sdk"

# Scratch inside TOOLS_DIR; native Windows curl cannot write MSYS paths like /tmp, so give it a
# mixed (C:/...) path there.
mktmp() { local d="$TOOLS_DIR/.tmp-$$-$RANDOM"; mkdir -p "$d"; echo "$d"; }
npath() { if [ "$OS" = windows ]; then cygpath -m "$1"; else echo "$1"; fi; }
fetch() { curl -fsSL -o "$(npath "$1")" "$2"; }

need() { command -v "$1" >/dev/null || { echo "missing: $1 (install it first)" >&2; exit 1; }; }
need curl; need unzip
[ "$OS" = linux ] && need tar

# --- JDK 17 ---------------------------------------------------------------------------
if [ ! -x "$JDK_DIR/bin/java$EXE" ]; then
  echo "== JDK 17 -> $JDK_DIR"
  tmp="$(mktmp)"
  url="https://api.adoptium.net/v3/binary/latest/17/ga/$OS/x64/jdk/hotspot/normal/eclipse"
  if [ "$OS" = windows ]; then
    fetch "$tmp/jdk.zip" "$url"; unzip -q "$tmp/jdk.zip" -d "$tmp/x"
  else
    fetch "$tmp/jdk.tgz" "$url"; mkdir -p "$tmp/x"; tar -xzf "$tmp/jdk.tgz" -C "$tmp/x"
  fi
  rm -rf "$JDK_DIR"; mv "$tmp"/x/jdk-17* "$JDK_DIR"; rm -rf "$tmp"
fi
"$JDK_DIR/bin/java$EXE" -version 2>&1 | head -1
export JAVA_HOME="$(npath "$JDK_DIR")"   # native tools (sdkmanager.bat) need C:/ form

# --- Android SDK ----------------------------------------------------------------------
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager$BAT"
if [ ! -f "$SDKMANAGER" ]; then
  echo "== Android cmdline-tools -> $SDK_DIR"
  tmp="$(mktmp)"
  os_tag=$([ "$OS" = windows ] && echo win || echo linux)
  fetch "$tmp/ct.zip" \
    "https://dl.google.com/android/repository/commandlinetools-${os_tag}-${CMDLINE_TOOLS_BUILD}_latest.zip"
  unzip -q "$tmp/ct.zip" -d "$tmp/x"
  mkdir -p "$SDK_DIR/cmdline-tools"; rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$tmp/x/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"; rm -rf "$tmp"
fi
sdk_root="$SDK_DIR"; [ "$OS" = windows ] && sdk_root="$(cygpath -w "$SDK_DIR")"
echo "== sdkmanager packages"
yes 2>/dev/null | "$SDKMANAGER" --sdk_root="$sdk_root" --licenses >/dev/null || true
"$SDKMANAGER" --sdk_root="$sdk_root" "platform-tools" "$PLATFORM" "$BUILD_TOOLS" "$BUILD_TOOLS_AGP" >/dev/null
[ -d "$SDK_DIR/platforms/android-35" ] || { echo "sdkmanager did not install $PLATFORM" >&2; exit 1; }

# --- Point the build at them ----------------------------------------------------------
sdk_prop="$SDK_DIR"; [ "$OS" = windows ] && sdk_prop="$(cygpath -m "$SDK_DIR")"
if [ -d "$REPO/android" ]; then
  printf 'sdk.dir=%s\n' "$sdk_prop" > "$REPO/android/local.properties"
  echo "wrote android/local.properties"
fi
jh="$JDK_DIR"; [ "$OS" = windows ] && jh="$(cygpath -m "$JDK_DIR")"
cat <<EOF

Toolchain ready.
  JAVA_HOME=$jh
  ANDROID_HOME=$sdk_prop
Build/test:  export JAVA_HOME="$jh"; cd android && ./gradlew testDebugUnitTest
EOF
