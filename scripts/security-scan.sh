#!/usr/bin/env bash
# Repo guard — blocks the tampering class this codebase keeps getting hit by.
#
# Incident history: a real line of code, then ~1,300 spaces to push the payload
# off-screen, then obfuscated JS in a build-time config file. The payload is
# regenerated every time; the delivery shape is not. So this scans for SHAPE
# first and known indicators second.
#
# The 2026-08 sample ("Pollin Ridder", version tag A8-3894) defeated the first
# version of this script by splitting every indicator across string
# concatenations: 'eth_blockN'+'umber' does not match /eth_blockNumber/ and
# '.publicnod'+'e.com' does not match /publicnode/. Everything below therefore
# matches a NORMALIZED view of the file, with quotes, commas, plus signs,
# whitespace and \xNN escapes folded away, so split literals rejoin first.
#
# Two classes of indicator, because precision matters more than reach here —
# a guard that cries wolf gets bypassed with --no-verify and then protects
# nothing:
#   HIGH    — meaningless outside this malware; blocks on its own.
#   CONTEXT — legitimate in ordinary code (child_process, windowsHide); blocks
#             only in a file that ALSO looks obfuscated.
#
# Modes:
#   staged            — what is about to be committed (pre-commit)
#   range <range>     — a commit range, e.g. origin/main...HEAD (CI / pre-push)
#   tree              — every tracked file (scheduled audit / one-off)
#
# Exit 0 clean, 1 findings, 2 usage error.
set -uo pipefail

MODE="${1:-staged}"
RANGE="${2:-}"
FAIL=0
WARN=0
ALLOWFILE=".security-scan-allow"

MAX_LINE=1000        # far above hand-written source, far below a real bundle
MAX_RUN_SPACES=400   # the off-screen padding trick
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

# Minified vendor bundles legitimately have very long lines; source does not.
is_scannable() {
  case "$1" in
    *.min.js|*.min.mjs|*.min.css|*-lock.json|package-lock.json|yarn.lock|pnpm-lock.yaml|bun.lockb) return 1 ;;
    *.js|*.mjs|*.cjs|*.ts|*.tsx|*.jsx|*.json|*.css|*.scss|*.sass|*.yml|*.yaml|*.sh|*.bash|*.zsh) return 0 ;;
    *) return 1 ;;
  esac
}

# Executed by the build => highest-value place to hide code.
is_config() {
  case "$1" in
    *.config.js|*.config.mjs|*.config.cjs|*.config.ts|*.config.mts\
    |next.config.*|postcss.config.*|tailwind.config.*|vite.config.*|webpack.config.*\
    |rollup.config.*|babel.config.*|metro.config.*|jest.config.*|svelte.config.*\
    |nuxt.config.*|astro.config.*|eslint.config.*|.eslintrc.js|.eslintrc.cjs) return 0 ;;
    *) return 1 ;;
  esac
}

# The guard's own files legitimately contain every indicator. Shape checks still
# apply to them, and any change to them raises the "modifies the guard" warning.
is_guard_file() {
  case "$1" in
    */security-scan.sh|security-scan.sh|.githooks/*|.security-toolkit/*|*/IOCS.md|IOCS.md|"$ALLOWFILE") return 0 ;;
    *) return 1 ;;
  esac
}

# Allowlist: one path or sha256:<hex> per line. Reviewable because it is committed.
is_allowed() {
  [ -f "$ALLOWFILE" ] || return 1
  local f="$1" sum
  grep -qxF -- "$f" "$ALLOWFILE" && return 0
  sum=$(content_of "$f" | shasum -a 256 2>/dev/null | awk '{print $1}')
  [ -n "$sum" ] && grep -qxF -- "sha256:$sum" "$ALLOWFILE"
}

# Icon and payment-logo components carry legitimate inline SVG and data: URIs,
# which are long lines and long base64 runs by nature. Recognise them by
# content rather than by path, so a payload cannot hide behind a filename.
looks_like_inline_asset() {
  printf '%s' "$1" | grep -qE 'data:(image|font)/|<svg|<path|\bd="[Mm][ 0-9.,-]'
}

