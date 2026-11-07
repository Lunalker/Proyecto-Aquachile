import { useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../services/api';
import { validarCandidato } from '../utils/validarCandidato';
import Icono from '../components/Icono';

const vacio = () => ({ nombre:'', correo:'', telefono:'', cargoPostula:'', familiaCargo:'', origen:'Externo', ubicacion:'', unidad:'' });
const campos = [
  { name:'nombre', label:'Nombre completo', required:true, placeholder:'Ej.: Persona de prueba' },
  { name:'correo', label:'Correo electrónico', type:'email', required:true, placeholder:'ejemplo@correo.test' },
  { name:'telefono', label:'Teléfono', type:'tel', placeholder:'Número ficticio' },
  { name:'cargoPostula', label:'Cargo al que postula', required:true, placeholder:'Ej.: Analista de procesos' },
  { name:'familiaCargo', label:'Familia de cargo', placeholder:'Ej.: Profesional A' },
  { name:'ubicacion', label:'Ubicación', placeholder:'Ej.: Oficina central' },
  { name:'unidad', label:'Unidad', placeholder:'Ej.: Industrial' },
];

export default function FormularioCandidato() {
  const [candidato, setCandidato] = useState(vacio);
  const [mensaje, setMensaje] = useState('');
  const [guardando, setGuardando] = useState(false);
  const [errores, setErrores] = useState({});
  const handleChange = (e) => {
    const { name, value } = e.target;
    setCandidato(prev => ({ ...prev, [name]: value }));
    setErrores(prev => ({ ...prev, [name]: undefined }));
    setMensaje('');
  };

  async function handleSubmit(event) {
    event.preventDefault();
    if (guardando) return;
    setMensaje('');
    const { datos, errores: erroresActuales, valido } = validarCandidato(candidato);
    setErrores(erroresActuales);
    if (!valido) return;
    setGuardando(true);
    try {
      await api.post('/candidatos', datos);
      setMensaje('¡Candidato registrado correctamente! Puedes ingresar otro registro.');
      setCandidato(vacio());
      setErrores({});
    } catch (error) {
      setMensaje(error.response?.status === 403 ? 'Tu usuario no tiene permiso para registrar candidatos.' : 'No se pudo guardar el candidato. Revisa los datos y la conexión.');
    } finally { setGuardando(false); }
  }

  return <div className="page-container form-shell">
    <div className="page-heading"><div><p className="eyebrow">GESTIÓN DE CANDIDATOS</p><h1 className="page-title">Registrar candidato</h1><p className="page-description">Completa la información principal para iniciar un proceso de evaluación.</p></div><Link to="/" className="btn btn-secondary"><Icono nombre="flecha" size={15} className="rotate-180"/>Volver al panel</Link></div>
    {mensaje && <div role="status" className={`alert-box ${mensaje.includes('correctamente') ? 'alert-box-success' : 'alert-box-error'}`}>{mensaje}</div>}
    <div className="form-helper-box"><strong>Registro de demostración:</strong> utiliza únicamente datos inventados. Los candidatos reales y los resultados reales están fuera del alcance académico.</div>
    {Object.keys(errores).length > 0 && <p role="alert" className="alert-box alert-box-error">Revisa los campos marcados antes de guardar.</p>}
    <form onSubmit={handleSubmit} noValidate className="panel form-panel">
      <div className="form-panel-head"><h2>Información del postulante</h2><p>Los campos marcados con * son obligatorios.</p></div>
      <div className="form-panel-body"><div className="form-grid">
        {campos.map(({name,label,type,required,placeholder})=><div className="field" key={name}><label htmlFor={name}>{label}{required ? ' *' : ''}</label><input id={name} type={type||'text'} name={name} value={candidato[name]} placeholder={placeholder || ''} onChange={handleChange} required={Boolean(required)} aria-invalid={Boolean(errores[name])} aria-describedby={errores[name] ? `error-${name}` : undefined} disabled={guardando}/>{errores[name] && <p id={`error-${name}`} className="field-error mt-1 text-sm text-red-700">{errores[name]}</p>}</div>)}
        <div className="field"><label htmlFor="origen">Origen del candidato</label><select id="origen" name="origen" value={candidato.origen} onChange={handleChange} aria-invalid={Boolean(errores.origen)} aria-describedby={errores.origen ? 'error-origen' : undefined} disabled={guardando}><option value="Externo">Externo</option><option value="Interno">Interno</option></select>{errores.origen && <p id="error-origen" className="field-error mt-1 text-sm text-red-700">{errores.origen}</p>}</div>
      </div></div>
      <div className="form-actions"><Link to="/" className="btn btn-secondary">Cancelar</Link><button type="submit" className="btn btn-primary" disabled={guardando}><Icono nombre="check" size={17}/>{guardando ? 'Guardando...' : 'Guardar candidato'}</button></div>
    </form>
  </div>;
}
