@echo off
cd /d %~dp0
set VERSION=v1.0.1

echo Building dist\MJDingTalk.exe (1-2 minutes on first build)...
python -m PyInstaller --noconfirm --clean --noconsole --onefile --icon assets/app.ico --add-data "assets;assets" --name MJDingTalk main.py
if not %errorlevel% == 0 (
    echo.
    echo Build FAILED - run: pip install pyinstaller PyQt6 uiautomation pywin32
    pause
    exit /b 1
)

copy /y config.json dist\config.json >nul
powershell -NoProfile -Command "Compress-Archive -Path 'dist\MJDingTalk.exe','dist\config.json' -DestinationPath 'dist\MJDingTalk-green-%VERSION%.zip' -Force"
echo.
echo Build OK: dist\MJDingTalk.exe
echo Pack OK : dist\MJDingTalk-green-%VERSION%.zip  (exe + config.json)
pause
