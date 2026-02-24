import { getSecretValue } from "@/lib/security/secretSource";

let hasChecked = false;

export function runSecurityConfigChecks(): void {
  if (hasChecked) return;
  hasChecked = true;

  if (process.env.NODE_ENV !== "production") return;

  const authSecret = getSecretValue({
    envVar: "AUTH_SECRET",
    fileEnvVar: "AUTH_SECRET_FILE",
  });
  if (!authSecret || authSecret.length < 32) {
    console.warn(
      "[security] auth_secret_missing_or_weak. Configure AUTH_SECRET/AUTH_SECRET_FILE with at least 32 characters.",
    );
  }

  const databaseUrl = getSecretValue({
    envVar: "DATABASE_URL",
    fileEnvVar: "DATABASE_URL_FILE",
  });
  if (!databaseUrl) {
    console.warn(
      "[security] database_url_missing. Configure DATABASE_URL/DATABASE_URL_FILE from a secret source.",
    );
  }

  const captchaSecret = getSecretValue({
    envVar: "AUTH_CAPTCHA_SECRET",
    fileEnvVar: "AUTH_CAPTCHA_SECRET_FILE",
  });
  if (!captchaSecret) {
    console.warn(
      "[security] captcha_not_configured. Repeated auth failures will not challenge bots.",
    );
  }
}
