#!/usr/bin/env node
/**
 * Cross-platform surface parity checker.
 *
 * The v2 -> v3 migration rewrites both native implementations, and neither
 * platform can be compile-checked from this repo (no android/gradlew, no Pods
 * installed). That makes silent surface drift the most likely way to break a
 * consumer: an Expo `Prop` quietly dropped on one platform, an event declared
 * on iOS but not Android, or a JS `add*Listener` pointing at an event name that
 * no longer exists natively. None of those fail a build — they just stop
 * working at runtime on one platform.
 *
 * This script reads the declarations straight out of the sources and asserts
 * they still agree. It is deliberately regex-based rather than a real parser:
 * the Expo module DSL declares everything as `Prop("name")` / `Events(...)`
 * string literals, so there is nothing to disambiguate, and this stays runnable
 * with zero dependencies in CI.
 *
 * Run with `npm run verify:parity`.
 */

import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = (rel) => readFileSync(path.join(root, rel), 'utf8')

const IOS_MODULE = 'ios/MapboxNavigationModule.swift'
const IOS_VIEW = 'ios/MapboxNavigationView.swift'
const ANDROID_VIEW = 'android/src/main/java/expo/modules/mapboxnavigation/MapboxNavigationView.kt'
const ANDROID_MODULE =
  'android/src/main/java/expo/modules/mapboxnavigation/MapboxNavigationModule.kt'
const JS_INDEX = 'src/index.tsx'
const JS_TYPES = 'src/MapboxNavigation.types.ts'

/**
 * Props that legitimately exist on one platform only.
 *
 * `androidActionButtons` configures the Android action-button row, which has no
 * iOS counterpart — iOS uses `nativeFloatingButtons` for the equivalent. Adding
 * to this list is a deliberate product decision, so it must be justified here
 * rather than silently tolerated.
 */
const PLATFORM_SPECIFIC_PROPS = {
  android: new Set(['androidActionButtons']),
  ios: new Set([]),
}

const matchAll = (src, re) => [...src.matchAll(re)].map((m) => m[1])

function extractProps(src) {
  return new Set(matchAll(src, /\bProp\(\s*"([A-Za-z0-9_]+)"\s*\)/g))
}

function extractAsyncFunctions(src) {
  return new Set(matchAll(src, /\bAsyncFunction\(\s*"([A-Za-z0-9_]+)"\s*\)/g))
}

/**
 * Pull event names out of every `Events(...)` call.
 *
 * Both platforms declare events twice — once at module level and once inside
 * the `View`. We union them, because the parity contract requires each event to
 * be available through both paths; checking the union catches a name that has
 * gone missing entirely, which is the regression that matters.
 */
function extractEvents(src) {
  const events = new Set()
  for (const block of src.matchAll(/\bEvents\(([\s\S]{0,2000}?)\)/g)) {
    for (const name of matchAll(block[1], /"([A-Za-z0-9_]+)"/g)) {
      events.add(name)
    }
  }
  return events
}

