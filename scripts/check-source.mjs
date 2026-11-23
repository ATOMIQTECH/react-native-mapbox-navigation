#!/usr/bin/env node
import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const MAX_LINES = 500

const LINE_ALLOWANCES = {
  'android/src/main/java/expo/modules/mapboxnavigation/MapboxNavigationView.kt': 3010,
  'ios/MapboxNavigationView.swift': 2701,
}

const ALLOWED_COMMENT = /^(?:WARNING:|frozen_string_literal:)/

const C_LIKE = new Set(['.swift', '.kt', '.kts', '.gradle'])
const HASH_LINE = new Set(['.rb', '.podspec', '.sh', '.pl'])

const root = path.resolve(import.meta.dirname, '..')
const fix = process.argv.includes('--fix')

function languageOf(file) {
  if (file.startsWith('.githooks/')) return 'hash'
  const extension = path.extname(file)
  if (C_LIKE.has(extension)) return extension === '.swift' ? 'swift' : 'kotlin'
  if (HASH_LINE.has(extension)) return 'hash'
  return null
}

function scanCLike(text, swift) {
  const comments = []
  let i = 0

  function skipString(quote, multiline, rawHashes) {
    const closing = multiline ? `"""${'#'.repeat(rawHashes)}` : `${quote}${'#'.repeat(rawHashes)}`
    while (i < text.length) {
      if (text.startsWith(closing, i)) {
        i += closing.length
        return
      }
      const char = text[i]
      if (char === '\\' && rawHashes === 0) {
        if (swift && text[i + 1] === '(') {
          i += 2
          scanCode(')')
          continue
        }
        i += 2
        continue
      }
      if (!swift && char === '$' && text[i + 1] === '{' && quote === '"') {
        i += 2
        scanCode('}')
        continue
      }
      i++
    }
  }

  function scanCode(closer) {
    let depth = 0
    while (i < text.length) {
      const char = text[i]
      if (closer && depth === 0 && char === closer) {
        i++
        return
      }
      if (char === '(' || char === '{' || char === '[') depth++
      else if (char === ')' || char === '}' || char === ']') depth = Math.max(0, depth - 1)
      if (text.startsWith('//', i)) {
        const end = text.indexOf('\n', i)
        const stop = end === -1 ? text.length : end
        comments.push([i, stop, text.slice(i + 2, stop)])
        i = stop
        continue
      }
      if (text.startsWith('/*', i)) {
        const start = i
        let nesting = 0
        while (i < text.length) {
          if (text.startsWith('/*', i)) {
            nesting++
            i += 2
          } else if (text.startsWith('*/', i)) {
            nesting--
            i += 2
            if (nesting === 0) break
          } else i++
        }
        comments.push([start, i, text.slice(start + 2, i - 2)])
        continue
      }
      if (swift && char === '#') {
        let hashes = 0
        while (text[i + hashes] === '#') hashes++
        if (text[i + hashes] === '"') {
          i += hashes
          const multiline = text.startsWith('"""', i)
          i += multiline ? 3 : 1
          skipString('"', multiline, hashes)
          continue
        }
      }
      if (text.startsWith('"""', i)) {
        i += 3
        skipString('"', true, 0)
        continue
      }
      if (char === '"') {
        i++
        skipString('"', false, 0)
        continue
      }
      if (!swift && char === "'") {
        i++
        skipString("'", false, 0)
        continue
      }
      i++
    }
  }

  scanCode(null)
  return comments
}

