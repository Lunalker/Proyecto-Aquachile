import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import { validarCandidato } from '../utils/validarCandidato';
import {
  CATALOGO_CARGOS,
  FAMILIAS_CARGO_BASE,
  UBICACIONES_BASE,
  UNIDADES_BASE,
  VALOR_OTRO,
  buscarInfoCargo,
  combinarOpcionesUnicas,
} from '../constants/catalogosCargos';
import Icono from '../components/Icono';
import AgendarCitaCalendar from '../components/AgendarCitaCalendar';

const LIMITE_BYTES = 5 * 1024 * 1024;
const EXTENSIONES = /\.(pdf|doc|docx)$/i;
const etiquetaTamano = (n) => (n < 1024 ? `${n} B` : `${(n / 1024 / 1024).toFixed(2)} MB`);

const vacio = () => ({
  nombre: '',
  correo: '',
  telefono: '',
  cargoPostula: '',
  familiaCargo: '',
  origen: 'Externo',
  ubicacion: '',
  unidad: '',
});

const modosIniciales = () => ({
  cargoPostula: false,
  familiaCargo: false,
  ubicacion: false,
  unidad: false,
});

const camposTexto = [
  { name: 'nombre', label: 'Nombre completo', required: true, placeholder: 'Ej.: Persona de prueba' },
  { name: 'correo', label: 'Correo electrónico', type: 'email', required: true, placeholder: 'ejemplo@correo.test' },
  { name: 'telefono', label: 'Teléfono', type: 'tel', placeholder: 'Número ficticio' },
];

