import { useRef, useState } from 'react';
import api from './services/api';

const LIMITE_BYTES = 5 * 1024 * 1024;
const EXTENSIONES = /\.(pdf|doc|docx)$/i;
const etiquetaTamano = (n) => (n < 1024 ? `${n} B` : `${(n / 1024 / 1024).toFixed(2)} MB`);

const estadoInicial = () => ({
  nombre: '',
  correo: '',
  telefono: '',
  cargoPostula: '',
  familiaCargo: '',
  ubicacion: '',
  unidad: '',
  origen: 'Externo',
});

const campos = [
  { name: 'nombre', label: 'Nombre completo', required: true, placeholder: 'Ej.: Camila Soto' },
  { name: 'correo', label: 'Correo electrónico', type: 'email', required: true, placeholder: 'ejemplo@correo.com' },
  { name: 'telefono', label: 'Teléfono de contacto', type: 'tel', placeholder: 'Ej.: +56 9 1234 5678' },
  { name: 'cargoPostula', label: 'Cargo al que postula', required: true, placeholder: 'Ej.: Analista de procesos' },
  { name: 'familiaCargo', label: 'Área o familia de cargo', placeholder: 'Ej.: Profesional A' },
  { name: 'ubicacion', label: 'Ciudad o ubicación', placeholder: 'Ej.: Puerto Montt' },
  { name: 'unidad', label: 'Unidad de interés', placeholder: 'Ej.: Industrial' },
];

function validarFormulario(datos) {
  const limpio = Object.fromEntries(
    Object.entries(datos).map(([k, v]) => [k, typeof v === 'string' ? v.trim() : v])
  );
  const errores = {};

  if (!limpio.nombre || limpio.nombre.length < 3) {
    errores.nombre = 'Ingresa tu nombre completo (mínimo 3 caracteres).';
  }
  if (!limpio.correo || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(limpio.correo)) {
    errores.correo = 'Ingresa un correo electrónico válido.';
  }
  if (!limpio.cargoPostula || limpio.cargoPostula.length < 2) {
    errores.cargoPostula = 'Indica el cargo al que postulas.';
  }
  if (limpio.telefono) {
    const digitos = limpio.telefono.replace(/\D/g, '');
    if (!/^\+?[\d\s()-]+$/.test(limpio.telefono) || digitos.length < 8 || digitos.length > 15) {
      errores.telefono = 'Ingresa un teléfono válido (8 a 15 dígitos) o déjalo vacío.';
    }
  }

  return { limpio, errores, valido: Object.keys(errores).length === 0 };
}

