@echo off
setlocal enabledelayedexpansion

echo ============================================
echo AI游戏后端 - 数据库初始化脚本
echo ============================================
echo.

cd /d "%~dp0backend"

REM === Step 1: 检查 Docker 是否运行 ===
echo [1/5] 检查 Docker 状态...
docker info >nul 2>&1
if errorlevel 1 (
    echo   Docker 未运行，正在尝试启动 Docker Desktop...
    powershell -Command "Start-Process 'C:\Program Files\Docker\Docker\Docker Desktop.exe' -WindowStyle Hidden"
    echo   等待 Docker Desktop 启动 (30秒)...
    timeout /t 30 /nobreak >nul
    docker info >nul 2>&1
    if errorlevel 1 (
        echo   [错误] Docker 启动失败，请手动启动 Docker Desktop 后重试
        echo   或使用: docker-compose up -d
        exit /b 1
    )
)

REM === Step 2: 启动 PostgreSQL 容器 ===
echo.
echo [2/5] 启动 PostgreSQL 容器...
docker ps --filter "name=aiyougame-db" --format "{{.Names}}" | findstr "aiyougame-db" >nul
if errorlevel 1 (
    echo   容器不存在，使用 docker-compose 启动...
    cd /d "%~dp0"
    docker-compose up -d
    if errorlevel 1 (
        echo   [错误] docker-compose 启动失败
        exit /b 1
    )
    echo   等待 PostgreSQL 就绪 (15秒)...
    timeout /t 15 /nobreak >nul
) else (
    echo   容器已在运行
)

REM === Step 3: 等待 PostgreSQL 完全就绪 ===
echo.
echo [3/5] 等待 PostgreSQL 就绪...
set retries=30
:wait_pg
docker exec aiyougame-db pg_isready -U postgres >nul 2>&1
if errorlevel 1 (
    set /a retries-=1
    if !retries! gtr 0 (
        echo   等待中... (!retries! 次重试)
        timeout /t 2 /nobreak >nul
        goto :wait_pg
    )
    echo   [错误] PostgreSQL 无法连接
    exit /b 1
)
echo   PostgreSQL 已就绪

REM === Step 4: 安装依赖 ===
echo.
echo [4/5] 安装后端依赖...
cd /d "%~dp0backend"
if not exist node_modules (
    call npm install
    if errorlevel 1 (
        echo   [错误] npm install 失败
        exit /b 1
    )
)

REM === Step 5: Prisma 数据库迁移 ===
echo.
echo [5/5] 执行数据库迁移...
call npx prisma migrate dev --name init --skip-generate
if errorlevel 1 (
    echo   [警告] 迁移可能已执行或失败，检查数据库状态...
    call npx prisma db push --skip-generate
)

REM === Step 6: 种子数据 ===
echo.
echo [6/6] 写入种子数据 (顾晨角色)...
call npx prisma db seed
if errorlevel 1 (
    echo   [错误] 种子数据写入失败
    exit /b 1
)

REM === 验证 ===
echo.
echo ============================================
echo 验证数据...
echo ============================================
docker exec aiyougame-db psql -U postgres -d aiyougame_dev -c "SELECT code, name, price FROM characters;" 2>nul
if errorlevel 1 (
    echo   [警告] 无法验证，可能需要手动检查
) else (
    echo.
    echo ============================================
    echo 数据库初始化完成!
    echo ============================================
    echo.
    echo 下一步:
    echo   1. 启动后端: cd backend ^&^& npm run start:dev
    echo   2. 运行测试: ..\test-api.bat
    echo   3. 查看数据: npx prisma studio
    echo.
)

endlocal
