# 晓雪WEB打印控件 版本说明

## v1.0.7 (2026-10-19)

### 新增
- **BarTender 模板列表 API**：WebSocket `bartenderTemplates` / SDK `Bartender#listTemplates()`，扫描安装目录 `reports/bt` 下 `.btw` 文件（与 Jasper 列出模板用法一致）。
- **授权策略补全**：未授权时 JasperReport、BarTender 的列出模板、预览、打印均不可用；文本/HTML 仍为试用版页眉页脚。

### 优化
- **SDK**：服务端返回 `type=error` 时正确结束对应 Promise，Demo 可展示「未授权」等提示；修复 `open` 事件监听器被提前移除导致打印机列表未加载的问题。
- **Demo / 在线示例**：BarTender、Jasper 支持「列出模板」下拉选择；优化未授权时的界面提示。
- **安装程序**：Inno Setup 中文界面、可选创建快捷方式、安装完成可选立即运行；安装包图标 `install.ico`；64 位默认安装到 Program Files。
- **模板与字体路径**：`reports/jr`（Jasper）、`reports/bt`（BarTender）、`fonts` 在安装目录（与 exe 同级）读取；打包时自动复制。
- **版本号**：Maven 对 `version.properties` 启用资源过滤，托盘/关于窗体显示真实版本号。
- **GitHub Pages**：SDK 统一发布在 `/sdk/` 路径，CI 部署时从仓库 `sdk/` 同步。

### 修复
- 一键打包脚本在依赖列表未变化时误报 ProGuard `BEGIN_LIBS` 标记缺失。
- 移除代码动态生成的领料单模板，改为仅使用 `reports/jr` 下 `.jasper` / `.jrxml` 文件。

### 打包
- 安装程序：`windows-晓雪WEB打印控件-1.0.7-x86_64.exe`。

---

## v1.0.6 (2026-10-10)

### 新增
- **JasperReport 7.0 报表引擎集成**：支持通过 JSON 传参填充 `.jasper` 模板，适用于领料单、送货单、发票等复杂报表。
- **内置生产领料单模板**：`reports/picking-slip.jasper`，支持表头、明细表格、二维码、中文字体、细边框样式。
- **报表模板管理**：SDK 提供 `getJasperTemplates()` 获取安装目录 `reports/` 下所有模板列表。
- **中文字体支持**：内置微软雅黑（msyh.ttf）、黑体（simhei.ttf）字体文件，PDF 导出中文正常显示。
- **二维码渲染**：通过 zxing 自动生成二维码图片嵌入报表，前端传 `qrContent` 即可。

### 优化
- 一键打包脚本自动复制 reports 模板到安装目录。
- 安装包输出到 build 根目录。

### 打包
- 重新打包安装程序：windows-晓雪WEB打印控件-1.0.6-x86_64.exe。

---

## v1.0.5 (2026-09-15)

### 变更
- **开机自启**：优化开机自启动注册表配置。

### 优化
- 无

### 打包
- 重新打包安装程序：windows-晓雪WEB打印控件-1.0.5-x86_64.exe。

---

## v1.0.4 (2026-09-11)

### 变更
- **品牌更名**：应用名称由"梦想家WEB打印控件"统一更名为"晓雪WEB打印控件"，覆盖托盘菜单、关于窗体、预览窗体、安装包名称及全部界面文字。
- **SDK 更名**：dreamer-printer-sdk.js 及压缩版更名为 xiaoxue-printer-sdk.js / xiaoxue-printer-sdk.min.js，demo 引用同步更新。
- **项目更名**：项目目录更名 xiaoxue-web-printer，项目英文名统一为 "Xiaoxue WEB Printer"（pom.xml / README）。
- **仓库迁移**：代码仓库迁移至 gitee.com/iteachyou/xiaoxue-web-printer.git。
- **Demo 落款**：示例页面落款更新为 "I Teach You, 我教你！"。

### 优化
- README 全面重写，补充项目介绍、功能特性、环境要求与打包说明。

### 打包
- 重新打包安装程序：windows-晓雪WEB打印控件-1.0.4-x86_64.exe。

---

## v1.0.3 (2026-08-28)

### 新增
- **授权安全加固**：内置公钥改为"拆段异或 + 运行时还原"存储，并集成 **ProGuard 混淆**（保留类名与 public/protected，仅混淆私有成员），显著提高反编译逆向门槛。公钥相关私有字段与方法（KEY_PARTS / KEYS / buildPublicKey 等）已不可见。
- **版本号统一读取**：版本号由 `pom.xml` 注入 `version.properties`（VersionUtil 读取），托盘菜单与关于窗体自动显示当前版本，打包时无需手工维护版本号。

### 优化
- **应用图标更新**：使用新的 `logo.png`（462×462）重新生成 `dreamer-print.ico`，包含 16/24/32/48/64/128/256 共 7 种尺寸，exe 图标与安装程序图标同步更新。
- **关于窗体**：移除"关闭"按钮，窗体固定尺寸不可拖动调整。
- **授权许可窗体**：宽度调整为 720、高度调整为 500。

### 修复
- 授权许可窗体尺寸显示问题。

---

## v1.0.2 (2026-08-27)

### 新增
- 集成 BarTender 模板打印（bartender-printer-sdk 2.1.1），支持模板参数获取、模板预览与打印。
- 授权许可功能完善：机器码展示、授权码输入、取消授权、邮箱联系入口。

### 优化
- 打包流程改进：统一应用名称为"晓雪WEB打印控件"，支持 Inno Setup 安装引导。

---

## v1.0.1 (2026-08-26)

### 新增
- SWT 原生托盘，支持任务列表、关于、授权许可、开机自启（复选框）等功能。
- 打印预览窗体：工具栏（打印/页面设置/打印机设置/导出PDF/导出图片/缩放/翻页/拖动/关闭）、标尺、多页预览。
- HTML 片段解析打印（基于 openhtmltopdf / pdfbox / jsoup）。

### 优化
- 预览窗体状态栏、滚动与缩放逻辑优化。
