# Security Policy

## Supported Versions

Only the latest published minor version receives security updates.

## Reporting a Vulnerability

- Open a private security advisory on GitHub, or
- Email the maintainers listed in the repository profile.

Include:
- Package version
- Platform (iOS/Android)
- Reproduction steps
- Impact assessment

## Dependency Audit Policy

`npm audit` output in Expo/React Native apps can include transitive advisories from upstream tooling.

Project policy:
1. Prioritize vulnerabilities reachable in production runtime dependencies.
2. Track dev/build-only advisories from Expo/Metro/Jest and resolve when upstream ships patched versions.
3. Never run `npm audit fix --force` blindly in release branches.
4. Keep Expo SDK and React Native versions updated to receive upstream security fixes.

## Token Security

- Never commit Mapbox tokens to git.
- Use environment variables or CI/CD secrets.
- Rotate tokens immediately if leaked.

Required tokens:
- `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN` (`pk...`)
- `MAPBOX_DOWNLOADS_TOKEN` (`sk...`, `DOWNLOADS:READ`)