export default function FormularioCandidato() {
  const [candidato, setCandidato] = useState(vacio);
  const [modoManual, setModoManual] = useState(modosIniciales);
  const [candidatosExistentes, setCandidatosExistentes] = useState([]);
  const [archivo, setArchivo] = useState(null);
  const [cvGuardado, setCvGuardado] = useState(null);
  const [abriendoCv, setAbriendoCv] = useState(false);
  const [mensaje, setMensaje] = useState('');
  const [guardando, setGuardando] = useState(false);
  const [errores, setErrores] = useState({});
  const inputArchivoRef = useRef(null);

  useEffect(() => {
    let activo = true;
    api
        .get('/candidatos')
        .then((res) => {
          if (activo && Array.isArray(res.data)) {
            setCandidatosExistentes(res.data);
          }
        })
        .catch(() => {});
    return () => {
      activo = false;
    };
  }, []);

  const opcionesCargos = useMemo(
      () =>
          combinarOpcionesUnicas(
              CATALOGO_CARGOS.map((c) => c.cargo),
              candidatosExistentes.map((c) => c.cargoPostula)
          ),
      [candidatosExistentes]
  );

  const opcionesFamilias = useMemo(
      () =>
          combinarOpcionesUnicas(
              FAMILIAS_CARGO_BASE,
              candidatosExistentes.map((c) => c.familiaCargo)
          ),
      [candidatosExistentes]
  );

  const opcionesUbicaciones = useMemo(
      () =>
          combinarOpcionesUnicas(
              UBICACIONES_BASE,
              candidatosExistentes.map((c) => c.ubicacion)
          ),
      [candidatosExistentes]
  );

  const opcionesUnidades = useMemo(
      () =>
          combinarOpcionesUnicas(
              UNIDADES_BASE,
              candidatosExistentes.map((c) => c.unidad)
          ),
      [candidatosExistentes]
  );

  const handleChange = (e) => {
    const { name, value } = e.target;
    setCandidato((prev) => ({ ...prev, [name]: value }));
    setErrores((prev) => ({ ...prev, [name]: undefined }));
    setMensaje('');
    setCvGuardado(null);
  };

  function handleSelectCatalogo(campo, valor) {
    setMensaje('');
    setCvGuardado(null);

    if (valor === VALOR_OTRO) {
      setModoManual((prev) => ({ ...prev, [campo]: true }));
      setCandidato((prev) => ({ ...prev, [campo]: '' }));
      setErrores((prev) => ({ ...prev, [campo]: undefined }));
      return;
    }

    if (campo === 'cargoPostula') {
      const info = buscarInfoCargo(valor);
      setCandidato((prev) => ({
        ...prev,
        cargoPostula: valor,
        familiaCargo: info ? info.familiaCargo : prev.familiaCargo,
        ubicacion: info ? info.ubicacion : prev.ubicacion,
        unidad: info ? info.unidad : prev.unidad,
      }));
      if (info) {
        setModoManual((prev) => ({
          ...prev,
          familiaCargo: false,
          ubicacion: false,
          unidad: false,
        }));
      }
      setErrores((prev) => ({
        ...prev,
        cargoPostula: undefined,
        familiaCargo: undefined,
        ubicacion: undefined,
        unidad: undefined,
      }));
      return;
    }

    setCandidato((prev) => ({ ...prev, [campo]: valor }));
    setErrores((prev) => ({ ...prev, [campo]: undefined }));
  }

  function volverASelect(campo) {
    setModoManual((prev) => ({ ...prev, [campo]: false }));
    setCandidato((prev) => ({ ...prev, [campo]: '' }));
    setErrores((prev) => ({ ...prev, [campo]: undefined }));
  }

  function elegirArchivo(evento) {
    const seleccionado = evento.target.files?.[0] || null;
    setArchivo(null);
    setMensaje('');
    setCvGuardado(null);
    setErrores((prev) => ({ ...prev, archivo: undefined }));

    if (!seleccionado) return;

    if (!EXTENSIONES.test(seleccionado.name)) {
      setErrores((prev) => ({ ...prev, archivo: 'Solo se aceptan documentos PDF, DOC o DOCX.' }));
      evento.target.value = '';
      return;
    }

    if (seleccionado.size === 0 || seleccionado.size > LIMITE_BYTES) {
      setErrores((prev) => ({ ...prev, archivo: 'El archivo debe pesar entre 1 byte y 5 MB.' }));
      evento.target.value = '';
      return;
    }

    setArchivo(seleccionado);
  }

  function quitarArchivo() {
    setArchivo(null);
    setErrores((prev) => ({ ...prev, archivo: undefined }));
    if (inputArchivoRef.current) {
      inputArchivoRef.current.value = '';
    }
  }

  function abrirArchivoLocal() {
    if (!archivo) return;
    const urlTemporal = URL.createObjectURL(archivo);
    if (archivo.name.toLowerCase().endsWith('.pdf')) {
      window.open(urlTemporal, '_blank', 'noopener,noreferrer');
    } else {
      const enlace = document.createElement('a');
      enlace.href = urlTemporal;
      enlace.download = archivo.name;
      document.body.appendChild(enlace);
      enlace.click();
      enlace.remove();
    }
    window.setTimeout(() => URL.revokeObjectURL(urlTemporal), 15000);
  }

  async function abrirCvGuardado() {
    if (!cvGuardado || abriendoCv) return;
    setAbriendoCv(true);
    try {
      const respuesta = await api.get(`/documentos/candidatos/${cvGuardado.id}/descargar`, {
        responseType: 'blob',
      });
      const tipo = cvGuardado.tipoContenido || 'application/pdf';
      const blob = new Blob([respuesta.data], { type: tipo });
      const urlTemporal = URL.createObjectURL(blob);

      if (tipo === 'application/pdf' || cvGuardado.nombreArchivo?.toLowerCase().endsWith('.pdf')) {
        window.open(urlTemporal, '_blank', 'noopener,noreferrer');
      } else {
        const enlace = document.createElement('a');
        enlace.href = urlTemporal;
        enlace.download = cvGuardado.nombreArchivo || 'curriculum';
        document.body.appendChild(enlace);
        enlace.click();
        enlace.remove();
      }
      window.setTimeout(() => URL.revokeObjectURL(urlTemporal), 15000);
    } catch {
      setMensaje('No se pudo abrir el CV guardado desde el servidor. Inténtalo desde la ficha del candidato.');
      setCvGuardado(null);
    } finally {
      setAbriendoCv(false);
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (guardando) return;
    setMensaje('');
    setCvGuardado(null);

    const { datos, errores: erroresActuales, valido } = validarCandidato(candidato);
    setErrores(erroresActuales);
    if (!valido) return;

    setGuardando(true);
    try {
      const respuesta = await api.post('/candidatos', datos);
      const nuevoId = respuesta.data?.id;

      if (respuesta.data) {
        setCandidatosExistentes((prev) => [...prev, respuesta.data]);
      }

      let mensajeFinal = '¡Candidato registrado correctamente! Puedes ingresar otro registro.';
      let docSubido = null;

      if (archivo && nuevoId) {
        try {
          const formData = new FormData();
          formData.append('archivo', archivo);
          const respDoc = await api.post(`/documentos/candidatos/${nuevoId}`, formData);
          docSubido = {
            id: nuevoId,
            nombreArchivo: respDoc.data?.nombreArchivo || archivo.name,
            tipoContenido: respDoc.data?.tipoContenido || archivo.type,
          };
          mensajeFinal = '¡Candidato registrado correctamente junto con su CV! Puedes verificar el archivo o ingresar otro registro.';
        } catch (errDoc) {
          const detalle =
              errDoc.response?.status === 400
                  ? 'el archivo no tiene un formato PDF o Word válido.'
                  : 'ocurrió un problema al subir el archivo.';
          mensajeFinal = `Candidato registrado (#${nuevoId}), pero no se pudo adjuntar el CV (${detalle}). Puedes subirlo desde su ficha.`;
        }
      }

      setMensaje(mensajeFinal);
      setCvGuardado(docSubido);
      setCandidato(vacio());
      setModoManual(modosIniciales());
      quitarArchivo();
      setErrores({});
    } catch (error) {
      setMensaje(
          error.response?.status === 403
              ? 'Tu usuario no tiene permiso para registrar candidatos.'
              : 'No se pudo guardar el candidato. Revisa los datos y la conexión.'
      );
    } finally {
      setGuardando(false);
    }
  }

  function renderCampoCatalogo({ name, label, required, opciones, placeholderSelect, placeholderManual }) {
    const esManual = modoManual[name];
    return (
        <div className="field" key={name}>
          <label htmlFor={name}>
            {label}
            {required ? ' *' : ''}
          </label>

          {!esManual ? (
              <select
                  id={name}
                  name={name}
                  value={candidato[name]}
                  onChange={(e) => handleSelectCatalogo(name, e.target.value)}
                  required={Boolean(required)}
                  aria-invalid={Boolean(errores[name])}
                  aria-describedby={errores[name] ? `error-${name}` : undefined}
                  disabled={guardando}
              >
                <option value="">{placeholderSelect}</option>
                {opciones.map((op) => (
                    <option key={op} value={op}>
                      {op}
                    </option>
                ))}
                <option value={VALOR_OTRO}>+ Otro (escribir manualmente)...</option>
              </select>
          ) : (
              <div className="flex items-center gap-2">
                <input
                    id={name}
                    type="text"
                    name={name}
                    value={candidato[name]}
                    placeholder={placeholderManual}
                    onChange={handleChange}
                    required={Boolean(required)}
                    aria-invalid={Boolean(errores[name])}
                    aria-describedby={errores[name] ? `error-${name}` : undefined}
                    disabled={guardando}
                    className="flex-1"
                />
                <button
                    type="button"
                    className="btn btn-secondary btn-small"
                    onClick={() => volverASelect(name)}
                    disabled={guardando}
                >
                  Ver lista
                </button>
              </div>
          )}

          {errores[name] && (
              <p id={`error-${name}`} className="field-error mt-1 text-sm text-red-700">
                {errores[name]}
              </p>
          )}
        </div>
    );
  }

  return (
      <div className="page-container form-shell">
        <div className="page-heading">
          <div>
            <p className="eyebrow">GESTIÓN DE CANDIDATOS</p>
            <h1 className="page-title">Registrar candidato</h1>
            <p className="page-description">Completa la información principal para iniciar un proceso de evaluación.</p>
          </div>
          <Link to="/" className="btn btn-secondary">
            <Icono nombre="flecha" size={15} className="rotate-180" />
            Volver al panel
          </Link>
        </div>

        {mensaje && (
            <div
                role="status"
                className={`alert-box ${mensaje.includes('correctamente') ? 'alert-box-success' : 'alert-box-error'}`}
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <span>{mensaje}</span>
                {cvGuardado && (
                    <div className="flex flex-wrap items-center gap-2">
                      <button
                          type="button"
                          className="btn btn-primary btn-small"
                          onClick={abrirCvGuardado}
                          disabled={abriendoCv}
                      >
                        <Icono nombre="documento" size={15} />
                        {abriendoCv ? 'Abriendo CV...' : `Abrir CV (${cvGuardado.nombreArchivo})`}
                      </button>
                      <Link to={`/candidatos/${cvGuardado.id}`} className="btn btn-secondary btn-small">
                        Ver ficha
                      </Link>
                    </div>
                )}
              </div>
            </div>
        )}

        <div className="form-helper-box">
          <strong>Registro de demostración:</strong> utiliza únicamente datos inventados y CV ficticios o anonimizados. Al elegir un cargo del catálogo se sugiere automáticamente su familia, ubicación y unidad.
        </div>

        {Object.values(errores).some(Boolean) && (
            <p role="alert" className="alert-box alert-box-error">
              Revisa los campos marcados antes de guardar.
            </p>
        )}

        <form onSubmit={handleSubmit} noValidate className="panel form-panel">
          <div className="form-panel-head">
            <h2>Información del postulante</h2>
            <p>Los campos marcados con * son obligatorios.</p>
          </div>

          <div className="form-panel-body">
            <div className="form-grid">
              {camposTexto.map(({ name, label, type, required, placeholder }) => (
                  <div className="field" key={name}>
                    <label htmlFor={name}>
                      {label}
                      {required ? ' *' : ''}
                    </label>
                    <input
                        id={name}
                        type={type || 'text'}
                        name={name}
                        value={candidato[name]}
                        placeholder={placeholder || ''}
                        onChange={handleChange}
                        required={Boolean(required)}
                        aria-invalid={Boolean(errores[name])}
                        aria-describedby={errores[name] ? `error-${name}` : undefined}
                        disabled={guardando}
                    />
                    {errores[name] && (
                        <p id={`error-${name}`} className="field-error mt-1 text-sm text-red-700">
                          {errores[name]}
                        </p>
                    )}
                  </div>
              ))}

              {renderCampoCatalogo({
                name: 'cargoPostula',
                label: 'Cargo al que postula',
                required: true,
                opciones: opcionesCargos,
                placeholderSelect: 'Selecciona un cargo...',
                placeholderManual: 'Ej.: Analista de procesos',
              })}

              {renderCampoCatalogo({
                name: 'familiaCargo',
                label: 'Familia de cargo',
                required: false,
                opciones: opcionesFamilias,
                placeholderSelect: 'Selecciona familia de cargo...',
                placeholderManual: 'Ej.: Profesional A',
              })}

              {renderCampoCatalogo({
                name: 'ubicacion',
                label: 'Ubicación',
                required: false,
                opciones: opcionesUbicaciones,
                placeholderSelect: 'Selecciona ubicación...',
                placeholderManual: 'Ej.: Oficina Central',
              })}

              {renderCampoCatalogo({
                name: 'unidad',
                label: 'Unidad',
                required: false,
                opciones: opcionesUnidades,
                placeholderSelect: 'Selecciona unidad...',
                placeholderManual: 'Ej.: Industrial',
              })}

              <div className="field">
                <label htmlFor="origen">Origen del candidato</label>
                <select
                    id="origen"
                    name="origen"
                    value={candidato.origen}
                    onChange={handleChange}
                    aria-invalid={Boolean(errores.origen)}
                    aria-describedby={errores.origen ? 'error-origen' : undefined}
                    disabled={guardando}
                >
                  <option value="Externo">Externo</option>
                  <option value="Interno">Interno</option>
                </select>
                {errores.origen && (
                    <p id="error-origen" className="field-error mt-1 text-sm text-red-700">
                      {errores.origen}
                    </p>
                )}
              </div>

              <div className="field form-wide">
                <label htmlFor="cv-candidato">Currículum vitae (opcional)</label>
                <div className="flex flex-wrap items-center gap-3">
                  <input
                      ref={inputArchivoRef}
                      id="cv-candidato"
                      type="file"
                      accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                      onChange={elegirArchivo}
                      aria-invalid={Boolean(errores.archivo)}
                      aria-describedby={errores.archivo ? 'error-archivo' : undefined}
                      disabled={guardando}
                      className="flex-1"
                  />
                  {archivo && (
                      <>
                        <button
                            type="button"
                            className="btn btn-soft btn-small"
                            onClick={abrirArchivoLocal}
                            disabled={guardando}
                        >
                          <Icono nombre="documento" size={15} />
                          Ver archivo
                        </button>
                        <button
                            type="button"
                            className="btn btn-secondary btn-small"
                            onClick={quitarArchivo}
                            disabled={guardando}
                        >
                          Quitar archivo
                        </button>
                      </>
                  )}
                </div>
                <span className="field-help">
                {archivo
                    ? `Archivo seleccionado: ${archivo.name} (${etiquetaTamano(archivo.size)})`
                    : 'Formatos admitidos: PDF, DOC o DOCX (máximo 5 MB).'}
              </span>
                {errores.archivo && (
                    <p id="error-archivo" className="field-error mt-1 text-sm text-red-700">
                      {errores.archivo}
                    </p>
                )}
              </div>

              <div className="field form-wide">
                <AgendarCitaCalendar
                  nombreCandidato={candidato.nombre || 'Postulante'}
                  correoCandidato={candidato.correo}
                  cargo={candidato.cargoPostula}
                  ubicacion={candidato.ubicacion}
                />
              </div>
            </div>
          </div>

          <div className="form-actions">
            <Link to="/" className="btn btn-secondary">
              Cancelar
            </Link>
            <button type="submit" className="btn btn-primary" disabled={guardando}>
              <Icono nombre="check" size={17} />
              {guardando ? 'Guardando...' : 'Guardar candidato'}
            </button>
          </div>
        </form>
      </div>
  );
}