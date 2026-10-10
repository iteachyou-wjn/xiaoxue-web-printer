# 功能特性

晓雪WEB打印控件围绕"**让浏览器打得出、打得准、可管理**"设计，下面按能力维度介绍其核心特性。

## 系统托盘驻留

- 服务启动后**无主界面**，常驻系统**托盘**，以 SWT 原生托盘与中文菜单呈现；
- 托盘**左键双击**打开打印任务列表，**右键**可访问任务列表、授权许可、关于与退出；
- 支持**开机自启**（安装时可勾选），打印服务随系统启动自动就绪。

## 本机 WebSocket 通信

- 服务仅监听回环地址 `ws://127.0.0.1:54321`，**不对外开放网卡端口**，降低被外部访问的风险；
- 连接管理完善：自动重连、可配置重连间隔与最大次数、连接超时兜底、断线自动清理挂起请求。

## 真实打印（doPrint）

- 将 **text 文本**或 **HTML 片段**渲染为 PDF 后，经 **Java Print Service** 输出到指定打印机；
- 支持任意本机已安装打印机，不弹系统打印对话框（静默打印）；
- 返回 `taskId`，任务异步执行、完成后自动清理。

## 分页预览（doPreview）

- 不打印、仅打开预览窗口，**所见即所得**；
- 预览窗口提供：**打印、页面设置、打印机设置、导出 PDF、导出图片、缩放、翻页、拖动、关闭**等完整工具栏；
- 支持标尺、多页预览；相同 printer / style / content 的重复预览会**激活已有窗口**。

## 打印样式精细控制

通过 `style` 字段可精确控制版式：

| 样式项 | 说明 |
| --- | --- |
| `paper` | 纸张（如 A4，或打印机支持的任意纸张） |
| `direction` | 方向：`horizontal`（横向）/ `vertical`（纵向） |
| `margin` | 页边距（cm）：`{ top, right, bottom, left }` |
| `zoom` | 缩放比例 |
| `fontFamily` / `fontSize` | 字体与字号（宋体、微软雅黑、黑体、楷体、仿宋、Arial 等） |
| `paperHeader` / `paperFooter` | 页眉 / 页脚 |

## 打印机与纸张动态获取

- `getPrinters()` 获取本机打印机列表；
- `getPageSize(printerName)` 获取指定打印机支持的纸张类型；
- 页面可据此动态渲染下拉选项，提升打印体验。

## 打印任务管理

- `getTaskList()` 获取任务列表，`getTaskStatus(taskId)` 查询单个任务；
- `cancelTask(taskId)` 取消**待打印 / 打印中**任务；
- `clearTasks()` 清除**已完成 / 失败**任务；
- 任务状态变化时，服务端主动向已连接客户端广播 `task_list_update` 事件，页面可实时刷新。

### 任务状态

| 状态 | 说明 | 是否可取消 |
| --- | --- | --- |
| `PENDING` | 待打印 | 是 |
| `PRINTING` | 打印中 | 是 |
| `COMPLETED` | 已完成 | 否 |
| `FAILED` | 失败（可通过 `errorMessage` 查看原因） | 否 |

## BarTender 标签打印

深度集成 BarTender（bartender-printer-sdk 2.1.1）：

- `getBartenderInstance()` 获取 BarTender 实例与版本信息（`available` / `version` / `fullVersion`）；
- `getTemplateParams(.btw)` 读取模板字段参数及控件类型（`Barcode` / `Text` / `Picture` / `RichText` / `RFID` 等）；
- `getTemplateImage(.btw)` / `getTemplateImageWithParams(.btw, params)` 获取模板图像（base64），支持**带参数预览**；
- 打印时可直接以 `content.type: 'bartender'` 调用本机 BarTender 渲染或打印模板，适合条码、标签、证卡等场景。

## JasperReport 报表打印

集成 JasperReport 7.0 报表引擎，适用于领料单、送货单、发票等复杂版式：

- `getJasperInstance().listTemplates()` 获取 `reports/jr/` 目录下所有 `.jasper` 模板；
- 前端通过 JSON 传参填充报表，支持明细表（数组）、二维码、中文页眉页脚；
- 内置生产领料单模板（`picking-slip`），开箱即用；
- 模板文件放在安装目录 `reports/jr/` 下，支持自定义扩展。

## 授权许可

- 内置**机器码**展示与**授权码**输入、取消授权、邮箱联系入口；
- 授权相关代码经过 **ProGuard 混淆**与公钥加固，提升逆向门槛；
- 版本号由构建统一注入，托盘菜单与关于窗体自动展示。

## 跨平台（可配置）

- 默认面向 **Windows x86_64** 构建；
- 通过替换 `pom.xml` 中的 SWT artifact，可适配 **macOS aarch64**（`org.eclipse.swt.cocoa.macosx.aarch64`）与 **Linux x86_64**（`org.eclipse.swt.gtk.linux.x86_64`）。

> 需要了解整体架构与打印链路，请看[技术架构](./architecture)。
