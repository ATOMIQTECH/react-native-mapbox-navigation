# Conventions

These apply to every source file in the repository. Each rule names the check that enforces it; `pnpm check` runs them all, the git hooks run them on commit, and the `check` workflow runs them on every pull request.

## Hard rules

1. **No comments.** Code says what it does. Why it does it goes in the commit message or the pull request, where it stays attached to the change. The only comments allowed are:

   | Where | Allowed |
   |---|---|
   | Everywhere | lint directives (`oxlint-disable-next-line <rule>`, `@ts-expect-error`), a shebang on line 1, and one line starting `WARNING:` for a hazard a reader must not miss |
   | `src/*.ts(x)` | TSDoc on the exported public API — it is what consumers read in their editor |
   | Ruby | `# frozen_string_literal: true` |

   TypeScript and JavaScript are checked by the `package/no-comments` oxlint rule (`lint-rules/`); Swift, Kotlin, Gradle, Ruby, shell and Perl by `scripts/check-source.mjs`. Both fix what they find: `pnpm lint:fix`, `node scripts/check-source.mjs --fix`.

2. **500 lines of code at most per file.** Blank and comment lines do not count. Files already over the limit carry an exact allowance (`package/max-file-lines` in `.oxlintrc.json`, `LINE_ALLOWANCES` in `scripts/check-source.mjs`). An allowance never rises: a file over it fails, and a file under it fails until the allowance is lowered to match.

3. **No new lint problems.** oxlint runs type-aware with the `correctness`, `suspicious` and `pedantic` categories as errors (`.oxlintrc.json`). Problems that predate the rule are counted per file and rule in `lint-baseline.json`. `pnpm lint` fails on any count above its baseline and on any count below it; run `pnpm lint --shrink-baseline` after fixing some. The baseline can only shrink — there is no command that raises it.

4. **Formatted by oxfmt.** `pnpm format` writes, `pnpm format:check` verifies (`.oxfmtrc.json`).

5. **Nothing unused, nothing undeclared.** knip fails on unused files, exports and dependencies, and on any import of a package `package.json` does not declare (`knip.json`). A dependency that only resolves because a package manager hoisted it is a bug for consumers on another package manager.

6. **Both platforms, same surface.** Every prop, function and event in `src/MapboxNavigation.types.ts` is declared on iOS and Android, and every native prop is read as well as stored (`pnpm verify:parity`).

7. **Conventional Commits.** `type(optional-scope): summary` — `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`. A `!` after the type marks a breaking change. Checked by commitlint on `commit-msg` and on every pull request.

8. **Standalone package.** Documentation, examples, comments and identifiers never name a consuming project. Refer to one as "a downstream app".

9. **Changelog.** Every change a consumer can observe adds an entry to `CHANGELOG.md` under the next version: what was wrong, what changed, and anything a consumer must do.

## Setup

```sh
pnpm install
git config core.hooksPath .githooks
```

The hooks are `pre-commit` (security scan, format, lint, source check), `commit-msg` (commitlint), `pre-push` and `post-merge` (security scan). They are local and can be skipped; the `check` and `security-scan` workflows cannot.

## Verifying a change

A clean build proves little in this package: it wraps two native SDKs that mostly fail by doing nothing. Run the change in the example app (`example/`) on a simulator or emulator and look at it.

The example's user journeys are Maestro flows in `example/.maestro/`, one per journey, using the screens' `testID`s. With the example built and installed and Metro running (`pnpm exec expo start` in `example/`), run them with `maestro test example/.maestro/` on each platform. They need this example's Metro on port 8081: a dev-client build reconnects to the last bundler it used, so if another project's Metro answers instead, reinstall the app; if Metro serves stale code, restart it with `--clear`.
