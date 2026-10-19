/**
 * VitePress build wrapper. On Windows, drive-letter casing in Rollup facadeModuleId
 * can differ from realpath srcPath and break resolvePageImports (vitepress 1.6.x).
 */
import { execSync } from 'node:child_process'
import { readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { platform } from 'node:os'

const docsRoot = join(dirname(fileURLToPath(import.meta.url)), '..')

function patchVitePressWindowsPathMatch() {
  if (platform() !== 'win32') return
  const vpNode = join(docsRoot, 'node_modules', 'vitepress', 'dist', 'node')
  const chunkFile = readdirSync(vpNode).find(
    (name) => name.startsWith('chunk-') && name.endsWith('.js')
  )
  if (!chunkFile) return
  const chunkPath = join(vpNode, chunkFile)
  const needle = 'chunk.facadeModuleId === srcPath'
  const replacement =
    'chunk.facadeModuleId?.toLowerCase() === srcPath.toLowerCase()'
  let source = readFileSync(chunkPath, 'utf8')
  if (source.includes(needle)) {
    writeFileSync(chunkPath, source.replace(needle, replacement))
  }
}

patchVitePressWindowsPathMatch()
execSync('vitepress build docs', { cwd: docsRoot, stdio: 'inherit' })
