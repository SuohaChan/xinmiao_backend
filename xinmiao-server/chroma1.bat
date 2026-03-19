@echo off
chcp 65001 >nul 2>&1
echo ==============================
echo Start Chroma via Docker Compose
echo ==============================

set "SCRIPT_DIR=%~dp0"
set "COMPOSE_FILE=%SCRIPT_DIR%..\docker-compose.yml"

echo [1/2] Starting Chroma (docker compose)...
docker compose -f "%COMPOSE_FILE%" up -d chroma
if %errorlevel% neq 0 (
    echo Failed to start Chroma. Ensure Docker Desktop is running.
    pause >nul
    exit /b 1
)

echo.
echo [2/2] Container status:
docker ps --filter "name=chroma-db-spring-ai" --format "Container ID: {{.ID}} | Status: {{.Status}} | Ports: {{.Ports}}"

echo.
echo Done! Chroma API: http://localhost:8000
pause >nul