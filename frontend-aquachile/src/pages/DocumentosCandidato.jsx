import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api, {
  analizarCvCandidato,
  analizarCvPostulacion,
  crearRequisitoCargo,
  descargarCvPostulacion,
  eliminarRequisitoCargo,
  guardarValidacionAnalisis,
  guardarValidacionPostulacion,
  listarPostulacionesCandidato,
  listarRequisitosCargo,
  obtenerAnalisisCv,
  obtenerAnalisisPostulacion,
  obtenerCvPostulacion,
} from '../services/api';
import Icono from '../components/Icono';

const LIMITE_BYTES = 5 * 1024 * 1024;
const EXTENSIONES = /\.(pdf|doc|docx)$/i;
const etiquetaTamano = n => n < 1024 ? `${n} B` : `${(n / 1024 / 1024).toFixed(2)} MB`;

const ETIQUETA_REVISION = {
  PENDIENTE_REVISION: 'Pendiente de revisión humana',
  EN_REVISION: 'En revisión por analista',
  VALIDADO: 'Validado por analista',
};

const ETIQUETA_ITEM = {
  PENDIENTE: 'Extraído por IA (Pendiente)',
  CONFIRMADO: 'Confirmado por analista',
  CORREGIDO: 'Corregido por analista',
  REQUIERE_VERIFICACION: 'Pendiente de verificar',
};

const ETIQUETA_CATEGORIA = {
  EXPERIENCIA: 'Experiencia laboral',
  FORMACION: 'Formación académica',
  CERTIFICACION: 'Certificación / Normativa',
  COMPETENCIA_TECNICA: 'Competencia técnica',
};

const TEMA_SEMAFORO = {
  ENCONTRADO: {
    etiqueta: 'Cumple · Evidencia verificada',
    icono: '✓',
    fondo: '#ecfdf5',
    borde: '#a7f3d0',
    acento: '#059669',
    texto: '#065f46',
    badgeBg: '#d1fae5',
  },
  REQUIERE_VERIFICACION: {
    etiqueta: 'Parcial · Requiere verificación',
    icono: '⚠',
    fondo: '#fffbeb',
    borde: '#fde68a',
    acento: '#d97706',
    texto: '#92400e',
    badgeBg: '#fef3c7',
  },
  NO_IDENTIFICADO_EN_CV: {
    etiqueta: 'No identificado en CV',
    icono: '✕',
    fondo: '#fef2f2',
    borde: '#fecaca',
    acento: '#dc2626',
    texto: '#991b1b',
    badgeBg: '#fee2e2',
  },
};

function formatearMeses(meses) {
  if (!meses || meses <= 0) return null;
  if (meses % 12 === 0) {
    const anos = meses / 12;
    return `Mín. ${anos} ${anos === 1 ? 'año' : 'años'}`;
  }
  return `Mín. ${meses} meses`;
}

function separarObservacionYPregunta(texto) {
  if (!texto) return { detalle: '', pregunta: '' };
  const idx = texto.indexOf('Pregunta sugerida:');
  if (idx === -1) return { detalle: texto, pregunta: '' };
  return {
    detalle: texto.slice(0, idx).trim(),
    pregunta: texto.slice(idx + 'Pregunta sugerida:'.length).trim(),
  };
}