export default function App() {
  const [postulante, setPostulante] = useState(estadoInicial);
  const [archivo, setArchivo] = useState(null);
  const [errores, setErrores] = useState({});
  const [enviando, setEnviando] = useState(false);
  const [enviado, setEnviado] = useState(false);
  const [mensaje, setMensaje] = useState('');
  const inputArchivoRef = useRef(null);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setPostulante((prev) => ({ ...prev, [name]: value }));
    setErrores((prev) => ({ ...prev, [name]: undefined }));
    setMensaje('');
  };

  function elegirArchivo(evento) {
    const seleccionado = evento.target.files?.[0] || null;
    setArchivo(null);
    setMensaje('');
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

  async function handleSubmit(event) {
    event.preventDefault();
    if (enviando) return;
    setMensaje('');

    const { limpio, errores: erroresValidacion, valido } = validarFormulario(postulante);
    setErrores(erroresValidacion);
    if (!valido) return;

    setEnviando(true);
    try {
      const resCandidato = await api.post('/candidatos', limpio);
      const nuevoId = resCandidato.data?.id;

      let textoExito = '¡Postulación enviada correctamente! Tus antecedentes quedaron registrados para revisión.';

      if (archivo && nuevoId) {
        try {
          const formData = new FormData();
          formData.append('archivo', archivo);
          await api.post(`/documentos/candidatos/${nuevoId}`, formData);
          textoExito = '¡Postulación y currículum enviados correctamente! Nuestro equipo revisará tus antecedentes.';
        } catch {
          textoExito = 'Tus datos fueron registrados, pero hubo un problema al adjuntar el currículum.';
        }
      }

      setMensaje(textoExito);
      setPostulante(estadoInicial());
      quitarArchivo();
      setErrores({});
      setEnviado(true);
    } catch (error) {
      if (error.response?.status === 409 || error.response?.status === 500) {
        setMensaje('Ya existe una postulación registrada con ese correo electrónico.');
      } else {
        setMensaje('No se pudo enviar la postulación. Verifica tu conexión e inténtalo nuevamente.');
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="portal-wrapper">
      <header className="portal-topbar">
        <div className="portal-topbar-inner">
          <div className="brand">
            <span className="brand-badge">AC</span>
            <div>
              <strong>AquaChile</strong>
              <small>PORTAL DE POSTULACIÓN EXTERNA</small>
            </div>
          </div>
          <span className="portal-tag">Selección y Talento</span>
        </div>
      </header>

      <main className="portal-main">
        <div className="portal-hero">
          <p className="eyebrow">TRABAJA CON NOSOTROS</p>
          <h1>Postulación de candidatos</h1>
          <p>Completa tus antecedentes y adjunta tu currículum para participar en nuestros procesos de evaluación.</p>
        </div>

        <div className="helper-banner">
          <strong>Aviso académico:</strong> utiliza datos ficticios y currículums de prueba para demostración del sistema.
        </div>

        {mensaje && (
          <div role="status" className={`alert ${enviado ? 'alert-success' : 'alert-error'}`}>
            {mensaje}
          </div>
        )}

        {enviado ? (
          <section className="card success-card">
            <h2>¡Gracias por postular!</h2>
            <p>Tu información ya se encuentra disponible en el panel del equipo de reclutamiento de AquaChile.</p>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setEnviado(false);
                setMensaje('');
              }}
            >
              Enviar otra postulación
            </button>
          </section>
        ) : (
          <form onSubmit={handleSubmit} noValidate className="card">
            <div className="card-head">
              <h2>Información del postulante</h2>
              <p>Los campos marcados con * son obligatorios.</p>
            </div>

            <div className="card-body">
              <div className="form-grid">
                {campos.map(({ name, label, type, required, placeholder }) => (
                  <div className="field" key={name}>
                    <label htmlFor={name}>
                      {label}
                      {required ? ' *' : ''}
                    </label>
                    <input
                      id={name}
                      type={type || 'text'}
                      name={name}
                      value={postulante[name]}
                      placeholder={placeholder}
                      onChange={handleChange}
                      disabled={enviando}
                    />
                    {errores[name] && <span className="field-error">{errores[name]}</span>}
                  </div>
                ))}

                <div className="field field-wide">
                  <label htmlFor="cv-externo">Currículum vitae (PDF, DOC o DOCX)</label>
                  <div className="file-row">
                    <input
                      ref={inputArchivoRef}
                      id="cv-externo"
                      type="file"
                      accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                      onChange={elegirArchivo}
                      disabled={enviando}
                    />
                    {archivo && (
                      <div className="file-actions">
                        <button type="button" className="btn btn-soft" onClick={abrirArchivoLocal} disabled={enviando}>
                          Ver archivo
                        </button>
                        <button type="button" className="btn btn-secondary" onClick={quitarArchivo} disabled={enviando}>
                          Quitar
                        </button>
                      </div>
                    )}
                  </div>
                  <small className="field-help">
                    {archivo
                      ? `Seleccionado: ${archivo.name} (${etiquetaTamano(archivo.size)})`
                      : 'Tamaño máximo permitido: 5 MB.'}
                  </small>
                  {errores.archivo && <span className="field-error">{errores.archivo}</span>}
                </div>
              </div>
            </div>

            <div className="card-footer">
              <button type="submit" className="btn btn-primary" disabled={enviando}>
                {enviando ? 'Enviando postulación...' : 'Enviar postulación'}
              </button>
            </div>
          </form>
        )}
      </main>
    </div>
  );
}