# SDK 引入与连接

`XiaoxuePrinterSDK` 是官方提供的前端客户端，封装了与本机打印服务（`ws://127.0.0.1:54321`）的全部通信。本节详细介绍引入方式、连接配置与生命周期管理。

## 引入 SDK

SDK 以单文件形式提供，浏览器与 Node.js 均可使用。

**浏览器（推荐使用压缩版）**

```html
<script src="xiaoxue-printer-sdk.min.js"></script>
```

引入后全局暴露 `XiaoxuePrinterSDK`（内部包含 `Bartender` 与 `Jasper` 实例类）。

**Node.js**

```js
const XiaoxuePrinterSDK = require('./xiaoxue-printer-sdk');
```

## 创建实例

```js
const sdk = new XiaoxuePrinterSDK({
  url: 'ws://127.0.0.1:54321',   // WebSocket 服务地址
  autoReconnect: true,           // 是否自动重连
  reconnectDelay: 3000,          // 重连间隔（毫秒）
  maxReconnectAttempts: Infinity,// 最大重连次数
  connectionTimeout: 5000,       // 连接超时（毫秒）
});
```

### 构造参数

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `url` | string | `ws://localhost:54321` | 打印服务地址 |
| `autoReconnect` | boolean | `true` | 是否自动重连 |
| `reconnectDelay` | number | `3000` | 重连间隔（毫秒） |
| `maxReconnectAttempts` | number | `Infinity` | 最大重连次数 |
| `connectionTimeout` | number | `5000` | 连接超时（毫秒） |

## 连接与断开

```js
// 建立连接（重复调用不会重复建连）
sdk.connect();

// 断开连接（同时停止自动重连）
sdk.disconnect();
```

## 自动连接

**绝大多数 API 无需手动 `connect()`**：`getPrinters()`、`getPageSize()`、`doPrint()`、`doPreview()`、`getBartenderInstance()`、`getJasperInstance()` 等在未连接时会自动建立连接（`_ensureConnected`，默认 8 秒超时）。

因此最简用法是：

```js
const sdk = new XiaoxuePrinterSDK();
sdk.doPrint({ printer: '默认打印机', content: { type: 'text', value: '你好' } })
  .then(r => console.log(r.taskId));
```

## 订阅事件

通过 `on(event, callback)` 订阅，`off(event, callback)` 取消订阅。链式调用返回实例本身。

```js
sdk
  .on('open', () => console.log('已连接'))
  .on('close', () => console.log('连接已断开'))
  .on('error', (e) => console.error('错误', e))
  .on('welcome', (msg) => console.log('欢迎消息：', msg.message, '端口：', msg.port))
  .on('taskListUpdate', (tasks) => console.log('任务列表更新：', tasks));
```

| 事件 | 触发时机 | 回调参数 |
| --- | --- | --- |
| `open` | WebSocket 连接建立成功 | — |
| `close` | 连接关闭 | — |
| `error` | 连接错误 | `err` |
| `message` | 收到任意消息 | 解析后的消息对象 |
| `welcome` | 服务端欢迎消息（连接后） | `msg`（含 `message`、`port` 等） |
| `taskListUpdate` | 服务端主动推送任务列表变化 | `tasks` 数组 |

::: tip 建议
在页面加载时订阅 `open` / `close` 更新连接状态指示，并将 `open` 作为初始化入口（如拉取打印机列表）。
:::

## 生命周期最佳实践

**原生页面**：页面加载时 `connect()`，页面卸载时 `disconnect()`。

**Vue / React 等框架**：在组件挂载时连接、卸载时断开（详见[打印服务](./print)）。

```js
// Vue 3 组合式 API
onMounted(() => {
  sdk.on('open', () => { connected.value = true; fetchPrinters() });
  sdk.connect();
});
onUnmounted(() => sdk.disconnect());
```

## 下一步

- 开始第一次打印 → [基础打印（文本 / HTML）](./print)
- 查看完整 API → [SDK 类参考](/api/sdk)
