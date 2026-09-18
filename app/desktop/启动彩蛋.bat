@echo off
cd /d %~dp0
if exist "dist\MJDingTalk.exe" (
    start "" "dist\MJDingTalk.exe"
) else (
    python main.py
)
