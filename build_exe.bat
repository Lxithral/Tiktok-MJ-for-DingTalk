@echo off
cd /d %~dp0
echo Building dist\MJDingTalk.exe (1-2 minutes on first build)...
python -m PyInstaller --noconfirm --clean --noconsole --onefile --icon assets/app.ico --add-data "assets;assets" --name MJDingTalk main.py
if %errorlevel% == 0 (
    echo.
    echo Build OK: dist\MJDingTalk.exe
) else (
    echo.
    echo Build FAILED - run: pip install pyinstaller PyQt6 uiautomation pywin32
)
pause
