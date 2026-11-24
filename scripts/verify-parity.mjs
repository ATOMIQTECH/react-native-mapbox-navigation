#!/usr/bin/env node

import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(import.meta.dirname, '..')
const read = (rel) => readFileSync(path.join(root, rel), 'utf8')

const IOS_MODULE = 'ios/MapboxNavigationModule.swift'
const IOS_VIEW = 'ios/MapboxNavigationView.swift'
const ANDROID_VIEW = 'android/src/main/java/expo/modules/mapboxnavigation/MapboxNavigationView.kt'
const ANDROID_MODULE =
  'android/src/main/java/expo/modules/mapboxnavigation/MapboxNavigationModule.kt'
const JS_INDEX = 'src/index.tsx'
const JS_TYPES = 'src/MapboxNavigation.types.ts'

const PLATFORM_SPECIFIC_PROPS = {
  android: new Set(['androidActionButtons']),
  ios: new Set([]),
}

const matchAll = (src, re) => [...src.matchAll(re)].map((m) => m[1])

function extractProps(src) {
  return new Set(matchAll(src, /\bProp\(\s*"([A-Za-z0-9_]+)"\s*(?:,[^){]*)?\)/g))
}

function extractAsyncFunctions(src) {
  return new Set(matchAll(src, /\bAsyncFunction\(\s*"([A-Za-z0-9_]+)"\s*\)/g))
}

function extractEvents(src) {
  const events = new Set()
  for (const block of src.matchAll(/\bEvents\(([\s\S]{0,2000}?)\)/g)) {
    for (const name of matchAll(block[1], /"([A-Za-z0-9_]+)"/g)) {
      events.add(name)
    }
  }
  return events
}

function extractJsSubscribedEvents(src) {
  return new Set(matchAll(src, /emitter\.addListener\(\s*'([A-Za-z0-9_]+)'/g))
}

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

for (const prop of new Set([...iosProps, ...androidProps])) {
  if (!types.includes(prop) && !js.includes(prop)) {
    fail(`Prop "${prop}" is declared natively but appears nowhere in ${JS_TYPES} or ${JS_INDEX}.`)
  }
}

const iosFns = extractAsyncFunctions(ios)
const androidFns = extractAsyncFunctions(android)

for (const fn of difference(androidFns, iosFns)) {
  fail(`AsyncFunction "${fn}" exists on Android but not iOS.`)
}
for (const fn of difference(iosFns, androidFns)) {
  fail(`AsyncFunction "${fn}" exists on iOS but not Android.`)
}

for (const fn of new Set([...iosFns, ...androidFns])) {
  if (!new RegExp(`export\\s+(async\\s+)?function\\s+${fn}\\b`).test(js)) {
    fail(`AsyncFunction "${fn}" is implemented natively but not exported from ${JS_INDEX}.`)
  }
}

const iosEvents = extractEvents(ios)
const androidEvents = extractEvents(android)

for (const event of difference(androidEvents, iosEvents)) {
  fail(`Event "${event}" is declared on Android but not iOS.`, event)
}
for (const event of difference(iosEvents, androidEvents)) {
  fail(`Event "${event}" is declared on iOS but not Android.`, event)
}

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
