# Operations Hardening Checklist

Use this for self-hosted production deployments.

These controls protect Server Mode infrastructure. Local Mode mobile data remains on-device unless a future explicit migration/import flow is designed.

## Secrets Management

1. Store `AUTH_SECRET`, `DATABASE_URL`, and `AUTH_CREDENTIALS_PRIVATE_KEY` in a secret manager.
2. Inject secrets via environment variables or mounted files (`*_FILE` vars).
3. Never commit real secrets in `.env` files.
4. Rotate secrets on a fixed schedule (recommended: every 60-90 days).

## Session And Auth Controls

1. Keep `AUTH_SESSION_MAX_AGE_SEC` between 7 and 30 days for browser convenience, and keep `AUTH_SESSION_ABSOLUTE_MAX_AGE_SEC` at or below 90 days.
2. Keep `AUTH_SESSION_RENEW_THRESHOLD_SEC` between 1 and 7 days so active sessions renew predictably without rewriting the cookie on every request.
3. Keep auth throttling + lockout variables enabled.
4. Monitor security event codes:
   - `auth_lockout`
   - `auth_limit_ip`
   - `auth_limit_email`
   - `auth_credential_envelope_invalid`
   - `auth_alert_ip_concentration`
   - `auth_alert_lockout_burst`
   - `auth_signal_anomaly`
   - `auth_session_absolute_expired`
   - `auth_session_renewed`
   - `auth_session_token_version_mismatch`
   - `auth_session_user_missing`
   - `request_rate_limit_triggered`
6. Inspect the `reason` detail on `request_rate_limit_triggered`; expected values are `api_rate_limit`, `infra_rate_limit`, `summary_rate_limit`, `change_password_rate_limit`, and `websocket_rate_limit`.

## Field Encryption At Rest

1. Set `DATA_ENCRYPTION_KEY_ID` and `DATA_ENCRYPTION_KEY`.
2. During key rotation, keep prior keys in `DATA_ENCRYPTION_KEYS` until all data has been re-written.
3. Store encryption keys in a secret manager, not in source control.

## Backups

1. Encrypt database backups at rest.
2. Encrypt backup transport paths.
3. Limit restore permissions to a small operator group.
4. Periodically test backup restore and integrity verification.
5. Apply retention policies and secure deletion for expired backups.

## Logs

1. Centralize logs in an append-only or tamper-resistant sink.
2. Restrict log access to operations/security roles.
3. Apply retention and automatic purge policies.
4. Avoid logging plaintext secrets, tokens, and full request bodies.
