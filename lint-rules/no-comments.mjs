const DEFAULTS = {
  allow: ['WARNING'],
  publicApi: [],
}

const DIRECTIVE =
  /^\s*(?:(?:oxlint|eslint)-(?:disable|enable)|@ts-(?:expect-error|ignore|nocheck)|global\s)/

const MEMBER_TYPES = new Set([
  'TSPropertySignature',
  'TSMethodSignature',
  'TSIndexSignature',
  'TSCallSignatureDeclaration',
  'TSEnumMember',
  'PropertyDefinition',
  'MethodDefinition',
])

function isAllowedLine(value, allow) {
  const text = value.trim()
  return allow.some((prefix) => text.startsWith(`${prefix}:`))
}

function removalRange(text, comment) {
  const [start, end] = comment.range
  let lineStart = start
  while (lineStart > 0 && (text[lineStart - 1] === ' ' || text[lineStart - 1] === '\t')) {
    lineStart--
  }
  let lineEnd = end
  while (lineEnd < text.length && (text[lineEnd] === ' ' || text[lineEnd] === '\t')) {
    lineEnd++
  }
  const ownsLine =
    (lineStart === 0 || text[lineStart - 1] === '\n') &&
    (lineEnd === text.length || text[lineEnd] === '\n' || text[lineEnd] === '\r')
  if (ownsLine) {
    if (text[lineEnd] === '\r') lineEnd++
    if (text[lineEnd] === '\n') lineEnd++
    return [lineStart, lineEnd]
  }
  return [lineStart, end]
}

export default {
  meta: {
    type: 'suggestion',
    fixable: 'code',
    docs: {
      description:
        'Disallow comments other than directives, `WARNING:` lines and TSDoc on the public API',
      recommended: true,
    },
    schema: [
      {
        type: 'object',
        properties: {
          allow: { type: 'array', items: { type: 'string' } },
          publicApi: { type: 'array', items: { type: 'string' } },
        },
        additionalProperties: false,
      },
    ],
    defaultOptions: [DEFAULTS],
    messages: {
      noComments:
        'Remove this comment. The code says what it does; why it does it belongs in the commit message or the PR. A hazard a reader must not miss gets one line starting `// WARNING:`.',
    },
  },

  create(context) {
    const { allow, publicApi } = { ...DEFAULTS, ...context.options[0] }
    const filename = context.filename.replaceAll('\\', '/')
    const isPublicApiFile = publicApi.some((pattern) => new RegExp(pattern).test(filename))
    const exportedRanges = []
    const documentableStarts = new Set()

    function recordExport(node) {
      exportedRanges.push(node.range)
      documentableStarts.add(node.range[0])
    }

    function recordMember(node) {
      const [start] = node.range
      if (exportedRanges.some(([from, to]) => start >= from && start < to)) {
        documentableStarts.add(start)
      }
    }

    const visitors = {
      ExportNamedDeclaration: recordExport,
      ExportDefaultDeclaration: recordExport,
      'Program:exit'() {
        const { sourceCode } = context
        const text = sourceCode.text
        for (const comment of sourceCode.getAllComments()) {
          if (comment.type === 'Shebang') continue
          if (DIRECTIVE.test(comment.value)) continue
          if (comment.type === 'Line' && isAllowedLine(comment.value, allow)) continue
          if (comment.type === 'Line' && comment.value.startsWith('/ <reference')) continue
          if (comment.type === 'Block' && comment.value.startsWith('*') && isPublicApiFile) {
            let next = comment.range[1]
            while (next < text.length && /\s/.test(text[next])) next++
            if (documentableStarts.has(next)) continue
          }
          context.report({
            loc: comment.loc,
            messageId: 'noComments',
            fix: (fixer) => fixer.removeRange(removalRange(text, comment)),
          })
        }
      },
    }
    for (const type of MEMBER_TYPES) visitors[type] = recordMember
    return visitors
  },
}
