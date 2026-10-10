# WebSocket 协议

本节描述打印服务的 WebSocket 通信协议，供需要**自行实现客户端**（而非使用官方 SDK）的开发者参考。

## 连接

- 地址：`ws://127.0.0.1:54321`
- 协议：WebSocket，消息体为 **JSON 文本**
- 服务仅监听本机回环地址

## 消息格式

客户端请求与服务端响应均为 JSON 对象，通过 `type` 字段区分。

**请求**

```json
{ "type": "doPrint", "printer": "默认打印机", "style": { ... }, "content": { ... } }
```

**响应**

```json
{ "type": "doPrint_result", "success": true, "taskId": "a1b2c3d4", "message": "打印任务已提交" }
```

## 指令总览

| 请求 type | 响应 type | 说明 |
| --- | --- | --- |
| `doPrint` | `doPrint_result` | 提交并执行打印任务 |
| `doPreview` | `doPreview_result` | 打开打印预览窗口 |
| `printers` | `printers_result` | 本机打印机列表 |
| `pageSize` | `pageSize_result` | 指定打印机的纸张列表 |
| `list` | `task_list` | 打印任务列表 |
| `status` | `task_status` | 单个任务状态 |
| `cancel` | `cancel_result` | 取消任务 |
| `clear` | `clear_result` | 清除已完成/失败任务 |
| `bartenderInstance` | `bartenderInstance_result` | BarTender 实例与版本 |
| `bartenderTemplates` | `bartenderTemplates_result` | BarTender 模板列表 |
| `bartenderTemplateParams` | `bartenderTemplateParams_result` | 模板参数 |
| `bartenderTemplateImage` | `bartenderTemplateImage_result` | 模板图像（base64） |
| `bartenderTemplateImageWithParams` | `bartenderTemplateImageWithParams_result` | 带参数模板图像 |
| `jasperTemplates` | `jasperTemplates_result` | JasperReport 模板列表 |

## doPrint（打印）

### 请求

```json
{
  "type": "doPrint",
  "printer": "默认打印机",
  "style": {
    "paper": "A4",
    "direction": "vertical",
    "margin": { "top": 1, "right": 1, "bottom": 1, "left": 1 },
    "fontFamily": "宋体",
    "fontSize": 12,
    "zoom": 1,
    "paperHeader": "销售报表",
    "paperFooter": "第 {page} 页"
  },
  "content": { "type": "text", "value": "打印内容" }
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `printer` | string | 否 | 打印机名称，缺省为默认打印机 |
| `style.paper` | string | 否 | 纸张（默认 `A4`） |
| `style.direction` | string | 否 | `horizontal` / `vertical`（默认 `vertical`） |
| `style.margin` | object | 否 | 页边距（cm）`{ top, right, bottom, left }` |
| `style.zoom` | number | 否 | 缩放（默认 `1`） |
| `style.fontFamily` | string | 否 | 字体（默认 `宋体`） |
| `style.fontSize` | number | 否 | 字号（默认 `12`） |
| `style.paperHeader` | string | 否 | 页眉 |
| `style.paperFooter` | string | 否 | 页脚 |
| `content.type` | string | 是 | `text` / `html` / `bartender` / `jasper` |
| `content.value` | string | 是 | 内容 / HTML 片段 / 模板文件名 |
| `content.params` | object | 否 | `bartender`：模板参数；`jasper`：报表参数 |

### content.type 说明

| type | value 含义 | params 说明 |
| --- | --- | --- |
| `text` | 纯文本内容 | — |
| `html` | HTML 片段（内联样式） | — |
| `bartender` | `.btw` 模板文件名（位于 `reports/bt/`） | 模板字段值对象 |
| `jasper` | `.jasper` 模板名（位于 `reports/jr/`） | 报表参数对象，`items` 为明细数组 |

### 响应

```json
{ "type": "doPrint_result", "success": true, "taskId": "a1b2c3d4", "message": "打印任务已提交" }
```

服务端创建任务后**异步**渲染并打印，完成后自动从任务列表清除。

## doPreview（预览）

请求载荷与 `doPrint` 完全相同（`type` 为 `doPreview`）。不打印，只打开预览窗口。

```json
{
  "type": "doPreview",
  "printer": "默认打印机",
  "style": { "paper": "A4", "direction": "vertical" },
  "content": { "type": "html", "value": "<h1>标题</h1>" }
}
```

响应：

```json
{ "type": "doPreview_result", "success": true, "message": "预览窗口已打开" }
```

## printers / pageSize（打印机与纸张）

```json
// 请求
{ "type": "printers" }
// 响应
{ "type": "printers_result", "printers": ["Microsoft Print to PDF", "HP LaserJet ..."] }

