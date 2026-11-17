@echo off
echo Deteniendo servidores en puertos 8080, 5173 y 5174...

for %%P in (8080 5173 5174) do (
    for /f "tokens=5" %%A in ('netstat -ano ^| findstr ":%%P " ^| findstr "LISTENING"') do (
        taskkill /F /PID %%A > nul 2>&1
    )
)

taskkill /FI "WINDOWTITLE eq AquaChile - *" /T /F > nul 2>&1
if exist "%~dp0.spring.status" del "%~dp0.spring.status" > nul 2>&1

echo Deteniendo contenedor MySQL en Docker...
pushd "%~dp0proyectoAquachile"
docker compose stop
popd

echo Listo. Todos los servicios fueron detenidos.
pause