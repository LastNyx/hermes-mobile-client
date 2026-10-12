#!/usr/bin/env bash
# Creates the release keystore OUTSIDE the repo and prints only its SHA-256 fingerprint.
# Passwords are random and written to <dir>/keystore.properties, never printed.
# Back the directory up somewhere other than git: losing the key means users must reinstall.
#   scripts/make-release-key.sh [dir]     (default: C:/home/keys on Windows, ~/keys elsewhere)
set -euo pipefail

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) DIR="${1:-C:/home/keys}"; KEYTOOL="C:/home/tools/jdk-17/bin/keytool.exe" ;;
  *) DIR="${1:-$HOME/keys}"; KEYTOOL="$HOME/tools/jdk-17/bin/keytool" ;;
esac
[ -x "$KEYTOOL" ] || KEYTOOL="$(command -v keytool)"

STORE="$DIR/hermes-client-release.jks"
PROPS="$DIR/keystore.properties"
ALIAS="hermes-client"

mkdir -p "$DIR"
if [ -e "$STORE" ]; then echo "refusing to overwrite $STORE" >&2; exit 1; fi

PASS="$(head -c 32 /dev/urandom | base64 | tr -d '=+/\n' | head -c 28)"

"$KEYTOOL" -genkeypair -v -keystore "$STORE" -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=Hermes Mobile Client, O=LastNyx" >/dev/null 2>&1

cat > "$PROPS" <<EOF
storeFile=$STORE
storePassword=$PASS
keyAlias=$ALIAS
keyPassword=$PASS
EOF
chmod 600 "$PROPS" "$STORE" 2>/dev/null || true

echo "keystore: $STORE"
echo "settings: $PROPS (passwords inside, not shown)"
"$KEYTOOL" -list -v -keystore "$STORE" -alias "$ALIAS" -storepass "$PASS" 2>/dev/null | grep -E "SHA256:|Valid from"
