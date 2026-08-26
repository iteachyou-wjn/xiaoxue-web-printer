# Dreamer Print Service

基于 SWT 的本机打印控件。启动后无主界面，驻留系统托盘，通过 WebSocket 接收本机页面的打印 / 预览请求，用 OpenHTMLtoPDF 渲染为 PDF 后交给系统打印机。

## 功能特性

- **系统托盘驻留**：无主窗口，SWT 原生托盘与中文菜单
- **本机 WebSocket**：仅监听 `127.0.0.1:54321`，不对外网卡开放
- **真实打印**：`doPrint` 将 text/html 渲染为 PDF，经 Java Print Service 输出
- **打印预览**：`doPreview` 弹出分页预览（缩放、页面设置、导出 PDF/图片）
- **任务列表**：托盘可查看进行中的打印任务
- **浏览器 SDK**：`dreamer-printer-sdk.js`，演示页 `dreamer-printer-demo.html`

## 技术栈

- **Java 17+**
- **SWT 3.131**（默认 Windows x86_64）
- **Java-WebSocket 1.5.6**
- **Fastjson2 2.0.64**
- **OpenHTMLtoPDF 1.0.10** + **PDFBox 2.0.31** + **jsoup 1.17.2**
- **SLF4J Simple** / **Maven**

## 项目结构

```
dreamer-print-service/
├── pom.xml
├── dreamer-printer-sdk.js             # 浏览器 / Node 客户端 SDK
├── dreamer-printer-demo.html          # 完整演示页（样式 / 预览 / 打印）
├── test-client.html                   # 精简 WebSocket 测试页
├── README.md
└── src/main/
    ├── java/cc/iteachyou/printservice/
    │   ├── MainApp.java               # 启动入口（shade JAR 的 mainClass）
    │   ├── tray/SystemTrayManager.java
    │   ├── websocket/
    │   │   ├── PrintWebSocketServer.java
    │   │   └── PrintTask.java
    │   ├── print/
    │   │   ├── HtmlRenderService.java # HTML/text → PDF
    │   │   ├── PrintTaskExecutor.java # PDF → 打印机
    │   │   └── PaperSizeUtil.java
    │   ├── ui/                        # 预览 / 任务列表 / 关于 / 授权
    │   └── util/
    └── resources/simplelogger.properties
```

## 快速开始

### 环境要求

- JDK 17 或更高
- Maven 3.6+
- Windows x86_64（SWT 平台相关包）

> 跨平台需替换 `pom.xml` 中的 SWT artifact：
> - macOS aarch64：`org.eclipse.swt.cocoa.macosx.aarch64`
> - Linux x86_64：`org.eclipse.swt.gtk.linux.x86_64`

### 编译运行

```bash
mvn clean package
java -jar target/dreamer-print-service-1.0.0.jar
```

IDE（STS）中运行 `cc.iteachyou.printservice.MainApp` 的 `main` 方法。启动后看系统托盘打印机图标。

## 打包为 Windows EXE（STS 视角）

本节说明如何在 STS 中将本项目打包为「绿色版 EXE（app-image，自带 JRE）」和「安装引导程序（Inno Setup）」。

> ⚠️ **关键前提**：**不要直接用 shade 生成的 fat jar 打包**。SWT 的 native 库（dll）打进 fat jar 后会加载失败，运行时提示 `Libraries for platform win32 cannot be loaded because of incompatible environment`。正确做法是「瘦 jar + 全部原始依赖 jar」交给 jpackage 打包（已实测验证）。

### 前置条件

| 工具 | 要求 | 本机路径参考 |
|---|---|---|
| JDK 17+（自带 jpackage） | 64 位 | `D:\Program Files\Java\jdk-17` |
| Maven 3.6+ | — | `D:\Dev\apache-maven-3.9.9` |
| Inno Setup 6（仅生成安装包时需要） | — | `D:\Program Files (x86)\Inno Setup 6` |

### 第 1 步：在 STS 中构建 jar

1. 在 STS 中右键项目 `dreamer-print-service` → **Run As → Maven build...**
2. Goals 填 `clean package`，点 **Run**，等待构建成功
3. 构建成功后 `target` 目录下会生成两个 jar：
   - `target\dreamer-print-service-1.0.0.jar` —— **fat jar**（shade 合并了全部依赖，仅用于 `java -jar` 运行，**不要**用于 jpackage）
   - `target\original-dreamer-print-service-1.0.0.jar` —— **瘦 jar**（只含本项目代码，jpackage 用这个）

> 若 STS 用的是内置 Maven：Window → Preferences → Maven → Installations → Add，指定 `D:\Dev\apache-maven-3.9.9` 后勾选启用。

### 第 2 步：复制依赖 jar（命令行）

打开 PowerShell / cmd，进入项目根目录：

```bat
cd /d D:\Stsworkspaces\dreamer-print-service
"D:\Dev\apache-maven-3.9.9\bin\mvn.cmd" dependency:copy-dependencies -DoutputDirectory=build\libs
```

把全部第三方依赖（含 `org.eclipse.swt.win32.win32.x86_64-3.131.0.jar`，保持原始结构）复制到 `build\libs`。

### 第 3 步：准备 jpackage 输入目录

```bat
mkdir build\input
copy target\original-dreamer-print-service-1.0.0.jar build\input\dreamer-print-service-1.0.0.jar
copy build\libs\*.jar build\input\
```

`build\input` 中应包含 1 个瘦 jar + 全部依赖 jar（共 15 个 jar）。

### 第 4 步：jpackage 生成含 JRE 的绿色版 exe

