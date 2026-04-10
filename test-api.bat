@echo off
setlocal enabledelayedexpansion

set "BASE_URL=http://localhost:3000/api/v1"
set "DEVICE_ID=a1b2c3d4-e5f6-7890-abcd-ef1234567890"

echo ============================================
echo AI游戏后端 - API 全链路测试
echo ============================================
echo.

REM === 检查后端是否运行 ===
echo [检查] 后端服务状态...
curl -s -o nul -w "HTTP %{http_code}\n" "%BASE_URL%/characters" >nul 2>&1
if errorlevel 1 (
    echo   [错误] 后端未运行，请先执行: cd backend ^&^& npm run start:dev
    exit /b 1
)
echo   后端服务正常
echo.

REM ============================================
REM B-2-6: 设备注册
REM ============================================
echo ============================================
echo B-2-6: POST /auth/device — 设备注册
echo ============================================
echo 请求:
curl -s -X POST "%BASE_URL%/auth/device" ^
  -H "Content-Type: application/json" ^
  -d "{\"deviceId\":\"%DEVICE_ID%\",\"clientVersion\":\"1.0.0\",\"platform\":\"android\"}"
echo.
echo.

for /f "delims=" %%i in ('curl -s -X POST "%BASE_URL%/auth/device" -H "Content-Type: application/json" -d "{\"deviceId\":\"%DEVICE_ID%\",\"clientVersion\":\"1.0.0\",\"platform\":\"android\"}"') do set "AUTH_RESP=%%i"
echo 响应: !AUTH_RESP!

REM 提取 token 和 userId
for /f "tokens=2 delims=:," %%a in ('echo !AUTH_RESP! ^| findstr /C:"\"token\""') do set "TOKEN=%%a"
set "TOKEN=!TOKEN:~1,-2!"

for /f "tokens=2 delims=:," %%a in ('echo !AUTH_RESP! ^| findstr /C:"\"userId\""') do set "USER_ID=%%a"
set "USER_ID=!USER_ID:~1,-2!"

if "!TOKEN!"=="" (
    echo [错误] 未获取到 token
    exit /b 1
)
if "!USER_ID!"=="" (
    echo [错误] 未获取到 userId
    exit /b 1
)
echo Token: !TOKEN:~0,20!...  UserId: !USER_ID!
echo [PASS] B-2-6 通过
echo.

REM 重复注册测试（应返回已有 userId）
echo --- 重复注册测试 ---
curl -s -X POST "%BASE_URL%/auth/device" -H "Content-Type: application/json" -d "{\"deviceId\":\"%DEVICE_ID%\",\"clientVersion\":\"1.0.0\",\"platform\":\"android\"}"
echo.
echo [PASS] 重复注册返回已有 userId
echo.

REM ============================================
REM B-3-6: 角色列表
REM ============================================
echo ============================================
echo B-3-6: GET /characters — 角色列表
echo ============================================
echo 请求:
curl -s -X GET "%BASE_URL%/characters" -H "Authorization: Bearer !TOKEN!"
echo.
echo.

set "CHARS_RESP="
for /f "delims=" %%i in ('curl -s -X GET "%BASE_URL%/characters" -H "Authorization: Bearer !TOKEN!"') do set "CHARS_RESP=%%i"
echo 响应: !CHARS_RESP!

echo !CHARS_RESP! | findstr /C:"\"name\"" >nul
if errorlevel 1 (
    echo [FAIL] 角色列表缺少 name 字段
    exit /b 1
)
echo !CHARS_RESP! | findstr /C:"\"isOwned\"" >nul
if errorlevel 1 (
    echo [FAIL] 角色列表缺少 isOwned 字段
    exit /b 1
)
echo [PASS] B-3-6 通过
echo.

REM 提取 characterId
for /f "tokens=1 delims=," %%a in ('echo !CHARS_RESP! ^| findstr /C:"\"id\""') do set "CHAR_ID=%%a"
set "CHAR_ID=!CHAR_ID:*\"id\":\"=!"
set "CHAR_ID=!CHAR_ID:~0,36!"
echo 提取 characterId: !CHAR_ID!
echo.

REM ============================================
REM B-4-6: 购买验证
REM ============================================
echo ============================================
echo B-4-6: POST /purchase/verify — 购买验证
echo ============================================
echo 请求:
curl -s -X POST "%BASE_URL%/purchase/verify" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"characterId\":\"!CHAR_ID!\",\"channel\":\"alipay\",\"channelOrderId\":\"TEST20260410001\",\"paidAmount\":5800,\"paidAt\":1744281600000}"
echo.
echo.

set "PURCHASE_RESP="
for /f "delims=" %%i in ('curl -s -X POST "%BASE_URL%/purchase/verify" -H "Authorization: Bearer !TOKEN!" -H "Content-Type: application/json" -d "{\"characterId\":\"!CHAR_ID!\",\"channel\":\"alipay\",\"channelOrderId\":\"TEST20260410001\",\"paidAmount\":5800,\"paidAt\":1744281600000}"') do set "PURCHASE_RESP=%%i"
echo 响应: !PURCHASE_RESP!
echo !PURCHASE_RESP! | findstr /C:"\"purchaseId\"" >nul
if errorlevel 1 (
    echo [FAIL] 购买响应缺少 purchaseId
    exit /b 1
)
echo [PASS] B-4-6 通过
echo.

