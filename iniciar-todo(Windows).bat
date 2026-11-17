@echo off
title Lanzador Proyecto AquaChile

echo ==================================================
echo   INICIANDO ENTORNO COMPLETO - AQUACHILE (WINDOWS)
echo ==================================================
echo.

REM Verifica e instala dependencias de React si faltan
for %%A in (frontend-aquachile portal-postulantes-aquachile) do (
    if not exist "%~dp0%%A\node_modules\.bin\vite.cmd" (
        echo [Preparando] Instalando dependencias en %%A...
        pushd "%~dp0%%A"
        if exist node_modules rmdir /s /q node_modules
        call npm install
        popd
    )
)

echo [1/4] Levantando MySQL en Docker...
pushd "%~dp0proyectoAquachile"
docker compose up -d
if errorlevel 1 (
    echo.
    echo [ERROR] No se pudo iniciar Docker. Asegurate de tener Docker Desktop abierto.
    popd
    pause
    exit /b 1
)
popd

echo Esperando 5 segundos a que MySQL acepte conexiones...
timeout /t 5 /nobreak > nul

echo RUNNING > "%~dp0.spring.status"

echo [2/4] Iniciando Backend Spring Boot (http://localhost:8080)...
pushd "%~dp0proyectoAquachile"
start "AquaChile - Spring Boot (8080)" cmd /k "call mvnw.cmd spring-boot:run & echo FAILED > ..\.spring.status"
popd

echo [3/4] Iniciando Panel Interno React (http://localhost:5173)...
pushd "%~dp0frontend-aquachile"
start "AquaChile - Panel Interno (5173)" cmd /k "npm run dev -- --port 5173"
popd

echo [4/4] Iniciando Portal Externo React (http://localhost:5174)...
pushd "%~dp0portal-postulantes-aquachile"
start "AquaChile - Portal Externo (5174)" cmd /k "npm run dev -- --port 5174"
popd

echo.
echo Esperando a que Spring Boot termine de arrancar...

set SEGUNDOS=0

:esperar_spring
REM 1. Verifica si el proceso de Spring Boot fallo y termino
findstr /C:"FAILED" "%~dp0.spring.status" > nul 2>&1
if %errorlevel%==0 (
    del "%~dp0.spring.status" > nul 2>&1
    echo.
    echo ==================================================
    echo   [ERROR] SPRING BOOT NO PUDO INICIAR
    echo ==================================================
    echo Revisa el detalle del error en la ventana:
    echo "AquaChile - Spring Boot (8080)"
    echo.
    echo Cerrando servidores de React...
    taskkill /FI "WINDOWTITLE eq AquaChile - Panel Interno*" /T /F > nul 2>&1
    taskkill /FI "WINDOWTITLE eq AquaChile - Portal Externo*" /T /F > nul 2>&1
    pause
    exit /b 1
)

REM 2. Verifica si el puerto 8080 ya acepta conexiones
netstat -ano | findstr ":8080 " | findstr "LISTENING" > nul 2>&1
if %errorlevel%==0 (
    del "%~dp0.spring.status" > nul 2>&1
    echo.
    echo ==================================================
    echo   [OK] TODO INICIADO Y LISTO PARA USAR
    echo   - Panel Interno:  http://localhost:5173
    echo   - Portal Externo: http://localhost:5174
    echo   - Backend API:    http://localhost:8080
    echo ==================================================
    echo.
    pause
    exit /b 0
)

if %SEGUNDOS% GEQ 60 (
    del "%~dp0.spring.status" > nul 2>&1
    echo.
    echo ==================================================
    echo   [AVISO] SPRING BOOT AUN NO RESPONDE (60s)
    echo   Puede estar descargando dependencias o bloqueado.
    echo   Revisa la ventana "AquaChile - Spring Boot (8080)".
    echo ==================================================
    echo.
    pause
    exit /b 0
)

<nul set /p=.
timeout /t 2 /nobreak > nul
set /a SEGUNDOS+=2
goto :esperar_spring