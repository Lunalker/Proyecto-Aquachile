import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import Icono from '../components/Icono';

const LIMITE_BYTES = 5 * 1024 * 1024;
const EXTENSIONES = /\.(pdf|doc|docx)$/i;
const etiquetaTamano = n => n < 1024 ? `${n} B` : `${(n / 1024 / 1024).toFixed(2)} MB`;

export default function DocumentosCandidato() {
  const { idCandidato } = useParams();
  const { state } = useLocation();
  const { usuario } = useAuth();
  const analista = usuario?.rol === 'ANALISTA';
  const url = `/documentos/candidatos/${idCandidato}`;
  const [documento, setDocumento] = useState(null);
  const [archivo, setArchivo] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [procesando, setProcesando] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');

  useEffect(() => {
    let activo = true;
    setCargando(true);
    setDocumento(null);
    setError('');
    api.get(url).then(respuesta => {
      if (activo) setDocumento(respuesta.data);
    }).catch(err => {
      if (!activo) return;
      if (err.response?.status !== 404) {
        setError('No se pudo consultar el CV. Comprueba la conexión con Spring Boot.');
      }
    }).finally(() => { if (activo) setCargando(false); });
    return () => { activo = false; };
  }, [url]);

  function elegirArchivo(evento) {
    const seleccionado = evento.target.files?.[0] || null;
    setArchivo(null);
    setError('');
    setMensaje('');
    if (!seleccionado) return;
    if (!EXTENSIONES.test(seleccionado.name)) {
      setError('Solo se aceptan documentos PDF, DOC o DOCX.');
      evento.target.value = '';
      return;
    }
    if (seleccionado.size === 0 || seleccionado.size > LIMITE_BYTES) {
      setError('El archivo debe pesar entre 1 byte y 5 MB.');
      evento.target.value = '';
      return;
    }
    setArchivo(seleccionado);
  }

  async function subir(evento) {
    evento.preventDefault();
    if (!analista || !archivo || procesando) return;
    setError('');
    setMensaje('');
    setProcesando(true);
    try {
      const datos = new FormData();
      datos.append('archivo', archivo);
      // Axios gestiona multipart/form-data y el interceptor añade X-CSRF-TOKEN.
      const respuesta = await api.post(url, datos);
      setDocumento(respuesta.data);
      setArchivo(null);
      evento.target.reset();
      setMensaje('CV guardado correctamente en la base de datos.');
    } catch (err) {
      if (err.response?.status === 403) setError('Tu rol no tiene permisos para adjuntar documentos.');
      else if (err.response?.status === 413) setError('El CV supera el límite de 5 MB.');
      else if (err.response?.status === 400) setError('El servidor rechazó el documento. Comprueba su formato real y tamaño.');
      else setError('No se pudo subir el CV. Revisa que Spring Boot y MySQL estén funcionando.');
    } finally { setProcesando(false); }
  }

  async function descargar() {
    if (!documento || procesando) return;
    setError('');
    setProcesando(true);
    try {
      const respuesta = await api.get(`${url}/descargar`, { responseType: 'blob' });
      const archivoBlob = new Blob([respuesta.data], { type: documento.tipoContenido });
      const enlaceTemporal = URL.createObjectURL(archivoBlob);
      const enlace = document.createElement('a');
      enlace.href = enlaceTemporal;
      enlace.download = documento.nombreArchivo;
      document.body.appendChild(enlace);
      enlace.click();
      enlace.remove();
      window.setTimeout(() => URL.revokeObjectURL(enlaceTemporal), 3000);
    } catch {
      setError('No se pudo descargar el CV. Inténtalo nuevamente.');
    } finally { setProcesando(false); }
  }

  async function eliminar() {
    if (!analista || !documento || procesando) return;
    if (!window.confirm('¿Eliminar el CV adjunto a este candidato? Esta acción no se puede deshacer.')) return;
    setError('');
    setMensaje('');
    setProcesando(true);
    try {
      await api.delete(url);
      setDocumento(null);
      setArchivo(null);
      setMensaje('El CV se eliminó correctamente.');
    } catch {
      setError('No se pudo eliminar el CV. Revisa la conexión.');
    } finally { setProcesando(false); }
  }

  return <div className="page-container form-shell">
    <div className="page-heading">
      <div>
        <p className="eyebrow">DOCUMENTACIÓN DE POSTULANTES</p>
        <h1 className="page-title">Currículum del candidato #{idCandidato}</h1>
        <p className="page-description">{state?.nombre ? `${state.nombre} · ` : ''}Archivo asociado al registro del postulante.</p>
      </div>
      <Link className="btn btn-secondary" to={analista ? `/candidatos/${idCandidato}` : '/solicitudes'}>
        <Icono nombre="flecha" size={15} className="rotate-180"/> Volver
      </Link>
    </div>

    <div className="form-helper-box"><strong>Datos académicos:</strong> utiliza solamente CV ficticios o anonimizados. No subas currículums reales con información personal.</div>
    {error && <p className="alert-box alert-box-error" role="alert">{error}</p>}
    {mensaje && <p className="alert-box alert-box-success" role="status">{mensaje}</p>}

    <section className="panel doc-panel" aria-label="Archivo de currículum">
      <div className="form-panel-head">
        <h2>Documento adjunto</h2>
        <p>Formatos admitidos: PDF, DOC o DOCX. Tamaño máximo: 5 MB.</p>
      </div>
      {cargando ? <div className="form-panel-body" role="status">Consultando documentos...</div>
        : documento ? <div className="form-panel-body doc-current">
          <div className="doc-file-symbol"><Icono nombre="clipboard" size={29}/></div>
          <div className="doc-file-info">
            <strong>{documento.nombreArchivo}</strong>
            <small>{etiquetaTamano(documento.tamanoBytes)} · Subido el {new Date(documento.fechaCarga).toLocaleDateString('es-CL')}</small>
          </div>
          <button type="button" className="btn btn-primary" disabled={procesando} onClick={descargar}>Descargar CV</button>
          {analista && <button type="button" className="btn btn-secondary" disabled={procesando} onClick={eliminar}>Eliminar CV</button>}
        </div> : <div className="form-panel-body doc-empty">
          <strong>Este candidato todavía no tiene un CV adjunto.</strong>
          <p>{analista ? 'Puedes subir uno desde el formulario inferior.' : 'El Analista de Reclutamiento puede adjuntarlo desde la ficha del candidato.'}</p>
        </div>}
    </section>

    {analista && <section className="panel doc-panel" aria-label="Subir currículum">
      <div className="form-panel-head">
        <h2>{documento ? 'Reemplazar currículum' : 'Adjuntar currículum'}</h2>
        <p>Se almacena una versión por candidato. Al subir otro CV, se reemplaza el anterior.</p>
      </div>
      <form onSubmit={subir} className="form-panel-body doc-upload-form">
        <div className="field">
          <label htmlFor="archivo-cv">Seleccionar documento PDF o Word</label>
          <input id="archivo-cv" type="file" accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            onChange={elegirArchivo} disabled={procesando} />
          <span className="field-help">{archivo ? `${archivo.name} · ${etiquetaTamano(archivo.size)}` : 'Elige un archivo auténtico de máximo 5 MB.'}</span>
        </div>
        <button type="submit" className="btn btn-primary" disabled={!archivo || procesando}>
          <Icono nombre="check" size={16}/>{procesando ? 'Guardando CV...' : documento ? 'Reemplazar CV' : 'Subir CV'}
        </button>
      </form>
    </section>}
  </div>;
}
