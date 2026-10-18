#!/usr/bin/env bash
# Build Hermes Mobile Client.  ./build.sh          -> unit tests + APK (HermesClient.apk)
#                              ./build.sh test     -> unit tests only
#                              ./build.sh install  -> also adb install
# Needs the toolchain from scripts/setup-toolchain.sh (JDK 17 + Android SDK). JAVA_HOME wins;
# otherwise the portable JDK under C:/home/tools (Windows) or ~/tools (Linux) is used.
set -euo pipefail
cd "$(dirname "$0")/android"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) TOOLS=/c/home/tools ;; *) TOOLS="$HOME/tools" ;; esac
export JAVA_HOME="${JAVA_HOME:-$TOOLS/jdk-17}"
[ -f local.properties ] || { echo "no android/local.properties: run scripts/setup-toolchain.sh" >&2; exit 1; }
# test = every module's JVM tests plus a compile of both apps (docs/HUB-DESIGN.md H16).
# Retries Gradle's Windows cache-move race ("Could not move temporary workspace"), which is not a test failure.
if [ "${1:-}" = test ]; then
  log="$(mktemp)"
  for i in 1 2 3 4; do
    r=0; ./gradlew -q :shared:desktopTest :shared:testDebugUnitTest :app:testDebugUnitTest :desktopApp:test \
      :app:assembleDebug :desktopApp:compileKotlin > "$log" 2>&1 || r=$?
    grep -q "Could not move temporary workspace" "$log" || break
  done
  grep -v '^w:' "$log" || true; rm -f "$log"; exit $r
fi
if [ -f keystore.properties ]; then
  ./gradlew -q testDebugUnitTest assembleRelease; cp app/build/outputs/apk/release/app-release.apk ../HermesClient.apk
else
  ./gradlew -q testDebugUnitTest assembleDebug; cp app/build/outputs/apk/debug/app-debug.apk ../HermesClient.apk
  echo "Note: debug-signed build (no android/keystore.properties)."
fi
echo "Built $(cd .. && pwd)/HermesClient.apk"
if [ "${1:-}" = install ]; then "$(sed -n 's/^sdk\.dir=//p' local.properties)/platform-tools/adb" install -r ../HermesClient.apk; fi
