import maxFileLines from './max-file-lines.mjs'
import noComments from './no-comments.mjs'

export default {
  meta: {
    name: 'package',
    version: '1.0.0',
  },
  rules: {
    'max-file-lines': maxFileLines,
    'no-comments': noComments,
  },
}
