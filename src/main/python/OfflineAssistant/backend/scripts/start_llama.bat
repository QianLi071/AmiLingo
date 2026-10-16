@echo off
chcp 65001 >nul
REM ============================================================
REM 启动 llama-server（Gemma GGUF 模型，监听 127.0.0.1:8081）
REM 本脚本位于 backend\scripts\，先切换到项目根目录
REM ============================================================
cd /d "%~dp0..\.."

REM 兼容两种构建产物位置：
REM   Visual Studio 生成器 -> build\bin\Release\llama-server.exe
REM   Ninja / 单配置生成器 -> build\bin\llama-server.exe
set "LLAMA_EXE=llama.cpp\llama.cpp\build\bin\Release\llama-server.exe"
if not exist "%LLAMA_EXE%" set "LLAMA_EXE=llama.cpp\llama.cpp\build\bin\llama-server.exe"

if not exist "%LLAMA_EXE%" (
    echo [错误] 未找到 llama-server.exe: %LLAMA_EXE%
    echo.
    echo 请先在 "x64 Native Tools Command Prompt for VS 2022" 中编译：
    echo   cd /d "%%~dp0..\..\llama.cpp\llama.cpp"
    echo   cmake -B build
    echo   cmake --build build --config Release --target llama-server
    pause
    exit /b 1
)

set "MODEL_FILE=models\gemma-4-E2B_q4_0-it.gguf"
if not exist "%MODEL_FILE%" (
    echo [错误] 未找到模型文件: %MODEL_FILE%
    pause
    exit /b 1
)

REM 多模态投影文件（Gemma mmproj，用于图片识别评分）
set "MMPROJ_FILE=models\gemma-4-E2B-it-mmproj.gguf"
set "MMPROJ_ARG="
if exist "%MMPROJ_FILE%" (
    set "MMPROJ_ARG=--mmproj %MMPROJ_FILE%"
) else (
    echo [警告] 未找到 mmproj 文件: %MMPROJ_FILE%，将以纯文本模式启动（不支持图片评分）
)

echo [启动] %LLAMA_EXE%
echo [模型] %MODEL_FILE%
echo [多模态] %MMPROJ_FILE%
echo [地址] http://127.0.0.1:8081
echo.
echo 看到 "server is listening" / "all slots are ready" 后服务才可用，
echo 然后在项目根目录执行: python -m backend.test_llama
echo 此窗口关闭即停止服务。
echo.
"%LLAMA_EXE%" -m "%MODEL_FILE%" %MMPROJ_ARG% --host 127.0.0.1 --port 8081 -c 8192

pause
