#!/usr/bin/env node
import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const root = path.resolve(import.meta.dirname, '..')
const baselinePath = path.join(root, 'lint-baseline.json')
const shrink = process.argv.includes('--shrink-baseline')

function runOxlint() {
  try {
    return execFileSync('pnpm', ['exec', 'oxlint', '--type-aware', '-f', 'json'], {
      cwd: root,
      encoding: 'utf8',
      maxBuffer: 64 * 1024 * 1024,
    })
  } catch (error) {
    if (typeof error.stdout === 'string' && error.stdout.length > 0) return error.stdout
    throw error
  }
}

function relative(file) {
  return path.relative(root, path.resolve(root, file)).split(path.sep).join('/')
}

const { diagnostics } = JSON.parse(runOxlint())
const counts = {}
const byKey = new Map()
for (const diagnostic of diagnostics) {
  const file = relative(diagnostic.filename)
  const key = `${file}\u0000${diagnostic.code}`
  counts[file] ??= {}
  counts[file][diagnostic.code] = (counts[file][diagnostic.code] ?? 0) + 1
  if (!byKey.has(key)) byKey.set(key, [])
  byKey.get(key).push(diagnostic)
}

const baseline = existsSync(baselinePath) ? JSON.parse(readFileSync(baselinePath, 'utf8')) : {}
const grown = []
const shrunk = []
for (const [file, rules] of Object.entries(counts)) {
  for (const [rule, count] of Object.entries(rules)) {
    const allowance = baseline[file]?.[rule] ?? 0
    if (count > allowance) grown.push({ file, rule, count, allowance })
  }
}
for (const [file, rules] of Object.entries(baseline)) {
  for (const [rule, allowance] of Object.entries(rules)) {
    const count = counts[file]?.[rule] ?? 0
    if (count < allowance) shrunk.push({ file, rule, count, allowance })
  }
}

if (shrink) {
  const next = {}
  for (const [file, rules] of Object.entries(baseline)) {
    for (const [rule, allowance] of Object.entries(rules)) {
      const count = Math.min(counts[file]?.[rule] ?? 0, allowance)
      if (count === 0) continue
      next[file] ??= {}
      next[file][rule] = count
    }
  }
  writeFileSync(baselinePath, `${JSON.stringify(next, null, 2)}\n`)
  console.log('lint-baseline.json lowered to the current counts. It never rises.')
}

for (const { file, rule, count, allowance } of grown) {
  console.error(`\n${file}: ${rule} — ${count} found, ${allowance} allowed. Fix the new ones:`)
  for (const diagnostic of byKey.get(`${file}\u0000${rule}`)) {
    const line = diagnostic.labels?.[0]?.span?.line ?? '?'
    console.error(`  ${file}:${line} ${diagnostic.message}`)
    if (diagnostic.help) console.error(`    ${diagnostic.help}`)
  }
}
if (!shrink) {
  for (const { file, rule, count, allowance } of shrunk) {
    console.error(
      `\n${file}: ${rule} — down to ${count} from an allowance of ${allowance}. Run \`pnpm lint --shrink-baseline\` so it cannot grow back.`
    )
  }
}

const failed = grown.length > 0 || (!shrink && shrunk.length > 0)
const remaining = Object.values(shrink ? JSON.parse(readFileSync(baselinePath, 'utf8')) : baseline)
  .flatMap((rules) => Object.values(rules))
  .reduce((sum, count) => sum + count, 0)
if (failed) process.exit(1)
console.log(`lint: no new problems. ${remaining} known ones left in lint-baseline.json.`)
