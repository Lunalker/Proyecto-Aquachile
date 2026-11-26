#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$DIR/logs"
mkdir -p "$LOG_DIR"

echo "=================================================="
echo "  INICIANDO ENTORNO COMPLETO - AQUACHILE (LINUX)"
echo "=================================================="
echo ""

# 1. Verifica e instala dependencias de React si faltan
for APP in "frontend-aquachile" "portal-postulantes-aquachile"; do
  if [ ! -f "$DIR/$APP/node_modules/.bin/vite" ]; then
    echo "[Preparando] Instalando dependencias en $APP..."
    rm -rf "$DIR/$APP/node_modules"
    (cd "$DIR/$APP" && npm install)
  fi
done

# 2. Levanta MySQL en Docker validando que el daemon esté activo
echo "[1/4] Levantando MySQL en Docker..."
if ! docker info >/dev/null 2>&1; then
  echo ""
  echo "[ERROR] No se pudo conectar con Docker. Asegúrate de tener el servicio de Docker iniciado (sudo systemctl start docker o Docker Desktop)."
  exit 1
fi

(cd "$DIR/proyectoAquachile" && docker compose up -d)

echo "Esperando 5 segundos a que MySQL acepte conexiones..."
sleep 5

chmod +x "$DIR/proyectoAquachile/mvnw"

BACK_LOG="$LOG_DIR/backend.log"
BACK_ERR="$LOG_DIR/backend-err.log"
FRONT_LOG="$LOG_DIR/frontend.log"
FRONT_ERR="$LOG_DIR/frontend-err.log"
PORTAL_LOG="$LOG_DIR/portal.log"
PORTAL_ERR="$LOG_DIR/portal-err.log"

rm -f "$BACK_LOG" "$BACK_ERR" "$FRONT_LOG" "$FRONT_ERR" "$PORTAL_LOG" "$PORTAL_ERR"

# 3. Inicia los servidores en segundo plano (desacoplados con nohup) y redirige logs a ./logs/
echo "[2/4] Iniciando Backend Spring Boot (http://localhost:8080)..."
(
  cd "$DIR/proyectoAquachile"
  nohup ./mvnw spring-boot:run > "$BACK_LOG" 2> "$BACK_ERR" &
  echo $! > "$LOG_DIR/.backend.pid"
)
PID_BACK=$(cat "$LOG_DIR/.backend.pid")

echo "[3/4] Iniciando Panel Interno React (http://localhost:5173)..."
(
  cd "$DIR/frontend-aquachile"
  nohup npm run dev -- --port 5173 > "$FRONT_LOG" 2> "$FRONT_ERR" &
  echo $! > "$LOG_DIR/.frontend.pid"
)
PID_FRONT=$(cat "$LOG_DIR/.frontend.pid")

echo "[4/4] Iniciando Portal Externo React (http://localhost:5174)..."
(
  cd "$DIR/portal-postulantes-aquachile"
  nohup npm run dev -- --port 5174 > "$PORTAL_LOG" 2> "$PORTAL_ERR" &
  echo $! > "$LOG_DIR/.portal.pid"
)
PID_PORTAL=$(cat "$LOG_DIR/.portal.pid")

echo ""
echo "Esperando a que Spring Boot termine de arrancar..."

SPRING_LISTO=0
MAX_SEGUNDOS=60

for ((i=1; i<=MAX_SEGUNDOS; i+=2)); do
  sleep 2

  # Verifica si el proceso de Spring Boot murió por un error
  if ! kill -0 "$PID_BACK" 2>/dev/null; then
    echo ""
    echo "=================================================="
    echo "  [ERROR] SPRING BOOT NO PUDO INICIAR"
    echo "=================================================="
    echo "Últimas líneas de logs/backend.log:"
    echo "--------------------------------------------------"
    [ -f "$BACK_LOG" ] && tail -n 25 "$BACK_LOG"
    if [ -s "$BACK_ERR" ]; then
      echo "--- Errores (logs/backend-err.log) ---"
      tail -n 15 "$BACK_ERR"
    fi
    echo "--------------------------------------------------"
    kill "$PID_FRONT" "$PID_PORTAL" 2>/dev/null || true
    rm -f "$LOG_DIR"/.*.pid
    exit 1
  fi

  # Verifica si el puerto 8080 ya acepta conexiones
  if (echo > /dev/tcp/localhost/8080) >/dev/null 2>&1; then
    SPRING_LISTO=1
    break
  fi

  printf "."
done

echo ""
if [ $SPRING_LISTO -eq 1 ]; then
  echo "=================================================="
  echo "  [OK] TODO INICIADO Y LISTO PARA USAR"
  echo "  - Panel Interno:  http://localhost:5173"
  echo "  - Portal Externo: http://localhost:5174"
  echo "  - Backend API:    http://localhost:8080"
  echo "  - Logs en:        ./logs/"
  echo "=================================================="
else
  echo "=================================================="
  echo "  [AVISO] SPRING BOOT AÚN NO RESPONDE (60s)"
  echo "  Revisa logs/backend.log para más detalles."
  echo "=================================================="
fi

echo ""
echo "Los servidores seguirán activos en segundo plano aunque cierres esta terminal."
echo "Para detenerlos ejecuta: ./detener-todo-Linux.sh"