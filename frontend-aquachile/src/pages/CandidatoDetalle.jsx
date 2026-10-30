import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api from '../services/api';

const API_CANDIDATOS = '/candidatos';

const datosFormulario = (candidato) => ({
  nombre: candidato.nombre ?? '',
  correo: candidato.correo ?? '',
  telefono: candidato.telefono ?? '',
  cargoPostula: candidato.cargoPostula ?? '',
  familiaCargo: candidato.familiaCargo ?? '',
  origen: candidato.origen ?? 'Externo',
  ubicacion: candidato.ubicacion ?? '',
  unidad: candidato.unidad ?? '',
});

const campos = [
  { nombre: 'nombre', etiqueta: 'Nombre completo', requerido: true },
  { nombre: 'correo', etiqueta: 'Correo electrónico', tipo: 'email', requerido: true },
  { nombre: 'telefono', etiqueta: 'Teléfono', tipo: 'tel' },
  { nombre: 'cargoPostula', etiqueta: 'Cargo al que postula', requerido: true },
  { nombre: 'familiaCargo', etiqueta: 'Familia de cargo' },
  { nombre: 'ubicacion', etiqueta: 'Ubicación' },
  { nombre: 'unidad', etiqueta: 'Unidad' },
];

function CandidatoDetalle() {
  const { idCandidato } = useParams();
  const [candidato, setCandidato] = useState(null);
  const [formulario, setFormulario] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [editando, setEditando] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');

  useEffect(() => {
    let activo = true;

    async function cargarCandidato() {
      setCargando(true);
      setError('');
      setMensaje('');
      setEditando(false);
      setCandidato(null);
      setFormulario(null);

      try {
        const respuesta = await api.get(`${API_CANDIDATOS}/${idCandidato}`);
        if (!activo) return;
        setCandidato(respuesta.data);
        setFormulario(datosFormulario(respuesta.data));
      } catch (err) {
        if (!activo) return;
        setError(err.response?.status === 404
          ? 'No se encontró el candidato solicitado.'
          : 'No se pudo cargar el candidato. Revisa que Spring Boot y MySQL estén funcionando.');
      } finally {
        if (activo) setCargando(false);
      }
    }

    cargarCandidato();
    return () => { activo = false; };
  }, [idCandidato]);

  const actualizarCampo = (event) => {
    const { name, value } = event.target;
    setFormulario((anterior) => ({ ...anterior, [name]: value }));
  };

  const cancelarEdicion = () => {
    setFormulario(datosFormulario(candidato));
    setError('');
    setEditando(false);
  };

  const guardarCambios = async (event) => {
    event.preventDefault();
    if (guardando || !formulario) return;
    setError('');
    setMensaje('');

    const datos = Object.fromEntries(
      Object.entries(formulario).map(([clave, valor]) => [clave, typeof valor === 'string' ? valor.trim() : valor])
    );
    if (!datos.nombre || !datos.correo || !datos.cargoPostula) {
      setError('Completa el nombre, correo y cargo antes de guardar.');
      return;
    }

    setGuardando(true);
    try {
      const respuesta = await api.put(`${API_CANDIDATOS}/${idCandidato}`, datos);
      setCandidato(respuesta.data);
      setFormulario(datosFormulario(respuesta.data));
      setEditando(false);
      setMensaje('Los datos del candidato se actualizaron correctamente.');
    } catch (err) {
      if (err.response?.status === 404) {
        setError('Este candidato ya no existe. Regresa al listado.');
      } else if (err.response?.status === 400) {
        setError('Revisa los campos obligatorios y el formato del correo.');
      } else if (err.response?.status === 409 || err.response?.status === 500) {
        setError('No se pudieron guardar los cambios. Verifica que el correo no pertenezca a otro candidato.');
      } else {
        setError('No se pudieron guardar los cambios. Revisa la conexión con Spring Boot.');
      }
    } finally {
      setGuardando(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl p-4 md:p-6">
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold text-gray-800">Ficha del candidato #{idCandidato}</h2>
          <p className="mt-1 text-sm text-gray-600">Consulta y actualización de los antecedentes básicos.</p>
        </div>
        <Link to="/" className="rounded border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-700 hover:bg-gray-100">
          Volver al dashboard
        </Link>
      </div>

      {cargando ? (
        <p className="rounded-lg bg-white p-6 text-gray-600" role="status">Cargando candidato...</p>
      ) : !candidato ? (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-red-800" role="alert">
          {error || 'No se pudo mostrar la información.'}
        </div>
      ) : (
        <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm md:p-6">
          {mensaje && <p className="mb-4 rounded bg-green-50 p-3 text-sm font-medium text-green-800" role="status">{mensaje}</p>}
          {error && <p className="mb-4 rounded bg-red-50 p-3 text-sm font-medium text-red-800" role="alert">{error}</p>}

          {!editando ? (
            <>
              <div className="grid gap-4 sm:grid-cols-2">
                {campos.map(({ nombre, etiqueta }) => (
                  <div key={nombre}>
                    <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">{etiqueta}</p>
                    <p className="mt-1 break-words text-gray-800">{candidato[nombre] || 'No registrado'}</p>
                  </div>
                ))}
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">Origen</p>
                  <p className="mt-1 text-gray-800">{candidato.origen || 'No registrado'}</p>
                </div>
              </div>
              <div className="mt-6 flex flex-wrap gap-3 border-t pt-5">
                <button
                  type="button"
                  onClick={() => { setError(''); setMensaje(''); setEditando(true); }}
                  className="rounded bg-blue-700 px-4 py-2 font-semibold text-white hover:bg-blue-800"
                >
                  Editar candidato
                </button>
                <Link
                  to={`/nueva-solicitud/${candidato.id}`}
                  className="rounded bg-indigo-600 px-4 py-2 font-semibold text-white hover:bg-indigo-700"
                >
                  Crear solicitud
                </Link>
              </div>
            </>
          ) : (
            <form onSubmit={guardarCambios} className="grid gap-4 sm:grid-cols-2">
              {campos.map(({ nombre, etiqueta, requerido, tipo }) => (
                <div key={nombre}>
                  <label htmlFor={`campo-${nombre}`} className="mb-1 block text-sm font-semibold text-gray-700">
                    {etiqueta}{requerido ? ' *' : ''}
                  </label>
                  <input
                    id={`campo-${nombre}`}
                    name={nombre}
                    type={tipo || 'text'}
                    required={Boolean(requerido)}
                    value={formulario[nombre]}
                    onChange={actualizarCampo}
                    disabled={guardando}
                    className="w-full rounded border border-gray-300 p-2 focus:border-blue-500 focus:outline-none"
                  />
                </div>
              ))}
              <div>
                <label htmlFor="campo-origen" className="mb-1 block text-sm font-semibold text-gray-700">Origen</label>
                <select
                  id="campo-origen"
                  name="origen"
                  value={formulario.origen}
                  onChange={actualizarCampo}
                  disabled={guardando}
                  className="w-full rounded border border-gray-300 bg-white p-2"
                >
                  <option value="Externo">Externo</option>
                  <option value="Interno">Interno</option>
                </select>
              </div>
              <div className="flex flex-wrap items-end gap-3 sm:col-span-2">
                <button type="submit" disabled={guardando} className="rounded bg-green-700 px-4 py-2 font-semibold text-white hover:bg-green-800 disabled:opacity-60">
                  {guardando ? 'Guardando...' : 'Guardar cambios'}
                </button>
                <button type="button" onClick={cancelarEdicion} disabled={guardando} className="rounded border px-4 py-2 font-semibold text-gray-700 hover:bg-gray-100 disabled:opacity-60">
                  Cancelar
                </button>
              </div>
            </form>
          )}
        </section>
      )}
    </div>
  );
}

export default CandidatoDetalle;