function scanHashLines(text) {
  const comments = []
  let offset = 0
  let heredoc = null
  const lines = text.split('\n')
  lines.forEach((line, index) => {
    const start = offset
    offset += line.length + 1
    if (heredoc) {
      if (line.trim() === heredoc) heredoc = null
      return
    }
    const opener = line.match(/<<[~-]?(['"]?)([A-Z_][A-Z0-9_]*)\1/)
    if (opener) heredoc = opener[2]
    const trimmed = line.trimStart()
    if (!trimmed.startsWith('#')) return
    if (index === 0 && trimmed.startsWith('#!')) return
    const column = line.length - trimmed.length
    comments.push([start + column, start + line.length, trimmed.slice(1)])
  })
  return comments
}

function isAllowed(body) {
  return ALLOWED_COMMENT.test(body.replace(/^[/*#!\s]+/, ''))
}

function isBlank(text, from, to) {
  return text.slice(from, to).trim() === ''
}

function lineBounds(text, start, end) {
  let lineStart = start
  while (lineStart > 0 && text[lineStart - 1] !== '\n') lineStart--
  let lineEnd = end
  while (lineEnd < text.length && text[lineEnd] !== '\n') lineEnd++
  return [lineStart, lineEnd]
}

function strip(text, comments) {
  const ranges = []
  for (const [start, end] of comments) {
    const [lineStart, lineEnd] = lineBounds(text, start, end)
    if (isBlank(text, lineStart, start) && isBlank(text, end, lineEnd)) {
      ranges.push([lineStart, Math.min(lineEnd + 1, text.length)])
    } else {
      let from = start
      while (from > lineStart && (text[from - 1] === ' ' || text[from - 1] === '\t')) from--
      ranges.push([from, end])
    }
  }
  ranges.sort((a, b) => a[0] - b[0])
  let result = ''
  let cursor = 0
  for (const [from, to] of ranges) {
    if (from < cursor) continue
    result += text.slice(cursor, from)
    cursor = to
    const before = result.endsWith('\n\n') || result.endsWith('{\n') || result.endsWith('(\n')
    const nextLineEnd = text.indexOf('\n', cursor)
    const nextLine = nextLineEnd === -1 ? '' : text.slice(cursor, nextLineEnd)
    if (before && nextLineEnd !== -1 && nextLine.trim() === '') {
      const following = ranges.find(([start]) => start >= nextLineEnd + 1)
      if (!following || following[0] !== nextLineEnd + 1) cursor = nextLineEnd + 1
    }
  }
  return result + text.slice(cursor)
}

function codeLineCount(text, comments) {
  const commentLines = new Set()
  for (const [start, end] of comments) {
    const first = text.slice(0, start).split('\n').length
    const last = text.slice(0, end).split('\n').length
    const [lineStart, lineEnd] = lineBounds(text, start, end)
    if (!isBlank(text, lineStart, start)) continue
    const lastLineEnd = lineBounds(text, end, end)[1]
    if (!isBlank(text, end, lastLineEnd) && lineEnd !== lastLineEnd) continue
    for (let line = first; line <= last; line++) commentLines.add(line)
  }
  return text
    .split('\n')
    .filter((line, index) => line.trim() !== '' && !commentLines.has(index + 1)).length
}

const files = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard'], {
  cwd: root,
  encoding: 'utf8',
})
  .split('\n')
  .filter((file) => file && languageOf(file))

const problems = []
for (const file of files) {
  const language = languageOf(file)
  const absolute = path.join(root, file)
  let text = readFileSync(absolute, 'utf8')
  const scan = () =>
    language === 'hash' ? scanHashLines(text) : scanCLike(text, language === 'swift')
  let comments = scan().filter(([, , body]) => !isAllowed(body))
  if (fix && comments.length > 0) {
    text = strip(text, comments)
    writeFileSync(absolute, text)
    comments = scan().filter(([, , body]) => !isAllowed(body))
  }
  for (const [start] of comments) {
    const line = text.slice(0, start).split('\n').length
    problems.push(
      `${file}:${line}: remove this comment. The code says what it does; why belongs in the commit message or the PR. A hazard a reader must not miss gets one line starting WARNING:.`
    )
  }
  const count = codeLineCount(text, scan())
  const allowance = LINE_ALLOWANCES[file]
  if (allowance === undefined && count > MAX_LINES) {
    problems.push(
      `${file}: ${count} lines of code, over the ${MAX_LINES} limit. Split it into files with one responsibility each.`
    )
  } else if (allowance !== undefined && count > allowance) {
    problems.push(
      `${file}: ${count} lines of code, over its allowance of ${allowance}. Move code out; do not raise the allowance.`
    )
  } else if (allowance !== undefined && count < allowance) {
    problems.push(
      `${file}: ${count} lines of code, under its allowance of ${allowance}. Lower the allowance in scripts/check-source.mjs to ${count}.`
    )
  }
}

if (problems.length > 0) {
  console.error(problems.join('\n'))
  console.error(`\n${problems.length} problem(s).`)
  process.exit(1)
}
console.log(`check-source: ${files.length} files clean.`)
