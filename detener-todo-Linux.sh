#!/usr/bin/env bash

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$DIR/logs"

echo "Deteniendo servidores en puertos 8080, 5173 y 5174..."

# 1. Detener por PIDs guardados (incluyendo procesos hijos de Maven/Node)
for PID_FILE in "$LOG_DIR"/.backend.pid "$LOG_DIR"/.frontend.pid "$LOG_DIR"/.portal.pid; do
  if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if [ -n "$PID" ]; then
      pkill -9 -P "$PID" 2>/dev/null || true
      kill -9 "$PID" 2>/dev/null || true
    fi
    rm -f "$PID_FILE"
  fi
done

# 2. Asegurar liberación de puertos 8080, 5173 y 5174
for PUERTO in 8080 5173 5174; do
  PIDS=$(lsof -ti :"$PUERTO" 2>/dev/null || fuser "$PUERTO"/tcp 2>/dev/null || true)
  if [ -n "$PIDS" ]; then
    kill -9 $PIDS 2>/dev/null || true
  fi
done

# 3. Limpiar archivo de estado temporal si existe
rm -f "$DIR/.spring.status"

echo "Deteniendo contenedor MySQL en Docker..."
(cd "$DIR/proyectoAquachile" && docker compose stop)

echo "Listo. Todos los servicios fueron detenidos."