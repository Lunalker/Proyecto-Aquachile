<# :
@echo off
title Lanzador Proyecto AquaChile
set "SCRIPT_ROOT=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Expression ([System.IO.File]::ReadAllText('%~f0'))"
if errorlevel 1 pause
exit /b
#>

$ErrorActionPreference = 'Continue'
$root = $env:SCRIPT_ROOT.TrimEnd('\')
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Write-Host '=================================================='
Write-Host '  INICIANDO ENTORNO COMPLETO - AQUACHILE (WINDOWS)'
Write-Host '=================================================='
Write-Host ''

# 1. Verifica e instala dependencias de React si faltan
foreach ($app in @('frontend-aquachile', 'portal-postulantes-aquachile')) {
    $vite = Join-Path $root "$app\node_modules\.bin\vite.cmd"
    if (-not (Test-Path $vite)) {
        Write-Host "[Preparando] Instalando dependencias en $app..."
        $nm = Join-Path $root "$app\node_modules"
        if (Test-Path $nm) { Remove-Item $nm -Recurse -Force }
        Push-Location (Join-Path $root $app)
        npm install
        Pop-Location
    }
}

# 2. Levanta MySQL en Docker
Write-Host '[1/4] Levantando MySQL en Docker...'
Push-Location (Join-Path $root 'proyectoAquachile')
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Host '[ERROR] No se pudo iniciar Docker. Asegurate de tener Docker Desktop abierto.'
    Pop-Location
    Read-Host 'Presiona Enter para salir'
    exit 1
}
Pop-Location

Write-Host 'Esperando 5 segundos a que MySQL acepte conexiones...'
Start-Sleep -Seconds 5

$backLog   = Join-Path $logDir 'backend.log'
$backErr   = Join-Path $logDir 'backend-err.log'
$frontLog  = Join-Path $logDir 'frontend.log'
$frontErr  = Join-Path $logDir 'frontend-err.log'
$portalLog = Join-Path $logDir 'portal.log'
$portalErr = Join-Path $logDir 'portal-err.log'

Remove-Item $backLog, $backErr -Force -ErrorAction SilentlyContinue

# 3. Inicia los servidores ocultos y redirige logs a .\logs\
Write-Host '[2/4] Iniciando Backend Spring Boot (http://localhost:8080)...'
$pBack = Start-Process -FilePath 'cmd.exe' `
    -ArgumentList '/c', 'mvnw.cmd spring-boot:run' `
    -WorkingDirectory (Join-Path $root 'proyectoAquachile') `
    -RedirectStandardOutput $backLog `
    -RedirectStandardError $backErr `
    -WindowStyle Hidden -PassThru

Write-Host '[3/4] Iniciando Panel Interno React (http://localhost:5173)...'
$pFront = Start-Process -FilePath 'cmd.exe' `
    -ArgumentList '/c', 'npm run dev -- --port 5173' `
    -WorkingDirectory (Join-Path $root 'frontend-aquachile') `
    -RedirectStandardOutput $frontLog `
    -RedirectStandardError $frontErr `
    -WindowStyle Hidden -PassThru

Write-Host '[4/4] Iniciando Portal Externo React (http://localhost:5174)...'
$pPortal = Start-Process -FilePath 'cmd.exe' `
    -ArgumentList '/c', 'npm run dev -- --port 5174' `
    -WorkingDirectory (Join-Path $root 'portal-postulantes-aquachile') `
    -RedirectStandardOutput $portalLog `
    -RedirectStandardError $portalErr `
    -WindowStyle Hidden -PassThru

Write-Host ''
Write-Host 'Esperando a que Spring Boot termine de arrancar...'

$listo = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2

    if ($pBack.HasExited) {
        Write-Host ''
        Write-Host '=================================================='
        Write-Host '  [ERROR] SPRING BOOT NO PUDO INICIAR'
        Write-Host '=================================================='
        Write-Host 'Ultimas lineas de logs\backend.log:'
        Write-Host '--------------------------------------------------'
        if (Test-Path $backLog) { Get-Content $backLog -Tail 25 }
        if ((Test-Path $backErr) -and ((Get-Item $backErr).Length -gt 0)) {
            Write-Host '--- Errores (backend-err.log) ---'
            Get-Content $backErr -Tail 15
        }
        Write-Host '--------------------------------------------------'
        Stop-Process -Id $pFront.Id -Force -ErrorAction SilentlyContinue
        Stop-Process -Id $pPortal.Id -Force -ErrorAction SilentlyContinue
        Read-Host 'Presiona Enter para salir'
        exit 1
    }

    $puerto = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
    if ($puerto) {
        $listo = $true
        break
    }

    Write-Host -NoNewline '.'
}

Write-Host ''
if ($listo) {
    Write-Host '=================================================='
    Write-Host '  [OK] TODO INICIADO Y LISTO PARA USAR'
    Write-Host '  - Panel Interno:  http://localhost:5173'
    Write-Host '  - Portal Externo: http://localhost:5174'
    Write-Host '  - Backend API:    http://localhost:8080'
    Write-Host '  - Logs en:        .\logs\'
    Write-Host '=================================================='
} else {
    Write-Host '=================================================='
    Write-Host '  [AVISO] SPRING BOOT AUN NO RESPONDE (60s)'
    Write-Host '  Revisa logs\backend.log para mas detalles.'
    Write-Host '=================================================='
}

Write-Host ''
Read-Host 'Presiona Enter para cerrar esta ventana (los servidores seguiran activos)'