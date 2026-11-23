#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=================================================="
echo "  INICIANDO ENTORNO COMPLETO - AQUACHILE (LINUX)"
echo "=================================================="

# Verifica e instala dependencias de React si faltan en Linux
for APP in "frontend-aquachile" "portal-postulantes-aquachile"; do
  if [ ! -f "$DIR/$APP/node_modules/.bin/vite" ]; then
    echo "[Preparando] Instalando dependencias en $APP..."
    cd "$DIR/$APP"
    rm -rf node_modules
    npm install
  fi
done

echo "[1/4] Levantando MySQL en Docker..."
cd "$DIR/proyectoAquachile"
docker compose up -d

echo "Esperando 5 segundos a que MySQL acepte conexiones..."
sleep 5

chmod +x "$DIR/proyectoAquachile/mvnw"

limpiar() {
  echo ""
  echo "Deteniendo servidores..."
  kill $PID_BACK $PID_FRONT $PID_PORTAL 2>/dev/null || true
  exit 0
}
trap limpiar INT TERM

echo "[2/4] Iniciando Backend Spring Boot (http://localhost:8080)..."
cd "$DIR/proyectoAquachile"
./mvnw spring-boot:run > "$DIR/backend.log" 2>&1 &
PID_BACK=$!

echo "[3/4] Iniciando Panel Interno React (http://localhost:5173)..."
cd "$DIR/frontend-aquachile"
npm run dev -- --port 5173 > "$DIR/frontend.log" 2>&1 &
PID_FRONT=$!

echo "[4/4] Iniciando Portal Externo React (http://localhost:5174)..."
cd "$DIR/portal-postulantes-aquachile"
npm run dev -- --port 5174 > "$DIR/portal.log" 2>&1 &
PID_PORTAL=$!

echo ""
echo "Esperando a que Spring Boot termine de arrancar..."

SPRING_LISTO=0
MAX_SEGUNDOS=60

for ((i=1; i<=MAX_SEGUNDOS; i+=2)); do
  # 1. Verifica si el proceso de Spring Boot murió por un error
  if ! kill -0 $PID_BACK 2>/dev/null; then
    echo ""
    echo "=================================================="
    echo "  [ERROR] SPRING BOOT NO PUDO INICIAR"
    echo "=================================================="
    echo "Ultimas lineas de backend.log:"
    echo "--------------------------------------------------"
    tail -n 25 "$DIR/backend.log"
    echo "--------------------------------------------------"
    kill $PID_FRONT $PID_PORTAL 2>/dev/null || true
    exit 1
  fi

  # 2. Verifica si el puerto 8080 ya acepta conexiones
  if (echo > /dev/tcp/localhost/8080) >/dev/null 2>&1; then
    SPRING_LISTO=1
    break
  fi

  printf "."
  sleep 2
done

echo ""
if [ $SPRING_LISTO -eq 1 ]; then
  echo "=================================================="
  echo "  [OK] TODO INICIADO Y LISTO PARA USAR"
  echo "  - Panel Interno:  http://localhost:5173"
  echo "  - Portal Externo: http://localhost:5174"
  echo "  - Backend API:    http://localhost:8080"
  echo ""
  echo "  Presiona Ctrl+C para detener los servidores."
  echo "=================================================="
else
  echo "=================================================="
  echo "  [AVISO] SPRING BOOT AUN NO RESPONDE (60s)"
  echo "  Puede estar descargando dependencias o bloqueado."
  echo "  Revisa el progreso con: tail -f backend.log"
  echo "=================================================="
  tail -n 15 "$DIR/backend.log"
fi

wait