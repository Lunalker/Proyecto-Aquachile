import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../services/api';
import Icono from '../components/Icono';

export default function NuevaSolicitud() {
  const { idCandidato } = useParams();
  const navigate = useNavigate();
  const [solicitud, setSolicitud] = useState({ cargo:'', familiaCargo:'', profesionalResponsable:'', observaciones:'' });
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);
  const handleChange = e => setSolicitud(prev => ({ ...prev, [e.target.name]: e.target.value }));

  async function handleSubmit(event) {
    event.preventDefault();
    if (guardando) return;
    setGuardando(true); setError('');
    try {
      await api.post('/solicitudes', { ...solicitud, candidato: { id:Number(idCandidato) } });
      navigate('/solicitudes', { replace:true });
    } catch (err) {
      setError(err.response?.status===403 ? 'No tienes permisos para crear solicitudes.' : 'No se pudo crear la solicitud. Revisa los datos y la conexión.');
    } finally { setGuardando(false); }
  }

  return <div className="page-container form-shell">
    <div className="page-heading"><div><p className="eyebrow">EVALUACIONES PSICOLABORALES</p><h1 className="page-title">Nueva solicitud</h1><p className="page-description">Solicitud asociada al candidato #{idCandidato}.</p></div><button type="button" onClick={()=>navigate('/')} className="btn btn-secondary"><Icono nombre="flecha" size={15} className="rotate-180"/>Volver al panel</button></div>
    {error && <div role="alert" className="alert-box alert-box-error">{error}</div>}
    <form onSubmit={handleSubmit} className="panel form-panel">
      <div className="form-panel-head"><h2>Datos de la solicitud</h2><p>Define el cargo, la familia y el profesional responsable.</p></div>
      <div className="form-panel-body"><div className="form-grid">
        {[{name:'cargo',label:'Cargo a evaluar'},{name:'familiaCargo',label:'Familia de cargo'},{name:'profesionalResponsable',label:'Profesional responsable (evaluador)'}].map(c=><div className="field" key={c.name}><label htmlFor={c.name}>{c.label} *</label><input id={c.name} name={c.name} required disabled={guardando} value={solicitud[c.name]} onChange={handleChange}/></div>)}
        <div className="field form-wide"><label htmlFor="observaciones">Observaciones iniciales</label><textarea id="observaciones" name="observaciones" value={solicitud.observaciones} onChange={handleChange} disabled={guardando} rows={4} placeholder="Agrega información general del proceso (solo datos ficticios)."/></div>
      </div></div>
      <div className="form-actions"><button type="button" className="btn btn-secondary" onClick={()=>navigate('/')}>Cancelar</button><button type="submit" className="btn btn-primary" disabled={guardando}><Icono nombre="check" size={16}/>{guardando?'Guardando...':'Guardar solicitud'}</button></div>
    </form>
  </div>;
}
