#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="KAN1409/AWAREX"
BACKUP_DIR="$HOME/.awarex-signing"
KEYSTORE="$BACKUP_DIR/awarex-release.p12"
CREDENTIALS="$BACKUP_DIR/credentials.env"
ALIAS="awarex-release"

fail() {
  printf 'AWAREX signing bootstrap failed: %s\n' "$1" >&2
  exit 1
}

install_termux_package_if_missing() {
  local command_name="$1"
  local package_name="$2"
  if command -v "$command_name" >/dev/null 2>&1; then
    return 0
  fi
  command -v pkg >/dev/null 2>&1 || fail "missing command: $command_name (and Termux pkg is unavailable)"
  printf 'Installing missing prerequisite: %s\n' "$package_name"
  pkg install -y "$package_name" >/dev/null
  command -v "$command_name" >/dev/null 2>&1 || fail "could not install command: $command_name"
}

install_termux_package_if_missing gh gh
install_termux_package_if_missing openssl openssl
install_termux_package_if_missing keytool openjdk-17
install_termux_package_if_missing base64 coreutils

gh auth status >/dev/null 2>&1 || fail "GitHub CLI is not authenticated"
gh repo view "$REPO" >/dev/null 2>&1 || fail "cannot access $REPO"

umask 077
mkdir -p "$BACKUP_DIR"

if [ -e "$KEYSTORE" ] || [ -e "$CREDENTIALS" ]; then
  fail "existing AWAREX signing material found in $BACKUP_DIR; refusing to overwrite it"
fi

STORE_PASS="$(openssl rand -hex 32)"
KEY_PASS="$STORE_PASS"

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storetype PKCS12 \
  -keystore "$KEYSTORE" \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=AWAREX, OU=Personal, O=KAN1409, L=Cairo, C=EG" \
  >/dev/null

chmod 600 "$KEYSTORE"

cat > "$CREDENTIALS" <<EOF
AWAREX_KEYSTORE_PASSWORD=$STORE_PASS
AWAREX_KEY_ALIAS=$ALIAS
AWAREX_KEY_PASSWORD=$KEY_PASS
EOF
chmod 600 "$CREDENTIALS"

KEYSTORE_B64="$(base64 < "$KEYSTORE" | tr -d '\n\r')"

printf '%s' "$KEYSTORE_B64" | gh secret set AWAREX_KEYSTORE_B64 --repo "$REPO"
printf '%s' "$STORE_PASS" | gh secret set AWAREX_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set AWAREX_KEY_ALIAS --repo "$REPO"
printf '%s' "$KEY_PASS" | gh secret set AWAREX_KEY_PASSWORD --repo "$REPO"

unset KEYSTORE_B64 STORE_PASS KEY_PASS

gh workflow run android.yml --repo "$REPO" --ref main

printf '\nAWAREX permanent signing identity created and stored in GitHub Actions secrets.\n'
printf 'Local backup directory: %s\n' "$BACKUP_DIR"
printf 'The workflow has been triggered. Keep that backup directory safe and never commit or share it.\n'
