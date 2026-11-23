#!/usr/bin/env bash

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Deteniendo servidores en puertos 8080, 5173 y 5174..."
for PUERTO in 8080 5173 5174; do
  PIDS=$(lsof -ti :$PUERTO 2>/dev/null || fuser $PUERTO/tcp 2>/dev/null || true)
  if [ -n "$PIDS" ]; then
    kill -9 $PIDS 2>/dev/null || true
  fi
done

echo "Deteniendo contenedor MySQL en Docker..."
cd "$DIR/proyectoAquachile"
docker compose stop

echo "Listo. Todos los servicios fueron detenidos."