normalize() {
  perl -0777 -pe '
    s/\\x([0-9a-fA-F]{2})/chr(hex($1))/ge;
    s/\\u\{?([0-9a-fA-F]{4})\}?/chr(hex($1))/ge;
    $_ = lc $_;
    s/[\x27\x22\x60,+\s]//g;
    s/\\//g;
  ' 2>/dev/null
}

# Static-decoded from the 2026-08 sample's obfuscated string table; see IOCS.md.
# Written as they appear AFTER normalization.
HIGH_FILE="$(mktemp -t repoguard-high)"
CTX_FILE="$(mktemp -t repoguard-ctx)"
trap 'rm -f "$HIGH_FILE" "$CTX_FILE"' EXIT

cat > "$HIGH_FILE" <<'IOCEOF'
eth_blocknumber
eth_gettransactioncount
eth_getblockbynumber
ethereum-rpc.publicnode.com
eth.drpc.org
public.blastapi.io
eth.blockscout.com
1rpc.io/eth
0xa322e5f3d311d3080e6f0121063e9adc2490ef1a
x-payload-b64
missingx-payload-b6
emptypayloadbody
:443/0x/ls
:443/0x/cl
module=account&action=txlist
q4fzkxx{!h
y-p_>d$0b&
@^1aqk
global[_v]=
global[_h]=
global[_h2]=
global[_t_s]=
global[_t_u]=
global[r]=require
global[m]=module
IOCEOF

cat > "$CTX_FILE" <<'CTXEOF'
node:child_process
windowshide
detached:!![]
fromcharcode
CTXEOF

echo "Security scan (mode: $MODE)"
CHECKED=0

while IFS= read -r f; do
  [ -z "$f" ] && continue

  # A real .env must never be committed. A previous incident also deleted the
  # .gitignore rules that prevent it, so never rely on .gitignore alone.
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

  # Someone weakening the guard in the same change that adds a payload is the
  # exact move the last incident used against .gitignore.
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

  raw=$(content_of "$f")
  [ -z "$raw" ] && continue

  ASSET=0
  looks_like_inline_asset "$raw" && ASSET=1
  OBFUSCATED=0

  # -- shape checks (survive payload regeneration) ---------------------------

  longest=$(printf '%s' "$raw" | awk '{ if (length($0) > m) m = length($0) } END { print m+0 }')
  if [ "${longest:-0}" -gt "$MAX_LINE" ]; then
    if [ "$ASSET" -eq 1 ]; then
      warning "$f — line of $longest chars, but the file holds inline SVG/data URIs"
    else
      report "$f — line of $longest chars (limit $MAX_LINE); padded-payload shape"
      OBFUSCATED=1
    fi
  fi

  # The padding trick itself: catches a payload split under the length limit.
  # perl, not grep: BSD grep rejects {n,} above 255 and errors out.
  if printf '%s' "$raw" | perl -0777 -ne "exit(/[ \\t]{$MAX_RUN_SPACES,}/ ? 0 : 1)"; then
    report "$f — run of ${MAX_RUN_SPACES}+ whitespace chars; off-screen padding shape"
    OBFUSCATED=1
  fi

  hexidents=$(printf '%s' "$raw" | grep -oE '_0x[0-9a-fA-F]{4,}' | wc -l | tr -d ' ')
  if [ "${hexidents:-0}" -ge "$MAX_HEX_IDENTS" ]; then
    report "$f — $hexidents hex-mangled identifiers; obfuscator output"; OBFUSCATED=1
  fi

  escapes=$(printf '%s' "$raw" | grep -oE '\\x[0-9a-fA-F]{2}' | wc -l | tr -d ' ')
  if [ "${escapes:-0}" -ge "$MAX_ESCAPES" ]; then
    report "$f — $escapes \\xNN escapes; obfuscator output"; OBFUSCATED=1
  fi

  if [ "$ASSET" -eq 0 ]; then
    b64run=$(printf '%s' "$raw" | perl -0777 -ne "
        my \$m = 0;
        while (/([A-Za-z0-9+\/]{$MAX_B64_RUN,}={0,2})/g) { \$m = length(\$1) if length(\$1) > \$m }
        print \$m;" 2>/dev/null)
    if [ "${b64run:-0}" -ge "$MAX_B64_RUN" ]; then
      report "$f — inline base64 blob of ${b64run} chars"; OBFUSCATED=1
    fi
  fi

  norm=$(printf '%s' "$raw" | normalize)

  # The string-array-rotation preamble every one of these samples ships with.
  if ! is_guard_file "$f" \
     && printf '%s' "$norm" | grep -qF 'push](' \
     && printf '%s' "$norm" | grep -qF 'shift]()'; then
    report "$f — string-array rotation preamble (push/shift); obfuscator output"
    OBFUSCATED=1
  fi

  # -- indicators (normalized, so split literals rejoin) --------------------

  if ! is_guard_file "$f"; then
    hits=$(printf '%s' "$norm" | grep -oF -f "$HIGH_FILE" 2>/dev/null | sort -u | tr '\n' ' ')
    [ -n "$hits" ] && report "$f — known payload indicators: $hits"

    ctx=$(printf '%s' "$norm" | grep -oF -f "$CTX_FILE" 2>/dev/null | sort -u | tr '\n' ' ')
    if [ -n "$ctx" ]; then
      if [ "$OBFUSCATED" -eq 1 ]; then
        report "$f — obfuscated AND using loader APIs: $ctx"
      else
        warning "$f — uses loader-adjacent APIs (normal in tooling): $ctx"
      fi
    fi
  fi

  # -- config files execute at build time: nothing below belongs in one ------

  if is_config "$f"; then
    ex=$(printf '%s' "$norm" | grep -oE 'child_process|eval\(|newfunction\(|spawnsync|\bspawn\(|atob\(|frombase64' \
         | sort -u | tr '\n' ' ')
    [ -n "$ex" ] && report "$f — config file contains runtime/exec APIs: $ex"
    # createRequire is legitimate in modern ESM configs, so warn, do not block.
    printf '%s' "$norm" | grep -qF 'createrequire' \
      && warning "$f — config file uses createRequire; confirm it is yours"
    # `eval` reached indirectly (obj[key](eval, src)) has no "eval(" to match.
    printf '%s' "$norm" | grep -qE '\(eval,|\[eval\]|=eval;' \
      && report "$f — config file passes eval as a value (indirect eval)"
  fi

  # -- npm lifecycle scripts: how this class of worm actually propagates -----

  if [ "$f" = "package.json" ]; then
    life=$(printf '%s' "$raw" | perl -0777 -ne '
      if (/"scripts"\s*:\s*\{(.*?)\}/s) { $s=$1;
        while ($s =~ /"(preinstall|install|postinstall|prepare|prepublish|prepublishOnly)"\s*:\s*"((?:[^"\\]|\\.)*)"/g) {
          print "$1=$2\n";
        }
      }' 2>/dev/null)
    while IFS= read -r L; do
      [ -z "$L" ] && continue
      if printf '%s' "$L" | grep -qiE 'node[[:space:]]+-e|curl|wget|base64|eval|child_process|\|[[:space:]]*(sh|bash)|chmod'; then
        report "package.json — lifecycle script runs fetched or evaluated code: ${L%%=*}"
      else
        warning "package.json — lifecycle script present, confirm you added it: ${L%%=*}"
      fi
    done <<< "$life"
  fi

  # -- CI workflows: a second execution surface, with secrets attached -------

  case "$f" in
    .github/workflows/*)
      printf '%s' "$raw" | grep -qE '(curl|wget)[^|]*\|[[:space:]]*(sudo[[:space:]]+)?(sh|bash)' \
        && report "$f — workflow pipes a download straight into a shell"
      ;;
  esac

done < <(files_to_check)

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
