import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api, {
  generarPreguntasApoyoSolicitud,
  obtenerAnalisisValidadoSolicitud,
} from '../services/api';
import Icono from '../components/Icono';

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

export default function GestionarEvaluacion() {
  const { idSolicitud } = useParams();
  const navigate = useNavigate();
  const [evaluacion, setEvaluacion] = useState({ resultado: '', estadoEvaluacion: 'Recomendado' });
  const [estadoSolicitud, setEstadoSolicitud] = useState('Finalizada');
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');

  // Antecedentes validados por el analista y preguntas de apoyo para el evaluador
  const [analisisValidado, setAnalisisValidado] = useState(null);
  const [cargandoAntecedentes, setCargandoAntecedentes] = useState(true);
  const [preguntasApoyo, setPreguntasApoyo] = useState(null);
  const [generandoPreguntas, setGenerandoPreguntas] = useState(false);
  const [errorIa, setErrorIa] = useState('');

  useEffect(() => {
    let activo = true;
    setCargandoAntecedentes(true);
    obtenerAnalisisValidadoSolicitud(idSolicitud)
        .then(data => {
          if (!activo) return;
          setAnalisisValidado(data);
          if (data?.preguntasApoyo) {
            setPreguntasApoyo(data.preguntasApoyo);
          }
        })
        .catch(() => {
          if (activo) setAnalisisValidado(null);
        })
        .finally(() => {
          if (activo) setCargandoAntecedentes(false);
        });
    return () => { activo = false; };
  }, [idSolicitud]);

  const handleChange = e => setEvaluacion(prev => ({ ...prev, [e.target.name]: e.target.value }));

  async function handleGenerarPreguntas() {
    if (generandoPreguntas) return;
    setGenerandoPreguntas(true);
    setErrorIa('');
    try {
      const resp = await generarPreguntasApoyoSolicitud(idSolicitud);
      setPreguntasApoyo(resp);
    } catch (err) {
      setErrorIa(err.response?.data?.message || 'No se pudieron generar preguntas sugeridas en este momento.');
    } finally {
      setGenerandoPreguntas(false);
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (guardando) return;
    setGuardando(true);
    setError('');
    try {
      await api.post('/evaluaciones', { ...evaluacion, solicitud: { id: Number(idSolicitud) } });
      await api.put(`/solicitudes/${idSolicitud}/estado`, estadoSolicitud, { headers: { 'Content-Type': 'text/plain' } });
      navigate('/solicitudes', { replace: true });
    } catch (err) {
      setError(err.response?.status === 403
          ? 'No tienes permisos para gestionar evaluaciones.'
          : 'No se pudo completar la operación. Si se creó la evaluación y falló el cambio de estado, revisa la solicitud antes de volver a guardar.');
    } finally { setGuardando(false); }
  }

  const datos = analisisValidado?.datosActuales;
  const matriz = datos?.matrizRequisitos || [];
  const totalCriterios = matriz.length;
  const totalEncontrados = matriz.filter(r => r.resultadoAnalisis === 'ENCONTRADO').length;
  const totalVerificar = matriz.filter(r => r.resultadoAnalisis === 'REQUIERE_VERIFICACION').length;
  const totalNoIdentificados = matriz.filter(r => r.resultadoAnalisis === 'NO_IDENTIFICADO_EN_CV').length;

  return <div className="page-container form-shell">
    <div className="page-heading">
      <div>
        <p className="eyebrow">PROFESIONAL EVALUADOR</p>
        <h1 className="page-title">Gestionar evaluación</h1>
        <p className="page-description">Solicitud #{idSolicitud} · Consulta de antecedentes validados y registro de evaluación.</p>
      </div>
      <button type="button" onClick={() => navigate('/solicitudes')} className="btn btn-secondary">
        <Icono nombre="flecha" size={15} className="rotate-180"/>Volver a solicitudes
      </button>
    </div>

    {error && <div role="alert" className="alert-box alert-box-error">{error}</div>}
    <div className="form-helper-box">
      Los antecedentes mostrados a continuación fueron revisados y validados por el Analista de Reclutamiento. La decisión y conclusiones finales de la evaluación son responsabilidad exclusiva del Evaluador.
    </div>

    {/* Panel de antecedentes validados por el analista */}
    <section className="panel form-panel" aria-label="Antecedentes documentales validados">
      <div className="form-panel-head">
        <h2>Antecedentes del CV validados por el analista</h2>
        <p>
          {analisisValidado
              ? `Candidato: ${analisisValidado.nombreCandidatoInterno} · Cargo: ${analisisValidado.cargoPostula || 'No especificado'} · Validado por ${analisisValidado.usuarioValidador || 'Analista'}`
              : 'Información documental verificada previamente en reclutamiento.'}
        </p>
      </div>

      <div className="form-panel-body" style={{ display: 'flex', flexDirection: 'column', gap: '1.15rem' }}>
        {cargandoAntecedentes ? (
            <p role="status">Consultando antecedentes validados...</p>
        ) : !analisisValidado || !datos ? (
            <p>
              Esta solicitud aún no cuenta con un análisis de CV validado por el analista. Puedes continuar con tu evaluación profesional de forma habitual.
            </p>
        ) : (
            <>
              <div>
                <strong>Formación / Título principal:</strong> {datos.tituloProfesional}
                <p style={{ marginTop: '0.25rem' }}>{datos.resumenProfesionalObjetivo}</p>
              </div>

              {analisisValidado.observacionesAnalista && (
                  <div className="form-helper-box" style={{ margin: 0 }}>
                    <strong>Observaciones del analista:</strong> {analisisValidado.observacionesAnalista}
                  </div>
              )}

              {/* Semáforo de criterios contrastados */}
              {totalCriterios > 0 && (
                  <div>
                    <h4 style={{ marginBottom: '0.5rem' }}>Semáforo de criterios del cargo ({totalEncontrados}/{totalCriterios} cumplidos)</h4>
                    <div
                        style={{ display: 'flex', height: '8px', borderRadius: '999px', overflow: 'hidden', background: '#e2e8f0', marginBottom: '0.75rem' }}
                    >
                      {totalEncontrados > 0 && <div style={{ width: `${(totalEncontrados / totalCriterios) * 100}%`, background: '#10b981' }} />}
                      {totalVerificar > 0 && <div style={{ width: `${(totalVerificar / totalCriterios) * 100}%`, background: '#f59e0b' }} />}
                      {totalNoIdentificados > 0 && <div style={{ width: `${(totalNoIdentificados / totalCriterios) * 100}%`, background: '#ef4444' }} />}
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                      {matriz.map((r, idx) => {
                        const tema = TEMA_SEMAFORO[r.resultadoAnalisis] || TEMA_SEMAFORO.REQUIERE_VERIFICACION;
                        return (
                            <div
                                key={idx}
                                style={{
                                  padding: '0.7rem 0.85rem',
                                  borderRadius: '8px',
                                  background: tema.fondo,
                                  border: `1px solid ${tema.borde}`,
                                  borderLeft: `5px solid ${tema.acento}`,
                                }}
                            >
                              <div style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.4rem', alignItems: 'center' }}>
                                <strong>{r.requisito} {r.obligatorio ? '(Obligatorio)' : '(Deseable)'}</strong>
                                <span style={{ fontSize: '0.75rem', fontWeight: 700, padding: '0.15rem 0.5rem', borderRadius: '999px', background: tema.badgeBg, color: tema.texto }}>
                            {tema.icono} {tema.etiqueta}
                          </span>
                              </div>
                              <p style={{ margin: '0.35rem 0 0', fontSize: '0.88rem', color: '#1e293b' }}>{r.observacionEvidencia}</p>
                              {r.evidencia?.fragmento && (
                                  <div style={{ marginTop: '0.35rem', fontSize: '0.82rem', color: '#334155' }}>
                                    <strong>Pág. {r.evidencia.pagina ?? '?'}:</strong> “{r.evidencia.fragmento}”
                                  </div>
                              )}
                            </div>
                        );
                      })}
                    </div>
                  </div>
              )}

              {datos.experienciasLaborales?.length > 0 && (
                  <div>
                    <h4>Experiencia laboral documentada</h4>
                    <ul style={{ paddingLeft: '1.2rem' }}>
                      {datos.experienciasLaborales.map((exp, idx) => (
                          <li key={idx} style={{ marginBottom: '0.4rem' }}>
                            <strong>{exp.cargo}</strong> en {exp.empresa} ({exp.periodo} · {exp.duracionCalculada})
                            {exp.evidencia?.fragmento && (
                                <div><small>Evidencia Pág. {exp.evidencia.pagina ?? '?'}: “{exp.evidencia.fragmento}”</small></div>
                            )}
                          </li>
                      ))}
                    </ul>
                  </div>
              )}

              <div className="form-grid">
                <div>
                  <h4>Formación académica</h4>
                  <p><small>
                    {datos.formacionAcademica?.length > 0
                        ? datos.formacionAcademica.map(f => `${f.titulo} (${f.institucion})`).join(' · ')
                        : 'No identificada en CV'}
                  </small></p>
                </div>
                <div>
                  <h4>Habilidades y certificaciones</h4>
                  <p><small>
                    {[
                      ...(datos.habilidadesTecnicas || []).map(h => h.nombre),
                      ...(datos.certificacionesYLicencias || []).map(c => c.nombre),
                    ].join(' · ') || 'No identificadas en CV'}
                  </small></p>
                </div>
              </div>

              {/* Generación de preguntas sugeridas de apoyo */}
              <div style={{ borderTop: '1px solid #e2e8f0', paddingTop: '0.85rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
                  <div>
                    <h4 style={{ margin: 0 }}>Preguntas de apoyo sugeridas por IA</h4>
                    <small>Generadas a partir de brechas, datos ambiguos o requisitos pendientes de verificación.</small>
                  </div>
                  <button
                      type="button"
                      className="btn btn-secondary"
                      disabled={generandoPreguntas}
                      onClick={handleGenerarPreguntas}
                  >
                    {generandoPreguntas ? 'Generando preguntas...' : preguntasApoyo ? 'Regenerar preguntas sugeridas' : 'Generar preguntas sugeridas'}
                  </button>
                </div>

                {errorIa && <p className="alert-box alert-box-error" style={{ marginTop: '0.5rem' }}>{errorIa}</p>}

                {preguntasApoyo?.preguntas?.length > 0 && (
                    <div style={{ marginTop: '0.75rem', display: 'flex', flexDirection: 'column', gap: '0.55rem' }}>
                      {preguntasApoyo.preguntas.map((p, idx) => (
                          <div key={idx} style={{ padding: '0.65rem 0.85rem', borderRadius: '8px', background: '#f0f9ff', border: '1px solid #bae6fd', borderLeft: '4px solid #0284c7' }}>
                            <strong style={{ color: '#0c4a6e' }}>{idx + 1}. {p.pregunta}</strong>
                            <div style={{ marginTop: '0.2rem' }}><small style={{ color: '#0369a1' }}>Foco: {p.objetivoVerificacion} ({p.antecedenteRelacionado})</small></div>
                          </div>
                      ))}
                    </div>
                )}
              </div>
            </>
        )}
      </div>
    </section>

    {/* Formulario oficial de evaluación (responsabilidad exclusiva del Evaluador) */}
    <form onSubmit={handleSubmit} className="panel form-panel">
      <div className="form-panel-head">
        <h2>Resultado de la evaluación</h2>
        <p>Completa la categoría y las observaciones generales.</p>
      </div>
      <div className="form-panel-body">
        <div className="form-grid">
          <div className="field">
            <label htmlFor="estadoSolicitud">Estado de la solicitud</label>
            <select id="estadoSolicitud" value={estadoSolicitud} onChange={e => setEstadoSolicitud(e.target.value)} disabled={guardando}>
              <option value="Pendiente">Pendiente</option>
              <option value="En proceso">En proceso</option>
              <option value="Finalizada">Finalizada</option>
            </select>
          </div>
          <div className="field">
            <label htmlFor="estadoEvaluacion">Categoría del evaluado</label>
            <select id="estadoEvaluacion" name="estadoEvaluacion" value={evaluacion.estadoEvaluacion} onChange={handleChange} disabled={guardando}>
              <option value="Recomendado">Recomendado</option>
              <option value="Recomendado con observaciones">Recomendado con observaciones</option>
              <option value="No recomendado">No recomendado</option>
            </select>
          </div>
          <div className="field form-wide">
            <label htmlFor="resultado">Resultado y observaciones psicolaborales *</label>
            <textarea
                id="resultado"
                name="resultado"
                value={evaluacion.resultado}
                onChange={handleChange}
                required
                disabled={guardando}
                rows={5}
                placeholder="Describe las observaciones generales utilizando datos ficticios..."
            />
          </div>
        </div>
      </div>
      <div className="form-actions">
        <button type="button" className="btn btn-secondary" onClick={() => navigate('/solicitudes')} disabled={guardando}>Volver sin guardar</button>
        <button type="submit" className="btn btn-primary" disabled={guardando}>
          <Icono nombre="check" size={16}/>{guardando ? 'Guardando...' : 'Guardar evaluación'}
        </button>
      </div>
    </form>
  </div>;
}