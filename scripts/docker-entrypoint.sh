#!/bin/sh
set -eu

load_secret_var() {
  var_name="$1"
  file_var_name="${var_name}_FILE"
  current_value="$(eval "printf '%s' \"\${$var_name:-}\"")"
  file_path="$(eval "printf '%s' \"\${$file_var_name:-}\"")"

  if [ -n "$current_value" ] || [ -z "$file_path" ]; then
    return
  fi

  if [ ! -f "$file_path" ]; then
    echo "[security] secret file for $var_name not found at $file_path" >&2
    exit 1
  fi

  file_value="$(tr -d '\r\n' < "$file_path")"
  if [ -z "$file_value" ]; then
    echo "[security] secret file for $var_name is empty at $file_path" >&2
    exit 1
  fi

  export "$var_name=$file_value"
}

load_secret_var "AUTH_SECRET"
load_secret_var "CRONJOB_SECRET"
load_secret_var "DATABASE_URL"
load_secret_var "AUTH_CAPTCHA_SECRET"
load_secret_var "DATA_ENCRYPTION_KEY"
load_secret_var "DATA_ENCRYPTION_KEYS"
load_secret_var "DATA_ENCRYPTION_AAD"
load_secret_var "AUTH_TRUST_HOST"

exec sh -c "npx prisma migrate deploy && npm run start"
