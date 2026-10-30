import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import TarjetaIndicador from '../components/TarjetaIndicador';

const API_URL = '';

function Dashboard() {
  const [candidatos, setCandidatos] = useState([]);
  const [solicitudes, setSolicitudes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [errorCandidatos, setErrorCandidatos] = useState('');
  const [errorSolicitudes, setErrorSolicitudes] = useState('');

  useEffect(() => {
    let activo = true;

    async function obtenerDatos() {
      // allSettled evita que un fallo en una API oculte los datos de la otra.
      const resultados = await Promise.allSettled([
        api.get(`${API_URL}/candidatos`),
        api.get(`${API_URL}/solicitudes`),
      ]);

      if (!activo) return;

      const [respuestaCandidatos, respuestaSolicitudes] = resultados;
      if (respuestaCandidatos.status === 'fulfilled' && Array.isArray(respuestaCandidatos.value.data)) {
        setCandidatos(respuestaCandidatos.value.data);
      } else {
        setErrorCandidatos('No se pudieron cargar los candidatos.');
      }

      if (respuestaSolicitudes.status === 'fulfilled' && Array.isArray(respuestaSolicitudes.value.data)) {
        setSolicitudes(respuestaSolicitudes.value.data);
      } else {
        setErrorSolicitudes('No se pudieron cargar los indicadores de solicitudes.');
      }

      setCargando(false);
    }

    obtenerDatos();
    return () => { activo = false; };
  }, []);

  const contarEstado = (estadoBuscado) => solicitudes.filter((solicitud) =>
    String(solicitud.estado ?? '').trim().toLowerCase() === estadoBuscado.toLowerCase()
  ).length;

  // Nunca mostrar 0 si falló una consulta: un guion significa "dato no disponible".
  const valorCandidatos = cargando ? '...' : errorCandidatos ? '—' : candidatos.length;
  const valorSolicitudes = (estado) => cargando ? '...' : errorSolicitudes ? '—' : contarEstado(estado);

  return (
    <div className="mx-auto max-w-6xl p-4 md:p-6">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold text-gray-800">Resumen de Evaluaciones Psicolaborales</h2>
          <p className="mt-1 text-sm text-gray-600">Indicadores generales y candidatos registrados.</p>
        </div>
        <Link to="/solicitudes" className="rounded bg-blue-700 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-800">
          Ver solicitudes
        </Link>
      </div>

      <section aria-label="Indicadores generales" className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <TarjetaIndicador titulo="Total candidatos" valor={valorCandidatos} descripcion="Personas registradas" color="azul" />
        <TarjetaIndicador titulo="Pendientes" valor={valorSolicitudes('Pendiente')} descripcion="Solicitudes por gestionar" color="amarillo" />
        <TarjetaIndicador titulo="En proceso" valor={valorSolicitudes('En proceso')} descripcion="Solicitudes en evaluación" color="celeste" />
        <TarjetaIndicador titulo="Finalizadas" valor={valorSolicitudes('Finalizada')} descripcion="Solicitudes terminadas" color="verde" />
      </section>

      {(errorCandidatos || errorSolicitudes) && !cargando && (
        <div className="mb-5 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800" role="alert">
          {errorCandidatos && <p>{errorCandidatos}</p>}
          {errorSolicitudes && <p>{errorSolicitudes}</p>}
          <p>Verifica que Spring Boot y MySQL estén funcionando.</p>
        </div>
      )}

      <section aria-label="Candidatos registrados" className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <h3 className="border-b px-5 py-4 text-lg font-semibold text-gray-800">Candidatos registrados</h3>
        {cargando ? (
          <p className="p-6 text-center text-gray-600" role="status">Cargando datos...</p>
        ) : errorCandidatos ? (
          <p className="p-6 text-center text-red-700">La lista de candidatos no está disponible.</p>
        ) : candidatos.length === 0 ? (
          <p className="p-6 text-center text-gray-600">Aún no hay candidatos registrados.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px] border-collapse text-left">
              <thead>
                <tr className="border-b bg-blue-50 text-blue-800">
                  <th className="p-4 font-semibold">ID</th>
                  <th className="p-4 font-semibold">Candidato</th>
                  <th className="p-4 font-semibold">Cargo y Familia</th>
                  <th className="p-4 font-semibold">Origen</th>
                  <th className="p-4 font-semibold">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {candidatos.map((candidato) => (
                  <tr key={candidato.id} className="border-b hover:bg-gray-50">
                    <td className="p-4 text-gray-500">#{candidato.id}</td>
                    <td className="p-4">
                      <p className="font-bold text-gray-800">{candidato.nombre || 'Sin nombre'}</p>
                      <p className="text-sm text-gray-500">{candidato.correo || 'Sin correo'}</p>
                      <p className="text-xs text-gray-400">{candidato.telefono || 'Sin teléfono'}</p>
                    </td>
                    <td className="p-4">
                      <p className="font-medium text-gray-800">{candidato.cargoPostula || 'Sin cargo'}</p>
                      <p className="text-sm text-gray-500">{candidato.familiaCargo || 'Sin familia'}</p>
                    </td>
                    <td className="p-4">
                      <span className={`rounded-full px-3 py-1 text-xs font-bold ${
                        candidato.origen === 'Interno' ? 'bg-indigo-100 text-indigo-700' : 'bg-emerald-100 text-emerald-700'
                      }`}>{candidato.origen || 'Sin origen'}</span>
                    </td>
                    <td className="p-4">
                      <div className="flex flex-wrap gap-2">
                        <Link
                          to={`/candidatos/${candidato.id}`}
                          className="inline-block rounded border border-blue-600 px-3 py-2 text-sm font-semibold text-blue-700 hover:bg-blue-50"
                        >
                          Ver / Editar
                        </Link>
                        <Link
                          to={`/nueva-solicitud/${candidato.id}`}
                          className="inline-block rounded bg-blue-600 px-3 py-2 text-sm font-semibold text-white hover:bg-blue-700"
                        >
                          Crear Solicitud
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

export default Dashboard;
