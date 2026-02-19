import { execSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const moduleDir = path.resolve(path.dirname(__filename), "..");
const repoRoot = path.resolve(moduleDir, "..", "..");
const androidDir = path.join(repoRoot, "android");
const envFilePath = path.join(repoRoot, ".env");

function loadDotEnv(filePath) {
  if (!existsSync(filePath)) {
    return;
  }

  const content = readFileSync(filePath, "utf8");
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#") || !trimmed.includes("=")) {
      continue;
    }

    const separatorIndex = trimmed.indexOf("=");
    const key = trimmed.slice(0, separatorIndex).trim();
    const rawValue = trimmed.slice(separatorIndex + 1).trim();
    if (!key || process.env[key]) {
      continue;
    }

    let value = rawValue;
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    process.env[key] = value;
  }
}

function run(command, cwd = moduleDir, options = {}) {
  const { optionalOnOfflineGradle = false } = options;
  console.log(`\n> ${command} (${cwd})`);
  try {
    const output = execSync(command, {
      cwd,
      stdio: "pipe",
      env: {
        ...process.env,
        GRADLE_USER_HOME:
          process.env.GRADLE_USER_HOME || "/tmp/gradle-user-home-react-native-mapbox-navigation",
      },
    });
    if (output?.length) {
      process.stdout.write(output);
    }
  } catch (error) {
    const stdout = String(error?.stdout || "");
    const stderr = String(error?.stderr || "");
    if (stdout) {
      process.stdout.write(stdout);
    }
    if (stderr) {
      process.stderr.write(stderr);
    }

    const combined = `${stdout}\n${stderr}\n${String(error?.message || "")}`;
    const skippableGradleEnvironmentIssue =
      combined.includes("UnknownHostException") ||
      combined.includes("FileLockContentionHandler") ||
      combined.includes("SocketException: Operation not permitted");

    if (optionalOnOfflineGradle && skippableGradleEnvironmentIssue) {
      console.warn(`\nSkipping optional Gradle check due to restricted/offline environment: ${command}`);
      return;
    }
    throw error;
  }
}

function runWithFallback(commands, cwd, options = {}) {
  let lastError;
  for (const command of commands) {
    try {
      run(command, cwd, options);
      return;
    } catch (error) {
      lastError = error;
      console.warn(`\nCommand failed, trying fallback: ${command}`);
    }
  }
  throw lastError;
}

loadDotEnv(envFilePath);

run("npx tsc --noEmit", repoRoot);

if (existsSync(path.join(androidDir, "gradlew"))) {
  runWithFallback(
    [
      "./gradlew :react-native-mapbox-navigation:compileDebugKotlin",
      "./gradlew :mapbox-navigation-native:compileDebugKotlin",
    ],
    androidDir,
    { optionalOnOfflineGradle: true }
  );
} else {
  console.log("\n> Skipping Android compile check (android/gradlew not found).");
}

run("npm pack --dry-run --cache /tmp/npm-cache-react-native-mapbox-navigation", moduleDir);

console.log("\nRelease verification completed.");
