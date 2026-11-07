import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api from '../services/api';
import { validarCandidato } from '../utils/validarCandidato';
import Icono from '../components/Icono';

const API_CANDIDATOS = '/candidatos';
const desdeCandidato = c => ({
  nombre:c.nombre ?? '', correo:c.correo ?? '', telefono:c.telefono ?? '',
  cargoPostula:c.cargoPostula ?? '', familiaCargo:c.familiaCargo ?? '',
  origen:c.origen ?? 'Externo', ubicacion:c.ubicacion ?? '', unidad:c.unidad ?? '',
});
const campos = [
  {nombre:'nombre', etiqueta:'Nombre completo', requerido:true},
  {nombre:'correo', etiqueta:'Correo electrónico', tipo:'email', requerido:true},
  {nombre:'telefono', etiqueta:'Teléfono', tipo:'tel'},
  {nombre:'cargoPostula', etiqueta:'Cargo al que postula', requerido:true},
  {nombre:'familiaCargo', etiqueta:'Familia de cargo'},
  {nombre:'ubicacion', etiqueta:'Ubicación'},
  {nombre:'unidad', etiqueta:'Unidad'},
];

export default function CandidatoDetalle() {
  const { idCandidato } = useParams();
  const [candidato, setCandidato] = useState(null);
  const [formulario, setFormulario] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [editando, setEditando] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');
  const [errores, setErrores] = useState({});

  useEffect(() => {
    let activo = true;
    async function cargar() {
      setCargando(true); setError(''); setMensaje(''); setEditando(false); setErrores({});
      setCandidato(null); setFormulario(null);
      try {
        const respuesta = await api.get(`${API_CANDIDATOS}/${idCandidato}`);
        if (!activo) return;
        setCandidato(respuesta.data);
        setFormulario(desdeCandidato(respuesta.data));
      } catch (err) {
        if (!activo) return;
        setError(err.response?.status === 404 ? 'No se encontró el candidato solicitado.' : 'No se pudo cargar el candidato. Revisa que Spring Boot y MySQL estén funcionando.');
      } finally { if (activo) setCargando(false); }
    }
    cargar();
    return () => { activo = false; };
  }, [idCandidato]);

  function actualizarCampo(event) {
    const {name,value} = event.target;
    setFormulario(anterior => ({...anterior,[name]:value}));
    setErrores(prev => ({...prev, [name]: undefined}));
  }
  function cancelarEdicion() {
    setFormulario(desdeCandidato(candidato)); setError(''); setErrores({}); setEditando(false);
  }
  async function guardarCambios(event) {
    event.preventDefault();
    if (guardando || !formulario) return;
    setError(''); setMensaje('');
    const { datos, errores: erroresActuales, valido } = validarCandidato(formulario);
    setErrores(erroresActuales);
    if (!valido) { setError('Revisa los campos marcados antes de guardar.'); return; }
    setGuardando(true);
    try {
      const respuesta = await api.put(`${API_CANDIDATOS}/${idCandidato}`, datos);
      setCandidato(respuesta.data); setFormulario(desdeCandidato(respuesta.data)); setEditando(false); setErrores({});
      setMensaje('Los datos del candidato se actualizaron correctamente.');
    } catch (err) {
      if (err.response?.status === 404) setError('Este candidato ya no existe. Regresa al listado.');
      else if (err.response?.status === 400) setError('Revisa los campos obligatorios y el formato del correo.');
      else if (err.response?.status === 409 || err.response?.status === 500) setError('No se pudieron guardar los cambios. Verifica que el correo no pertenezca a otro candidato.');
      else setError('No se pudieron guardar los cambios. Revisa la conexión con Spring Boot.');
    } finally { setGuardando(false); }
  }

  return <div className="page-container form-shell">
    <div className="page-heading"><div><p className="eyebrow">GESTIÓN DE CANDIDATOS</p><h1 className="page-title">Ficha de candidato #{idCandidato}</h1><p className="page-description">Consulta y actualiza los antecedentes registrados.</p></div><Link to="/" className="btn btn-secondary"><Icono nombre="flecha" size={15} className="rotate-180"/>Volver al panel</Link></div>
    {mensaje && <div role="status" className="alert-box alert-box-success">{mensaje}</div>}
    {error && <div role="alert" className="alert-box alert-box-error">{error}</div>}
    {cargando ? <div className="panel empty-state" role="status">Cargando candidato...</div>
      : !candidato ? <div className="panel empty-state">No se pudo mostrar la información.</div>
      : <section className="panel form-panel">
        <div className="form-panel-head"><h2>{editando ? 'Editar información' : 'Información del postulante'}</h2><p>{editando ? 'Modifica los datos necesarios y guarda los cambios.' : 'Antecedentes principales asociados al registro.'}</p></div>
        {!editando ? <>
          <dl className="detail-grid">
            {campos.map(({nombre,etiqueta})=><div key={nombre} className="detail-item"><dt>{etiqueta}</dt><dd>{candidato[nombre] || 'No registrado'}</dd></div>)}
            <div className="detail-item"><dt>Origen</dt><dd><span className={`status-badge ${String(candidato.origen).toLowerCase()==='interno'?'interno':'externo'}`}>{candidato.origen || 'No registrado'}</span></dd></div>
          </dl>
          <div className="form-actions"><button type="button" className="btn btn-secondary" onClick={()=>{setError('');setMensaje('');setErrores({});setEditando(true);}}><Icono nombre="editar" size={16}/>Editar candidato</button><Link className="btn btn-primary" to={`/nueva-solicitud/${candidato.id}`}><Icono nombre="mas" size={16}/>Crear solicitud</Link></div>
        </> : <form onSubmit={guardarCambios} noValidate>
          <div className="form-panel-body"><div className="form-grid">
            {campos.map(({nombre,etiqueta,requerido,tipo})=><div className="field" key={nombre}><label htmlFor={`campo-${nombre}`}>{etiqueta}{requerido?' *':''}</label><input id={`campo-${nombre}`} name={nombre} type={tipo||'text'} required={Boolean(requerido)} value={formulario[nombre]} onChange={actualizarCampo} aria-invalid={Boolean(errores[nombre])} aria-describedby={errores[nombre] ? `error-${nombre}` : undefined} disabled={guardando}/>{errores[nombre] && <p id={`error-${nombre}`} className="field-error mt-1 text-sm text-red-700">{errores[nombre]}</p>}</div>)}
            <div className="field"><label htmlFor="campo-origen">Origen</label><select id="campo-origen" name="origen" value={formulario.origen} onChange={actualizarCampo} disabled={guardando}><option value="Externo">Externo</option><option value="Interno">Interno</option></select></div>
          </div></div>
          <div className="form-actions"><button type="button" className="btn btn-secondary" disabled={guardando} onClick={cancelarEdicion}>Cancelar</button><button type="submit" className="btn btn-primary" disabled={guardando}><Icono nombre="check" size={16}/>{guardando ? 'Guardando...' : 'Guardar cambios'}</button></div>
        </form>}
      </section>}
  </div>;
}
