import { readFileSync, writeFileSync, existsSync, statSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

// Use the application's existing Markdown renderer; no separate docs toolchain.
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const require = createRequire(resolve(root, 'apps/serviceflow-web/package.json'))
const MarkdownIt = require('markdown-it')
const md = new MarkdownIt({ html: false, linkify: false, typographer: false })
const chapters = [
  ['overview', '开始阅读'],
  ['chat', 'Agent 与业务执行'],
  ['orders', 'Agent 与业务执行'],
  ['confirmation', 'Agent 与业务执行'],
  ['refund', 'Agent 与业务执行'],
  ['after-sales', 'Agent 与业务执行'],
  ['knowledge', '知识与 RAG'],
  ['rag', '知识与 RAG'],
  ['evaluation', '知识与 RAG'],
  ['operations', '开发与验证'],
  ['verification', '开发与验证'],
  ['glossary', '开发与验证'],
]
const sourcePath = (id) => resolve(root, 'docs/wiki', `${id}.md`)
const slug = (value) =>
  value
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .trim()
    .replace(/\s+/g, '-')
const route = (id, anchor = '') => `#article/${id}${anchor ? '/' + encodeURIComponent(anchor) : ''}`
const pages = chapters.map(([id, group]) => {
  const source = readFileSync(sourcePath(id), 'utf8')
  const tokens = md.parse(source, {})
  const headings = []
  const used = new Map()
  let title
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i]
    if (token.type !== 'heading_open') continue
    const text = tokens[i + 1].content
    if (token.tag === 'h1') {
      if (title) throw new Error(`${id}: use one page title`)
      title = text
    }
    const base = slug(text)
    const count = used.get(base) || 0
    used.set(base, count + 1)
    const anchor = base + (count ? `-${count}` : '')
    token.attrSet('id', anchor)
    if (token.tag === 'h2' || token.tag === 'h3')
      headings.push({ title: text, id: anchor, level: token.tag })
  }
  if (!title) throw new Error(`${id}: missing title`)
  return { id, group, title, tokens, headings }
})
const byPath = new Map(pages.map((page) => [sourcePath(page.id), page]))
let links = 0
function resolveLink(href, page) {
  if (/^https?:\/\//.test(href)) return href
  if (/^[a-z]+:/i.test(href)) throw new Error(`${page.id}: unsupported URL ${href}`)
  const [path, hash = ''] = href.split('#')
  const target = path
    ? resolve(dirname(sourcePath(page.id)), decodeURIComponent(path))
    : sourcePath(page.id)
  const repoPath = relative(root, target).split(sep).join('/')
  if (repoPath.startsWith('../') || !existsSync(target))
    throw new Error(`${page.id}: missing/outside link ${href}`)
  links++
  const article = byPath.get(target)
  if (article) {
    const anchor = decodeURIComponent(hash)
    if (anchor && !article.tokens.some((token) => token.attrGet('id') === anchor)) {
      throw new Error(`${page.id}: missing heading ${href}`)
    }
    return route(article.id, anchor)
  }
  // Body/search work offline; repository files are explicit source links.
  const kind = statSync(target).isDirectory() ? 'tree' : 'blob'
  return `https://github.com/Titans23/serviceflow/${kind}/main/${repoPath.split('/').map(encodeURIComponent).join('/')}${hash ? '#' + hash : ''}`
}
function visit(tokens, page) {
  for (const token of tokens) {
    if (token.type === 'link_open') {
      const href = resolveLink(token.attrGet('href'), page)
      token.attrSet('href', href)
      if (!href.startsWith('#')) {
        token.attrSet('target', '_blank')
        token.attrSet('rel', 'noopener noreferrer')
      }
    }
    if (token.type === 'image')
      throw new Error(`${page.id}: images must be embedded for offline use`)
    if (token.children) visit(token.children, page)
  }
}
const data = pages.map((page) => {
  visit(page.tokens, page)
  return {
    id: page.id,
    group: page.group,
    title: page.title,
    headings: page.headings,
    html: md.renderer.render(page.tokens, md.options, {}),
    text: page.tokens
      .filter((token) => token.type === 'inline' || token.type === 'fence')
      .map((token) => token.content.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1'))
      .join(' ')
      .replace(/\s+/g, ' '),
  }
})
const template = readFileSync(resolve(root, 'scripts/docs/wiki-template.html'), 'utf8')
const output = template.replace('/* WIKI_DATA */', JSON.stringify(data).replace(/</g, '\\u003c'))
const destination = resolve(root, 'docs/architecture-guide.html')
if (process.argv.includes('--check')) {
  if (!existsSync(destination) || readFileSync(destination, 'utf8') !== output) {
    throw new Error('Wiki is stale. Run: node scripts/docs/build-wiki.mjs')
  }
  console.log(
    `Wiki checked: ${pages.length} chapters, ${links} local links, generated HTML current.`,
  )
} else {
  writeFileSync(destination, output)
  console.log(
    `Wiki built: ${pages.length} chapters, ${links} local links → docs/architecture-guide.html`,
  )
}
