import { readFileSync } from "fs";

const secretCache = new Map<string, string | undefined>();

export function getSecretValue(params: {
  envVar: string;
  fileEnvVar?: string;
}): string | undefined {
  const { envVar, fileEnvVar } = params;
  const cacheKey = `${envVar}:${fileEnvVar ?? ""}`;
  if (secretCache.has(cacheKey)) {
    return secretCache.get(cacheKey);
  }

  const direct = normalizeSecret(process.env[envVar]);
  if (direct) {
    secretCache.set(cacheKey, direct);
    return direct;
  }

  if (fileEnvVar) {
    const filePath = normalizeSecret(process.env[fileEnvVar]);
    if (filePath) {
      try {
        const fromFile = normalizeSecret(readFileSync(filePath, "utf8"));
        secretCache.set(cacheKey, fromFile);
        return fromFile;
      } catch (error) {
        console.warn(
          `[security] unable_to_read_secret_file env=${envVar} fileEnv=${fileEnvVar} path=${filePath} error=${String(
            error,
          )}`,
        );
      }
    }
  }

  secretCache.set(cacheKey, undefined);
  return undefined;
}

function normalizeSecret(value: string | undefined): string | undefined {
  if (!value) return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}
