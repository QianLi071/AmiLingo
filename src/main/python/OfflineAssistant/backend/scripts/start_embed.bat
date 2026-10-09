@echo off
chcp 65001 >nul
REM ============================================================
REM 启动 llama-server（Qwen3-Embedding GGUF，监听 127.0.0.1:8082）
REM 本脚本位于 backend\scripts\，先切换到项目根目录
REM ============================================================
cd /d "%~dp0..\.."

REM 兼容两种构建产物位置（同 start_llama.bat）
set "LLAMA_EXE=llama.cpp\llama.cpp\build\bin\Release\llama-server.exe"
if not exist "%LLAMA_EXE%" set "LLAMA_EXE=llama.cpp\llama.cpp\build\bin\llama-server.exe"

if not exist "%LLAMA_EXE%" (
    echo [错误] 未找到 llama-server.exe: %LLAMA_EXE%
    pause
    exit /b 1
)

set "MODEL_FILE=models\Qwen3-Embedding-0.6B-Q8_0.gguf"
if not exist "%MODEL_FILE%" (
    echo [错误] 未找到模型文件: %MODEL_FILE%
    pause
    exit /b 1
)

echo [启动] %LLAMA_EXE%
echo [模型] %MODEL_FILE%
echo [地址] http://127.0.0.1:8082
echo.
echo 显存不足时，可在下方命令追加 -ngl 0（Embedding 走 CPU）降低显存占用。
echo 看到 "server is listening" / "all slots are ready" 后服务才可用。
echo 此窗口关闭即停止服务。
echo.
"%LLAMA_EXE%" -m "%MODEL_FILE%" --host 127.0.0.1 --port 8082 --embedding -c 8192

pause