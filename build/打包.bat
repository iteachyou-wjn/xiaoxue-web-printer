@echo off
chcp 65001 >nul
title 梦想家WEB打印控件 - 一键打包
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
if errorlevel 1 (
  echo.
  echo 打包过程中出现错误，请查看上方日志。
  pause
)
