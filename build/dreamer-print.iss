#define MyAppName "晓雪WEB打印控件"
#define MyAppVersion "1.0.7"
#define MyAppPublisher "I Teach You"
#define MyAppExeName "晓雪WEB打印控件.exe"

[Setup]
AppId={{B8F3A2E1-5C4D-4E6F-9A8B-7C2D3E4F5A6B}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
; 64 位安装模式，{autopf} 才会是 C:\Program Files，而不是 Program Files (x86)
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
OutputDir=.
OutputBaseFilename=windows-晓雪WEB打印控件-{#MyAppVersion}-x86_64
SetupIconFile=install.ico
Compression=lzma
SolidCompression=yes
UninstallDisplayIcon={app}\{#MyAppExeName}
ShowLanguageDialog=no

[Languages]
Name: "chinesesimp"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"

[Files]
Source: "app-image\晓雪WEB打印控件\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs

[Tasks]
Name: shortcuts; Description: "创建桌面和开始菜单快捷方式"; GroupDescription: "附加选项:"; Flags: checkedonce
Name: autostart; Description: "开机自动启动 {#MyAppName}"; GroupDescription: "附加选项:"; Flags: checkedonce

[Icons]
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: shortcuts
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: shortcuts

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "立即运行 {#MyAppName}"; Flags: nowait postinstall skipifsilent

[Registry]
; 与托盘菜单「开机自启」使用同一个 Run 项，勾选后登录 Windows 时启动已安装的 exe
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "{#MyAppName}"; ValueData: """{app}\{#MyAppExeName}"""; Tasks: autostart; Flags: uninsdeletevalue