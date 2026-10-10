# 事件参考

`XiaoxuePrinterSDK` 提供事件订阅机制，用于感知连接状态与服务端推送。通过 `on(event, callback)` 订阅、`off(event, callback)` 取消。

## 事件总览

| 事件 | 触发时机 | 回调参数 |
| --- | --- | --- |
| `open` | WebSocket 连接建立成功 | — |
| `close` | 连接关闭（含被动断开） | — |
| `error` | 连接错误 | `err` |
| `message` | 收到任意服务端消息 | 解析后的消息对象 |
| `welcome` | 连接后收到服务端欢迎消息 | `msg` |
| `taskListUpdate` | 服务端主动推送任务列表变化 | `tasks: Array` |

## open

连接建立成功后触发。通常在此拉取打印机列表、更新页面连接状态。

```js
sdk.on('open', () => {
  console.log('已连接到打印服务');
  refreshPrinters();
});
```

## close

连接关闭时触发（服务端重启、主动 `disconnect()`、网络异常等）。

```js
sdk.on('close', () => {
  console.log('连接已断开');
  setConnected(false);
});
```

::: tip 自动重连
连接异常断开后，SDK 默认自动重连（`autoReconnect: true`）。重连成功后仍会触发 `open`。
:::

## error

连接错误时触发。回调参数为错误对象。

```js
sdk.on('error', (e) => {
  console.error('连接错误：', e && e.message || '未知错误');
});
```

## message

收到**任意**服务端消息时触发，参数为解析后的消息对象。适合用于调试或通用拦截。

```js
sdk.on('message', (msg) => {
  console.log('收到消息：', msg);
});
```

## welcome

连接建立后，服务端会发送欢迎消息（含服务端口等信息）。

```js
sdk.on('welcome', (msg) => {
  console.log('欢迎消息：', msg.message, '端口：', msg.port);
});
```

## taskListUpdate

服务端在打印任务状态变化时，主动向所有已连接客户端推送最新的任务列表。可用于实时刷新任务 UI，无需轮询。

```js
sdk.on('taskListUpdate', (tasks) => {
  console.log('任务列表更新：', tasks);
  renderTasks(tasks);
});
```

## 订阅与取消订阅

```js
const handler = (tasks) => { console.log(tasks); };

sdk.on('taskListUpdate', handler);   // 订阅
sdk.off('taskListUpdate', handler);  // 取消订阅
```

两者均返回 SDK 实例本身，支持链式调用：

```js
sdk
  .on('open', onOpen)
  .on('close', onClose)
  .connect();
```
