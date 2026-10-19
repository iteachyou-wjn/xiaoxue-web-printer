/**
 * Copy VitePress dist to repo-root pages/ for GitHub Pages, then sync SDK.
 */
import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const distDir = join(repoRoot, 'docs', 'docs', '.vitepress', 'dist')
const pagesDir = join(repoRoot, 'pages')
const sdkDir = join(repoRoot, 'sdk')

if (!existsSync(distDir)) {
  console.error('Missing dist. Run npm run docs:build in docs/ first.')
  process.exit(1)
}

rmSync(pagesDir, { recursive: true, force: true })
mkdirSync(pagesDir, { recursive: true })
cpSync(distDir, pagesDir, { recursive: true })

const pagesSdk = join(pagesDir, 'sdk')
mkdirSync(pagesSdk, { recursive: true })
for (const name of ['xiaoxue-printer-sdk.js', 'xiaoxue-printer-sdk.min.js']) {
  cpSync(join(sdkDir, name), join(pagesSdk, name))
}

console.log('Synced', distDir, '->', pagesDir, '+ sdk/')
