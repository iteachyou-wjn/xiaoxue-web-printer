# 梦想家WEB打印控件 · 文档站点

基于 [VitePress](https://vitepress.dev) 搭建的「梦想家WEB打印控件」官方介绍与文档项目。

## 本地开发

```bash
# 安装依赖
npm install

# 启动本地开发服务器（默认 http://localhost:5173）
npm run docs:dev
```

## 构建与预览

```bash
# 构建静态站点（输出到 docs/.vitepress/dist）
npm run docs:build

# 本地预览构建产物
npm run docs:preview
```

## 目录结构

```
dreamer-printer-web/
├── package.json
├── docs/
│   ├── .vitepress/
│   │   └── config.mjs        # 站点配置（导航 / 侧边栏 / 搜索等）
│   ├── public/
│   │   ├── demo/             # 官方 Demo 演示页（可在线访问）
│   │   ├── dreamer-printer-sdk.js       # 官方 SDK（源码版）
│   │   ├── dreamer-printer-sdk.min.js   # 官方 SDK（压缩版）
│   │   └── dreamer-logo.svg
│   ├── index.md              # 首页
│   ├── guide/                # 开始使用：简介 / 特性 / 架构 / 快速开始 / 部署 / FAQ
│   ├── usage/                # 使用指南：SDK 连接 / 打印 / 预览 / 任务 / BarTender / 框架集成
│   ├── api/                  # API 参考：SDK / 事件 / WebSocket 协议
│   └── changelog.md          # 更新日志
```

## 内容维护

- 所有文档为 Markdown，修改后保存即热更新；
- Demo 演示页直接复用 `demo/` 目录中的官方示例，`../dreamer-printer-sdk.min.js` 相对路径可正常解析；
- 如需部署到子路径（如 GitHub Pages 的仓库路径），在 `docs/.vitepress/config.mjs` 中配置 `base`：
  ```js
  export default defineConfig({
    base: '/dreamer-printer-web/',   // 按实际部署路径修改
    ...
  })
  ```

## 关联仓库

- 打印服务源码与 SDK：`dreamer-print-service`（Java + SWT 本机打印服务）