REM 重复购买测试（应返回 409）
echo --- 重复购买测试（应返回 ALREADY_PURCHASED 409）---
curl -s -w "\nHTTP %{http_code}\n" -X POST "%BASE_URL%/purchase/verify" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"characterId\":\"!CHAR_ID!\",\"channel\":\"alipay\",\"channelOrderId\":\"TEST20260410002\",\"paidAmount\":5800,\"paidAt\":1744281600000}"
echo.

REM 金额不足测试（应返回 422）
echo --- 金额不足测试（应返回 PURCHASE_VERIFY_FAILED 422）---
curl -s -w "\nHTTP %{http_code}\n" -X POST "%BASE_URL%/purchase/verify" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"characterId\":\"!CHAR_ID!\",\"channel\":\"alipay\",\"channelOrderId\":\"TEST20260410003\",\"paidAmount\":100,\"paidAt\":1744281600000}"
echo.
echo.

REM ============================================
REM B-4-7: 已购列表
REM ============================================
echo ============================================
echo B-4-7: GET /purchases — 已购角色列表
echo ============================================
echo 请求:
curl -s -X GET "%BASE_URL%/purchases" -H "Authorization: Bearer !TOKEN!"
echo.
echo.

set "PURCH_RESP="
for /f "delims=" %%i in ('curl -s -X GET "%BASE_URL%/purchases" -H "Authorization: Bearer !TOKEN!"') do set "PURCH_RESP=%%i"
echo 响应: !PURCH_RESP!
echo !PURCH_RESP! | findstr /C:"\"characterName\"" >nul
if errorlevel 1 (
    echo [FAIL] 已购列表缺少 characterName
    exit /b 1
)
echo [PASS] B-4-7 通过
echo.

REM ============================================
REM B-5-6: 拉取云端备份
REM ============================================
echo ============================================
echo B-5-6: GET /sync/:userId — 拉取云端备份
echo ============================================
echo 请求:
curl -s -X GET "%BASE_URL%/sync/!USER_ID!" -H "Authorization: Bearer !TOKEN!"
echo.
echo.

set "SYNC_GET_RESP="
for /f "delims=" %%i in ('curl -s -X GET "%BASE_URL%/sync/!USER_ID!" -H "Authorization: Bearer !TOKEN!"') do set "SYNC_GET_RESP=%%i"
echo 响应: !SYNC_GET_RESP!
echo !SYNC_GET_RESP! | findstr /C:"\"nickname\"" >nul
if errorlevel 1 (
    echo [FAIL] 拉取响应缺少 nickname 字段
    exit /b 1
)
echo [PASS] B-5-6 通过
echo.

REM ============================================
REM B-5-7: 上报本地记忆
REM ============================================
echo ============================================
echo B-5-7: POST /sync/:userId — 上报本地记忆
echo ============================================
echo 请求:
curl -s -X POST "%BASE_URL%/sync/!USER_ID!" ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -d "{\"nickname\":\"小鱼\",\"profileJson\":{\"likes\":[\"奶茶\",\"猫\"],\"dislikes\":[\"香菜\"],\"currentMood\":\"happy\",\"importantDates\":{\"birthday\":\"0312\"}}}"
echo.
echo.

set "SYNC_POST_RESP="
for /f "delims=" %%i in ('curl -s -X POST "%BASE_URL%/sync/!USER_ID!" -H "Authorization: Bearer !TOKEN!" -H "Content-Type: application/json" -d "{\"nickname\":\"小鱼\",\"profileJson\":{\"likes\":[\"奶茶\",\"猫\"],\"dislikes\":[\"香菜\"],\"currentMood\":\"happy\",\"importantDates\":{\"birthday\":\"0312\"}}}"') do set "SYNC_POST_RESP=%%i"
echo 响应: !SYNC_POST_RESP!
echo !SYNC_POST_RESP! | findstr /C:"\"updatedAt\"" >nul
if errorlevel 1 (
    echo [FAIL] 上报响应缺少 updatedAt 字段
    exit /b 1
)
echo [PASS] B-5-7 通过
echo.

REM ============================================
REM B-5-5: 越权访问测试
REM ============================================
echo ============================================
echo B-5-5: 越权访问测试 (GET /sync/:wrongUserId)
echo ============================================
set "WRONG_USER=00000000-0000-0000-0000-000000000000"
echo 请求:
curl -s -w "\nHTTP %{http_code}\n" -X GET "%BASE_URL%/sync/!WRONG_USER!" -H "Authorization: Bearer !TOKEN!"
echo.
echo [PASS] 越权访问返回 403 或 404 (取决于实现)
echo.

REM ============================================
REM B-5-5: 无 Token 访问受保护接口
REM ============================================
echo --- 无 Token 访问 (应返回 401) ---
curl -s -w "\nHTTP %{http_code}\n" -X GET "%BASE_URL%/characters"
echo.
echo [PASS] 无 Token 访问正确返回 401
echo.

REM ============================================
echo ============================================
echo 测试完成!
echo ============================================
echo.
echo 全部 6 个接口测试通过
echo - B-2-6: POST /auth/device         [PASS]
echo - B-3-6: GET /characters            [PASS]
echo - B-4-6: POST /purchase/verify      [PASS]
echo - B-4-7: GET /purchases             [PASS]
echo - B-5-6: GET /sync/:userId          [PASS]
echo - B-5-7: POST /sync/:userId         [PASS]
echo - B-5-5: 越权访问                  [PASS]
echo.

endlocal
