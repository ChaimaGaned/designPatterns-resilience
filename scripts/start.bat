@echo off
setlocal

set "ROOT=%~dp0.."
pushd "%ROOT%"
set "ROOT=%CD%"

echo.
echo  =========================================
echo   E-COMMERCE RESILIENCE PLATFORM
echo   Demarrage de tous les services...
echo  =========================================
echo.

echo [0/5] Arret des anciens processus du projet...
for %%P in (8081 8082 8083 8084 9090 8088) do (
    for /f "tokens=5" %%A in ('netstat -ano ^| findstr /R /C:":%%P .*LISTENING"') do (
        taskkill /F /PID %%A >nul 2>&1
    )
)
taskkill /F /IM nginx.exe >nul 2>&1
timeout /t 2 /nobreak > nul

echo [1/5] Build order-service...
pushd order-service
call mvn clean package -q -DskipTests
if errorlevel 1 (echo ERREUR build order-service & popd & popd & exit /b 1)
popd

echo [2/5] Build inventory-service...
pushd inventory-service
call mvn clean package -q -DskipTests
if errorlevel 1 (echo ERREUR build inventory-service & popd & popd & exit /b 1)
popd

echo [3/5] Build payment-mock...
pushd payment-mock
call mvn clean package -q -DskipTests
if errorlevel 1 (echo ERREUR build payment-mock & popd & popd & exit /b 1)
popd

echo.
echo [4/5] Demarrage des services (4 instances + payment-mock)...
echo.

start "OrderService-1 :8081" /D "%ROOT%" cmd /k "java -jar order-service\target\order-service-1.0.0.jar --server.port=8081 --payment.service.url=http://localhost:9090"
start "OrderService-2 :8083" /D "%ROOT%" cmd /k "java -jar order-service\target\order-service-1.0.0.jar --server.port=8083 --payment.service.url=http://localhost:9090"
start "InventoryService-1 :8082" /D "%ROOT%" cmd /k "java -jar inventory-service\target\inventory-service-1.0.0.jar --server.port=8082"
start "InventoryService-2 :8084" /D "%ROOT%" cmd /k "java -jar inventory-service\target\inventory-service-1.0.0.jar --server.port=8084"
start "PaymentMock :9090" /D "%ROOT%" cmd /k "java -jar payment-mock\target\payment-mock-1.0.0.jar --server.port=9090"

echo.
echo [5/5] Attente du demarrage (15 secondes)...
timeout /t 15 /nobreak > nul

echo.
echo Demarrage Nginx local...
set "NGINX_DIR=%ROOT%\nginx\nginx-1.24.0"
set "NGINX_EXE=%NGINX_DIR%\nginx.exe"
set "NGINX_CONF=%ROOT%\nginx\nginx.conf"

if exist "%NGINX_EXE%" (
    "%NGINX_EXE%" -p "%NGINX_DIR%" -c "%NGINX_CONF%" -t
    if errorlevel 1 (
        echo  ERREUR: configuration Nginx invalide.
    ) else (
        echo  Arret des anciennes instances Nginx...
        taskkill /F /IM nginx.exe >nul 2>&1
        timeout /t 1 /nobreak > nul
        start "Nginx :8088" /D "%NGINX_DIR%" "%NGINX_EXE%" -p "%NGINX_DIR%" -c "%NGINX_CONF%"
        timeout /t 2 /nobreak > nul
        echo  Nginx demarre sur http://localhost:8088 avec nginx\nginx.conf
    )
) else (
    where nginx >nul 2>&1
    if errorlevel 1 (
        echo  ATTENTION: Nginx non trouve.
        echo  Gardez nginx\nginx-1.24.0 dans le projet ou installez Nginx dans PATH.
    ) else (
        nginx -t -c "%NGINX_CONF%"
        if errorlevel 1 (
            echo  ERREUR: configuration Nginx invalide.
        ) else (
            nginx -s stop >nul 2>&1
            nginx -c "%NGINX_CONF%"
            echo  Nginx demarre sur http://localhost:8088 avec nginx\nginx.conf
        )
    )
)

echo.
echo  =========================================
echo   SERVICES DEMARRES
echo  =========================================
echo.
echo  Order Service     : http://localhost:8081 et :8083
echo  Inventory Service : http://localhost:8082 et :8084
echo  Payment Mock      : http://localhost:9090
echo  Via Nginx         : http://localhost:8088
echo.
echo  Pour tester       : Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
echo                       .\scripts\test.ps1
echo.

popd
endlocal
