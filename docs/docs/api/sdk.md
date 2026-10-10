# SDK 类参考

本文是 `XiaoxuePrinterSDK` 与 `Bartender` 两个类的完整 API 参考。

## XiaoxuePrinterSDK

打印服务 WebSocket 客户端，封装连接管理与打印服务全部 API。

### 构造方法

```js
new XiaoxuePrinterSDK(options)
```

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `options.url` | string | `ws://localhost:54321` | WebSocket 服务地址 |
| `options.autoReconnect` | boolean | `true` | 是否自动重连 |
| `options.reconnectDelay` | number | `3000` | 重连间隔（毫秒） |
| `options.maxReconnectAttempts` | number | `Infinity` | 最大重连次数 |
| `options.connectionTimeout` | number | `5000` | 连接超时（毫秒） |

### 连接管理

#### `connect()`

建立连接。若已连接 / 正在连接则忽略。返回实例本身。

```js
sdk.connect();
```

#### `disconnect()`

断开连接并停止自动重连。

```js
sdk.disconnect();
```

### 事件订阅

#### `on(event, callback)`

订阅事件，链式调用。返回实例本身。

```js
sdk.on('open', () => {});
```

#### `off(event, callback)`

取消订阅。返回实例本身。

```js
sdk.off('open', handler);
```

事件列表见[事件参考](./events)。

### 打印与预览

#### `doPrint(json)` → `Promise<{ success, taskId, message }>`

提交并执行打印任务。未连接时自动连接。

```js
sdk.doPrint({
  printer: '默认打印机',
  style: { paper: 'A4', direction: 'vertical', margin: { top: 1, right: 1, bottom: 1, left: 1 }, zoom: 1, fontFamily: '宋体', fontSize: 12, paperHeader: '', paperFooter: '' },
  content: { type: 'text', value: '打印内容' }
}).then(r => console.log(r.taskId));
```

`content.type` 支持 `text` / `html` / `bartender` / `jasper`：

- `text` / `html`：普通打印
- `bartender`：`value` 为 `.btw` 路径，`params` 传模板参数
- `jasper`：`value` 为模板名（`reports/` 目录下的 `.jasper` 文件），`params` 传报表参数

#### `doPreview(json)` → `Promise<{ success, message }>`

打开打印预览窗口（不打印），参数同 `doPrint`。相同 printer / style / content 会激活已有窗口。

```js
sdk.doPreview({ printer: '默认打印机', content: { type: 'html', value: '<h1>标题</h1>' } });
```

### 打印机与纸张

#### `getPrinters()` → `Promise<{ printers: string[] }>`

获取本机可用打印机列表。未连接时自动连接。

```js
const { printers } = await sdk.getPrinters();
```

#### `getPageSize(printerName)` → `Promise<{ printerName, sizes: string[] }>`

获取指定打印机的可用纸张类型。未连接时自动连接。

```js
const { sizes } = await sdk.getPageSize('Microsoft Print to PDF');
```

#### `getJasperInstance()` → `Promise<Jasper>`

获取 JasperReport 实例（含模板列表能力）。未连接时自动连接。

```js
const jr = await sdk.getJasperInstance();
const r = await jr.listTemplates();
```

### 任务管理

#### `getTaskList()` → `Promise<{ tasks: [...] }>`

获取打印任务列表。

```js
const { tasks } = await sdk.getTaskList();
```

#### `getTaskStatus(taskId)` → `Promise<{ task?, found, message? }>`

查询单个任务状态。

```js
const r = await sdk.getTaskStatus('a1b2c3d4');
```

#### `cancelTask(taskId)` → `Promise<{ success, message }>`

取消待打印 / 打印中的任务。

```js
await sdk.cancelTask('a1b2c3d4');
```

#### `clearTasks()` → `Promise<{ success, message }>`

清除已完成 / 失败的任务。

```js
await sdk.clearTasks();
```

### BarTender

#### `getBartenderInstance()` → `Promise<Bartender>`

获取 BarTender 实例（含版本信息与模板能力）。未连接时自动连接。

```js
const bt = await sdk.getBartenderInstance();
console.log(bt.available, bt.version);
```

## Bartender

通过 `XiaoxuePrinterSDK#getBartenderInstance()` 获取。

### 实例属性

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| `available` | boolean | BarTender 是否可用 |
| `version` | string | 版本号 |
| `fullVersion` | string | 完整版本信息 |

### `refresh()` → `Promise<Object>`

刷新 BarTender 实例信息。

```js
await bt.refresh();
```

### `getTemplateParams(templateName)` → `Promise<{ success, templatePath, params: [{ name, type }] }>`

获取模板参数（参数名 + 控件类型）。模板放在安装目录 `reports/bt/` 下，传入文件名即可。`type` 为 `Barcode` / `Text` / `Picture` / `RichText` / `RFID` 等（条码不细分）。

```js
const r = await bt.getTemplateParams('a.btw');
```

### `getTemplateImage(templateName, dpi = 300)` → `Promise<{ success, templatePath, image }>`

获取无参数模板图像，`image` 为 base64。

```js
const r = await bt.getTemplateImage('a.btw');
img.src = 'data:image/png;base64,' + r.image;
```

### `getTemplateImageWithParams(templateName, params = {}, dpi = 300)` → `Promise<{ success, templatePath, image }>`

获取带参数的模板预览图像（base64）。

```js
const r = await bt.getTemplateImageWithParams('a.btw', { 字段1: '值1' });
```

### `listTemplates()` → `Promise<{ success, templates: [{ name, file, size }] }>`

列出安装目录 `reports/bt/` 下所有 BarTender 模板（.btw）。

```js
const r = await bt.listTemplates();
// r.templates => [{ name: 'mylabel', file: 'mylabel.btw', size: 12345 }]
```

## Jasper

通过 `XiaoxuePrinterSDK#getJasperInstance()` 获取。

### `listTemplates()` → `Promise<{ success, templates: [{ name, file, size }] }>`

列出安装目录 `reports/jr/` 下所有 JasperReport 模板（.jasper / .jrxml）。

```js
const jr = await sdk.getJasperInstance();
const r = await jr.listTemplates();
// r.templates => [{ name: 'picking-slip', file: 'picking-slip.jasper', size: 34329 }]
```

### 打印 / 预览报表

通过 `doPrint()` / `doPreview()`，将 `content.type` 设为 `jasper`，`value` 为模板名，`params` 传报表参数：

```js
sdk.doPrint({
  printer: '默认打印机',
  style: { paper: 'A4', direction: 'vertical' },
  content: {
    type: 'jasper',
    value: 'picking-slip',           // 模板名
    params: {
      billNo: 'LL20261010001',       // 表头参数
      qrContent: 'LL20261010001',    // 二维码内容
      items: [                        // 明细数据
        { seq: 1, materialName: '螺丝M6', quantity: 100 }
      ]
    }
  }
}).then(r => console.log('任务ID：', r.taskId));
```

## 错误处理

所有返回 Promise 的 API 在失败时会 reject，错误信息可通过 `catch` 获取：

- 未连接且连接失败：`未连接到打印服务，请先 connect()` 或连接超时
- 服务端返回 `success: false`：`{ message }`
- 请求超时：`请求超时（{expectType}）`

```js
sdk.doPrint(req)
  .then(r => console.log(r.taskId))
  .catch(e => console.error(e.message));
```
