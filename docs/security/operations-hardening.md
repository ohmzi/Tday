# Operations Hardening Checklist

Use this for self-hosted production deployments.

## Secrets Management

1. Store `AUTH_SECRET`, `CRONJOB_SECRET`, and `DATABASE_URL` in a secret manager.
2. Inject secrets via environment variables or mounted files (`*_FILE` vars).
3. Never commit real secrets in `.env` files.
4. Rotate secrets on a fixed schedule (recommended: every 60-90 days).

## Session And Auth Controls

1. Keep `AUTH_SESSION_MAX_AGE_SEC` between 12h and 24h.
2. Keep auth throttling + lockout variables enabled.
3. Configure `AUTH_CAPTCHA_SECRET` so CAPTCHA is enforced after repeated failures.
4. Monitor security event codes:
   - `auth_lockout`
   - `auth_limit_ip`
   - `auth_limit_email`
   - `auth_alert_ip_concentration`
   - `auth_alert_lockout_burst`
   - `auth_signal_anomaly`

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
