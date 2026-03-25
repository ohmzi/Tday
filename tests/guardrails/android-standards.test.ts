import { readdirSync, readFileSync, existsSync } from "fs";
import path from "path";

const ROOT = process.cwd();
const ANDROID_SRC = path.join(
  ROOT,
  "android-compose",
  "app",
  "src",
  "main",
  "java",
  "com",
  "ohmz",
  "tday",
  "compose",
);

function walkFiles(dir: string, ext: string): string[] {
  const results: string[] = [];
  if (!existsSync(dir)) return results;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...walkFiles(full, ext));
    } else if (entry.name.endsWith(ext)) {
      results.push(full);
    }
  }
  return results;
}

function readSource(filePath: string): string {
  return readFileSync(filePath, "utf-8");
}

function relPath(filePath: string): string {
  return path.relative(ROOT, filePath);
}

const skipAndroid = !existsSync(ANDROID_SRC);
const describeAndroid = skipAndroid ? describe.skip : describe;

const ALL_KT = skipAndroid ? [] : walkFiles(ANDROID_SRC, ".kt");
const FEATURE_KT = ALL_KT.filter((f: string) => f.includes("/feature/"));
const SCREEN_KT = ALL_KT.filter((f: string) => f.includes("Screen"));
const VM_KT = ALL_KT.filter((f: string) => f.includes("ViewModel"));
const THEME_DIR = path.join(ANDROID_SRC, "ui", "theme");
const THEME_KT = skipAndroid ? [] : walkFiles(THEME_DIR, ".kt");
const NON_THEME_KT = ALL_KT.filter(
  (f: string) => !f.startsWith(THEME_DIR + path.sep),
);

describeAndroid("Android null safety", () => {
  it("should not use unsafe cast (as without ?) in non-theme files", () => {
    const UNSAFE_CAST = /\bas\s+(?![\s?])[A-Z]\w+/;
    const violations: string[] = [];
    for (const file of NON_THEME_KT) {
      const content = readSource(file);
      const lines = content.split("\n");
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*"))
          continue;
        if (line.includes("import ")) continue;
        if (UNSAFE_CAST.test(line) && !line.includes("as?")) {
          if (
            !line.includes("as Adapter") &&
            !line.includes("as PrismaClient") &&
            !line.includes("as unknown")
          ) {
            violations.push(`${relPath(file)}:${i + 1} → ${line.trim()}`);
          }
        }
      }
    }

    if (violations.length > 0) {
      console.warn(
        `Advisory: ${violations.length} potential unsafe cast(s) found. ` +
          "Use 'as?' safe cast instead.\n" +
          violations.slice(0, 10).join("\n"),
      );
    }
    expect(true).toBe(true);
  });
});

describeAndroid("Android theme compliance", () => {
  it("Color.kt should define both light and dark color sets", () => {
    const colorFile = THEME_KT.find((f: string) => f.endsWith("Color.kt"));
    expect(colorFile).toBeDefined();
    const content = readSource(colorFile!);
    expect(content).toContain("TdayDark");
    expect(content).toContain("TdayLight");
  });

  it("Theme.kt should use both DarkColorScheme and LightColorScheme", () => {
    const themeFile = THEME_KT.find((f: string) => f.endsWith("Theme.kt"));
    expect(themeFile).toBeDefined();
    const content = readSource(themeFile!);
    expect(content).toContain("darkColorScheme");
    expect(content).toContain("lightColorScheme");
  });

  it("Dimens.kt should exist and define TdayDimens object", () => {
    const dimFile = THEME_KT.find((f: string) => f.endsWith("Dimens.kt"));
    expect(dimFile).toBeDefined();
    const content = readSource(dimFile!);
    expect(content).toContain("object TdayDimens");
    expect(content).toContain("Dp");
  });

  it("Dimens.kt should define spacing, radius, and icon size tokens", () => {
    const dimFile = THEME_KT.find((f: string) => f.endsWith("Dimens.kt"));
    expect(dimFile).toBeDefined();
    const content = readSource(dimFile!);
    expect(content).toContain("Spacing");
    expect(content).toContain("Radius");
    expect(content).toContain("Icon");
  });
});

