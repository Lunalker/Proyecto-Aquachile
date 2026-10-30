import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../services/api';

export default function NuevaSolicitud() {
  const {idCandidato} = useParams();
  const navigate = useNavigate();
  const [solicitud,setSolicitud] = useState({cargo:'',familiaCargo:'',profesionalResponsable:'',observaciones:''});
  const [error,setError]=useState('');
  const [guardando,setGuardando]=useState(false);
  const handleChange = e => setSolicitud(prev=>({...prev,[e.target.name]:e.target.value}));
  async function handleSubmit(e) {
    e.preventDefault();
    if (guardando) return;
    setGuardando(true);setError('');
    try {
      await api.post('/solicitudes',{...solicitud,candidato:{id:Number(idCandidato)}});
      navigate('/solicitudes',{replace:true});
    } catch(err) {
      setError(err.response?.status===403?'No tienes permisos para crear solicitudes.':'No se pudo crear la solicitud. Revisa los datos y la conexión.');
    } finally {setGuardando(false);}
  }
  const campos=[{name:'cargo',label:'Cargo a Evaluar'},{name:'familiaCargo',label:'Familia de Cargo'},{name:'profesionalResponsable',label:'Profesional Responsable (Evaluador)'}];
  return <div className="p-6 max-w-2xl mx-auto">
    <h2 className="text-2xl font-bold mb-6 text-gray-800">Crear Solicitud para Candidato #{idCandidato}</h2>
    {error&&<p role="alert" className="mb-4 bg-red-50 p-3 text-red-800 rounded">{error}</p>}
    <form onSubmit={handleSubmit} className="bg-white p-6 rounded-lg shadow-md border grid grid-cols-1 gap-4">
      {campos.map(c=><div key={c.name}>
        <label htmlFor={c.name} className="block text-gray-700 font-medium mb-1">{c.label}</label>
        <input id={c.name} name={c.name} value={solicitud[c.name]} onChange={handleChange} required disabled={guardando} className="w-full border border-gray-300 rounded p-2 focus:outline-none focus:border-blue-500" />
      </div>)}
      <div><label htmlFor="observaciones" className="block text-gray-700 font-medium mb-1">Observaciones iniciales</label>
        <textarea id="observaciones" name="observaciones" value={solicitud.observaciones} onChange={handleChange} disabled={guardando} rows="3" className="w-full border border-gray-300 rounded p-2" />
      </div>
      <div className="mt-4 flex justify-between">
        <button type="button" onClick={()=>navigate('/')} className="text-gray-500 hover:text-gray-800 font-medium">Cancelar</button>
        <button type="submit" disabled={guardando} className="bg-blue-600 text-white font-bold py-2 px-6 rounded shadow hover:bg-blue-700 disabled:opacity-60">{guardando?'Guardando...':'Guardar Solicitud'}</button>
      </div>
    </form>
  </div>;
}
