import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import Icono from './Icono';

const STORAGE_KEY = 'aquachile_notificaciones_vistas';

function leerVistas() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return new Set(raw ? JSON.parse(raw) : []);
  } catch {
    return new Set();
  }
}

function guardarVistas(setIds) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([...setIds]));
  } catch {
    // ignorar errores de cuota
  }
}

function formatearFecha(fechaIso) {
  if (!fechaIso) return 'Reciente';
  const fecha = new Date(fechaIso);
  if (Number.isNaN(fecha.getTime())) return 'Reciente';
  return fecha.toLocaleString('es-CL', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function MenuNotificaciones() {
  const navigate = useNavigate();
  const contenedorRef = useRef(null);
  const [abierto, setAbierto] = useState(false);
  const [postulaciones, setPostulaciones] = useState([]);
  const [vistas, setVistas] = useState(() => leerVistas());

  useEffect(() => {
    let activo = true;

    async function cargarPostulaciones() {
      try {
        const res = await api.get('/postulaciones');
        if (!activo || !Array.isArray(res.data)) return;
        const ordenadas = [...res.data].sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
        setPostulaciones(ordenadas.slice(0, 15));
      } catch {
        // silencio si hay corte temporal de red
      }
    }

    cargarPostulaciones();
    const intervalo = window.setInterval(cargarPostulaciones, 5000);
    window.addEventListener('focus', cargarPostulaciones);

    return () => {
      activo = false;
      window.clearInterval(intervalo);
      window.removeEventListener('focus', cargarPostulaciones);
    };
  }, []);

  useEffect(() => {
    if (!abierto) return undefined;
    function manejarClickFuera(e) {
      if (contenedorRef.current && !contenedorRef.current.contains(e.target)) {
        setAbierto(false);
      }
    }
    function manejarEscape(e) {
      if (e.key === 'Escape') setAbierto(false);
    }
    document.addEventListener('mousedown', manejarClickFuera);
    window.addEventListener('keydown', manejarEscape);
    return () => {
      document.removeEventListener('mousedown', manejarClickFuera);
      window.removeEventListener('keydown', manejarEscape);
    };
  }, [abierto]);

  const noLeidas = postulaciones.filter((p) => !vistas.has(p.id)).length;

  function marcarTodasLeidas() {
    const nuevoSet = new Set(vistas);
    postulaciones.forEach((p) => nuevoSet.add(p.id));
    setVistas(nuevoSet);
    guardarVistas(nuevoSet);
  }

  function abrirPostulacion(item) {
    const nuevoSet = new Set(vistas);
    nuevoSet.add(item.id);
    setVistas(nuevoSet);
    guardarVistas(nuevoSet);
    setAbierto(false);

    if (item.candidatoId) {
      navigate(`/candidatos/${item.candidatoId}`);
    } else {
      navigate('/solicitudes');
    }
  }

  return (
    <div ref={contenedorRef} className="relative">
      <button
        type="button"
        onClick={() => setAbierto((prev) => !prev)}
        aria-label={`Notificaciones de postulaciones (${noLeidas} sin leer)`}
        aria-expanded={abierto}
        className="relative inline-flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-600 transition hover:bg-slate-50 hover:text-slate-900"
      >
        <Icono nombre="campana" size={18} />
        {noLeidas > 0 && (
          <span
            style={{
              position: 'absolute',
              top: '-4px',
              right: '-4px',
              minWidth: '18px',
              height: '18px',
              padding: '0 4px',
              borderRadius: '9999px',
              backgroundColor: '#e11d48',
              color: '#ffffff',
              fontSize: '11px',
              fontWeight: 700,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 1px 2px rgba(0,0,0,0.2)',
            }}
          >
            {noLeidas > 9 ? '9+' : noLeidas}
          </span>
        )}
      </button>

      {abierto && (
        <div
          role="dialog"
          aria-label="Notificaciones del portal externo"
          className="absolute right-0 z-50 mt-2 w-80 sm:w-96 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl"
        >
          <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50/80 px-4 py-3">
            <div>
              <h3 className="text-sm font-semibold text-slate-900">Postulaciones externas</h3>
              <p className="text-xs text-slate-500">
                {noLeidas > 0 ? `${noLeidas} nueva(s) sin revisar` : 'Todas revisadas'}
              </p>
            </div>
            {noLeidas > 0 && (
              <button
                type="button"
                onClick={marcarTodasLeidas}
                className="text-xs font-medium text-teal-700 hover:text-teal-900 hover:underline"
              >
                Marcar todo leído
              </button>
            )}
          </div>

          <div className="max-h-80 overflow-y-auto divide-y divide-slate-100">
            {postulaciones.length === 0 ? (
              <div className="px-4 py-8 text-center text-sm text-slate-500">
                Aún no hay postulaciones desde el portal externo.
              </div>
            ) : (
              postulaciones.map((item) => {
                const esNueva = !vistas.has(item.id);
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => abrirPostulacion(item)}
                    className={`flex w-full items-start gap-3 px-4 py-3 text-left transition hover:bg-slate-50 ${
                      esNueva ? 'bg-teal-50/40' : 'bg-white'
                    }`}
                  >
                    <span
                      style={{
                        marginTop: '6px',
                        width: '8px',
                        height: '8px',
                        borderRadius: '9999px',
                        flexShrink: 0,
                        backgroundColor: esNueva ? '#0d9488' : 'transparent',
                      }}
                    />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-slate-900">
                        {item.candidatoNombre || item.nombreCandidato || 'Postulante externo'}
                      </p>
                      <p className="truncate text-xs text-slate-600">
                        Postuló a{' '}
                        <span className="font-medium text-slate-800">
                          {item.ofertaCargo || item.cargoOferta || item.cargo || 'Oferta publicada'}
                        </span>
                      </p>
                      <p className="mt-1 text-[11px] text-slate-400">
                        {formatearFecha(item.fechaPostulacion || item.fechaCreacion)}
                      </p>
                    </div>
                  </button>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}