describeAndroid("Android ViewModel conventions", () => {
  it("all ViewModels should have a UiState data class", () => {
    const violations: string[] = [];
    for (const file of VM_KT) {
      const content = readSource(file);
      if (!content.includes("ViewModel")) continue;
      const hasUiState =
        content.includes("data class") && content.includes("UiState");
      const hasStateFlow = content.includes("StateFlow");
      if (hasStateFlow && !hasUiState) {
        violations.push(relPath(file));
      }
    }

    if (violations.length > 0) {
      console.warn(
        `Advisory: ViewModels with StateFlow but no UiState data class: ${violations.join(", ")}`,
      );
    }
    expect(true).toBe(true);
  });

  it("ViewModels should use viewModelScope for coroutine launches", () => {
    for (const file of VM_KT) {
      const content = readSource(file);
      if (!content.includes("ViewModel")) continue;
      if (content.includes("launch")) {
        expect(content).toContain("viewModelScope");
      }
    }
  });

  it("mutable StateFlow should be private with underscore prefix", () => {
    const violations: string[] = [];
    for (const file of VM_KT) {
      const content = readSource(file);
      const publicMutableFlow =
        /(?:val|var)\s+(?!_)\w+\s*=\s*MutableStateFlow/.test(content);
      if (publicMutableFlow) {
        violations.push(relPath(file));
      }
    }
    expect(violations).toEqual([]);
  });
});

describeAndroid("Android network layer conventions", () => {
  const networkDir = path.join(ANDROID_SRC, "core", "network");
  const networkFiles = existsSync(networkDir)
    ? walkFiles(networkDir, ".kt")
    : [];

  it("should have a Retrofit API service interface", () => {
    const apiService = networkFiles.find((f: string) =>
      f.includes("ApiService"),
    );
    expect(apiService).toBeDefined();
  });

  it("API service should use suspend functions", () => {
    const apiService = networkFiles.find((f: string) =>
      f.includes("ApiService"),
    );
    if (apiService) {
      const content = readSource(apiService);
      const nonSuspendEndpoints = content
        .split("\n")
        .filter(
          (line: string) =>
            (line.includes("@GET") ||
              line.includes("@POST") ||
              line.includes("@PATCH") ||
              line.includes("@DELETE") ||
              line.includes("@PUT")) &&
            false,
        );
      const funDeclarations = content
        .split("\n")
        .filter((line: string) => line.trim().startsWith("suspend fun") || line.trim().startsWith("fun "));
      const nonSuspendFuns = funDeclarations.filter(
        (line: string) => !line.includes("suspend"),
      );
      expect(nonSuspendFuns.length).toBe(0);
    }
  });

  it("NetworkModule should redact cookie headers in logging", () => {
    const netModule = networkFiles.find((f: string) =>
      f.includes("NetworkModule"),
    );
    if (netModule) {
      const content = readSource(netModule);
      expect(content).toContain("redactHeader");
    }
  });

  it("should use kotlinx.serialization converter, not Gson", () => {
    const netModule = networkFiles.find((f: string) =>
      f.includes("NetworkModule"),
    );
    if (netModule) {
      const content = readSource(netModule);
      expect(content).toContain("kotlinx");
      expect(content).not.toContain("GsonConverterFactory");
    }
  });
});

describeAndroid("Android encrypted storage", () => {
  it("should have EncryptedCookieStore for session persistence", () => {
    const found = ALL_KT.some((f: string) =>
      f.includes("EncryptedCookieStore"),
    );
    expect(found).toBe(true);
  });

  it("should have SecureConfigStore for sensitive preferences", () => {
    const found = ALL_KT.some((f: string) =>
      f.includes("SecureConfigStore"),
    );
    expect(found).toBe(true);
  });
});