// 请求（需 printerName）
{ "type": "pageSize", "printerName": "Microsoft Print to PDF" }
// 响应
{ "type": "pageSize_result", "printerName": "Microsoft Print to PDF", "sizes": ["A4", "A5", ...] }
```

## 任务管理（list / status / cancel / clear）

```json
// 任务列表
{ "type": "list" }
→ { "type": "task_list", "tasks": [ ... ] }

// 单个任务状态
{ "type": "status", "taskId": "a1b2c3d4" }
→ { "type": "task_status", "found": true, "task": { ... } }

// 取消任务
{ "type": "cancel", "taskId": "a1b2c3d4" }
→ { "type": "cancel_result", "success": true, "message": "任务已取消" }

// 清除已完成/失败任务
{ "type": "clear" }
→ { "type": "clear_result", "success": true, "message": "已清除" }
```

### 任务状态

| 状态 | 说明 |
| --- | --- |
| `PENDING` | 待打印（可取消） |
| `PRINTING` | 打印中（可取消） |
| `COMPLETED` | 已完成 |
| `FAILED` | 失败（`errorMessage` 可查看原因） |

## BarTender 指令

模板文件放在安装目录 `reports/bt/` 下，传入文件名即可。

```json
// 获取实例
{ "type": "bartenderInstance" }
→ { "type": "bartenderInstance_result", "success": true, "available": true, "version": "2022 R3", "fullVersion": "..." }

// 模板列表
{ "type": "bartenderTemplates" }
→ { "type": "bartenderTemplates_result", "success": true, "templates": [{ "name": "mylabel", "file": "mylabel.btw", "size": 12345 }] }

// 模板参数
{ "type": "bartenderTemplateParams", "templatePath": "mylabel.btw" }
→ { "type": "bartenderTemplateParams_result", "success": true, "templatePath": "mylabel.btw", "params": [{ "name": "barcode1", "type": "Barcode" }] }

// 模板图像（无参数）
{ "type": "bartenderTemplateImage", "templatePath": "mylabel.btw", "dpi": 300 }
→ { "type": "bartenderTemplateImage_result", "success": true, "templatePath": "mylabel.btw", "image": "<base64>" }

// 模板图像（带参数）
{ "type": "bartenderTemplateImageWithParams", "templatePath": "mylabel.btw", "params": { "字段1": "值1" }, "dpi": 300 }
→ { "type": "bartenderTemplateImageWithParams_result", "success": true, "templatePath": "mylabel.btw", "image": "<base64>" }
```

## JasperReport 指令

模板文件放在安装目录 `reports/jr/` 下。

```json
// 模板列表
{ "type": "jasperTemplates" }
→ { "type": "jasperTemplates_result", "success": true, "templates": [{ "name": "picking-slip", "file": "picking-slip.jasper", "size": 34329 }] }
```

打印 / 预览报表时，在 `doPrint` / `doPreview` 的 `content` 中指定：

```json
{
  "type": "doPreview",
  "printer": "默认打印机",
  "style": { "paper": "A4", "direction": "vertical" },
  "content": {
    "type": "jasper",
    "value": "picking-slip",
    "params": {
      "billNo": "LL20261010001",
      "billDate": "2026-10-10",
      "qrContent": "LL20261010001",
      "items": [
        { "seq": 1, "materialCode": "M001", "materialName": "螺丝M6", "quantity": 100 }
      ]
    }
  }
}
```

## 服务端主动推送

连接建立后与任务变化时，服务端会主动推送消息：

```json
// 欢迎消息
{ "type": "welcome", "message": "欢迎使用晓雪WEB打印控件", "port": 54321 }

// 任务列表变化
{ "type": "task_list_update", "tasks": [ ... ] }
```

## 错误响应

服务端在执行失败时返回 `success: false`，并附带 `message` 说明原因：

```json
{ "type": "doPrint_result", "success": false, "message": "打印机不存在" }
```

::: tip 使用官方 SDK
自行实现协议需要处理连接重试、请求排队、超时等细节。**建议直接使用官方 `XiaoxuePrinterSDK`**，这些都已内置，详见 [SDK 类参考](./sdk)。
:::
