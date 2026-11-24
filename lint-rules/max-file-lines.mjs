const DEFAULTS = {
  max: 500,
  allow: {},
}

function countCodeLines(sourceCode) {
  const skipped = new Set()
  for (const comment of sourceCode.getAllComments()) {
    for (let line = comment.loc.start.line; line <= comment.loc.end.line; line++) {
      skipped.add(line)
    }
  }
  let count = 0
  sourceCode.lines.forEach((line, index) => {
    if (line.trim().length > 0 && !skipped.has(index + 1)) count++
  })
  return count
}

export default {
  meta: {
    type: 'suggestion',
    docs: {
      description: 'Cap file length, with exact per-file allowances that can only shrink',
      recommended: true,
    },
    schema: [
      {
        type: 'object',
        properties: {
          max: { type: 'integer', minimum: 1 },
          allow: { type: 'object', additionalProperties: { type: 'integer', minimum: 1 } },
        },
        additionalProperties: false,
      },
    ],
    defaultOptions: [DEFAULTS],
    messages: {
      tooLong:
        'File has {{count}} lines of code; the limit is {{max}}. Split it into modules with one responsibility each rather than raising the limit.',
      grew: 'File has {{count}} lines of code but its allowance is {{allowance}}. Move code out until it is back under the allowance; do not raise it.',
      shrank:
        'File has {{count}} lines of code, below its allowance of {{allowance}}. Lower the allowance in .oxlintrc.json to {{count}} so it cannot grow back.',
    },
  },

  create(context) {
    const { max, allow } = { ...DEFAULTS, ...context.options[0] }
    const filename = context.filename.replaceAll('\\', '/')
    const allowance = Object.entries(allow).find(([path]) => filename.endsWith(`/${path}`))?.[1]

    return {
      'Program:exit'() {
        const count = countCodeLines(context.sourceCode)
        const loc = { start: { line: 1, column: 0 }, end: { line: 1, column: 0 } }
        if (allowance === undefined) {
          if (count > max) context.report({ loc, messageId: 'tooLong', data: { count, max } })
          return
        }
        if (count > allowance) {
          context.report({ loc, messageId: 'grew', data: { count, allowance } })
        } else if (count < allowance) {
          context.report({ loc, messageId: 'shrank', data: { count, allowance } })
        }
      },
    }
  },
}
