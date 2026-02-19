# Publishing & CI/CD Guide

## 1. Create the package repository

Recommended repo name: `react-native-mapbox-navigation`.

Use this module as the repository root (do not keep it under `modules/` after copying).

Required top-level items:
- `src/`, `android/`, `ios/`
- `app.plugin.js`, `expo-module.config.json`, `package.json`
- `README.md`, `QUICKSTART.md`, `CHANGELOG.md`, `docs/`
- `.github/workflows/`
- `website/` (GitHub Pages)

## 2. Prepare GitHub repo

1. Create GitHub repo `react-native-mapbox-navigation`.
2. Copy this folder contents into that repo root.
3. Push initial commit:

```bash
git init
git add .
git commit -m "chore: initial release-ready package"
git branch -M main
git remote add origin git@github.com:<your-org-or-user>/react-native-mapbox-navigation.git
git push -u origin main
```

## 3. Enable CI

This package includes workflow templates you can use directly:
- `.github/workflows/ci.yml`
- `.github/workflows/deploy-pages.yml`

`ci.yml` validates:
- TypeScript check (`npx tsc --noEmit`)
- Android module compile (`./gradlew ...compileDebugKotlin`)
- npm tarball dry-run (`npm pack --dry-run`)

## 4. Enable GitHub Pages

1. In GitHub repo: `Settings -> Pages`.
2. Source: `GitHub Actions`.
3. Keep `website/` as docs source for deployed site.
4. Push to `main`; workflow will publish automatically.

## 5. Local release verification

From package root:

```bash
npm run verify
```

This runs:
1. TypeScript check
2. Android compile check
3. `npm pack --dry-run`

## 6. Test before npm publish

### Pack locally

```bash
npm pack --cache /tmp/npm-cache-react-native-mapbox-navigation
```

### Install in a clean test app

```bash
npm install /absolute/path/to/react-native-mapbox-navigation-<version>.tgz
```

Then validate both platforms:

```bash
npx expo prebuild --clean
npx expo run:android
npx expo run:ios
```

## 7. Publish steps (after testing)

```bash
npm version patch   # or minor / major
git push --follow-tags
npm publish --access public
```

## 8. Recommended production setup

- Protect `main` and require PR checks.
- Enable npm trusted publishing from GitHub Actions.
- Create GitHub Releases for each version tag.
- Keep `CHANGELOG.md` updated per release.
