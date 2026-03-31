#!/usr/bin/env bash
set -euo pipefail
mkdir -p "$(dirname "$0")/ssl"
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "$(dirname "$0")/ssl/key.pem" \
  -out "$(dirname "$0")/ssl/cert.pem" \
  -subj "/CN=localhost"
echo "Development TLS certificate generated in nginx/ssl/"
