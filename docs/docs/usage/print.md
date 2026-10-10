# 打印服务

本页汇总打印服务的核心能力：**基础打印（文本 / HTML）**、**打印预览**、**打印任务管理**，以及 **Vue / React / Angular 等框架的集成方式**。

## 基础打印（文本 / HTML）

`doPrint()` 是核心打印入口：将打印内容（text 或 HTML）连同打印样式提交给本机服务，服务端渲染为 PDF 后输出到指定打印机，**全程不弹出系统打印对话框（静默打印）**。

```js
sdk.doPrint({
  printer: '默认打印机',
  style: {
    paper: 'A4',                       // 纸张
    direction: 'vertical',             // horizontal 横向 / vertical 纵向
    margin: { top: 1, right: 1, bottom: 1, left: 1 },  // 页边距（cm）
    zoom: 1,                           // 缩放
    fontFamily: '宋体',                 // 字体
    fontSize: 12,                      // 字号
    paperHeader: '销售报表',            // 页眉
    paperFooter: '第 {page} 页'         // 页脚
  },
  content: { type: 'text', value: '打印内容' }
}).then(r => console.log('任务ID：', r.taskId))
  .catch(e => console.error('打印失败：', e.message));
```

### content 类型

| type | value 含义 |
| --- | --- |
| `text` | 纯文本，支持换行与空白字符 |
| `html` | HTML 片段，保留内联样式渲染（标题、段落、列表、表格、图片等） |
| `bartender` | BarTender 模板路径（.btw），配合 `params` 传模板参数（见 [BarTender 模板打印](./bartender)） |
| `jasper` | JasperReport 模板名，配合 `params` 传报表参数（见 [JasperReport 报表](./jasper)） |

HTML 示例：

```js
sdk.doPrint({
  printer: '默认打印机',
  style: { paper: 'A4', direction: 'vertical', margin: { top: 1, right: 1, bottom: 1, left: 1 } },
  content: { type: 'html', value: '<h1 style="text-align:center">标题</h1><table><tr><th>项目</th><th>金额</th></tr></table>' }
});
```

::: tip HTML 注意事项
样式请使用**内联 style**，外部 CSS 不会被加载；图片支持 base64 或 http(s) 可访问地址。
:::

### 响应结果

```json
{ "type": "doPrint_result", "success": true, "taskId": "a1b2c3d4", "message": "打印任务已提交" }
```

任务异步执行，完成后自动从任务列表清除。

### 打印前先选打印机与纸张

```js
const { printers } = await sdk.getPrinters();      // 获取打印机列表
const { sizes } = await sdk.getPageSize(printers[0]);  // 获取指定打印机纸张
```

## 打印预览

`doPreview()` 不执行打印，由服务端弹出**分页预览窗口**，参数与 `doPrint()` 完全一致：

```js
sdk.doPreview({
  printer: '默认打印机',
  style: { paper: 'A4', direction: 'vertical', margin: { top: 1, right: 1, bottom: 1, left: 1 } },
  content: { type: 'html', value: '<h1>标题</h1><p>正文</p>' }
}).then(r => console.log('预览指令已发送：', r.message));
```

预览窗口提供：**打印、页面设置、打印机设置、导出 PDF、导出图片、缩放、翻页、拖动、标尺、关闭**。相同的 printer / style / content 再次调用会**激活已有窗口**。推荐流程：先 `doPreview` 确认版式，再 `doPrint` 正式输出。

## 打印任务管理

| 方法 | 说明 |
| --- | --- |
| `getTaskList()` | 获取任务列表 `{ tasks: [...] }` |
| `getTaskStatus(taskId)` | 查询单个任务状态 `{ task?, found }` |
| `cancelTask(taskId)` | 取消待打印 / 打印中任务 `{ success, message }` |
| `clearTasks()` | 清除已完成 / 失败任务 `{ success, message }` |

```js
// 实时监听任务变化（服务端主动推送，无需轮询）
sdk.on('taskListUpdate', (tasks) => renderTasks(tasks));

// 查询 / 取消 / 清除
sdk.getTaskStatus('a1b2c3d4').then(r => console.log(r.task));
sdk.cancelTask('a1b2c3d4').then(r => console.log(r.message));
sdk.clearTasks().then(r => console.log(r.message));
```

### 任务状态

| 状态 | 说明 | 可取消 |
| --- | --- | --- |
| `PENDING` | 待打印 | 是 |
| `PRINTING` | 打印中 | 是 |
| `COMPLETED` | 已完成 | 否 |
| `FAILED` | 失败（`errorMessage` 查看原因） | 否 |

## 前端框架集成

SDK 是纯 JS 单文件，通过 `script` 标签以全局变量方式引入，可零成本集成到任意框架。通用原则：**组件挂载时 `connect()`、卸载时 `disconnect()`，通过 `open` / `close` 事件同步连接状态**。

Vue 3 示例：

```vue
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const sdk = new XiaoxuePrinterSDK({ url: 'ws://127.0.0.1:54321' })
const connected = ref(false)
const printers = ref([])

onMounted(() => {
  sdk.on('open', () => { connected.value = true; fetchPrinters() })
  sdk.connect()
})
onUnmounted(() => sdk.disconnect())

async function fetchPrinters() {
  const r = await sdk.getPrinters()
  printers.value = r.printers || []
}
function doPrint() {
  sdk.doPrint({ printer: '默认打印机', content: { type: 'text', value: '打印内容' } })
    .then(r => console.log('任务ID:', r.taskId))
}
</script>
```

各框架的完整可运行示例见 `demo/` 目录：

| 示例 | 说明 |
| --- | --- |
| `demo/normal-print-demo.html` | 普通打印：文本 / HTML / BarTender |
| `demo/print-demo-vue2.html` / `print-demo-vue3.html` | Vue 2 / Vue 3 集成示例 |
| `demo/print-demo-react.html` | React 集成示例 |
| `demo/print-demo-anjular.html` | Angular 集成示例（`PrintService` 单例封装） |

> 更多 API 细节见 [SDK 类参考](/api/sdk)。
