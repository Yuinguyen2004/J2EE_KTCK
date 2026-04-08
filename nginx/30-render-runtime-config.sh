#!/bin/sh
set -eu

: "${GOOGLE_AUTH_ORIGIN:=${BACKEND_UPSTREAM:-}}"

envsubst '${GOOGLE_AUTH_ORIGIN}' \
  < /opt/runtime-config.js.template \
  > /usr/share/nginx/html/runtime-config.js