/** Event names JS actually subscribes to via the module emitter. */
function extractJsSubscribedEvents(src) {
  return new Set(matchAll(src, /emitter\.addListener\(\s*'([A-Za-z0-9_]+)'/g))
}

/**
 * Known, pre-existing gaps that predate the v3 migration.
 *
 * These are reported as warnings rather than failures so CI can be green on the
 * current tree without the debt becoming invisible. Each entry needs a reason
 * and an owner decision — the goal is for this object to be empty.
 *
 * `onDestinationChanged`: emitted on Android from the `setDestination` prop
 * setter, but absent from `ios/` entirely — it has never fired there. A real
 * gap; the iOS side still needs implementing.
 *
 * `onDestinationPreview`: declared on Android but, as of the v3 migration,
 * emitted by neither platform. It mirrored Drop-In's route-preview phase, which
 * Navigation SDK v3 removed; it never fired on iOS either. The JS helper is
 * kept and marked `@deprecated` so existing subscriptions do not break, so the
 * declaration remaining here is expected rather than a regression.
 */
const KNOWN_GAPS = new Set(['onDestinationPreview', 'onDestinationChanged'])

const failures = []
const warnings = []

const fail = (message, subject) => {
  if (subject !== undefined && KNOWN_GAPS.has(subject)) {
    warnings.push(message)
    return
  }
  failures.push(message)
}

const sorted = (set) => [...set].sort()
const difference = (a, b) => new Set([...a].filter((x) => !b.has(x)))

const ios = read(IOS_MODULE)
const android = read(ANDROID_MODULE)
const js = read(JS_INDEX)
const types = read(JS_TYPES)

// --- Props -----------------------------------------------------------------
const iosProps = extractProps(ios)
const androidProps = extractProps(android)

if (iosProps.size === 0 || androidProps.size === 0) {
  fail(
    `Parsed 0 props from one of the native modules (iOS: ${iosProps.size}, Android: ${androidProps.size}). ` +
      'The Expo module DSL probably changed shape — fix this script before trusting it.'
  )
}

const missingOnIos = difference(androidProps, iosProps)
const missingOnAndroid = difference(iosProps, androidProps)

for (const prop of missingOnIos) {
  if (!PLATFORM_SPECIFIC_PROPS.android.has(prop)) {
    fail(
      `Prop "${prop}" is declared on Android but not iOS. Add it, or list it in PLATFORM_SPECIFIC_PROPS.`
    )
  }
}

for (const prop of missingOnAndroid) {
  if (!PLATFORM_SPECIFIC_PROPS.ios.has(prop)) {
    fail(
      `Prop "${prop}" is declared on iOS but not Android. Add it, or list it in PLATFORM_SPECIFIC_PROPS.`
    )
  }
}

// Every native prop must be expressible from TypeScript, or consumers cannot
// reach it without a cast.
for (const prop of new Set([...iosProps, ...androidProps])) {
  if (!types.includes(prop) && !js.includes(prop)) {
    fail(`Prop "${prop}" is declared natively but appears nowhere in ${JS_TYPES} or ${JS_INDEX}.`)
  }
}

// --- Async functions -------------------------------------------------------
const iosFns = extractAsyncFunctions(ios)
const androidFns = extractAsyncFunctions(android)

for (const fn of difference(androidFns, iosFns)) {
  fail(`AsyncFunction "${fn}" exists on Android but not iOS.`)
}
for (const fn of difference(iosFns, androidFns)) {
  fail(`AsyncFunction "${fn}" exists on iOS but not Android.`)
}

// Each native function must be exported from JS, or it is unreachable.
for (const fn of new Set([...iosFns, ...androidFns])) {
  if (!new RegExp(`export\\s+(async\\s+)?function\\s+${fn}\\b`).test(js)) {
    fail(`AsyncFunction "${fn}" is implemented natively but not exported from ${JS_INDEX}.`)
  }
}

// --- Events ----------------------------------------------------------------
const iosEvents = extractEvents(ios)
const androidEvents = extractEvents(android)

for (const event of difference(androidEvents, iosEvents)) {
  fail(`Event "${event}" is declared on Android but not iOS.`, event)
}
for (const event of difference(iosEvents, androidEvents)) {
  fail(`Event "${event}" is declared on iOS but not Android.`, event)
}

// The regression this specifically guards: a JS listener helper wired to an
// event name that no longer exists natively fails silently and forever.
for (const event of extractJsSubscribedEvents(js)) {
  if (!iosEvents.has(event)) {
    fail(
      `JS subscribes to "${event}" but iOS declares no such event — the listener would never fire.`,
      event
    )
  }
  if (!androidEvents.has(event)) {
    fail(
      `JS subscribes to "${event}" but Android declares no such event — the listener would never fire.`,
      event
    )
  }
}

// --- Write-only props ------------------------------------------------------
/**
 * Catch props that are accepted and then ignored.
 *
 * This is the failure this migration was most likely to produce and the hardest
 * to notice: an Expo `Prop` setter stores a value on the view, nothing ever
 * reads it, and the prop silently does nothing forever. It compiles, it type-
 * checks, and the surface checks above all pass — the prop *is* declared on both
 * platforms and reachable from TypeScript. Counting reads is what actually finds
 * it. The first run of this check found ten on Android (including
 * `distanceUnit`, which meant Android quietly used the device locale while iOS
 * honoured the prop) and one on iOS.
 *
 * A "read" is any mention of the field that is not an assignment to it and not
 * its declaration. That is deliberately crude, and it errs towards silence: a
 * field read only inside a string interpolation counts. It is enough to catch a
 * value that is stored and then genuinely abandoned.
 */
function writeOnlyFields(src, declarationPattern) {
  const declared = new Set(matchAll(src, declarationPattern))
  const orphans = []

  for (const field of declared) {
    const mentions = [...src.matchAll(new RegExp(`(?<![.\\w])${field}\\b`, 'g'))]
    const reads = mentions.filter((match) => {
      const after = src.slice(match.index + field.length, match.index + field.length + 4)
      if (/^\s*=(?!=)/.test(after)) return false
      const before = src.slice(Math.max(0, match.index - 8), match.index)
      return !/var\s+$/.test(before)
    })
    if (reads.length === 0) orphans.push(field)
  }

  return orphans
}

const iosView = read(IOS_VIEW)
const androidView = read(ANDROID_VIEW)

// Only check fields that correspond to a declared prop — the views hold plenty
// of internal state whose lifecycle this heuristic has no opinion about.
const declaredProps = new Set([...iosProps, ...androidProps])
const orphanCheck = [
  ['iOS', writeOnlyFields(iosView, /^\s*var ([A-Za-z_]\w*)\s*:/gm), IOS_VIEW],
  [
    'Android',
    writeOnlyFields(androidView, /^\s*private var ([A-Za-z_]\w*)\s*[:=]/gm),
    ANDROID_VIEW,
  ],
]

for (const [platform, orphans, file] of orphanCheck) {
  for (const field of orphans) {
    if (!declaredProps.has(field)) continue
    fail(
      `Prop "${field}" is stored in ${file} but never read — it is accepted on ${platform} ` +
        'and does nothing. Wire it, or drop the field and report it as unsupported at runtime.'
    )
  }
}

// --- Report ----------------------------------------------------------------
const summary =
  `props: ${iosProps.size} iOS / ${androidProps.size} Android · ` +
  `functions: ${iosFns.size} / ${androidFns.size} · ` +
  `events: ${iosEvents.size} / ${androidEvents.size}`

if (warnings.length > 0) {
  console.warn(
    `\n! ${warnings.length} known pre-existing gap(s) — tracked in KNOWN_GAPS, not yet fixed:\n`
  )
  for (const message of warnings) {
    console.warn(`  • ${message}`)
  }
  console.warn('')
}

if (failures.length > 0) {
  console.error(`✗ Surface parity check failed (${summary})\n`)
  for (const message of failures) {
    console.error(`  • ${message}`)
  }
  console.error('')
  process.exit(1)
}

console.log(`✓ Surface parity check passed — ${summary}`)
console.log(`  props:     ${sorted(new Set([...iosProps, ...androidProps])).join(', ')}`)
console.log(`  functions: ${sorted(new Set([...iosFns, ...androidFns])).join(', ')}`)
console.log(`  events:    ${sorted(new Set([...iosEvents, ...androidEvents])).join(', ')}`)
