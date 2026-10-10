# 常见问题

## 连接相关

### 页面一直显示"未连接"？

1. 确认本机打印服务已启动（系统托盘有打印机图标）；
2. 确认服务端口正常监听：`netstat -ano | findstr 54321`；
3. 确认 SDK 的 `url` 指向 `ws://127.0.0.1:54321`（默认即此地址）；
4. 浏览器安全策略：本页必须通过 `http://` 或 `https://` 打开（部分浏览器限制 file:// 下的 WebSocket，请使用本地 HTTP 服务访问 demo）。

### 为什么服务只监听 127.0.0.1？

出于安全考虑，服务默认只绑定回环地址，**不对外开放网卡端口**。如需局域网访问，将 `MainApp` 中的 `WS_HOST` 改为 `0.0.0.0`，但**必须自行增加鉴权**（本项目未内置）。

## 打印相关

### 打印内容空白或样式丢失？

- 确认 `content.value` 是完整内容，`type` 与内容匹配（文本用 `text`，富样式用 `html`）；
- HTML 请使用**内联 style**，外部 CSS 不会被加载；
- 图片使用 base64 或 http(s) 可访问地址。

### 打印没有反应 / 任务失败？

- 通过 `getTaskStatus(taskId)` 查询任务，`FAILED` 状态的 `errorMessage` 会说明原因；
- 确认选择了真实存在的打印机；
- 查看打印服务日志（SLF4J Simple 输出）。

### 想静默打印，不弹系统对话框？

`doPrint()` 本身就是静默打印——内容渲染为 PDF 后直接输出到指定打印机，**不弹系统打印对话框**。

## 安装与打包相关

### 系统托盘图标不显示？

SWT 的 `display.getSystemTray()` 在 Windows / macOS 上支持；Linux 部分桌面环境需要扩展。

### 打包 exe 后运行无反应 / SWT 报错？

- 症状：运行 exe 无界面、进程一闪而过；`java -jar` 报 `Libraries for platform win32 cannot be loaded because of incompatible environment`
- 原因：用 shade 的 fat jar 打包，SWT 本地库（dll）被破坏
- 解决：改用「瘦 jar + 原始依赖」通过 jpackage 打包，详见[安装部署](./installation)

### 为什么之前用 dorkbox 会报 `bounds is null`？

dorkbox SystemTray 在 Windows 原生托盘上的已知 bug（issue #209）。本项目已改用 **SWT 原生托盘**，不再依赖 dorkbox。

## BarTender 相关

### BarTender `available` 为 false？

确认本机已正确安装并启动 BarTender 软件；版本信息可通过 `getBartenderInstance().version` 查看。

### 模板路径怎么写？

使用本机绝对路径，如 `D:\labels\mylabel.btw`，注意 JS 字符串中 `\` 需写成 `\\`。

## 其他

### 如何开机自启？

- **Windows**：快捷方式放入「启动」文件夹；或安装时勾选开机自启；
- **macOS**：登录项；
- **Linux**：systemd 或 `.desktop`。

### 跨平台支持吗？

默认面向 Windows x86_64。通过替换 `pom.xml` 中的 SWT artifact 可适配 macOS aarch64 与 Linux x86_64，详见[安装部署](./installation)。

### 有哪些授权方式？

内置机器码展示与授权码输入、取消授权、邮箱联系入口。授权相关代码经过 ProGuard 混淆加固。

> 仍无法解决？请查看[更新日志](/changelog)确认版本，或联系 I Teach You 团队。
