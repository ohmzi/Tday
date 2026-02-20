# Cloudflare Auth Hardening Checklist

Use these edge rules in front of the Docker-hosted Tday backend.

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
- `probe_failed_contract`

Operational checks:

1. Alert when `auth_lockout` grows rapidly in a short window.
2. Alert when `auth_limit_ip` spikes from a narrow IP range.
3. Alert when `probe_failed_contract` appears repeatedly from one source.
4. Compare Cloudflare challenged requests vs backend `429` counts to verify edge + app-layer defense-in-depth.
