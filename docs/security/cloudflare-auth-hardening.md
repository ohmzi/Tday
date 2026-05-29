# Cloudflare Auth Hardening Checklist

Use these edge rules in front of the Docker-hosted T'Day backend.

This applies to Server Mode traffic. Local Mode mobile workspaces do not call the backend and are outside Cloudflare edge controls.

## Rate-limit rules

1. Path: `/api/auth/callback/credentials`
   - Method: `POST`
   - Action: `Managed Challenge`
   - Suggested threshold: `12 requests / 5 minutes per IP`

2. Path: `/api/auth/register`
   - Method: `POST`
   - Action: `Managed Challenge` (or `Block` for high-risk traffic)
   - Suggested threshold: `6 requests / 60 minutes per IP`

3. Path: `/api/auth/csrf`
   - Method: `GET`
   - Action: `Managed Challenge`
   - Suggested threshold: `40 requests / 1 minute per IP`

## Trusted client IP order in backend

The backend now resolves IP in this order:

1. `CF-Connecting-IP`
2. `X-Forwarded-For` (first entry)
3. `X-Real-IP`
4. framework fallback (`request.ip`)

## Monitoring / query guidance

Structured security reason codes now emitted to logs/event table:

- `auth_limit_ip`
- `auth_limit_email`
- `auth_lockout`
- `auth_captcha_failed`
- `register_captcha_failed`
- `auth_captcha_misconfigured`
- `auth_credential_envelope_invalid`
- `auth_alert_ip_concentration`
- `auth_alert_lockout_burst`
- `auth_signal_anomaly`
- `auth_session_absolute_expired`
- `auth_session_renewed`
- `auth_session_token_version_mismatch`
- `auth_session_user_missing`
- `request_rate_limit_triggered`

`request_rate_limit_triggered` includes a `reason` detail such as `api_rate_limit`, `infra_rate_limit`, `summary_rate_limit`, `change_password_rate_limit`, or `websocket_rate_limit`.

Operational checks:

1. Alert when `auth_lockout` grows rapidly in a short window.
2. Alert when `auth_limit_ip` spikes from a narrow IP range.
3. Alert when `auth_signal_anomaly` appears repeatedly from one source or identifier.
4. Alert when `auth_session_token_version_mismatch` or `auth_session_user_missing` clusters outside expected sign-out, password-change, or account-removal activity.
5. Compare Cloudflare challenged requests vs backend `429` counts to verify edge + app-layer defense-in-depth.
