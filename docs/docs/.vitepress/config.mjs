import { defineConfig } from 'vitepress'

export default defineConfig({
  title: '晓雪WEB打印控件',
  description: '基于 WebSocket 的本机 Web 打印控件，让浏览器轻松实现真实、稳定、可预览的打印能力。',

  lang: 'zh-CN',
  base: '/xiaoxue-web-printer/',
  cleanUrls: true,
  lastUpdated: true,

  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/xiaoxue-web-printer/favicon.png' }],
    ['meta', { name: 'keywords', content: '晓雪WEB打印控件,Web打印,打印控件,BarTender,静默打印,套打,浏览器打印' }],
    ['meta', { name: 'theme-color', content: '#04a858' }],
  ],

  themeConfig: {
    logo: '/dreamer-logo.png',

    nav: [
      { text: '首页', link: '/' },
      { text: '指南', link: '/guide/intro', activeMatch: '/guide/' },
      { text: '使用指南', link: '/usage/getting-started', activeMatch: '/usage/' },
      { text: 'API 参考', link: '/api/sdk', activeMatch: '/api/' },
      { text: '在线 Demo', link: '/demo/', target: '_blank' },
      { text: '更新日志', link: '/changelog' },
    ],

    sidebar: [
      {
        text: '开始使用',
        collapsed: false,
        items: [
          { text: '产品简介', link: '/guide/intro' },
          { text: '功能特性', link: '/guide/features' },
          { text: '技术架构', link: '/guide/architecture' },
          { text: '快速开始', link: '/guide/quick-start' },
          { text: '安装部署', link: '/guide/installation' },
        ],
      },
      {
        text: '使用指南',
        collapsed: false,
        items: [
          { text: 'SDK 接入与连接', link: '/usage/getting-started' },
          { text: '打印服务', link: '/usage/print' },
          { text: 'BarTender 模板打印', link: '/usage/bartender' },
          { text: 'JasperReport 报表', link: '/usage/jasper' },
        ],
      },
      {
        text: 'API 参考',
        collapsed: false,
        items: [
          { text: 'SDK 类参考', link: '/api/sdk' },
          { text: '事件参考', link: '/api/events' },
          { text: 'WebSocket 协议', link: '/api/protocol' },
        ],
      },
      {
        text: '其他',
        collapsed: false,
        items: [
          { text: '常见问题', link: '/guide/faq' },
          { text: '更新日志', link: '/changelog' },
        ],
      },
    ],

    outline: {
      level: [2, 3],
      label: '本页目录',
    },

    docFooter: {
      prev: '上一篇',
      next: '下一篇',
    },

    footer: {
      message: '基于 MIT 协议开源',
      copyright: 'Copyright © 2026 I Teach You, 我教你！',
    },

    search: {
      provider: 'local',
      options: {
        translations: {
          button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
          modal: {
            noResultsText: '未找到相关结果',
            resetButtonTitle: '清除查询',
            footer: { selectText: '选择', navigateText: '切换', closeText: '关闭' },
          },
        },
      },
    },
  },
})
