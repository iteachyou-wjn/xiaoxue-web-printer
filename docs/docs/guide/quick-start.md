# 快速开始

本页带你在 5 分钟内完成一次真实的 Web 打印。整体只需要三步：**启动本机打印服务 → 引入 SDK → 调用打印**。

## 第 1 步：启动本机打印服务

> 打印服务是"真实打印"的发动机，所有打印 / 预览请求都由它在本机完成。

下载或构建得到「晓雪WEB打印控件」程序（绿色版 exe 或安装版均可），双击启动。启动成功后：

1. 系统托盘出现打印机图标；
2. 服务监听本机 `ws://127.0.0.1:54321`。

可用以下命令确认服务已就绪：

```bat
netstat -ano | findstr 54321
```

出现 `LISTENING` 记录即表示服务正常运行。

::: tip 如何获取程序？
- 官方分发：直接运行分发的绿色版 / 安装版 exe；
- 自行构建：见 [安装部署](./installation) 中的源码编译与打包说明。
:::

## 第 2 步：引入 SDK

将 `xiaoxue-printer-sdk.min.js`（或源码版 `xiaoxue-printer-sdk.js`）放入项目，在页面中引入：

```html
<script src="path/to/xiaoxue-printer-sdk.min.js"></script>
```

引入后，全局会暴露 `XiaoxuePrinterSDK` 与 `Bartender` 两个类。

## 第 3 步：创建实例并连接

```js
// 创建 SDK 实例，url 指向本机打印服务
const sdk = new XiaoxuePrinterSDK({ url: 'ws://127.0.0.1:54321' });

// 订阅连接事件（可选）
sdk.on('open', () => console.log('已连接到打印服务'));
sdk.on('close', () => console.log('连接已断开'));

// 建立连接
sdk.connect();
```

::: tip 更省事的方式
`getPrinters()`、`getPageSize()`、`doPrint()`、`doPreview()` 等方法在未连接时会**自动连接**，因此你其实可以直接跳到打印调用，SDK 会自动帮你完成连接。
:::

## 第 4 步：获取打印机与纸张（可选）

在打印前，先拿到本机可用打印机与纸张，供用户选择：

```js
// 获取本机打印机列表
sdk.getPrinters().then(r => {
  console.log(r.printers); // ["Microsoft Print to PDF", "HP LaserJet ...", ...]
});

// 获取指定打印机的可用纸张
sdk.getPageSize('Microsoft Print to PDF').then(r => {
  console.log(r.sizes); // ["A4", "A5", ...]
});
```

## 第 5 步：提交打印任务

```js
sdk.doPrint({
  printer: '默认打印机',           // 或打印机列表中的具体名称
  style: {
    paper: 'A4',
    direction: 'vertical',
    margin: { top: 1, right: 1, bottom: 1, left: 1 },
    zoom: 1,
    fontFamily: '宋体',
    fontSize: 12,
    paperHeader: '销售报表',
    paperFooter: '第 {page} 页'
  },
  content: {
    type: 'text',
    value: '尊敬的客户：\n\n感谢您选择晓雪WEB打印控件。'
  }
}).then(r => {
  console.log('打印任务已提交，任务ID：', r.taskId);
}).catch(e => {
  console.error('打印失败：', e.message);
});
```

`content.type` 支持：

| type | value 含义 |
| --- | --- |
| `text` | 纯文本，支持换行与空白字符 |
| `html` | HTML 片段，保留内联样式渲染（标题、段落、表格、图片等） |
| `bartender` | BarTender 模板路径（.btw），配合 `params` 字段传模板参数 |

## 第 6 步：打印预览

将 `doPrint` 换成 `doPreview`，参数完全一致：

```js
sdk.doPreview({
  printer: '默认打印机',
  style: { paper: 'A4', direction: 'vertical', margin: { top: 1, right: 1, bottom: 1, left: 1 } },
  content: { type: 'html', value: '<h1>标题</h1><p>正文</p>' }
}).then(r => {
  console.log('预览指令已发送', r.message);
});
```

服务端会弹出分页预览窗口，支持缩放、翻页、导出 PDF / 图片、页面设置等操作。

## 完整可运行示例

项目 `demo/` 目录提供开箱即用的示例页，直接打开即可体验：

| 示例 | 说明 |
| --- | --- |
| `demo/index.html` | Demo 首页（入口导航） |
| `demo/normal-print-demo.html` | 普通打印：文本 / HTML / BarTender |
| `demo/print-demo-vue2.html` | Vue 2 集成示例 |
| `demo/print-demo-vue3.html` | Vue 3 集成示例 |
| `demo/print-demo-react.html` | React 集成示例 |
| `demo/print-demo-anjular.html` | Angular 集成示例 |

在线版可访问本网站的 **Demo 演示** 页面。

::: warning 运行前提
Demo 页需要本机已启动打印服务（第 1 步），否则页面会显示"未连接"。
:::

## 下一步

- 了解 API 全貌 → [SDK 类参考](/api/sdk)
- 深入打印样式、预览、任务管理与框架集成 → [打印服务](/usage/print)
- 标签 / 条码打印 → [BarTender 模板打印](/usage/bartender)