```bat
"D:\Program Files\Java\jdk-17\bin\jpackage.exe" --type app-image ^
  --name 梦想家WEB打印控件 --app-version 1.0.0 --vendor iteachyou ^
  --input build\input --main-jar dreamer-print-service-1.0.0.jar ^
  --main-class cc.iteachyou.printservice.MainApp ^
  --icon build\dreamer-print.ico --dest build\app-image
```

产物：`build\app-image\梦想家WEB打印控件\梦想家WEB打印控件.exe`（目录内含 `app\` + `runtime\`，自带 JRE，双击即可运行，无需本机装 Java）。

### 第 5 步：Inno Setup 生成安装引导程序

已提供脚本 `build\dreamer-print.iss`（中文/英文向导、可选桌面图标与开机自启、含卸载项）。如需调整安装细节，直接编辑该文件后编译：

```bat
"D:\Program Files (x86)\Inno Setup 6\ISCC.exe" build\dreamer-print.iss
```

产物：`build\梦想家WEB打印控件-Setup-1.0.0.exe`。

### 第 6 步：验证

1. 直接运行绿色版 exe（或安装后启动），系统托盘出现应用图标；
2. 命令行执行 `netstat -ano | findstr 54321`，应能看到监听；
3. 用浏览器打开 `dreamer-printer-demo.html`，`DreamerPrinterSDK.getPrinters()` 能返回本机打印机列表。

### 重新打包的完整命令序列

```bat
cd /d D:\Stsworkspaces\dreamer-print-service
"D:\Dev\apache-maven-3.9.9\bin\mvn.cmd" clean package -DskipTests
"D:\Dev\apache-maven-3.9.9\bin\mvn.cmd" dependency:copy-dependencies -DoutputDirectory=build\libs
rem --- 第 3 步（准备 input）---
rem --- 第 4 步（jpackage）---
rem --- 第 5 步（ISCC）---
```

### 常见问题：运行 exe 后无反应 / SWT 报错

- **症状**：运行 exe 无界面、进程一闪而过；`java -jar` 报 `Libraries for platform win32 cannot be loaded because of incompatible environment`
- **原因**：用 shade 的 fat jar 打包，SWT 本地库（dll）被破坏
- **解决**：按第 2~4 步改用「瘦 jar + 原始依赖」重新打包，即可正常启动（托盘 + WebSocket 均正常）

## 使用说明

### 系统托盘

- **左键双击**：任务列表
- **右键**：任务列表 / 授权许可 / 关于 / 退出

### WebSocket

服务只接受本机连接：`ws://127.0.0.1:54321`。打印入口只有 `doPrint`（旧版 `submit` 已移除，服务端会返回错误提示）。

#### 打印：doPrint

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
    "paperHeader": "",
    "paperFooter": ""
  },
  "content": { "type": "text", "value": "打印内容" }
}
```

`content.type` 可为 `text` 或 `html`。响应：

```json
{ "type": "doPrint_result", "success": true, "taskId": "a1b2c3d4", "message": "打印任务已提交" }
```

服务端会创建任务、异步渲染并打印，完成后从任务列表清除。

#### 预览：doPreview

载荷与 `doPrint` 相同。不打印，只打开预览窗；相同 printer/style/content 会激活已有窗口。

#### 其它指令

| type | 说明 |
|------|------|
| `printers` | 本机打印机列表 |
| `pageSize` | 指定打印机支持的纸张（需 `printerName`） |
| `list` / `status` | 任务列表 / 单个任务 |
| `cancel` / `clear` | 取消待打印任务 / 清除已完成与失败任务 |

任务状态变化时向已连接客户端广播 `{ "type": "task_list_update", "tasks": [...] }`。

### 浏览器 SDK

```html
<script src="dreamer-printer-sdk.js"></script>
<script>
  DreamerPrinterSDK.doPrint({
    printer: '默认打印机',
    style: { paper: 'A4', fontFamily: '宋体', fontSize: 12 },
    content: { type: 'html', value: '<h1>标题</h1><p>正文</p>' }
  }).then(r => console.log(r.taskId));

  DreamerPrinterSDK.doPreview({ /* 同上 */ });
</script>
```

完整交互见 `dreamer-printer-demo.html`；精简调试见 `test-client.html`。

## 打印链路

`doPrint` / 预览窗「打印」共用：

1. `HtmlRenderService` 把 text/html + style 编成 XHTML（`@page` 纸张/边距/页眉页脚，图片内嵌）
2. OpenHTMLtoPDF 渲染为 PDF（Windows 注册宋体/微软雅黑等）
3. `PrintTaskExecutor` 用 PDFBox `PDFPageable` + `PrinterJob` 输出

## 配置

修改 `MainApp` 中的常量：

```java
public static final String WS_HOST = "127.0.0.1";
public static final int WS_PORT = 54321;
```

默认只绑回环地址。若需局域网访问，把 `WS_HOST` 改为 `0.0.0.0`（同时应自行加鉴权，本项目未内置）。

## 常见问题

### 系统托盘图标不显示？

SWT `display.getSystemTray()` 在 Windows/macOS 上支持；Linux 部分桌面环境需要扩展。

### 开机自启？

Windows：快捷方式放入「启动」文件夹；macOS：登录项；Linux：systemd 或 `.desktop`。

### 为什么之前用 dorkbox 会报 `bounds is null`？

dorkbox SystemTray 在 Windows 原生托盘上的已知 bug（issue #209）。本项目已用 SWT 原生托盘，不再依赖 dorkbox。

## License

MIT License
