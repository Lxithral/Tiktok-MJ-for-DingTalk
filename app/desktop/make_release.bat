@echo off
rem 打 GitHub Release 发布包: 构建 exe -> 暂存到 %RELEASE_DIR% -> 打 win64 zip
rem 产物: %RELEASE_DIR%\MJDingTalk.exe + config.json + README.txt + MJDingTalk-<版本>-win64.zip
rem 解压 zip 后双击 MJDingTalk.exe 即可直接使用
cd /d %~dp0
set VERSION=v1.4.0
set RELEASE_DIR=D:\desktop\MJ-DingTalk-release
set ZIP_NAME=MJDingTalk-%VERSION%-win64.zip

echo [1/3] Building MJDingTalk.exe (1-2 minutes on first build)...
python -m PyInstaller --noconfirm --clean --noconsole --onefile --icon assets/app.ico --add-data "assets;assets" --name MJDingTalk main.py
if not %errorlevel% == 0 (
    echo.
    echo Build FAILED - run: pip install pyinstaller PyQt6 uiautomation pywin32
    pause
    exit /b 1
)

echo [2/3] Staging release folder %RELEASE_DIR% ...
if not exist "%RELEASE_DIR%" mkdir "%RELEASE_DIR%"
copy /y dist\MJDingTalk.exe "%RELEASE_DIR%\MJDingTalk.exe" >nul
copy /y config.json "%RELEASE_DIR%\config.json" >nul
copy /y release_note.txt "%RELEASE_DIR%\README.txt" >nul

echo [3/3] Packing %ZIP_NAME% ...
powershell -NoProfile -Command "Compress-Archive -Path '%RELEASE_DIR%\MJDingTalk.exe','%RELEASE_DIR%\config.json','%RELEASE_DIR%\README.txt' -DestinationPath '%RELEASE_DIR%\%ZIP_NAME%' -Force"
if not %errorlevel% == 0 (
    echo Pack FAILED
    pause
    exit /b 1
)

echo.
echo Release OK : %RELEASE_DIR%\%ZIP_NAME%   ^(upload this to GitHub Release^)
echo Loose files: %RELEASE_DIR%\MJDingTalk.exe  ^(double-click to run^)
pause
