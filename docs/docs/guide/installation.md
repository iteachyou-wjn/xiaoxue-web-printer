# 安装部署

本页介绍打印服务的**环境要求**、**源码编译**以及 **Windows EXE 打包**的完整流程。

## 环境要求

| 项目 | 要求 | 参考路径 |
| --- | --- | --- |
| JDK | 17 或更高 | `D:\Program Files\Java\jdk-17` |
| Maven | 3.6+ | `D:\Dev\apache-maven-3.9.9` |
| Inno Setup | 6（生成安装包时需要） | `D:\Program Files (x86)\Inno Setup 6` |
| 操作系统 | Windows x86_64 | — |

## 方式一：直接运行（开发调试）

```bash
mvn clean package
java -jar target/dreamer-print-service-1.0.6.jar
```

启动后系统托盘出现打印机图标，即表示服务已就绪。

## 方式二：一键打包（推荐）

项目提供了一键打包脚本 `build\打包.bat`，双击后输入版本号即可自动完成全部流程：

```
[1/6] 更新版本号到指定版本
[2/6] Maven 构建
[3/6] 复制依赖到 build/libs
[4/6] ProGuard 混淆瘦 jar
[5/6] jpackage 生成 app-image（自动复制 reports 模板）
[6/6] Inno Setup 编译安装包到 build/Output
```

产物：

- 绿色版：`build\app-image\晓雪WEB打印控件\`（自带 JRE，可直接运行）
- 安装包：`build\windows-晓雪WEB打印控件-版本号-x86_64.exe`

### 目录结构

```
build/
├── 打包.bat              # 双击入口
├── build.ps1             # 打包脚本
├── dreamer-print.iss     # Inno Setup 脚本
├── dreamer-print.ico     # 应用图标
├── proguard.pro          # ProGuard 混淆配置
├── tools/                # ProGuard 工具 jar
├── input/                # 中间产物（瘦 jar + 依赖）
├── libs/                 # 依赖 jar
├── app-image/            # 绿色版输出
│   └── 晓雪WEB打印控件/
│       ├── 晓雪WEB打印控件.exe
│       ├── app/
│       ├── runtime/
│       └── reports/
│           └── jr/       # JasperReport 模板
└── Output/               # 安装包输出目录
```

## 模板文件目录

| 目录 | 用途 |
| --- | --- |
| `reports/jr/` | JasperReport 模板（.jasper） |
| `reports/bt/` | BarTender 模板（.btw） |

将模板文件放到对应目录后，前端通过 SDK 的 `listTemplates()` 获取列表。

## 服务配置

服务地址与端口在 `MainApp` 中定义：

```java
public static final String WS_HOST = "127.0.0.1";
public static final int WS_PORT = 54321;
```

默认只绑定回环地址。若需局域网访问，将 `WS_HOST` 改为 `0.0.0.0`（**同时应自行增加鉴权，本项目未内置**）。

## 验证安装

1. 运行程序后，系统托盘出现应用图标；
2. 命令行执行 `netstat -ano | findstr 54321`，应能看到监听；
3. 用浏览器打开 demo 页面，`getPrinters()` 能返回本机打印机列表。

> 更多使用问题见[常见问题](./faq)。
