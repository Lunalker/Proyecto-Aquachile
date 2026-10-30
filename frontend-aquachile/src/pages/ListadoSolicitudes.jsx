import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import EstadoBadge from '../components/EstadoBadge';
import { useAuth } from '../context/AuthContext';

const URL_SOLICITUDES = '/solicitudes';

// Permite buscar sin distinguir mayúsculas ni tildes.
function normalizarTexto(valor) {
  return String(valor ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

function ListadoSolicitudes() {
  const { usuario } = useAuth();
  const [solicitudes, setSolicitudes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  const [busqueda, setBusqueda] = useState('');
  const [filtroEstado, setFiltroEstado] = useState('Todos');
  const [filtroFecha, setFiltroFecha] = useState('');

  useEffect(() => {
    let activo = true;

    async function obtenerSolicitudes() {
      try {
        const respuesta = await api.get(URL_SOLICITUDES);
        if (!Array.isArray(respuesta.data)) {
          throw new Error('La respuesta del servidor no es una lista de solicitudes.');
        }
        if (activo) setSolicitudes(respuesta.data);
      } catch (err) {
        console.error('Error al obtener solicitudes:', err);
        if (activo) setError('No fue posible cargar las solicitudes. Comprueba que Spring Boot esté encendido.');
      } finally {
        if (activo) setCargando(false);
      }
    }

    obtenerSolicitudes();
    return () => { activo = false; };
  }, []);

  // La tabla se actualiza automáticamente cuando se escribe o cambia un filtro.
  const solicitudesFiltradas = useMemo(() => {
    const textoBuscado = normalizarTexto(busqueda);

    return solicitudes.filter((solicitud) => {
      const textoSolicitud = normalizarTexto([
        solicitud.candidato?.nombre,
        solicitud.cargo,
        solicitud.familiaCargo,
        solicitud.profesionalResponsable,
      ].join(' '));

      const coincideTexto = !textoBuscado || textoSolicitud.includes(textoBuscado);
      const coincideEstado = filtroEstado === 'Todos' ||
        normalizarTexto(solicitud.estado) === normalizarTexto(filtroEstado);
      const coincideFecha = !filtroFecha ||
        String(solicitud.fechaSolicitud ?? '').slice(0, 10) === filtroFecha;

      return coincideTexto && coincideEstado && coincideFecha;
    });
  }, [solicitudes, busqueda, filtroEstado, filtroFecha]);

  const hayFiltros = Boolean(busqueda || filtroFecha || filtroEstado !== 'Todos');

  function limpiarFiltros() {
    setBusqueda('');
    setFiltroEstado('Todos');
    setFiltroFecha('');
  }

  return (
    <div className="mx-auto max-w-6xl p-4 md:p-6">
      <h2 className="mb-2 text-2xl font-bold text-gray-800">Panel de Evaluación Psicolaboral</h2>
      <p className="mb-6 text-sm text-gray-600">
        Consulta solicitudes y filtra por candidato, cargo, familia, responsable, estado o fecha.
      </p>

      <section className="mb-5 rounded-xl border border-gray-200 bg-white p-4 shadow-sm" aria-label="Filtros de solicitudes">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <div>
            <label htmlFor="buscar-solicitud" className="mb-1 block text-sm font-medium text-gray-700">
              Buscar solicitud
            </label>
            <input
              id="buscar-solicitud"
              type="search"
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              placeholder="Nombre, cargo o responsable"
              className="w-full rounded-md border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none"
            />
          </div>
          <div>
            <label htmlFor="filtrar-estado" className="mb-1 block text-sm font-medium text-gray-700">
              Estado
            </label>
            <select
              id="filtrar-estado"
              value={filtroEstado}
              onChange={(e) => setFiltroEstado(e.target.value)}
              className="w-full rounded-md border border-gray-300 bg-white px-3 py-2 focus:border-blue-500 focus:outline-none"
            >
              <option value="Todos">Todos los estados</option>
              <option value="Pendiente">Pendiente</option>
              <option value="En proceso">En proceso</option>
              <option value="Finalizada">Finalizada</option>
            </select>
          </div>
          <div>
            <label htmlFor="filtrar-fecha" className="mb-1 block text-sm font-medium text-gray-700">
              Fecha de solicitud
            </label>
            <input
              id="filtrar-fecha"
              type="date"
              value={filtroFecha}
              onChange={(e) => setFiltroFecha(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none"
            />
          </div>
        </div>
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-gray-600" aria-live="polite">
            {cargando ? 'Cargando solicitudes...' : error ? 'No se pudieron consultar los registros' :
              `Mostrando ${solicitudesFiltradas.length} de ${solicitudes.length} solicitudes`}
          </p>
          <button
            type="button"
            onClick={limpiarFiltros}
            disabled={!hayFiltros}
            className="rounded-md border border-gray-300 px-3 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Limpiar filtros
          </button>
        </div>
      </section>

      <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        {cargando ? (
          <p className="p-6 text-center text-gray-600" role="status">Cargando solicitudes...</p>
        ) : error ? (
          <p className="p-6 text-center text-red-700" role="alert">{error}</p>
        ) : solicitudes.length === 0 ? (
          <p className="p-6 text-center text-gray-600">Todavía no hay solicitudes registradas.</p>
        ) : solicitudesFiltradas.length === 0 ? (
          <div className="p-6 text-center">
            <p className="mb-3 text-gray-600">No hay solicitudes que coincidan con los filtros.</p>
            <button type="button" onClick={limpiarFiltros} className="font-semibold text-blue-700 hover:underline">
              Mostrar todas
            </button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[840px] border-collapse text-left">
              <thead>
                <tr className="border-b bg-gray-50 text-gray-700">
                  <th className="p-4 font-semibold">ID</th>
                  <th className="p-4 font-semibold">Candidato</th>
                  <th className="p-4 font-semibold">Cargo / Familia</th>
                  <th className="p-4 font-semibold">Responsable</th>
                  <th className="p-4 font-semibold">Fecha</th>
                  <th className="p-4 font-semibold">Estado</th>
                  <th className="p-4 font-semibold">Acción</th>
                </tr>
              </thead>
              <tbody>
                {solicitudesFiltradas.map((solicitud) => (
                  <tr key={solicitud.id} className="border-b hover:bg-gray-50">
                    <td className="p-4 text-gray-500">#{solicitud.id}</td>
                    <td className="p-4 font-semibold text-gray-800">{solicitud.candidato?.nombre || 'Sin asignar'}</td>
                    <td className="p-4 text-gray-600">
                      <div>{solicitud.cargo || 'Sin cargo'}</div>
                      <div className="text-xs text-gray-500">{solicitud.familiaCargo || 'Sin familia'}</div>
                    </td>
                    <td className="p-4 text-gray-600">{solicitud.profesionalResponsable || 'Sin responsable'}</td>
                    <td className="p-4 text-gray-600">{String(solicitud.fechaSolicitud ?? '').slice(0, 10) || 'Sin fecha'}</td>
                    <td className="p-4"><EstadoBadge estado={solicitud.estado} /></td>
                    <td className="p-4">
                      {usuario?.rol === 'EVALUADOR' ? (
                        <Link to={`/evaluar/${solicitud.id}`} className="inline-block rounded bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700">Gestionar</Link>
                      ) : <span className="text-sm text-gray-500">Solo consulta</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

export default ListadoSolicitudes;
