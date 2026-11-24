#!/usr/bin/env bash
set -uo pipefail

LIBDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib"
if [ ! -f "$LIBDIR/inspect-file.pl" ]; then
  LIBDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/security-scan-lib"
fi
[ -f "$LIBDIR/inspect-file.pl" ] || { echo "missing $LIBDIR/inspect-file.pl" >&2; exit 2; }

MODE="${1:-staged}"
RANGE="${2:-}"
FAIL=0
WARN=0
ALLOWFILE=".security-scan-allow"

MAX_LINE=1000        # far above hand-written source, far below a real bundle
MAX_RUN_SPACES=100   # the off-screen padding trick (real sample used 273)
MAX_HEX_IDENTS=25    # _0xabcd1234 density => obfuscator output
MAX_ESCAPES=50       # \x27\x20 density => obfuscator output
MAX_B64_RUN=512      # inline base64 blob

RED=''; BOLD=''; RESET=''
if [ -t 1 ]; then RED=$'\033[31m'; BOLD=$'\033[1m'; RESET=$'\033[0m'; fi

report()  { echo "  ${RED}✗${RESET} $1"; FAIL=1; }
warning() { echo "  ${BOLD}!${RESET} $1"; WARN=1; }

usage() { echo "usage: $0 {staged|range <range>|tree}" >&2; exit 2; }
case "$MODE" in
  staged|tree) ;;
  range) [ -n "$RANGE" ] || usage ;;
  *) usage ;;
esac

files_to_check() {
  case "$MODE" in
    staged) git diff --cached --name-only --diff-filter=ACM ;;
    range)  git diff --name-only --diff-filter=ACM "$RANGE" ;;
    tree)   git ls-files ;;
  esac
}

content_of() {
  if [ "$MODE" = "staged" ]; then git show ":$1" 2>/dev/null; else cat "$1" 2>/dev/null; fi
}

is_scannable() {
  case "$1" in
    *.min.js|*.min.mjs|*.min.css|*-lock.json|package-lock.json|yarn.lock|pnpm-lock.yaml|bun.lockb) return 1 ;;
    *.js|*.mjs|*.cjs|*.ts|*.tsx|*.jsx|*.json|*.css|*.scss|*.sass|*.yml|*.yaml|*.sh|*.bash|*.zsh) return 0 ;;
    *) return 1 ;;
  esac
}

is_config() {
  case "$1" in
    *.config.js|*.config.mjs|*.config.cjs|*.config.ts|*.config.mts\
    |next.config.*|postcss.config.*|tailwind.config.*|vite.config.*|webpack.config.*\
    |rollup.config.*|babel.config.*|metro.config.*|jest.config.*|svelte.config.*\
    |nuxt.config.*|astro.config.*|eslint.config.*|.eslintrc.js|.eslintrc.cjs) return 0 ;;
    *) return 1 ;;
  esac
}

is_guard_file() {
  case "$1" in
    */security-scan.sh|security-scan.sh|.githooks/*|.security-toolkit/*|*/IOCS.md|IOCS.md|"$ALLOWFILE") return 0 ;;
    *) return 1 ;;
  esac
}

is_allowed() {
  [ -f "$ALLOWFILE" ] || return 1
  local f="$1" sum
  grep -qxF -- "$f" "$ALLOWFILE" && return 0
  sum=$(content_of "$f" | shasum -a 256 2>/dev/null | awk '{print $1}')
  [ -n "$sum" ] && grep -qxF -- "sha256:$sum" "$ALLOWFILE"
}


LIST_FILE="$(mktemp "${TMPDIR:-/tmp}/repoguard-list.XXXXXX")"
trap 'rm -f "$LIST_FILE" "$LIST_FILE.err"' EXIT
if ! files_to_check > "$LIST_FILE" 2>"$LIST_FILE.err"; then
  echo "Security scan (mode: $MODE)"
  echo "  ${RED}✗${RESET} cannot enumerate files to scan:"
  sed 's/^/      /' "$LIST_FILE.err" >&2
  echo ""
  echo "${RED}${BOLD}SCAN FAILED${RESET} — refusing to report clean on an error."
  rm -f "$LIST_FILE.err"
  exit 2
fi
rm -f "$LIST_FILE.err"

echo "Security scan (mode: $MODE)"
CHECKED=0

while IFS= read -r f; do
  [ -z "$f" ] && continue

  case "$f" in
    .env|.env.*)
      case "$f" in
        *.example|*.sample|*.template) ;;
        *) report "$f — refusing to commit a real env file" ;;
      esac
      ;;
    .npmrc|.yarnrc|.yarnrc.yml)
      content_of "$f" | grep -qiE '_authtoken|_password|npmauthtoken' \
        && report "$f — contains a registry credential"
      ;;
  esac

  if [ "$MODE" != "tree" ]; then
    case "$f" in
      "$ALLOWFILE"|scripts/security-scan.sh|.githooks/*|.github/workflows/security-scan.yml)
        warning "$f — this change modifies the guard itself; review it by hand" ;;
    esac
  fi

  is_scannable "$f" || continue
  if is_allowed "$f"; then
    echo "  · $f — allowlisted, skipped"
    continue
  fi
  CHECKED=$((CHECKED+1))

  g=0; is_guard_file "$f" && g=1
  c=0; is_config "$f"     && c=1

  while IFS='|' read -r kind msg; do
    [ -z "${kind:-}" ] && continue
    case "$kind" in
      BLOCK) report  "$msg" ;;
      WARN)  warning "$msg" ;;
    esac
  done < <(content_of "$f" | perl "$LIBDIR/inspect-file.pl" "$f" "$g" "$c")

done < "$LIST_FILE"

echo ""
if [ "$FAIL" -ne 0 ]; then
  echo "${RED}${BOLD}BLOCKED${RESET} — $CHECKED file(s) scanned. Do NOT commit until each finding is explained."
  echo ""
  echo "Inspect a flagged file without executing it:"
  echo "  awk '{ print NR\": \"length(\$0) }' <file> | sort -k2 -rn | head"
  echo "  grep -c '_0x' <file>"
  echo ""
  echo "If a finding is genuinely legitimate, add its path or sha256 to $ALLOWFILE"
  echo "in a SEPARATE commit so the exception is reviewable. Never use --no-verify."
  exit 1
fi
if [ "$WARN" -ne 0 ]; then
  echo "Passed with warnings — $CHECKED file(s) scanned."
else
  echo "  ✓ clean — $CHECKED file(s) scanned."
fi
exit 0