export default function DocumentosCandidato() {
  const { idCandidato } = useParams();
  const { state } = useLocation();
  const { usuario } = useAuth();
  const analista = usuario?.rol === 'ANALISTA';
  const urlLegacy = `/documentos/candidatos/${idCandidato}`;

  const [postulaciones, setPostulaciones] = useState([]);
  const [postulacionActivaId, setPostulacionActivaId] = useState(state?.postulacionId || null);
  const [documento, setDocumento] = useState(null);
  const [candidatoInfo, setCandidatoInfo] = useState(null);
  const [archivo, setArchivo] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [procesando, setProcesando] = useState(false);
  const [analizando, setAnalizando] = useState(false);
  const [guardandoValidacion, setGuardandoValidacion] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');

  const [analisisMeta, setAnalisisMeta] = useState(null);
  const [datosEditables, setDatosEditables] = useState(null);
  const [observacionesAnalista, setObservacionesAnalista] = useState('');

  const [requisitosCargo, setRequisitosCargo] = useState([]);
  const [nuevoReqCategoria, setNuevoReqCategoria] = useState('EXPERIENCIA');
  const [nuevoReqNombre, setNuevoReqNombre] = useState('');
  const [nuevoReqDesc, setNuevoReqDesc] = useState('');
  const [nuevoReqMeses, setNuevoReqMeses] = useState('');
  const [nuevoReqObligatorio, setNuevoReqObligatorio] = useState(true);
  const [guardandoReq, setGuardandoReq] = useState(false);

  const postulacionActual = postulaciones.find(p => p.id === postulacionActivaId) || null;

  useEffect(() => {
    let activo = true;
    setCargando(true);
    setDocumento(null);
    setAnalisisMeta(null);
    setDatosEditables(null);
    setError('');

    async function cargarTodo() {
      try {
        const [listaPostulaciones, candRes] = await Promise.all([
          listarPostulacionesCandidato(idCandidato).catch(() => []),
          analista
              ? api.get(`/candidatos/${idCandidato}`).catch(() => ({ data: null }))
              : Promise.resolve({ data: null }),
        ]);

        if (!activo) return;
        const posts = Array.isArray(listaPostulaciones) ? listaPostulaciones : [];
        setPostulaciones(posts);
        setCandidatoInfo(candRes.data);

        const elegida = posts.find(p => p.id === postulacionActivaId) || posts[0] || null;
        if (elegida) {
          if (elegida.id !== postulacionActivaId) {
            setPostulacionActivaId(elegida.id);
          }
          const [cvMeta, analisisRes] = await Promise.all([
            obtenerCvPostulacion(elegida.id).catch(() => null),
            obtenerAnalisisPostulacion(elegida.id).catch(() => null),
          ]);
          if (!activo) return;

          if (cvMeta) {
            setDocumento({
              id: cvMeta.id,
              postulacionId: cvMeta.postulacionId,
              nombreArchivo: cvMeta.nombreOriginal,
              tipoContenido: cvMeta.tipoMime,
              tamanoBytes: cvMeta.tamanoBytes,
              fechaCarga: cvMeta.fechaCarga,
              hashSha256: cvMeta.hashSha256,
              contenedorUuid: cvMeta.contenedorUuid,
              nombreContenedor: cvMeta.nombreContenedor,
              analizableIa: cvMeta.analizableIa,
              motivoNoAnalizable: cvMeta.motivoNoAnalizable,
            });
          }
          if (analisisRes) {
            aplicarRespuestaAnalisis(analisisRes);
          }
          const cargo = elegida.ofertaCargo || candRes.data?.cargoPostula;
          if (cargo) {
            const reqs = await listarRequisitosCargo(cargo).catch(() => []);
            if (activo) setRequisitosCargo(reqs);
          }
        } else {
          // Respaldo para registros heredados sin postulación asociada
          const [docRes, analisisRes] = await Promise.all([
            api.get(urlLegacy).catch(err => (err.response?.status === 404 ? { data: null } : Promise.reject(err))),
            obtenerAnalisisCv(idCandidato).catch(() => null),
          ]);
          if (!activo) return;
          setDocumento(docRes.data);
          if (analisisRes) {
            aplicarRespuestaAnalisis(analisisRes);
          }
          const cargo = candRes.data?.cargoPostula || analisisRes?.cargoPostula;
          if (cargo) {
            const reqs = await listarRequisitosCargo(cargo).catch(() => []);
            if (activo) setRequisitosCargo(reqs);
          }
        }
      } catch {
        if (activo) setError('No se pudo consultar la documentación. Comprueba la conexión con Spring Boot.');
      } finally {
        if (activo) setCargando(false);
      }
    }

    cargarTodo();
    return () => { activo = false; };
  }, [idCandidato, postulacionActivaId, urlLegacy, analista]);

  function aplicarRespuestaAnalisis(resp) {
    setAnalisisMeta(resp);
    setDatosEditables(resp?.datosActuales ? JSON.parse(JSON.stringify(resp.datosActuales)) : null);
    setObservacionesAnalista(resp?.observacionesAnalista || '');
  }

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
      const respuesta = await api.post(urlLegacy, datos);
      setDocumento(respuesta.data);
      setAnalisisMeta(null);
      setDatosEditables(null);
      setObservacionesAnalista('');
      setArchivo(null);
      evento.target.reset();
      setMensaje('CV guardado correctamente. Cualquier análisis anterior fue invalidado para esta nueva versión.');
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
      const respuesta = postulacionActual
          ? await descargarCvPostulacion(postulacionActual.id)
          : await api.get(`${urlLegacy}/descargar`, { responseType: 'blob' });

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
    if (!analista || !documento || postulacionActual || procesando) return;
    if (!window.confirm('¿Eliminar el CV adjunto a este candidato? Esta acción invalidará el análisis asociado.')) return;
    setError('');
    setMensaje('');
    setProcesando(true);
    try {
      await api.delete(urlLegacy);
      setDocumento(null);
      setAnalisisMeta(null);
      setDatosEditables(null);
      setArchivo(null);
      setMensaje('El CV se eliminó y su análisis quedó invalidado.');
    } catch {
      setError('No se pudo eliminar el CV. Revisa la conexión.');
    } finally { setProcesando(false); }
  }

  async function ejecutarAnalisisIa(forzar = false) {
    if (!analista || !documento || analizando || procesando) return;
    if (documento.analizableIa === false) {
      setError(documento.motivoNoAnalizable || 'Este formato no admite análisis automático con IA.');
      return;
    }
    setError('');
    setMensaje('');
    setAnalizando(true);
    try {
      const resp = postulacionActual
          ? await analizarCvPostulacion(postulacionActual.id, { forzar })
          : await analizarCvCandidato(idCandidato, { forzar });
      aplicarRespuestaAnalisis(resp);
      setMensaje(forzar
          ? 'CV reanalizado con IA. Revisa el semáforo de criterios antes de validar.'
          : 'Análisis documental generado. Revisa las evidencias y colores antes de validar.');
    } catch (err) {
      const msg = err.response?.data?.message || 'No se pudo completar el análisis documental con IA.';
      setError(msg);
    } finally {
      setAnalizando(false);
    }
  }

  function actualizarCampoGeneral(campo, valor) {
    setDatosEditables(prev => prev ? { ...prev, [campo]: valor } : prev);
  }

  function actualizarItemLista(listaKey, index, campo, valor, marcarCorregido = false) {
    setDatosEditables(prev => {
      if (!prev || !Array.isArray(prev[listaKey])) return prev;
      const copia = prev[listaKey].map((item, idx) => {
        if (idx !== index) return item;
        return {
          ...item,
          [campo]: valor,
          ...(marcarCorregido ? { estadoRevisionItem: 'CORREGIDO' } : {}),
        };
      });
      return { ...prev, [listaKey]: copia };
    });
  }

  async function guardarRevision(confirmarValidacion) {
    if (!analista || !datosEditables || guardandoValidacion) return;
    setError('');
    setMensaje('');
    setGuardandoValidacion(true);
    try {
      const payload = {
        datosRevisados: datosEditables,
        observacionesAnalista,
        confirmarValidacion,
      };
      const resp = postulacionActual
          ? await guardarValidacionPostulacion(postulacionActual.id, payload)
          : await guardarValidacionAnalisis(idCandidato, payload);
      aplicarRespuestaAnalisis(resp);
      setMensaje(confirmarValidacion
          ? 'Antecedentes validados correctamente. Ya están disponibles para el evaluador.'
          : 'Borrador de revisión humana guardado.');
    } catch (err) {
      setError(err.response?.data?.message || 'No se pudo guardar la revisión del análisis.');
    } finally {
      setGuardandoValidacion(false);
    }
  }

  const cargoActual = postulacionActual?.ofertaCargo || candidatoInfo?.cargoPostula || analisisMeta?.cargoPostula || '';

  async function agregarRequisito(e) {
    e.preventDefault();
    if (!analista || !cargoActual || !nuevoReqNombre.trim() || guardandoReq) return;
    setGuardandoReq(true);
    setError('');
    try {
      const creado = await crearRequisitoCargo({
        cargo: cargoActual,
        familiaCargo: postulacionActual?.ofertaCategoria || candidatoInfo?.familiaCargo || analisisMeta?.familiaCargo || null,
        categoria: nuevoReqCategoria,
        nombreRequisito: nuevoReqNombre.trim(),
        descripcion: nuevoReqDesc.trim() || null,
        experienciaMinimaMeses: nuevoReqMeses ? Number(nuevoReqMeses) : null,
        obligatorio: nuevoReqObligatorio,
      });
      setRequisitosCargo(prev => [...prev, creado]);
      setNuevoReqNombre('');
      setNuevoReqDesc('');
      setNuevoReqMeses('');
      setMensaje('Criterio agregado al cargo. Pulsa "Reanalizar CV" para contrastarlo.');
    } catch (err) {
      setError(err.response?.data?.message || 'No se pudo registrar el requisito del cargo.');
    } finally {
      setGuardandoReq(false);
    }
  }

  async function cargarPlantillaCargo() {
    if (!analista || !cargoActual || guardandoReq) return;
    setGuardandoReq(true);
    setError('');
    try {
      const { data } = await api.post('/requisitos-cargo/plantilla', {
        cargo: cargoActual,
        familiaCargo: postulacionActual?.ofertaCategoria || candidatoInfo?.familiaCargo || analisisMeta?.familiaCargo || null,
      });
      setRequisitosCargo(Array.isArray(data) ? data : []);
      setMensaje(`Criterios base cargados para "${cargoActual}". Pulsa "Analizar CV con IA" o "Reanalizar CV".`);
    } catch (err) {
      setError(err.response?.data?.message || 'No se pudo cargar la plantilla base del cargo.');
    } finally {
      setGuardandoReq(false);
    }
  }

  async function quitarRequisito(idReq) {
    if (!analista) return;
    try {
      await eliminarRequisitoCargo(idReq);
      setRequisitosCargo(prev => prev.filter(r => r.id !== idReq));
    } catch {
      setError('No se pudo eliminar el requisito del cargo.');
    }
  }

  const esPdfAnalizable = documento && documento.analizableIa !== false;

  const matriz = datosEditables?.matrizRequisitos || [];
  const totalCriterios = matriz.length;
  const totalEncontrados = matriz.filter(r => r.resultadoAnalisis === 'ENCONTRADO').length;
  const totalVerificar = matriz.filter(r => r.resultadoAnalisis === 'REQUIERE_VERIFICACION').length;
  const totalNoIdentificados = matriz.filter(r => r.resultadoAnalisis === 'NO_IDENTIFICADO_EN_CV').length;
  const obligatorios = matriz.filter(r => r.obligatorio);
  const obligatoriosCumplidos = obligatorios.filter(r => r.resultadoAnalisis === 'ENCONTRADO').length;
  const todosObligatoriosOk = obligatorios.length > 0 && obligatoriosCumplidos === obligatorios.length;

  return <div className="page-container form-shell">
    <div className="page-heading">
      <div>
        <p className="eyebrow">DOCUMENTACIÓN Y ANÁLISIS ASISTIDO POR IA</p>
        <h1 className="page-title">
          {postulacionActual
              ? `Currículum de postulación #${postulacionActual.id}`
              : `Currículum del candidato #${idCandidato}`}
        </h1>
        <p className="page-description">
          {state?.nombre || candidatoInfo?.nombre || postulacionActual?.candidatoNombre
              ? `${state?.nombre || candidatoInfo?.nombre || postulacionActual?.candidatoNombre} · `
              : ''}
          {cargoActual ? `Cargo: ${cargoActual}` : 'Archivo asociado al registro del postulante.'}
        </p>
      </div>
      <Link className="btn btn-secondary" to={analista ? `/candidatos/${idCandidato}` : '/solicitudes'}>
        <Icono nombre="flecha" size={15} className="rotate-180"/> Volver
      </Link>
    </div>

    {/* Selector de postulación cuando el candidato postuló a una o más ofertas */}
    {postulaciones.length > 0 && (
        <section className="panel doc-panel" aria-label="Postulaciones asociadas">
          <div className="form-panel-head">
            <h2>Postulaciones registradas ({postulaciones.length})</h2>
            <p>Cada postulación mantiene un contenedor único e independiente para su CV y análisis documental.</p>
          </div>
          <div className="form-panel-body" style={{ display: 'flex', flexWrap: 'wrap', gap: '0.6rem' }}>
            {postulaciones.map(p => {
              const seleccionada = p.id === postulacionActivaId;
              return (
                  <button
                      key={p.id}
                      type="button"
                      className={seleccionada ? 'btn btn-primary' : 'btn btn-secondary'}
                      onClick={() => setPostulacionActivaId(p.id)}
                  >
                    Postulación #{p.id} · {p.ofertaCargo}
                  </button>
              );
            })}
          </div>
        </section>
    )}

    <div className="form-helper-box">
      <strong>Herramienta de apoyo profesional:</strong> la IA extrae antecedentes y citas verificables desde PDFs anonimizados. La validación y decisión final corresponden exclusivamente al equipo humano de AquaChile.
    </div>
    {error && <p className="alert-box alert-box-error" role="alert">{error}</p>}
    {mensaje && <p className="alert-box alert-box-success" role="status">{mensaje}</p>}

    {/* 1. Documento adjunto */}
    <section className="panel doc-panel" aria-label="Archivo de currículum">
      <div className="form-panel-head">
        <h2>Documento adjunto</h2>
        <p>Formatos admitidos: PDF (con análisis IA), DOC o DOCX (solo revisión manual). Máximo 5 MB.</p>
      </div>
      {cargando ? <div className="form-panel-body" role="status">Consultando documentos...</div>
          : documento ? <div className="form-panel-body">
            <div className="doc-current">
              <div className="doc-file-symbol"><Icono nombre="clipboard" size={29}/></div>
              <div className="doc-file-info">
                <strong>{documento.nombreArchivo}</strong>
                <small>
                  {etiquetaTamano(documento.tamanoBytes)} · Subido el {new Date(documento.fechaCarga).toLocaleDateString('es-CL')}
                  {documento.contenedorUuid ? ` · Contenedor: ${documento.contenedorUuid}` : ''}
                </small>
              </div>
              {analista && (
                  <>
                    <button
                        type="button"
                        className="btn btn-primary"
                        disabled={procesando || analizando || !esPdfAnalizable}
                        onClick={() => ejecutarAnalisisIa(false)}
                    >
                      {analizando ? 'Analizando CV...' : analisisMeta ? 'Ver análisis guardado' : 'Analizar CV con IA'}
                    </button>
                    {analisisMeta && esPdfAnalizable && (
                        <button
                            type="button"
                            className="btn btn-secondary"
                            disabled={procesando || analizando}
                            onClick={() => ejecutarAnalisisIa(true)}
                        >
                          Reanalizar CV
                        </button>
                    )}
                  </>
              )}
              <button type="button" className="btn btn-secondary" disabled={procesando} onClick={descargar}>Descargar CV</button>
              {analista && !postulacionActual && (
                  <button type="button" className="btn btn-secondary" disabled={procesando} onClick={eliminar}>Eliminar CV</button>
              )}
            </div>
            {!esPdfAnalizable && (
                <p className="alert-box alert-box-error" style={{ marginTop: '0.75rem' }}>
                  {documento.motivoNoAnalizable || 'El análisis automático con IA está deshabilitado para archivos Word (DOC/DOCX). Sube una versión en PDF si deseas analizarlo.'}
                </p>
            )}
          </div> : <div className="form-panel-body doc-empty">
            <strong>Este candidato todavía no tiene un CV adjunto.</strong>
            <p>{analista ? 'Puedes subir uno desde el formulario inferior.' : 'El Analista de Reclutamiento puede adjuntarlo desde la ficha del candidato.'}</p>
          </div>}
    </section>

    {/* 2. Configuración profesional de requisitos del cargo (solo ANALISTA) */}
    {analista && cargoActual && (
        <section className="panel doc-panel" aria-label="Requisitos profesionales del cargo">
          <div className="form-panel-head" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem' }}>
            <div>
              <h2>Matriz de criterios del cargo: {cargoActual}</h2>
              <p>Define categoría, criticidad y experiencia mínima. La IA evaluará exclusivamente esta matriz.</p>
            </div>
            <button
                type="button"
                className="btn btn-secondary"
                disabled={guardandoReq}
                onClick={cargarPlantillaCargo}
            >
              <Icono nombre="mas" size={15} /> Cargar plantilla base del cargo
            </button>
          </div>
          <div className="form-panel-body" style={{ display: 'flex', flexDirection: 'column', gap: '0.9rem' }}>
            {requisitosCargo.length === 0 ? (
                <div style={{ padding: '0.85rem', borderRadius: '8px', background: '#f8fafc', border: '1px dashed #cbd5e1' }}>
                  <small>No hay criterios configurados todavía para <strong>{cargoActual}</strong>. Puedes cargar la plantilla base con un clic o agregar criterios personalizados abajo.</small>
                </div>
            ) : (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '0.6rem' }}>
                  {requisitosCargo.map(req => {
                    const tagMeses = formatearMeses(req.experienciaMinimaMeses);
                    return (
                        <div
                            key={req.id}
                            style={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              alignItems: 'flex-start',
                              gap: '0.6rem',
                              padding: '0.65rem 0.8rem',
                              border: '1px solid #cbd5e1',
                              borderLeft: `4px solid ${req.obligatorio ? '#0f766e' : '#64748b'}`,
                              borderRadius: '8px',
                              background: '#ffffff',
                            }}
                        >
                          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem', alignItems: 'center' }}>
                        <span style={{ fontSize: '0.72rem', fontWeight: 700, padding: '0.15rem 0.45rem', borderRadius: '999px', background: '#e0f2fe', color: '#0369a1' }}>
                          {ETIQUETA_CATEGORIA[req.categoria] || 'Experiencia laboral'}
                        </span>
                              <span style={{
                                fontSize: '0.72rem',
                                fontWeight: 700,
                                padding: '0.15rem 0.45rem',
                                borderRadius: '999px',
                                background: req.obligatorio ? '#fee2e2' : '#f1f5f9',
                                color: req.obligatorio ? '#991b1b' : '#475569',
                              }}>
                          {req.obligatorio ? 'Obligatorio' : 'Deseable'}
                        </span>
                              {tagMeses && (
                                  <span style={{ fontSize: '0.72rem', fontWeight: 700, padding: '0.15rem 0.45rem', borderRadius: '999px', background: '#fef3c7', color: '#92400e' }}>
                            {tagMeses}
                          </span>
                              )}
                            </div>
                            <strong style={{ fontSize: '0.92rem' }}>{req.nombreRequisito}</strong>
                            {req.descripcion && <small style={{ color: '#475569' }}>{req.descripcion}</small>}
                          </div>
                          <button type="button" className="btn btn-secondary" onClick={() => quitarRequisito(req.id)}>Quitar</button>
                        </div>
                    );
                  })}
                </div>
            )}

            <form onSubmit={agregarRequisito} className="form-grid" style={{ alignItems: 'end', borderTop: '1px solid #e2e8f0', paddingTop: '0.85rem' }}>
              <div className="field">
                <label htmlFor="req-categoria">Dimensión / Categoría</label>
                <select
                    id="req-categoria"
                    value={nuevoReqCategoria}
                    onChange={e => setNuevoReqCategoria(e.target.value)}
                    disabled={guardandoReq}
                >
                  <option value="EXPERIENCIA">Experiencia laboral</option>
                  <option value="FORMACION">Formación académica</option>
                  <option value="CERTIFICACION">Certificación / Normativa</option>
                  <option value="COMPETENCIA_TECNICA">Competencia técnica</option>
                </select>
              </div>
              <div className="field">
                <label htmlFor="req-nombre">Criterio o requisito *</label>
                <input
                    id="req-nombre"
                    type="text"
                    value={nuevoReqNombre}
                    onChange={e => setNuevoReqNombre(e.target.value)}
                    placeholder="Ej: Operación de sistemas SCADA o sala de control"
                    disabled={guardandoReq}
                />
              </div>
              <div className="field">
                <label htmlFor="req-meses">Exp. mínima en meses (opcional)</label>
                <input
                    id="req-meses"
                    type="number"
                    min="1"
                    max="240"
                    value={nuevoReqMeses}
                    onChange={e => setNuevoReqMeses(e.target.value)}
                    placeholder="Ej: 12 o 24"
                    disabled={guardandoReq}
                />
              </div>
              <div className="field">
                <label htmlFor="req-desc">Detalle verificable (opcional)</label>
                <input
                    id="req-desc"
                    type="text"
                    value={nuevoReqDesc}
                    onChange={e => setNuevoReqDesc(e.target.value)}
                    placeholder="Ej: En plantas de proceso o centros de cultivo"
                    disabled={guardandoReq}
                />
              </div>
              <div className="field" style={{ flexDirection: 'row', alignItems: 'center', gap: '0.5rem' }}>
                <input
                    id="req-oblig"
                    type="checkbox"
                    checked={nuevoReqObligatorio}
                    onChange={e => setNuevoReqObligatorio(e.target.checked)}
                />
                <label htmlFor="req-oblig" style={{ margin: 0 }}>Criterio obligatorio</label>
              </div>
              <div>
                <button type="submit" className="btn btn-secondary" disabled={!nuevoReqNombre.trim() || guardandoReq}>
                  <Icono nombre="mas" size={15} /> Agregar criterio
                </button>
              </div>
            </form>
          </div>
        </section>
    )}

    {/* 3. Panel estructurado de análisis documental y revisión humana */}
    {analisisMeta && datosEditables && (
        <section className="panel doc-panel" aria-label="Revisión humana del análisis IA">
          <div className="form-panel-head">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
              <h2>Análisis documental y revisión humana</h2>
              <span className={`status-badge ${analisisMeta.estadoRevision === 'VALIDADO' ? 'interno' : 'externo'}`}>
              {ETIQUETA_REVISION[analisisMeta.estadoRevision] || analisisMeta.estadoRevision}
            </span>
            </div>
            <p>
              Generado el {new Date(analisisMeta.fechaGeneracion).toLocaleString('es-CL')} · Modelo: {analisisMeta.modeloUtilizado}
              {analisisMeta.usuarioValidador ? ` · Validado por ${analisisMeta.usuarioValidador}` : ''}
            </p>
          </div>

          <div className="form-panel-body" style={{ display: 'flex', flexDirection: 'column', gap: '1.35rem' }}>
            <div className="form-grid">
              <div className="field">
                <label htmlFor="ia-titulo">Título / Formación profesional principal</label>
                <input
                    id="ia-titulo"
                    type="text"
                    value={datosEditables.tituloProfesional || ''}
                    disabled={!analista}
                    onChange={e => actualizarCampoGeneral('tituloProfesional', e.target.value)}
                />
              </div>
              <div className="field form-wide">
                <label htmlFor="ia-resumen">Resumen profesional objetivo</label>
                <textarea
                    id="ia-resumen"
                    rows={3}
                    value={datosEditables.resumenProfesionalObjetivo || ''}
                    disabled={!analista}
                    onChange={e => actualizarCampoGeneral('resumenProfesionalObjetivo', e.target.value)}
                />
              </div>
            </div>

            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.65rem' }}>
                <h3 style={{ margin: 0 }}>Semáforo de contraste contra criterios del cargo</h3>
                {obligatorios.length > 0 && (
                    <span style={{
                      fontSize: '0.82rem',
                      fontWeight: 700,
                      padding: '0.3rem 0.7rem',
                      borderRadius: '999px',
                      background: todosObligatoriosOk ? '#d1fae5' : '#fef3c7',
                      color: todosObligatoriosOk ? '#065f46' : '#92400e',
                      border: `1px solid ${todosObligatoriosOk ? '#6ee7b7' : '#fcd34d'}`,
                    }}>
                  {todosObligatoriosOk
                      ? `✓ Obligatorios respaldados (${obligatoriosCumplidos}/${obligatorios.length})`
                      : `⚠ Obligatorios respaldados: ${obligatoriosCumplidos} de ${obligatorios.length}`}
                </span>
                )}
              </div>

              {totalCriterios === 0 ? (
                  <p><small>No se contrastaron requisitos específicos porque este cargo aún no tiene criterios configurados.</small></p>
              ) : (
                  <>
                    <div style={{ padding: '0.9rem', borderRadius: '10px', background: '#f8fafc', border: '1px solid #cbd5e1', marginBottom: '0.9rem' }}>
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: '0.65rem', marginBottom: '0.75rem' }}>
                        <div style={{ padding: '0.6rem 0.8rem', borderRadius: '8px', background: '#ecfdf5', border: '1px solid #a7f3d0', color: '#065f46' }}>
                          <div style={{ fontSize: '0.78rem', fontWeight: 600 }}>✓ Evidencia verificada</div>
                          <strong style={{ fontSize: '1.35rem' }}>{totalEncontrados}</strong> <small>de {totalCriterios}</small>
                        </div>
                        <div style={{ padding: '0.6rem 0.8rem', borderRadius: '8px', background: '#fffbeb', border: '1px solid #fde68a', color: '#92400e' }}>
                          <div style={{ fontSize: '0.78rem', fontWeight: 600 }}>⚠ Parcial / Por verificar</div>
                          <strong style={{ fontSize: '1.35rem' }}>{totalVerificar}</strong> <small>de {totalCriterios}</small>
                        </div>
                        <div style={{ padding: '0.6rem 0.8rem', borderRadius: '8px', background: '#fef2f2', border: '1px solid #fecaca', color: '#991b1b' }}>
                          <div style={{ fontSize: '0.78rem', fontWeight: 600 }}>✕ No identificado en CV</div>
                          <strong style={{ fontSize: '1.35rem' }}>{totalNoIdentificados}</strong> <small>de {totalCriterios}</small>
                        </div>
                      </div>

                      <div
                          aria-label="Distribución visual de cumplimiento de criterios"
                          style={{ display: 'flex', height: '10px', borderRadius: '999px', overflow: 'hidden', background: '#e2e8f0' }}
                      >
                        {totalEncontrados > 0 && (
                            <div style={{ width: `${(totalEncontrados / totalCriterios) * 100}%`, background: '#10b981', transition: 'width 0.25s ease' }} />
                        )}
                        {totalVerificar > 0 && (
                            <div style={{ width: `${(totalVerificar / totalCriterios) * 100}%`, background: '#f59e0b', transition: 'width 0.25s ease' }} />
                        )}
                        {totalNoIdentificados > 0 && (
                            <div style={{ width: `${(totalNoIdentificados / totalCriterios) * 100}%`, background: '#ef4444', transition: 'width 0.25s ease' }} />
                        )}
                      </div>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.8rem' }}>
                      {matriz.map((req, idx) => {
                        const tema = TEMA_SEMAFORO[req.resultadoAnalisis] || TEMA_SEMAFORO.REQUIERE_VERIFICACION;
                        const { detalle, pregunta } = separarObservacionYPregunta(req.observacionEvidencia);
                        const reqConfig = requisitosCargo.find(r => r.id === req.requisitoId || r.nombreRequisito.toLowerCase() === req.requisito.toLowerCase());

                        return (
                            <div
                                key={idx}
                                style={{
                                  padding: '0.95rem 1rem',
                                  borderRadius: '10px',
                                  background: tema.fondo,
                                  border: `1px solid ${tema.borde}`,
                                  borderLeft: `6px solid ${tema.acento}`,
                                }}
                            >
                              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '0.5rem' }}>
                                <div>
                                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem', alignItems: 'center', marginBottom: '0.35rem' }}>
                              <span style={{
                                fontSize: '0.76rem',
                                fontWeight: 700,
                                padding: '0.2rem 0.6rem',
                                borderRadius: '999px',
                                background: tema.badgeBg,
                                color: tema.texto,
                                border: `1px solid ${tema.borde}`,
                              }}>
                                {tema.icono} {tema.etiqueta}
                              </span>
                                    <span style={{
                                      fontSize: '0.74rem',
                                      fontWeight: 700,
                                      padding: '0.18rem 0.5rem',
                                      borderRadius: '999px',
                                      background: req.obligatorio ? '#1e293b' : '#ffffff',
                                      color: req.obligatorio ? '#ffffff' : '#475569',
                                      border: '1px solid #cbd5e1',
                                    }}>
                                {req.obligatorio ? 'Obligatorio' : 'Deseable'}
                              </span>
                                    {reqConfig?.categoria && (
                                        <span style={{ fontSize: '0.74rem', fontWeight: 600, padding: '0.18rem 0.5rem', borderRadius: '999px', background: '#ffffff', color: '#0369a1', border: '1px solid #bae6fd' }}>
                                  {ETIQUETA_CATEGORIA[reqConfig.categoria] || reqConfig.categoria}
                                </span>
                                    )}
                                    {reqConfig?.experienciaMinimaMeses && (
                                        <span style={{ fontSize: '0.74rem', fontWeight: 600, padding: '0.18rem 0.5rem', borderRadius: '999px', background: '#ffffff', color: '#475569', border: '1px solid #cbd5e1' }}>
                                  {formatearMeses(reqConfig.experienciaMinimaMeses)}
                                </span>
                                    )}
                                  </div>
                                  <strong style={{ fontSize: '1rem', color: '#0f172a' }}>{req.requisito}</strong>
                                </div>

                                {analista ? (
                                    <div style={{ display: 'flex', gap: '0.45rem', flexWrap: 'wrap' }}>
                                      <select
                                          aria-label={`Estado de cumplimiento ${req.requisito}`}
                                          value={req.resultadoAnalisis}
                                          onChange={e => actualizarItemLista('matrizRequisitos', idx, 'resultadoAnalisis', e.target.value, true)}
                                          style={{ background: '#ffffff', fontWeight: 600 }}
                                      >
                                        <option value="ENCONTRADO">✓ Evidencia verificada (Verde)</option>
                                        <option value="REQUIERE_VERIFICACION">⚠ Requiere verificación (Ámbar)</option>
                                        <option value="NO_IDENTIFICADO_EN_CV">✕ No identificado en CV (Rojo)</option>
                                      </select>
                                      <select
                                          aria-label={`Estado de revisión ${req.requisito}`}
                                          value={req.estadoRevisionItem || 'PENDIENTE'}
                                          onChange={e => actualizarItemLista('matrizRequisitos', idx, 'estadoRevisionItem', e.target.value)}
                                          style={{ background: '#ffffff' }}
                                      >
                                        <option value="PENDIENTE">Extraído por IA</option>
                                        <option value="CONFIRMADO">Confirmado</option>
                                        <option value="CORREGIDO">Corregido</option>
                                        <option value="REQUIERE_VERIFICACION">Pendiente verificar</option>
                                      </select>
                                    </div>
                                ) : (
                                    <small style={{ fontWeight: 600, color: tema.texto }}>
                                      {ETIQUETA_ITEM[req.estadoRevisionItem] || req.estadoRevisionItem}
                                    </small>
                                )}
                              </div>

                              {detalle && (
                                  <p style={{ margin: '0.5rem 0 0', fontSize: '0.9rem', color: '#1e293b' }}>
                                    {detalle}
                                  </p>
                              )}

                              {pregunta && (
                                  <div style={{ marginTop: '0.5rem', padding: '0.45rem 0.65rem', borderRadius: '6px', background: 'rgba(255,255,255,0.85)', border: `1px dashed ${tema.acento}`, fontSize: '0.85rem', color: tema.texto }}>
                                    <strong>Pregunta sugerida para entrevista:</strong> {pregunta}
                                  </div>
                              )}

                              {req.evidencia?.fragmento ? (
                                  <div style={{ marginTop: '0.55rem', padding: '0.45rem 0.65rem', borderRadius: '6px', background: '#ffffff', borderLeft: `3px solid ${tema.acento}`, fontSize: '0.84rem' }}>
                                    <strong>Cita verificable (Pág. {req.evidencia.pagina ?? '?'} · {req.evidencia.estadoVerificacion}):</strong>{' '}
                                    “{req.evidencia.fragmento}”
                                  </div>
                              ) : (
                                  <div style={{ marginTop: '0.45rem', fontSize: '0.82rem', color: tema.texto }}>
                                    <em>Sin cita textual directa en el documento.</em>
                                  </div>
                              )}
                            </div>
                        );
                      })}
                    </div>
                  </>
              )}
            </div>

            <div>
              <h3>Experiencias laborales documentadas ({datosEditables.experienciasLaborales?.length || 0})</h3>
              {(!datosEditables.experienciasLaborales || datosEditables.experienciasLaborales.length === 0) ? (
                  <p><small>No se identificaron experiencias laborales explícitas en el CV.</small></p>
              ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                    {datosEditables.experienciasLaborales.map((exp, idx) => (
                        <div key={idx} style={{ padding: '0.75rem', border: '1px solid #cbd5e1', borderRadius: '8px', background: '#f8fafc' }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.5rem' }}>
                            <strong>{exp.cargo} · {exp.empresa}</strong>
                            <small>{ETIQUETA_ITEM[exp.estadoRevisionItem] || 'Extraído por IA'}</small>
                          </div>
                          <p style={{ margin: '0.25rem 0' }}>
                            <small>Periodo: <strong>{exp.periodo}</strong> · Duración: <strong>{exp.duracionCalculada}</strong></small>
                          </p>
                          {exp.funciones?.length > 0 && (
                              <ul style={{ margin: '0.25rem 0 0.5rem 1.2rem' }}>
                                {exp.funciones.map((fn, fIdx) => <li key={fIdx}><small>{fn}</small></li>)}
                              </ul>
                          )}
                          {exp.evidencia?.fragmento && (
                              <p style={{ margin: '0.25rem 0', padding: '0.35rem 0.5rem', background: '#ffffff', borderLeft: '3px solid #0284c7' }}>
                                <small>
                                  <strong>Evidencia (Pág. {exp.evidencia.pagina ?? '?'} · {exp.evidencia.estadoVerificacion}):</strong>{' '}
                                  “{exp.evidencia.fragmento}”
                                </small>
                              </p>
                          )}
                          {analista && (
                              <div style={{ marginTop: '0.5rem', display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
                                <input
                                    type="text"
                                    value={exp.cargo}
                                    aria-label={`Cargo experiencia ${idx + 1}`}
                                    onChange={e => actualizarItemLista('experienciasLaborales', idx, 'cargo', e.target.value, true)}
                                />
                                <select
                                    value={exp.estadoRevisionItem || 'PENDIENTE'}
                                    aria-label={`Estado experiencia ${idx + 1}`}
                                    onChange={e => actualizarItemLista('experienciasLaborales', idx, 'estadoRevisionItem', e.target.value)}
                                >
                                  <option value="PENDIENTE">Extraído por IA</option>
                                  <option value="CONFIRMADO">Confirmado por analista</option>
                                  <option value="CORREGIDO">Corregido por analista</option>
                                  <option value="REQUIERE_VERIFICACION">Pendiente de verificar</option>
                                </select>
                              </div>
                          )}
                        </div>
                    ))}
                  </div>
              )}
            </div>

            <div>
              <h3>Formación académica ({datosEditables.formacionAcademica?.length || 0})</h3>
              {(!datosEditables.formacionAcademica || datosEditables.formacionAcademica.length === 0) ? (
                  <p><small>No identificada explícitamente en el CV.</small></p>
              ) : (
                  <ul style={{ paddingLeft: '1.2rem' }}>
                    {datosEditables.formacionAcademica.map((f, idx) => (
                        <li key={idx} style={{ marginBottom: '0.4rem' }}>
                          <strong>{f.titulo}</strong> — {f.institucion} ({f.periodo}, {f.estadoAcademico})
                          {f.evidencia?.fragmento && (
                              <div><small>Evidencia Pág. {f.evidencia.pagina ?? '?'}: “{f.evidencia.fragmento}”</small></div>
                          )}
                        </li>
                    ))}
                  </ul>
              )}
            </div>

            <div className="form-grid">
              <div>
                <h4>Habilidades técnicas</h4>
                <p><small>
                  {datosEditables.habilidadesTecnicas?.length > 0
                      ? datosEditables.habilidadesTecnicas.map(h => `${h.nombre}${h.evidencia?.pagina ? ` (Pág. ${h.evidencia.pagina})` : ''}`).join(' · ')
                      : 'NO_IDENTIFICADO_EN_CV'}
                </small></p>
              </div>
              <div>
                <h4>Competencias declaradas</h4>
                <p><small>
                  {datosEditables.competenciasDeclaradas?.length > 0
                      ? datosEditables.competenciasDeclaradas.map(c => `${c.nombre}${c.evidencia?.pagina ? ` (Pág. ${c.evidencia.pagina})` : ''}`).join(' · ')
                      : 'NO_IDENTIFICADO_EN_CV'}
                </small></p>
              </div>
              <div>
                <h4>Certificaciones y licencias</h4>
                <p><small>
                  {datosEditables.certificacionesYLicencias?.length > 0
                      ? datosEditables.certificacionesYLicencias.map(c => `${c.nombre} (${c.vigencia})`).join(' · ')
                      : 'NO_IDENTIFICADO_EN_CV'}
                </small></p>
              </div>
              <div>
                <h4>Datos faltantes o ambiguos</h4>
                {datosEditables.datosFaltantesOAmbiguos?.length > 0 ? (
                    <ul style={{ paddingLeft: '1.1rem', margin: 0 }}>
                      {datosEditables.datosFaltantesOAmbiguos.map((d, idx) => (
                          <li key={idx}><small><strong>{d.antecedente}:</strong> {d.motivo}</small></li>
                      ))}
                    </ul>
                ) : (
                    <p><small>Sin ambigüedades críticas detectadas.</small></p>
                )}
              </div>
            </div>

            <div className="field form-wide">
              <label htmlFor="obs-analista">Observaciones y notas de verificación del analista</label>
              <textarea
                  id="obs-analista"
                  rows={3}
                  value={observacionesAnalista}
                  disabled={!analista || guardandoValidacion}
                  onChange={e => setObservacionesAnalista(e.target.value)}
                  placeholder="Registra aclaraciones, puntos verificados telefónicamente o aspectos a profundizar en entrevista..."
              />
            </div>

            {analista && (
                <div className="form-actions">
                  <button
                      type="button"
                      className="btn btn-secondary"
                      disabled={guardandoValidacion}
                      onClick={() => guardarRevision(false)}
                  >
                    Guardar borrador de revisión
                  </button>
                  <button
                      type="button"
                      className="btn btn-primary"
                      disabled={guardandoValidacion}
                      onClick={() => guardarRevision(true)}
                  >
                    <Icono nombre="check" size={16}/>
                    {guardandoValidacion ? 'Validando...' : 'Validar antecedentes para evaluador'}
                  </button>
                </div>
            )}
          </div>
        </section>
    )}

    {/* 4. Carga directa heredada solo cuando no existe postulación pública */}
    {analista && postulaciones.length === 0 && (
        <section className="panel doc-panel" aria-label="Subir currículum">
          <div className="form-panel-head">
            <h2>{documento ? 'Reemplazar currículum' : 'Adjuntar currículum'}</h2>
            <p>Carga administrativa interna. Los postulantes externos deben postular mediante ofertas publicadas.</p>
          </div>
          <form onSubmit={subir} className="form-panel-body doc-upload-form">
            <div className="field">
              <label htmlFor="archivo-cv">Seleccionar documento PDF o Word</label>
              <input
                  id="archivo-cv"
                  type="file"
                  accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                  onChange={elegirArchivo}
                  disabled={procesando}
              />
              <span className="field-help">{archivo ? `${archivo.name} · ${etiquetaTamano(archivo.size)}` : 'Elige un archivo auténtico de máximo 5 MB.'}</span>
            </div>
            <button type="submit" className="btn btn-primary" disabled={!archivo || procesando}>
              <Icono nombre="check" size={16}/>{procesando ? 'Guardando CV...' : documento ? 'Reemplazar CV' : 'Subir CV'}
            </button>
          </form>
        </section>
    )}
  </div>;
}