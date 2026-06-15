#!/usr/bin/env bash
# Generate RSA key pair for JWT RS256 signing
set -e

KEYS_DIR="$(dirname "$0")/../keys"
mkdir -p "$KEYS_DIR"

openssl genrsa -out "$KEYS_DIR/private.pem" 4096
openssl rsa -in "$KEYS_DIR/private.pem" -pubout -out "$KEYS_DIR/public.pem"

echo "Keys generated:"
echo "  Private: $KEYS_DIR/private.pem"
echo "  Public:  $KEYS_DIR/public.pem"
echo ""
echo "IMPORTANT: Never commit keys/ — it is in .gitignore"
echo "           For Railway: add private.pem content as JWT_PRIVATE_KEY env var"
