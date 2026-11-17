@echo off
title Lanzador Proyecto AquaChile

echo ==================================================
echo   INICIANDO ENTORNO COMPLETO - AQUACHILE
echo ==================================================
echo.

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

echo [2/4] Iniciando Backend Spring Boot (http://localhost:8080)...
pushd "%~dp0proyectoAquachile"
start "AquaChile - Spring Boot (8080)" cmd /k "mvnw.cmd spring-boot:run"
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
echo ==================================================
echo   TODO INICIADO CORRECTAMENTE
echo   - Panel Interno:  http://localhost:5173
echo   - Portal Externo: http://localhost:5174
echo   - Backend API:    http://localhost:8080
echo ==================================================
echo.
pause