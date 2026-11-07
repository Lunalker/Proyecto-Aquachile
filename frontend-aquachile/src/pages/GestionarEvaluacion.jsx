import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../services/api';
import Icono from '../components/Icono';

export default function GestionarEvaluacion() {
  const { idSolicitud } = useParams();
  const navigate = useNavigate();
  const [evaluacion, setEvaluacion] = useState({resultado:'',estadoEvaluacion:'Recomendado'});
  const [estadoSolicitud, setEstadoSolicitud] = useState('Finalizada');
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const handleChange = e => setEvaluacion(prev => ({ ...prev, [e.target.name]: e.target.value }));

  async function handleSubmit(event) {
    event.preventDefault();
    if (guardando) return;
    setGuardando(true); setError('');
    try {
      // Se conserva la operación actual del backend: registro y posterior actualización de estado.
      await api.post('/evaluaciones', { ...evaluacion, solicitud:{id:Number(idSolicitud)} });
      await api.put(`/solicitudes/${idSolicitud}/estado`, estadoSolicitud, { headers:{'Content-Type':'text/plain'} });
      navigate('/solicitudes', { replace:true });
    } catch (err) {
      setError(err.response?.status===403
        ? 'No tienes permisos para gestionar evaluaciones.'
        : 'No se pudo completar la operación. Si se creó la evaluación y falló el cambio de estado, revisa la solicitud antes de volver a guardar.');
    } finally { setGuardando(false); }
  }

  return <div className="page-container form-shell">
    <div className="page-heading"><div><p className="eyebrow">PROFESIONAL EVALUADOR</p><h1 className="page-title">Gestionar evaluación</h1><p className="page-description">Solicitud #{idSolicitud} · Registro de resultados y seguimiento.</p></div><button type="button" onClick={()=>navigate('/solicitudes')} className="btn btn-secondary"><Icono nombre="flecha" size={15} className="rotate-180"/>Volver a solicitudes</button></div>
    {error && <div role="alert" className="alert-box alert-box-error">{error}</div>}
    <div className="form-helper-box">Registra únicamente observaciones ficticias. Esta etapa guarda la evaluación y actualiza el estado mediante dos operaciones independientes del backend.</div>
    <form onSubmit={handleSubmit} className="panel form-panel">
      <div className="form-panel-head"><h2>Resultado de la evaluación</h2><p>Completa la categoría y las observaciones generales.</p></div>
      <div className="form-panel-body"><div className="form-grid">
        <div className="field"><label htmlFor="estadoSolicitud">Estado de la solicitud</label><select id="estadoSolicitud" value={estadoSolicitud} onChange={e=>setEstadoSolicitud(e.target.value)} disabled={guardando}><option value="Pendiente">Pendiente</option><option value="En proceso">En proceso</option><option value="Finalizada">Finalizada</option></select></div>
        <div className="field"><label htmlFor="estadoEvaluacion">Categoría del evaluado</label><select id="estadoEvaluacion" name="estadoEvaluacion" value={evaluacion.estadoEvaluacion} onChange={handleChange} disabled={guardando}><option value="Recomendado">Recomendado</option><option value="Recomendado con observaciones">Recomendado con observaciones</option><option value="No recomendado">No recomendado</option></select></div>
        <div className="field form-wide"><label htmlFor="resultado">Resultado y observaciones psicolaborales *</label><textarea id="resultado" name="resultado" value={evaluacion.resultado} onChange={handleChange} required disabled={guardando} rows={5} placeholder="Describe las observaciones generales utilizando datos ficticios..."/></div>
      </div></div>
      <div className="form-actions"><button type="button" className="btn btn-secondary" onClick={()=>navigate('/solicitudes')} disabled={guardando}>Volver sin guardar</button><button type="submit" className="btn btn-primary" disabled={guardando}><Icono nombre="check" size={16}/>{guardando?'Guardando...':'Guardar evaluación'}</button></div>
    </form>
  </div>;
}
